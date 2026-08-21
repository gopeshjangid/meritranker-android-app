package com.example.meritrankerstudent.ui.doubt

import android.content.Context
import org.json.JSONObject
import java.util.Random

data class TutorSuggestion(
    val id: String,
    val subject: String,
    val subjectHi: String,
    val type: String,
    val title: String,
    val titleHi: String,
    val text: String,
    val textHi: String
)

object TutorSuggestionSelector {

    private var cachedSuggestions: List<TutorSuggestion>? = null
    private var lastSelectedIds: Set<String> = emptySet()
    private val random = Random()

    fun loadSuggestions(context: Context): List<TutorSuggestion> {
        cachedSuggestions?.let { return it }
        return try {
            val jsonString = context.assets.open("smart_tutor_suggestions.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val array = root.getJSONArray("suggestions")
            val list = mutableListOf<TutorSuggestion>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TutorSuggestion(
                        id = obj.getString("id"),
                        subject = obj.getString("subject"),
                        subjectHi = obj.optString("subjectHi", obj.getString("subject")),
                        type = obj.optString("type", "doubt"),
                        title = obj.getString("title"),
                        titleHi = obj.optString("titleHi", obj.getString("title")),
                        text = obj.getString("text"),
                        textHi = obj.optString("textHi", obj.getString("text"))
                    )
                )
            }
            cachedSuggestions = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Selects 3 diverse suggestions across subjects, minimizing immediate repetition.
     */
    fun selectSuggestions(
        context: Context,
        count: Int = 3
    ): List<TutorSuggestion> {
        val all = loadSuggestions(context)
        if (all.isEmpty()) return emptyList()
        if (all.size <= count) return all

        // Filter out items used in the immediately preceding selection if possible
        val candidatePool = if (all.size - lastSelectedIds.size >= count) {
            all.filterNot { lastSelectedIds.contains(it.id) }
        } else {
            all
        }

        // Group by subject to guarantee subject diversity
        val bySubject = candidatePool.groupBy { it.subject }.toMutableMap()
        val selected = mutableListOf<TutorSuggestion>()

        val subjects = bySubject.keys.shuffled(random)
        for (sub in subjects) {
            if (selected.size >= count) break
            val itemsInSub = bySubject[sub] ?: continue
            val chosen = itemsInSub[random.nextInt(itemsInSub.size)]
            selected.add(chosen)
        }

        // Fallback if less than target count selected
        if (selected.size < count) {
            val remaining = candidatePool.filterNot { selected.any { s -> s.id == it.id } }.shuffled(random)
            selected.addAll(remaining.take(count - selected.size))
        }

        lastSelectedIds = selected.map { it.id }.toSet()
        return selected
    }
}
