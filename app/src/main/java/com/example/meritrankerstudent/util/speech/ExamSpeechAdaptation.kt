package com.example.meritrankerstudent.util.speech

/**
 * Domain Speech Adaptation for Government Exam Preparation.
 * 
 * Provides bounded phrase sets to Google Cloud STT V2 Chirp 3 for biasing
 * recognition toward exam terminology without deterministic replacement rules.
 */
object ExamSpeechAdaptation {

    private val CORE_EXAM_PHRASES = listOf(
        "SSC CGL", "SSC CHSL", "RRB NTPC", "SBI PO", "IBPS PO", "UPSC", "NDA", "CDS",
        "Tier 1", "Tier 2", "Cutoff", "Previous Year Question", "Mock Test", "Answer Key"
    )

    private val QUANT_PHRASES = listOf(
        "Compound Interest", "Simple Interest", "Profit and Loss", "Cost Price", "Selling Price",
        "Marked Price", "Discount Percentage", "Ratio and Proportion", "Time and Work",
        "Time Speed and Distance", "Pipe and Cistern", "Quadratic Equation", "Pythagoras Theorem",
        "Trigonometry", "Mensuration", "Permutation and Combination", "Probability", "Percentage",
        "चक्रवृद्धि ब्याज", "साधारण ब्याज", "लाभ और हानि", "क्रय मूल्य", "विक्रय मूल्य",
        "अंकित मूल्य", "प्रतिशत", "अनुपात और समानुपात", "समय और कार्य", "चाल समय दूरी",
        "द्विघात समीकरण", "पाइथागोरस प्रमेय", "त्रिकोणमिति", "क्षेत्रमिति", "प्रायिकता"
    )

    private val REASONING_PHRASES = listOf(
        "Syllogism", "Venn Diagram", "Blood Relation", "Seating Arrangement", "Circular Arrangement",
        "Direction Sense", "Coding Decoding", "Number Series", "Alphabet Series", "Statement and Conclusion",
        "Statement and Assumption", "Assertion and Reason", "Mirror Image", "Water Image", "Paper Folding",
        "न्याय वाक्य", "वेन आरेख", "रक्त संबंध", "बैठक व्यवस्था", "दिशा परीक्षण",
        "कोडिंग डिकोडिंग", "संख्या श्रृंखला", "कथन और निष्कर्ष", "कथन और पूर्वधारणा"
    )

    private val POLITY_AND_GK_PHRASES = listOf(
        "Article 14", "Article 19", "Article 21", "Article 32", "Article 370", "Fundamental Rights",
        "Directive Principles", "Preamble", "Supreme Court", "High Court", "Election Commission",
        "Lok Sabha", "Rajya Sabha", "President of India", "Governor", "Panchayati Raj",
        "अनुच्छेद 14", "अनुच्छेद 19", "अनुच्छेद 21", "अनुच्छेद 32", "मौलिक अधिकार",
        "नीति निर्देशक तत्व", "प्रस्तावना", "सर्वोच्च न्यायालय", "उच्च न्यायालय", "चुनाव आयोग",
        "लोकसभा", "राज्यसभा", "राष्ट्रपति", "राज्यपाल", "पंचायती राज"
    )

    /**
     * Builds a bounded phrase set (max 50 terms) based on the user's active subject/exam.
     */
    fun getPhrasesForContext(subject: String?, examCategory: String?): List<String> {
        val phrases = mutableListOf<String>()
        phrases.addAll(CORE_EXAM_PHRASES)

        val subLower = subject?.lowercase() ?: ""
        if (subLower.contains("quant") || subLower.contains("math") || subLower.contains("गणित")) {
            phrases.addAll(QUANT_PHRASES)
        } else if (subLower.contains("reasoning") || subLower.contains("logic") || subLower.contains("तर्क")) {
            phrases.addAll(REASONING_PHRASES)
        } else if (subLower.contains("polity") || subLower.contains("gk") || subLower.contains("gs") || subLower.contains("संविधान")) {
            phrases.addAll(POLITY_AND_GK_PHRASES)
        } else {
            // General exam prep mix (top representative terms)
            phrases.addAll(QUANT_PHRASES.take(12))
            phrases.addAll(REASONING_PHRASES.take(12))
            phrases.addAll(POLITY_AND_GK_PHRASES.take(12))
        }

        return phrases.distinct().take(60)
    }
}
