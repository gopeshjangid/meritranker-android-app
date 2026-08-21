package com.example.meritrankerstudent.ui.doubt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorSuggestionSelectorTest {

    @Test
    fun testSuggestionDataModel() {
        val suggestion = TutorSuggestion(
            id = "quant_01",
            subject = "Quantitative Aptitude",
            subjectHi = "मात्रात्मक योग्यता",
            type = "doubt",
            title = "Profit & Loss Calculation",
            titleHi = "लाभ व हानि का प्रश्न",
            text = "A shopkeeper marks goods 40% above cost price...",
            textHi = "एक दुकानदार वस्तु का मूल्य..."
        )

        assertEquals("quant_01", suggestion.id)
        assertEquals("Quantitative Aptitude", suggestion.subject)
        assertEquals("doubt", suggestion.type)
        assertNotNull(suggestion.text)
        assertTrue(suggestion.titleHi.isNotEmpty())
    }
}
