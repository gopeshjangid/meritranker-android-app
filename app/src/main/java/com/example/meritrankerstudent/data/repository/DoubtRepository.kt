package com.example.meritrankerstudent.data.repository

import com.example.meritrankerstudent.data.model.ConversationSession
import com.example.meritrankerstudent.data.model.ConversationTurn
import com.example.meritrankerstudent.data.model.DoubtMessage
import com.example.meritrankerstudent.data.model.DoubtRequest
import com.example.meritrankerstudent.data.remote.TutorRequestPayload
import com.example.meritrankerstudent.data.remote.TutorStreamClient
import com.example.meritrankerstudent.data.remote.TutorStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

interface DoubtRepository {
    val messages: Flow<List<DoubtMessage>>
    val sessions: Flow<List<ConversationSession>>
    
    fun setActiveConversation(conversationId: String)
    suspend fun fetchUserSessions(userId: String): List<ConversationSession>
    suspend fun fetchConversationTurns(userId: String, conversationId: String): List<ConversationTurn>
    suspend fun sendDoubtQueryStream(
        request: DoubtRequest,
        authToken: String? = null,
        onChunk: (String) -> Unit
    ): DoubtMessage
    suspend fun clearHistory(userId: String)
    suspend fun reportAssistantResponse(
        messageId: String,
        conversationId: String,
        category: String,
        comment: String?,
        authToken: String? = null
    ): Result<Unit>
}

class DefaultDoubtRepository(
    private val tutorStreamClient: TutorStreamClient = TutorStreamClient(),
    private val conversationRepository: ConversationRepository = DefaultConversationRepository.instance
) : DoubtRepository {

    companion object {
        val instance by lazy { DefaultDoubtRepository() }
        private val _messages = MutableStateFlow<List<DoubtMessage>>(emptyList())
        private val _sessions = MutableStateFlow<List<ConversationSession>>(emptyList())
        private val localTurnsMap = mutableMapOf<String, MutableList<ConversationTurn>>() // conversationId -> turns
        private var activeConversationId: String? = null
    }

    override val messages: Flow<List<DoubtMessage>> = _messages.asStateFlow()
    override val sessions: Flow<List<ConversationSession>> = _sessions.asStateFlow()

    override fun setActiveConversation(conversationId: String) {
        activeConversationId = conversationId
        val turns = localTurnsMap[conversationId] ?: emptyList()
        _messages.value = turnsToMessages(turns)
        android.util.Log.d("MeritRankerChat", "ACTIVE_CONVERSATION_CHANGED activeConversationId=$conversationId turnsCount=${turns.size}")
    }

    override suspend fun fetchUserSessions(userId: String): List<ConversationSession> {
        val remoteSessions = conversationRepository.fetchSessionsByUser(userId)
        _sessions.value = remoteSessions
        return remoteSessions
    }

    override suspend fun fetchConversationTurns(userId: String, conversationId: String): List<ConversationTurn> {
        val turns = conversationRepository.fetchTurnsByConversation(conversationId)
        val localList = localTurnsMap[conversationId] ?: emptyList()
        val combinedTurns = (localList + turns).distinctBy { it.id }.sortedBy { it.createdAt }
        localTurnsMap[conversationId] = combinedTurns.toMutableList()

        if (activeConversationId == conversationId) {
            _messages.value = turnsToMessages(combinedTurns)
        }
        return combinedTurns
    }

    private fun turnsToMessages(turns: List<ConversationTurn>): List<DoubtMessage> {
        return turns.flatMap { turn ->
            listOf(
                DoubtMessage(
                    id = "user_${turn.id}",
                    sender = "USER",
                    text = turn.originalQuery,
                    timestamp = turn.createdAt,
                    imageUri = turn.imageUri
                ),
                DoubtMessage(
                    id = "ai_${turn.id}",
                    sender = "AI",
                    text = turn.finalAnswer,
                    timestamp = turn.createdAt + 10,
                    actionRoute = turn.actionRoute,
                    actionText = turn.actionText,
                    practiceTestId = turn.practiceTestId,
                    practiceTitle = if (turn.practiceTestId != null) (turn.actionText ?: "Practice Quiz") else null
                )
            )
        }
    }

    override suspend fun sendDoubtQueryStream(
        request: DoubtRequest,
        authToken: String?,
        onChunk: (String) -> Unit
    ): DoubtMessage {
        activeConversationId = request.conversationId
        val userMessageText = request.displayText?.ifBlank { null }
            ?: request.query.ifEmpty { "📷 Attached Question Image" }
        val transportQuery = request.resolvedQuery?.ifBlank { null }
            ?: request.query
        val actionType = request.actionType ?: "NORMAL"

        android.util.Log.d(
            "MeritRankerChat",
            "TUTOR_REQUEST_STARTED conversationId=${request.conversationId} turnId=${request.turnId} queryLen=${transportQuery.length} actionType=$actionType"
        )

        val payload = TutorRequestPayload(
            conversationId = request.conversationId,
            turnId = request.turnId,
            userId = request.userId,
            query = transportQuery,
            examProfileId = request.examProfileId,
            examId = request.examId,
            examStage = request.examStage,
            language = request.language,
            imageBase64 = request.imageBase64,
            imageMimeType = request.imageMimeType
        )

        val userMessage = DoubtMessage(
            id = "user_${request.turnId}",
            sender = "USER",
            text = userMessageText,
            timestamp = System.currentTimeMillis(),
            imageUri = request.imageUri,
            actionType = request.actionType
        )

        val aiPlaceholder = DoubtMessage(
            id = "ai_${request.turnId}",
            sender = "AI",
            text = "",
            timestamp = System.currentTimeMillis() + 10,
            isThinking = true,
            thinkingStatus = "Thinking…"
        )

        val currentList = _messages.value.filter { msg ->
            // Keep only messages belonging to this turn or prior messages of this conversation
            true
        }.toMutableList()

        if (!currentList.any { it.id == userMessage.id }) {
            currentList.add(userMessage)
            currentList.add(aiPlaceholder)
        }
        _messages.value = currentList

        var accumulatedAnswer = ""
        var structuredActionRoute: String? = null
        var structuredActionText: String? = null

        try {
            tutorStreamClient.streamTutorResponse(payload, authToken).collect { event ->
                if (activeConversationId != request.conversationId) {
                    android.util.Log.d("MeritRankerChat", "STREAM_CHUNK_IGNORED_STALE_REQUEST requestConvId=${request.conversationId} activeConvId=$activeConversationId")
                    return@collect
                }

                when (event) {
                    is TutorStreamEvent.Status -> {
                        val statusLabel = event.label ?: event.stage ?: "Thinking…"
                        val updatedList = _messages.value.map { msg ->
                            if (msg.id == "ai_${request.turnId}") {
                                msg.copy(isThinking = true, thinkingStatus = statusLabel)
                            } else {
                                msg
                            }
                        }
                        _messages.value = updatedList
                    }
                    is TutorStreamEvent.Chunk -> {
                        accumulatedAnswer += event.text
                        onChunk(event.text)

                        // Update live AI message bubble and remove thinking state
                        val updatedList = _messages.value.map { msg ->
                            if (msg.id == "ai_${request.turnId}") {
                                msg.copy(
                                    text = accumulatedAnswer,
                                    isThinking = false,
                                    thinkingStatus = null
                                )
                            } else {
                                msg
                            }
                        }
                        _messages.value = updatedList
                    }
                    is TutorStreamEvent.PracticeGenerationStarted -> {
                        val updatedList = _messages.value.map { msg ->
                            if (msg.id == "ai_${request.turnId}") {
                                msg.copy(
                                    isThinking = false,
                                    thinkingStatus = null,
                                    practiceTestId = event.practiceTestId,
                                    isPracticeGenerating = true,
                                    practiceTitle = event.title ?: msg.practiceTitle ?: "Practice Quiz",
                                    practiceTotalQuestions = event.questionCount ?: msg.practiceTotalQuestions,
                                    practiceStatus = "GENERATING",
                                    actionRoute = "QUIZ",
                                    actionText = "Start Practice"
                                )
                            } else {
                                msg
                            }
                        }
                        _messages.value = updatedList
                        android.util.Log.d("MeritRankerChat", "PRACTICE_GENERATION_STARTED testId=${event.practiceTestId}")
                    }
                    is TutorStreamEvent.Completed -> {
                        val finalAnswer = event.finalAnswer.ifEmpty { accumulatedAnswer }
                        accumulatedAnswer = finalAnswer
                        structuredActionRoute = event.actionRoute
                        structuredActionText = event.actionText

                        val resolvedTestId = event.practiceTestId
                        val updatedList = _messages.value.map { msg ->
                            if (msg.id == "ai_${request.turnId}") {
                                msg.copy(
                                    text = finalAnswer,
                                    isThinking = false,
                                    thinkingStatus = null,
                                    actionRoute = event.actionRoute ?: msg.actionRoute,
                                    actionText = event.actionText ?: msg.actionText,
                                    practiceTestId = resolvedTestId ?: msg.practiceTestId,
                                    practiceTitle = event.title ?: msg.practiceTitle,
                                    practiceTotalQuestions = event.questionCount ?: msg.practiceTotalQuestions
                                )
                            } else {
                                msg
                            }
                        }
                        _messages.value = updatedList
                        android.util.Log.d("MeritRankerChat", "STREAM_COMPLETED conversationId=${request.conversationId} turnId=${request.turnId}")
                    }
                    is TutorStreamEvent.Error -> {
                        if (accumulatedAnswer.isEmpty()) {
                            // Update placeholder with error status and propagate error
                            val updatedList = _messages.value.map { msg ->
                                if (msg.id == "ai_${request.turnId}") {
                                    msg.copy(
                                        isThinking = false,
                                        thinkingStatus = null,
                                        errorMessage = event.message
                                    )
                                } else {
                                    msg
                                }
                            }
                            _messages.value = updatedList
                            throw java.io.IOException(event.message)
                        } else {
                            // Keep partial streamed content if connection dropped mid-stream
                            val updatedList = _messages.value.map { msg ->
                                if (msg.id == "ai_${request.turnId}") {
                                    msg.copy(
                                        text = accumulatedAnswer,
                                        isThinking = false,
                                        thinkingStatus = null
                                    )
                                } else {
                                    msg
                                }
                            }
                            _messages.value = updatedList
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (accumulatedAnswer.isEmpty()) {
                val filteredList = _messages.value.filterNot { it.id == "ai_${request.turnId}" }
                _messages.value = filteredList
                throw e
            }
            if (activeConversationId == request.conversationId) {
                val updatedList = _messages.value.map { msg ->
                    if (msg.id == "ai_${request.turnId}") {
                        msg.copy(text = accumulatedAnswer)
                    } else {
                        msg
                    }
                }
                _messages.value = updatedList
            }
        }

        val aiMessageInList = _messages.value.firstOrNull { it.id == "ai_${request.turnId}" }
        val completedTurn = ConversationTurn(
            id = request.turnId,
            conversationId = request.conversationId,
            userId = request.userId,
            originalQuery = userMessageText,
            finalAnswer = accumulatedAnswer,
            examId = request.examId,
            language = request.language,
            createdAt = System.currentTimeMillis(),
            actionRoute = structuredActionRoute ?: aiMessageInList?.actionRoute,
            actionText = structuredActionText ?: aiMessageInList?.actionText,
            imageUri = request.imageUri,
            practiceTestId = aiMessageInList?.practiceTestId,
            responseType = if (aiMessageInList?.practiceTestId != null) "practice_generation" else null
        )

        val convList = localTurnsMap.getOrPut(request.conversationId) { mutableListOf() }
        convList.add(completedTurn)

        conversationRepository.saveTurnLocally(completedTurn)
        conversationRepository.saveSessionLocally(
            ConversationSession(
                id = request.conversationId,
                userId = request.userId,
                title = userMessageText.take(40).ifEmpty { "AI Tutor Image Doubt" },
                lastActivityAt = System.currentTimeMillis(),
                examId = request.examId,
                language = request.language
            )
        )

        fetchUserSessions(request.userId)

        return DoubtMessage(
            id = "ai_${request.turnId}",
            sender = "AI",
            text = accumulatedAnswer,
            timestamp = completedTurn.createdAt + 10,
            actionRoute = structuredActionRoute,
            actionText = structuredActionText
        )
    }

    override suspend fun clearHistory(userId: String) {
        localTurnsMap.clear()
        _messages.value = emptyList()
        _sessions.value = emptyList()
    }

    override suspend fun reportAssistantResponse(
        messageId: String,
        conversationId: String,
        category: String,
        comment: String?,
        authToken: String?
    ): Result<Unit> {
        return tutorStreamClient.submitReport(
            messageId = messageId,
            conversationId = conversationId,
            category = category,
            comment = comment,
            authToken = authToken
        )
    }
}
