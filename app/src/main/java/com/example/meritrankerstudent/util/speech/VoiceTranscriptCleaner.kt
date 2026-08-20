package com.example.meritrankerstudent.util.speech

import java.util.Locale

/**
 * Safe, Low-Risk On-Device Transcript Normalizer for MeritRanker.
 *
 * Adheres strictly to Student Speech Fidelity:
 * - NO automatic translation between languages.
 * - NO artificial script conversion (English stays English, Hindi stays Devanagari, mixed stays mixed).
 * - NO aggressive speculative phonetic substitution.
 * - Clean whitespace and immediate stutter repetitions.
 * - Gentle punctuation fallback if not already provided by recognizer.
 * - Safe N-best candidate selection based on confidence and complete utterances.
 */
object VoiceTranscriptCleaner {

    // Common acoustic noise artifacts and filler words (leading/trailing only)
    private val FILLER_WORDS = hashSetOf(
        "um", "umm", "uh", "uhh", "uhm", "er", "err", "ah", "ahh", "hmm", "hmmm",
        "मतलब", "हम्म", "उम"
    )

    // Common direct interrogative question openers
    private val DIRECT_QUESTION_WORDS = listOf(
        "what", "why", "how", "when", "where", "which", "who", "whom", "whose",
        "is it", "can you", "could you", "should i",
        "kya", "kaise", "kyon", "kyu", "kyun", "kab", "kahan", "kaun",
        "क्या", "कैसे", "क्यों", "कब", "कहाँ", "कौन"
    )

    /**
     * Selects the best candidate from recognizer hypotheses based on confidence score
     * and complete utterance length, without altering script or language.
     */
    fun selectBestCandidate(
        candidates: List<String>?,
        confidenceScores: FloatArray? = null,
        targetLanguage: String = "en"
    ): String {
        if (candidates.isNullOrEmpty()) return ""

        var bestCandidate = candidates.first()
        var highestScore = Float.NEGATIVE_INFINITY

        candidates.forEachIndexed { index, candidate ->
            if (candidate.isBlank()) return@forEachIndexed

            var score = 0f
            val rawConfidence = confidenceScores?.getOrNull(index) ?: 0.5f
            score += rawConfidence * 10f

            val words = candidate.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }

            // Prefer natural full phrase over 1-syllable fragments
            if (words.size >= 2) {
                score += 4f
            } else if (words.size == 1 && words.first().length == 1) {
                score -= 8f // Penalize single isolated noise character (e.g. "c", "p")
            }

            if (score > highestScore) {
                highestScore = score
                bestCandidate = candidate
            }
        }

        return cleanTranscript(bestCandidate, targetLanguage)
    }

    /**
     * Cleans and normalizes transcript preserving exact student speech fidelity.
     */
    fun cleanTranscript(rawTranscript: String?, targetLanguage: String = "en"): String {
        if (rawTranscript.isNullOrBlank()) return ""

        var text = rawTranscript.trim()

        // 1. Remove non-printable / mic noise symbols
        text = text.replace(Regex("""[*_~`#]"""), " ")

        // 2. Tokenize and remove consecutive duplicate/stutter tokens (e.g. "what what" -> "what")
        val words = text.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""

        val deduplicatedWords = mutableListOf<String>()
        var previousWordLower = ""

        for (w in words) {
            val currentLower = w.lowercase(Locale.ROOT).replace(Regex("""[^\w\u0900-\u097F]"""), "")
            // Deduplicate immediately repeated tokens (stutter)
            if (currentLower.isNotBlank() && currentLower == previousWordLower) {
                continue
            }
            deduplicatedWords.add(w)
            previousWordLower = currentLower
        }

        // 3. Remove pure leading and trailing filler words
        var startIndex = 0
        while (startIndex < deduplicatedWords.size) {
            val cleanWord = deduplicatedWords[startIndex].lowercase(Locale.ROOT).replace(Regex("""[^\w\u0900-\u097F]"""), "")
            if (FILLER_WORDS.contains(cleanWord)) {
                startIndex++
            } else {
                break
            }
        }

        var endIndex = deduplicatedWords.size - 1
        while (endIndex >= startIndex) {
            val cleanWord = deduplicatedWords[endIndex].lowercase(Locale.ROOT).replace(Regex("""[^\w\u0900-\u097F]"""), "")
            if (FILLER_WORDS.contains(cleanWord)) {
                endIndex--
            } else {
                break
            }
        }

        if (startIndex > endIndex) return ""

        val cleanedWords = deduplicatedWords.subList(startIndex, endIndex + 1)
        var result = cleanedWords.joinToString(" ").trim()

        // 4. Normalize multi-spaces
        result = result.replace(Regex("""\s{2,}"""), " ")

        // 5. Intelligent fallback question mark punctuation (if not already ending in punctuation)
        val lowerResult = result.lowercase(Locale.ROOT)
        val isDirectQuestion = DIRECT_QUESTION_WORDS.any { prefix ->
            lowerResult.startsWith(prefix) ||
                    lowerResult.startsWith("$prefix ") ||
                    lowerResult.contains(" $prefix ") ||
                    lowerResult.endsWith(" $prefix") ||
                    result.startsWith(prefix) ||
                    result.contains(" $prefix ")
        }

        if (isDirectQuestion && !result.endsWith("?") && !result.endsWith(".") && !result.endsWith("!") && !result.endsWith("।")) {
            result = "$result?"
        }

        return result
    }

    /**
     * Light preview cleanup for real-time partial feedback during speech.
     */
    fun cleanPreview(partialText: String?, targetLanguage: String = "en"): String {
        if (partialText.isNullOrBlank()) return ""
        val text = partialText.trim().replace(Regex("""\s{2,}"""), " ")
        if (text.isNotEmpty() && text[0].isLowerCase()) {
            return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        return text
    }
}
