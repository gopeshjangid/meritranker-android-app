package com.example.meritrankerstudent.data.repository

import com.amplifyframework.api.aws.GsonVariablesSerializer
import com.amplifyframework.api.graphql.SimpleGraphQLRequest
import com.amplifyframework.core.Amplify
import com.example.meritrankerstudent.MeritRankerApplication
import com.example.meritrankerstudent.data.local.AppDatabase
import com.example.meritrankerstudent.data.local.ConversationSessionEntity
import com.example.meritrankerstudent.data.local.ConversationTurnEntity
import com.example.meritrankerstudent.data.local.FreshnessPolicy
import com.example.meritrankerstudent.data.local.FreshnessStatus
import com.example.meritrankerstudent.data.local.SyncMetadataEntity
import com.example.meritrankerstudent.data.model.ConversationSession
import com.example.meritrankerstudent.data.model.ConversationTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ConversationRepository {
    fun observeSessionsByUser(userId: String): Flow<List<ConversationSession>>
    fun observeTurnsByConversation(conversationId: String): Flow<List<ConversationTurn>>
    suspend fun fetchSessionsByUser(userId: String, forceRefresh: Boolean = false): List<ConversationSession>
    suspend fun fetchTurnsByConversation(conversationId: String, forceRefresh: Boolean = false): List<ConversationTurn>
    fun saveSessionLocally(session: ConversationSession)
    fun saveTurnLocally(turn: ConversationTurn)
}

class DefaultConversationRepository(
    private val database: AppDatabase? = MeritRankerApplication.instance?.let { AppDatabase.getInstance(it) },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ConversationRepository {

    companion object {
        val instance by lazy { DefaultConversationRepository() }
        private val cachedSessions = mutableListOf<ConversationSession>()
        private val cachedTurns = mutableMapOf<String, MutableList<ConversationTurn>>() // conversationId -> turns
    }

    override fun observeSessionsByUser(userId: String): Flow<List<ConversationSession>> {
        val dao = database?.conversationDao()
        if (dao != null) {
            return dao.getSessionsByUser(userId).map { list ->
                list.map { entityToSession(it) }
            }
        }
        return flowOf(getFilteredSessions(userId))
    }

    override fun observeTurnsByConversation(conversationId: String): Flow<List<ConversationTurn>> {
        val dao = database?.conversationDao()
        if (dao != null) {
            return dao.getTurnsByConversation(conversationId).map { list ->
                list.map { entityToTurn(it) }
            }
        }
        return flowOf(cachedTurns[conversationId] ?: emptyList())
    }

    override fun saveSessionLocally(session: ConversationSession) {
        synchronized(cachedSessions) {
            val index = cachedSessions.indexOfFirst { it.id == session.id }
            if (index >= 0) {
                cachedSessions[index] = session
            } else {
                cachedSessions.add(0, session)
            }
        }
        coroutineScope.launch {
            database?.conversationDao()?.upsertSession(
                ConversationSessionEntity(
                    id = session.id,
                    userId = session.userId,
                    title = session.title,
                    lastActivityAt = session.lastActivityAt,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun saveTurnLocally(turn: ConversationTurn) {
        synchronized(cachedTurns) {
            val list = cachedTurns.getOrPut(turn.conversationId) { mutableListOf() }
            val index = list.indexOfFirst { it.id == turn.id }
            if (index >= 0) {
                list[index] = turn
            } else {
                list.add(turn)
            }
        }
        coroutineScope.launch {
            database?.conversationDao()?.upsertTurn(
                ConversationTurnEntity(
                    id = turn.id,
                    userId = turn.userId,
                    conversationId = turn.conversationId,
                    originalQuery = turn.originalQuery,
                    finalAnswer = turn.finalAnswer,
                    examId = turn.examId,
                    language = turn.language,
                    createdAt = turn.createdAt,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun fetchSessionsByUser(userId: String, forceRefresh: Boolean): List<ConversationSession> {
        val dao = database?.conversationDao()
        val syncDao = database?.syncMetadataDao()

        val localEntities = dao?.getSessionsByUserSync(userId) ?: emptyList()
        val cached = if (localEntities.isNotEmpty()) {
            localEntities.map { entityToSession(it) }
        } else {
            getFilteredSessions(userId)
        }

        val cacheKey = "conversation_sessions_$userId"
        val syncMeta = syncDao?.getMetadata(userId, "CONVERSATION_SESSIONS", userId)
        val freshness = if (forceRefresh) FreshnessStatus.STALE_BUT_USABLE else FreshnessPolicy.evaluate(syncMeta?.lastSuccessfulFetchAt, FreshnessPolicy.CONVERSATION_SESSIONS_TTL_MS)

        if (cached.isNotEmpty() && freshness == FreshnessStatus.FRESH) {
            android.util.Log.d("MeritRankerHistory", "CACHE_HIT (FRESH) for sessions count=${cached.size}")
            return cached
        }

        if (cached.isNotEmpty() && freshness == FreshnessStatus.STALE_BUT_USABLE) {
            android.util.Log.d("MeritRankerHistory", "CACHE_HIT (STALE_BUT_USABLE) for sessions, triggering background revalidation")
            coroutineScope.launch {
                try {
                    performRemoteFetchSessions(userId)
                } catch (e: Exception) {
                    android.util.Log.w("MeritRankerHistory", "Background session fetch failed: ${e.message}")
                }
            }
            return cached
        }

        return performRemoteFetchSessions(userId)
    }

    private suspend fun performRemoteFetchSessions(userId: String): List<ConversationSession> {
        val hashedUser = userId.hashCode().toString()
        android.util.Log.d("MeritRankerHistory", "HISTORY_REMOTE_START operation=listConversationSessionsByUser userHash=$hashedUser authMode=AMAZON_COGNITO_USER_POOLS")

        val remoteList = suspendCancellableCoroutine<List<ConversationSession>> { continuation ->
            val document = """
                query ListConversationSessionsByUser(${'$'}userId: String!) {
                    listConversationSessionsByUser(userId: ${'$'}userId) {
                        items {
                            id
                            userId
                            title
                            lastActivityAt
                            createdAt
                            updatedAt
                        }
                    }
                }
            """.trimIndent()

            val request = SimpleGraphQLRequest<String>(
                document,
                mapOf("userId" to userId),
                String::class.java,
                GsonVariablesSerializer()
            )

            val startTime = System.currentTimeMillis()

            try {
                Amplify.API.query(
                    request,
                    { response ->
                        val duration = System.currentTimeMillis() - startTime
                        if (continuation.isActive) {
                            if (response.hasErrors() || response.data == null || response.data == "null") {
                                val errMessage = response.errors.firstOrNull()?.message ?: "Empty response data"
                                android.util.Log.d("MeritRankerHistory", "HISTORY_REMOTE_ERROR errorCategory=GRAPHQL_ERROR durationMs=$duration message=$errMessage")
                                continuation.resume(emptyList())
                            } else {
                                try {
                                    val json = JSONObject(response.data)
                                    val listObj = json.optJSONObject("listConversationSessionsByUser")
                                    val items = listObj?.optJSONArray("items")
                                    val parsedSessions = mutableListOf<ConversationSession>()

                                    if (items != null) {
                                        for (i in 0 until items.length()) {
                                            val item = items.getJSONObject(i)
                                            val session = parseSession(item)
                                            parsedSessions.add(session)
                                            saveSessionLocally(session)
                                        }
                                    }

                                    if (parsedSessions.isEmpty()) {
                                        android.util.Log.d("MeritRankerHistory", "HISTORY_REMOTE_EMPTY durationMs=$duration")
                                        continuation.resume(emptyList())
                                    } else {
                                        android.util.Log.d("MeritRankerHistory", "HISTORY_REMOTE_SUCCESS count=${parsedSessions.size} durationMs=$duration")
                                        continuation.resume(parsedSessions.sortedByDescending { it.lastActivityAt })
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.d("MeritRankerHistory", "HISTORY_REMOTE_ERROR errorCategory=PARSE_ERROR message=${e.localizedMessage}")
                                    continuation.resume(emptyList())
                                }
                            }
                        }
                    },
                    { error ->
                        if (continuation.isActive) {
                            android.util.Log.d("MeritRankerHistory", "HISTORY_REMOTE_ERROR errorCategory=NETWORK_ERROR message=${error.message}")
                            continuation.resume(emptyList())
                        }
                    }
                )
            } catch (e: Throwable) {
                if (continuation.isActive) {
                    android.util.Log.d("MeritRankerHistory", "HISTORY_REMOTE_ERROR errorCategory=EXCEPTION message=${e.localizedMessage}")
                    continuation.resume(emptyList())
                }
            }
        }

        if (remoteList.isNotEmpty()) {
            val entities = remoteList.map { sessionToEntity(it) }
            database?.conversationDao()?.upsertSessions(entities)
            database?.syncMetadataDao()?.upsertMetadata(
                SyncMetadataEntity(
                    ownerUserId = userId,
                    resourceType = "CONVERSATION_SESSIONS",
                    resourceScope = userId,
                    lastSuccessfulFetchAt = System.currentTimeMillis(),
                    syncState = "IDLE"
                )
            )
            return remoteList
        }

        return getFilteredSessions(userId)
    }

    override suspend fun fetchTurnsByConversation(conversationId: String, forceRefresh: Boolean): List<ConversationTurn> {
        val dao = database?.conversationDao()
        val localEntities = dao?.getTurnsByConversationSync(conversationId) ?: emptyList()
        val cached = if (localEntities.isNotEmpty()) {
            localEntities.map { entityToTurn(it) }
        } else {
            cachedTurns[conversationId] ?: emptyList()
        }

        if (cached.isNotEmpty() && !forceRefresh) {
            android.util.Log.d("MeritRankerHistory", "CACHE_HIT for turns in conversationId=$conversationId count=${cached.size}")
            return cached
        }

        return performRemoteFetchTurns(conversationId)
    }

    private suspend fun performRemoteFetchTurns(conversationId: String): List<ConversationTurn> {
        android.util.Log.d("MeritRankerHistory", "TURNS_REMOTE_START operation=listConversationTurnsByConversation conversationId=$conversationId")

        val remoteTurns = suspendCancellableCoroutine<List<ConversationTurn>> { continuation ->
            val document = """
                query ListConversationTurnsByConversation(${'$'}conversationId: String!) {
                    listConversationTurnsByConversation(conversationId: ${'$'}conversationId) {
                        items {
                            id
                            userId
                            conversationId
                            originalQuery
                            finalAnswer
                            examId
                            examStage
                            language
                            topic
                            createdAt
                            updatedAt
                        }
                    }
                }
            """.trimIndent()

            val request = SimpleGraphQLRequest<String>(
                document,
                mapOf("conversationId" to conversationId),
                String::class.java,
                GsonVariablesSerializer()
            )

            val startTime = System.currentTimeMillis()

            try {
                Amplify.API.query(
                    request,
                    { response ->
                        val duration = System.currentTimeMillis() - startTime
                        if (continuation.isActive) {
                            if (response.hasErrors() || response.data == null || response.data == "null") {
                                val errMessage = response.errors.firstOrNull()?.message ?: "Empty data"
                                android.util.Log.d("MeritRankerHistory", "TURNS_REMOTE_ERROR errorCategory=GRAPHQL_ERROR durationMs=$duration message=$errMessage")
                                continuation.resume(emptyList())
                            } else {
                                try {
                                    val json = JSONObject(response.data)
                                    val listObj = json.optJSONObject("listConversationTurnsByConversation")
                                    val items = listObj?.optJSONArray("items")
                                    val remoteList = mutableListOf<ConversationTurn>()

                                    if (items != null) {
                                        for (i in 0 until items.length()) {
                                            val item = items.getJSONObject(i)
                                            val turn = parseTurn(item)
                                            remoteList.add(turn)
                                            saveTurnLocally(turn)
                                        }
                                    }

                                    android.util.Log.d("MeritRankerHistory", "TURNS_REMOTE_SUCCESS count=${remoteList.size} durationMs=$duration")
                                    continuation.resume(remoteList.sortedBy { it.createdAt })
                                } catch (e: Exception) {
                                    android.util.Log.d("MeritRankerHistory", "TURNS_REMOTE_ERROR errorCategory=PARSE_ERROR message=${e.localizedMessage}")
                                    continuation.resume(emptyList())
                                }
                            }
                        }
                    },
                    { error ->
                        if (continuation.isActive) {
                            android.util.Log.d("MeritRankerHistory", "TURNS_REMOTE_ERROR errorCategory=NETWORK_ERROR message=${error.message}")
                            continuation.resume(emptyList())
                        }
                    }
                )
            } catch (e: Throwable) {
                if (continuation.isActive) {
                    android.util.Log.d("MeritRankerHistory", "TURNS_REMOTE_ERROR errorCategory=EXCEPTION message=${e.localizedMessage}")
                    continuation.resume(emptyList())
                }
            }
        }

        if (remoteTurns.isNotEmpty()) {
            val entities = remoteTurns.map { turnToEntity(it) }
            database?.conversationDao()?.upsertTurns(entities)
            return remoteTurns
        }

        return cachedTurns[conversationId] ?: emptyList()
    }

    private fun getFilteredSessions(userId: String): List<ConversationSession> {
        return synchronized(cachedSessions) {
            cachedSessions.filter { it.userId == userId }.sortedByDescending { it.lastActivityAt }
        }
    }

    private fun parseSession(json: JSONObject): ConversationSession {
        val lastActStr = json.optString("lastActivityAt")
        val lastActLong = try {
            if (lastActStr.isNotEmpty()) SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(lastActStr)?.time ?: System.currentTimeMillis()
            else System.currentTimeMillis()
        } catch (_: Exception) { System.currentTimeMillis() }

        return ConversationSession(
            id = json.getString("id"),
            userId = json.getString("userId"),
            title = json.optString("title", "Practice Doubt"),
            lastActivityAt = lastActLong
        )
    }

    private fun parseTurn(json: JSONObject): ConversationTurn {
        val createdStr = json.optString("createdAt")
        val createdLong = try {
            if (createdStr.isNotEmpty()) SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(createdStr)?.time ?: System.currentTimeMillis()
            else System.currentTimeMillis()
        } catch (_: Exception) { System.currentTimeMillis() }

        return ConversationTurn(
            id = json.getString("id"),
            userId = json.getString("userId"),
            conversationId = json.getString("conversationId"),
            originalQuery = json.getString("originalQuery"),
            finalAnswer = json.getString("finalAnswer"),
            examId = json.optString("examId").takeIf { it.isNotBlank() },
            examStage = json.optString("examStage").takeIf { it.isNotBlank() },
            language = json.optString("language", "english"),
            topic = json.optString("topic").takeIf { it.isNotBlank() },
            createdAt = createdLong
        )
    }

    private fun sessionToEntity(s: ConversationSession): ConversationSessionEntity {
        return ConversationSessionEntity(
            id = s.id,
            userId = s.userId,
            title = s.title,
            lastActivityAt = s.lastActivityAt,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    private fun entityToSession(e: ConversationSessionEntity): ConversationSession {
        return ConversationSession(
            id = e.id,
            userId = e.userId,
            title = e.title,
            lastActivityAt = e.lastActivityAt
        )
    }

    private fun turnToEntity(t: ConversationTurn): ConversationTurnEntity {
        return ConversationTurnEntity(
            id = t.id,
            userId = t.userId,
            conversationId = t.conversationId,
            originalQuery = t.originalQuery,
            finalAnswer = t.finalAnswer,
            examId = t.examId,
            language = t.language,
            createdAt = t.createdAt,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    private fun entityToTurn(e: ConversationTurnEntity): ConversationTurn {
        return ConversationTurn(
            id = e.id,
            userId = e.userId,
            conversationId = e.conversationId,
            originalQuery = e.originalQuery,
            finalAnswer = e.finalAnswer,
            examId = e.examId,
            language = e.language,
            createdAt = e.createdAt
        )
    }
}
