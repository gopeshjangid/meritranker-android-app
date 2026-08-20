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

        assertTrue(formatted.contains("(-b ± √(b² - 4ac)) / (2a)"))
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

        assertEquals("(45) / (60) × 100 = 75%", formatted)
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
    fun inlineMathAndText_parsesSpansCorrectly() {
        val input = "Solve \$x^2 - 5x + 6 = 0\$ using quadratic formula."
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)

        assertEquals(1, blocks.size)
        assertTrue(blocks.first() is RichBlock.Paragraph)

        val paragraph = blocks.first() as RichBlock.Paragraph
        assertEquals(3, paragraph.elements.size)
        assertTrue(paragraph.elements[0] is InlineSpan.Text)
        assertTrue(paragraph.elements[1] is InlineSpan.Math)
        assertTrue(paragraph.elements[2] is InlineSpan.Text)

        val mathSpan = paragraph.elements[1] as InlineSpan.Math
        assertEquals("x^2 - 5x + 6 = 0", mathSpan.formula)
    }
}
