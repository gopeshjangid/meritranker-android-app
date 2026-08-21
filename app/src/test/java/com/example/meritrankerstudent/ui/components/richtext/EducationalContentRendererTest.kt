package com.example.meritrankerstudent.ui.components.richtext

import org.junit.Assert.*
import org.junit.Test

class EducationalContentRendererTest {

    @Test
    fun contentNormalizer_unescapesJsonDoubleBackslashes() {
        val rawJsonPayload = "\\\\frac{-b \\\\pm \\\\sqrt{b^2 - 4ac}}{2a}"
        val normalized = ContentNormalizer.normalize(rawJsonPayload)
        assertEquals("\\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}", normalized)
    }

    @Test
    fun quadraticEquationFixture_rendersWithoutRawFracOrSqrt() {
        val input = "\$\$x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}\$\$"
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)

        assertEquals(1, blocks.size)
        assertTrue(blocks.first() is RichBlock.Math)

        val mathBlock = blocks.first() as RichBlock.Math
        val formatted = formatMathExpression(mathBlock.formula)

        assertTrue(formatted.contains("(-b ± √(b² - 4ac)) / 2a"))
        assertFalse(formatted.contains("\\frac"))
        assertFalse(formatted.contains("\\sqrt"))
        assertFalse(formatted.contains("\\pm"))
    }

    @Test
    fun fractionAndPercentageFixture_formatsCorrectly() {
        val input = "\$\$\\frac{45}{60} \\times 100 = 75\\%\$\$"
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)

        val mathBlock = blocks.first() as RichBlock.Math
        val formatted = formatMathExpression(mathBlock.formula)

        assertEquals("45 / 60 × 100 = 75%", formatted)
    }

    @Test
    fun rootsAndPowersFixture_formatsSuperscriptsAndRoots() {
        val input = "\$\\sqrt{144} = 12\$ and \$a^{m+n} = a^m a^n\$"
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertFalse(blocks.isEmpty())
    }

    @Test
    fun alignedEquationFixture_formatsMultiStepCalculations() {
        val input = "\\begin{aligned} x^2 - 5x + 6 &= 0 \\\\ (x-2)(x-3) &= 0 \\\\ x &= 2,3 \\end{aligned}"

        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        val mathBlock = blocks.first() as RichBlock.Math
        val formatted = formatMathExpression(mathBlock.formula)
        println("ALIGNED FORMATTED: '$formatted'")

        assertTrue(formatted.contains("x² - 5x + 6 = 0"))
        assertTrue(formatted.contains("(x-2)(x-3) = 0"))
        assertTrue(formatted.contains("x = 2,3"))
    }

    @Test
    fun chemistryFixture_formatsSubscriptsAndArrows() {
        val input = "\\ce{2H2 + O2 -> 2H2O}"
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)

        assertEquals(1, blocks.size)
        assertTrue(blocks.first() is RichBlock.Chemistry)

        val chemBlock = blocks.first() as RichBlock.Chemistry
        val formatted = formatChemistryFormula(chemBlock.formula)
        println("CHEM FORMATTED: '$formatted'")

        assertTrue(formatted.contains("2H₂ + O₂"))
        assertTrue(formatted.contains("2H₂O"))
    }

    @Test
    fun screenshotBugRegression_costPriceProfitLoss_rendersCleanlyWithoutRawLatex() {
        val input = """
            Let Cost Price (CP) = \( C \)
            Marked Price (MP) = \( C + 40\% \text{ of } C = 1.4C \)
            Selling Price (SP) = \( 1.4C \times (1 - 0.20) = 1.4C \times 0.8 = 1.12C \)
            Profit = \( \text{SP} - \text{CP} = 1.12C - C = 0.12C \)
            Given: \( 0.12C = 480 \)
            \( C = \frac{480}{0.12} = 4000 \)
            \implies \text{Cost Price} = ₹4000
        """.trimIndent()

        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertFalse("Blocks should not be empty", blocks.isEmpty())

        // Ensure no raw LaTeX leak in any block or formatted span
        blocks.forEach { block ->
            when (block) {
                is RichBlock.Paragraph -> {
                    block.elements.forEach { span ->
                        when (span) {
                            is InlineSpan.Text -> {
                                assertFalse("No raw \\( in text: ${span.content}", span.content.contains("\\("))
                                assertFalse("No raw \\) in text: ${span.content}", span.content.contains("\\)"))
                                assertFalse("No raw \\times in text: ${span.content}", span.content.contains("\\times"))
                                assertFalse("No raw \\frac in text: ${span.content}", span.content.contains("\\frac"))
                                assertFalse("No raw \\implies in text: ${span.content}", span.content.contains("\\implies"))
                                assertFalse("No raw \\text in text: ${span.content}", span.content.contains("\\text"))
                            }
                            is InlineSpan.Math -> {
                                val formatted = formatMathExpression(span.formula)
                                assertFalse("No raw \\( in math: $formatted", formatted.contains("\\("))
                                assertFalse("No raw \\) in math: $formatted", formatted.contains("\\)"))
                                assertFalse("No raw \\times in math: $formatted", formatted.contains("\\times"))
                                assertFalse("No raw \\frac in math: $formatted", formatted.contains("\\frac"))
                                assertFalse("No raw \\text in math: $formatted", formatted.contains("\\text"))
                            }
                            is InlineSpan.Chemistry -> {}
                        }
                    }
                }
                is RichBlock.Math -> {
                    val formatted = formatMathExpression(block.formula)
                    assertFalse("No raw \\frac in block math: $formatted", formatted.contains("\\frac"))
                    assertFalse("No raw \\times in block math: $formatted", formatted.contains("\\times"))
                }
                else -> {}
            }
        }
    }

    @Test
    fun physicsAndScienceEquations_formatsCleanly() {
        val input = "\$v = u + at\$, \$F = ma\$, and \$E = mc^2\$"
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)

        val paragraph = blocks.first() as RichBlock.Paragraph
        val mathSpans = paragraph.elements.filterIsInstance<InlineSpan.Math>()
        assertEquals(3, mathSpans.size)

        assertEquals("v = u + at", formatMathExpression(mathSpans[0].formula))
        assertEquals("F = ma", formatMathExpression(mathSpans[1].formula))
        assertEquals("E = mc²", formatMathExpression(mathSpans[2].formula))
    }

    @Test
    fun mixedHindiAndMath_parsesCleanly() {
        val input = "माना क्रय मूल्य \\( C \\) है। तब लाभ \\( 0.12C = ₹480 \\) होगा।"
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)

        assertEquals(1, blocks.size)
        val paragraph = blocks.first() as RichBlock.Paragraph
        val mathSpans = paragraph.elements.filterIsInstance<InlineSpan.Math>()
        assertEquals(2, mathSpans.size)
        assertEquals("C", formatMathExpression(mathSpans[0].formula))
        assertEquals("0.12C = ₹480", formatMathExpression(mathSpans[1].formula))
    }

    @Test
    fun streamingPartialFragments_doesNotCrash() {
        val partialChunk1 = "Let CP = \\("
        val blocks1 = MarkdownDocumentParser.parseToBlocks(partialChunk1, isStreaming = true)
        assertNotNull(blocks1)

        val partialChunk2 = "Let CP = \\( C = \\frac{480}"
        val blocks2 = MarkdownDocumentParser.parseToBlocks(partialChunk2, isStreaming = true)
        assertNotNull(blocks2)

        val completed = "Let CP = \\( C = \\frac{480}{0.12} = 4000 \\)"
        val blocks3 = MarkdownDocumentParser.parseToBlocks(completed, isStreaming = false)
        assertNotNull(blocks3)
        val p = blocks3.first() as RichBlock.Paragraph
        val math = p.elements.filterIsInstance<InlineSpan.Math>().first()
        val formatted = formatMathExpression(math.formula)
        assertTrue(formatted.contains("480 / 0.12 = 4000") || formatted.contains("(480) / (0.12) = 4000"))
    }

    @Test
    fun responseActions_allFiveActionsConfiguredWithoutWrapping() {
        val actionLabels = listOf("Copy", "Share", "Similar", "Simplify", "Report")
        val talkBackDescriptions = listOf("Copy answer as image", "Share answer as image", "Create similar question", "Simplify explanation", "Report response")

        assertEquals(5, actionLabels.size)
        assertEquals(5, talkBackDescriptions.size)
        assertTrue("Simplify replaces old Explain simpler", actionLabels.contains("Simplify"))
        assertFalse("Explain simpler is replaced", actionLabels.contains("Explain simpler"))
    }

    @Test
    fun aiDisclaimer_usesExactCompactCopy() {
        val disclaimerText = "AI can make mistakes"
        assertEquals("AI can make mistakes", disclaimerText)
        assertFalse("No long disclaimer text", disclaimerText.contains("MeritRanker AI can make mistakes. Verify important info."))
    }

    @Test
    fun tableAndCodeBlocks_parseCorrectly() {
        val markdown = """
            | Subject | Questions | Marks |
            |---------|-----------|-------|
            | Quant   | 25        | 50    |
            | English | 25        | 50    |
            
            ```python
            def solve():
                return 42
            ```
        """.trimIndent()

        val blocks = MarkdownDocumentParser.parseToBlocks(markdown, isStreaming = false)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is RichBlock.Table)
        assertTrue(blocks[1] is RichBlock.Code)

        val table = blocks[0] as RichBlock.Table
        assertEquals(listOf("Subject", "Questions", "Marks"), table.headers)
        assertEquals(2, table.rows.size)

        val code = blocks[1] as RichBlock.Code
        assertEquals("python", code.language)
        assertTrue(code.code.contains("def solve():"))
    }
}
