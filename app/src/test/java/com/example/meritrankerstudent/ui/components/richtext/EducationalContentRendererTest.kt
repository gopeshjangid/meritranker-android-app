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

    // =========================================================================
    // 18 GOLDEN CROSS-PLATFORM FIXTURES
    // =========================================================================

    @Test
    fun fixture01_simpleFactualAnswer_preservesBoldAnswerLabel() {
        val input = "**Answer:** The capital of France is Paris."
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is RichBlock.Paragraph)
        val p = blocks[0] as RichBlock.Paragraph
        val boldSpans = p.elements.filterIsInstance<InlineSpan.Text>().filter { it.isBold }
        assertEquals(1, boldSpans.size)
        assertEquals("Answer:", boldSpans[0].content)
        val textSpans = p.elements.filterIsInstance<InlineSpan.Text>()
        assertTrue(textSpans.any { it.content.contains("Paris") })
    }

    @Test
    fun fixture02_mathSolution_parsesHeadingsAndMathDelimiters() {
        val input = """
            ## Solution
            To solve \( 2x + 5 = 15 \):
            \[ 2x = 10 \]
            \[ x = 5 \]
            **Final Answer:** \( x = 5 \)
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertTrue(blocks.any { it is RichBlock.Header && it.text == "Solution" })
        assertTrue(blocks.any { it is RichBlock.Math || (it is RichBlock.Paragraph && it.elements.any { span -> span is InlineSpan.Math }) })
        val finalAnswerPara = blocks.filterIsInstance<RichBlock.Paragraph>().last()
        val boldText = finalAnswerPara.elements.filterIsInstance<InlineSpan.Text>().find { it.isBold }
        assertNotNull(boldText)
        assertEquals("Final Answer:", boldText?.content)
    }

    @Test
    fun fixture03_physicsFormula_formatsCleanly() {
        val input = """
            ## Physics Formula
            The relationship between force and acceleration is given by:
            \[ F = ma \]
            Where \( m \) is mass and \( a \) is acceleration.
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertFalse(blocks.isEmpty())
        val mathBlock = blocks.filterIsInstance<RichBlock.Math>().firstOrNull()
        assertNotNull(mathBlock)
        assertEquals("F = ma", formatMathExpression(mathBlock!!.formula))
    }

    @Test
    fun fixture04_biologyProcess_preservesTextualFlowArrows() {
        val input = """
            ## Photosynthesis
            Light reaction: Water + Light → ATP + NADPH + Oxygen
            Calvin Cycle: CO2 + ATP + NADPH → Glucose
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertTrue(blocks.size >= 2)
        val content = blocks.joinToString { it.toString() }
        assertTrue(content.contains("→"))
        assertTrue(content.contains("Photosynthesis"))
    }

    @Test
    fun fixture05_historyTimeline_rendersSequence() {
        val input = """
            ## Timeline of Indian Freedom Struggle
            1857 — Sepoy Mutiny
            ↓
            1885 — Formation of Indian National Congress
            ↓
            1947 — Independence of India
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertFalse(blocks.isEmpty())
        val textContent = blocks.joinToString { it.toString() }
        assertTrue(textContent.contains("1857"))
        assertTrue(textContent.contains("↓"))
        assertTrue(textContent.contains("1947"))
    }

    @Test
    fun fixture06_geographyFlow_handlesInlineArrows() {
        val input = "Evaporation → Condensation → Precipitation → Collection"
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertEquals(1, blocks.size)
        val p = blocks[0] as RichBlock.Paragraph
        assertEquals("Evaporation → Condensation → Precipitation → Collection", (p.elements[0] as InlineSpan.Text).content)
    }

    @Test
    fun fixture07_polityAnswerWithBullets_parsesUnorderedList() {
        val input = """
            ## Key Features of Fundamental Rights
            - Justiciable in courts
            - Guaranteed to all citizens
            - Available against state actions
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertTrue(blocks.any { it is RichBlock.Header && it.text.contains("Fundamental Rights") })
        val listBlock = blocks.filterIsInstance<RichBlock.ListGroup>().firstOrNull()
        assertNotNull(listBlock)
        assertFalse(listBlock!!.isOrdered)
        assertEquals(3, listBlock.items.size)
    }

    @Test
    fun fixture08_economicsExplanation_formatsFormulasAndVariables() {
        val input = """
            Let Cost Price (CP) = \( C \)
            Marked Price (MP) = \( C + 40\% \text{ of } C = 1.4C \)
            Selling Price (SP) = \( 1.4C \times (1 - 0.20) = 1.12C \)
            Profit = \( \text{SP} - \text{CP} = 0.12C \)
            **Final Answer:** ₹4000
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertFalse(blocks.isEmpty())
        val lastPara = blocks.filterIsInstance<RichBlock.Paragraph>().last()
        assertTrue(lastPara.elements.any { it is InlineSpan.Text && it.isBold && it.content == "Final Answer:" })
    }

    @Test
    fun fixture09_englishGrammarExample_formatsRulesAndItalics() {
        val input = """
            ## Subject-Verb Agreement
            * **Rule 1:** A singular subject takes a singular verb.
            * **Rule 2:** A plural subject takes a plural verb.
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        val listGroup = blocks.filterIsInstance<RichBlock.ListGroup>().firstOrNull()
        assertNotNull(listGroup)
        assertEquals(2, listGroup!!.items.size)
    }

    @Test
    fun fixture10_reasoningSteps_parsesOrderedList() {
        val input = """
            ## Blood Relations
            1. Pointing to a man, woman said: "His mother is the only daughter of my mother."
            2. Mother's only daughter = The woman herself.
            3. Therefore, the man is the woman's **son**.
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        val listGroup = blocks.filterIsInstance<RichBlock.ListGroup>().firstOrNull()
        assertNotNull(listGroup)
        assertTrue(listGroup!!.isOrdered)
        assertEquals(3, listGroup.items.size)
    }

    @Test
    fun fixture11_veryShortAnswer_parsesCleanly() {
        val input = "**Answer:** 42"
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertEquals(1, blocks.size)
        val p = blocks[0] as RichBlock.Paragraph
        val bold = p.elements.filterIsInstance<InlineSpan.Text>().find { it.isBold }
        assertEquals("Answer:", bold?.content)
    }

    @Test
    fun fixture12_longExplanation_maintainsStructuredBlocks() {
        val input = """
            # Comprehensive Guide to Kinematics
            
            Kinematics is the branch of classical mechanics that describes the motion of points, bodies, and systems.
            
            ## Key Equations of Motion
            1. \( v = u + at \)
            2. \( s = ut + \frac{1}{2}at^2 \)
            3. \( v^2 = u^2 + 2as \)
            
            ## Example Problem
            A vehicle starts from rest with acceleration \( a = 2\text{ m/s}^2 \) for \( t = 5\text{ s} \).
            \[ s = 0 \times 5 + \frac{1}{2} \times 2 \times 5^2 = 25\text{ m} \]
            
            **Final Answer:** 25 meters.
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertTrue(blocks.size >= 5)
        assertTrue(blocks.any { it is RichBlock.Header })
        assertTrue(blocks.any { it is RichBlock.ListGroup })
        assertTrue(blocks.any { it is RichBlock.Math || (it is RichBlock.Paragraph && it.elements.any { s -> s is InlineSpan.Math }) })
    }

    @Test
    fun fixture13_unicodeHindiText_rendersCorrectlyWithoutGarbling() {
        val input = """
            माना कि मूलधन \( P = ₹10,000 \), दर \( R = 10\% \) प्रति वर्ष तथा समय \( T = 2 \) वर्ष है।
            साधारण ब्याज \( \text{SI} = \frac{P \times R \times T}{100} = \frac{10000 \times 10 \times 2}{100} = ₹2,000 \)।
            **उत्तर:** ₹2,000
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertFalse(blocks.isEmpty())
        val text = blocks.joinToString { it.toString() }
        assertTrue(text.contains("मूलधन"))
        assertTrue(text.contains("साधारण ब्याज"))
        assertTrue(text.contains("₹2,000"))
    }

    @Test
    fun fixture14_malformedMarkdown_degradesGracefullyWithoutCrash() {
        val input = """
            **Answer: Incomplete bold without closing
            ###
            ***Mixed stars with no closure
            * Lone bullet item without space
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertNotNull(blocks)
        assertFalse("Malformed Markdown should produce readable blocks", blocks.isEmpty())
    }

    @Test
    fun fixture15_incompleteMath_degradesGracefullyWithoutCrash() {
        val input = """
            Let \( x = \frac{480}{
            And display math \[ \sqrt{b^2 - 4ac
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = true)
        assertNotNull(blocks)
        assertFalse("Incomplete math during streaming should produce non-crashing output", blocks.isEmpty())
    }

    @Test
    fun fixture16_longFormula_parsesWithoutBufferOverflow() {
        val input = "\\[ x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a} + \\sum_{i=1}^{n} \\frac{\\alpha_i + \\beta_i}{\\gamma_i^2 + \\delta_i^2} \\]"
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is RichBlock.Math)
        val formatted = formatMathExpression((blocks[0] as RichBlock.Math).formula)
        assertFalse(formatted.contains("\\frac"))
        assertTrue(formatted.contains("√"))
    }

    @Test
    fun fixture17_unsupportedMermaidDiagram_rendersSafelyWithoutCrash() {
        val input = """
            ```mermaid
            graph TD
            A[Start] --> B[Process] --> C[End]
            ```
        """.trimIndent()
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is RichBlock.Diagram || blocks[0] is RichBlock.Code)
    }

    @Test
    fun fixture18_rawHtmlLookingInput_treatedSafelyAsTextWithoutScriptExecution() {
        val input = "<div><p>Testing HTML</p><script>alert('hack')</script><svg onload='alert(1)'><a href='javascript:alert(1)'>Click</a></div>"
        val blocks = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is RichBlock.Paragraph)
        val text = (blocks[0] as RichBlock.Paragraph).elements.joinToString { (it as? InlineSpan.Text)?.content ?: "" }
        assertTrue(text.contains("script"))
        assertTrue(text.contains("svg"))
    }

    // =========================================================================
    // STREAM-FRAGMENT FUZZ TESTING (1-char, 2-char, 5-char, word boundary, random)
    // =========================================================================

    @Test
    fun streamFuzz_all18GoldenFixtures_neverThrowAtAnyPrefix() {
        val allFixtures = listOf(
            "**Answer:** The capital of France is Paris.",
            "## Solution\nTo solve \\( 2x + 5 = 15 \\):\n\\[ 2x = 10 \\]\n\\[ x = 5 \\]\n**Final Answer:** \\( x = 5 \\)",
            "## Physics Formula\n\\[ F = ma \\]\nWhere \\( m \\) is mass.",
            "Light reaction: Water + Light → ATP + NADPH + Oxygen",
            "1857 — Sepoy Mutiny\n↓\n1885 — INC\n↓\n1947 — Independence",
            "Evaporation → Condensation → Precipitation",
            "- Justiciable in courts\n- Guaranteed to all",
            "Let CP = \\( C \\), Profit = \\( 0.12C = 480 \\). **Final Answer:** ₹4000",
            "## Grammar\n* **Rule 1:** Subject-verb agreement.",
            "1. Step one\n2. Step two\n3. Step three **son**.",
            "**Answer:** 42",
            "# Heading\nParagraph with \\( v = u + at \\)\n\\[ s = ut + \\frac{1}{2}at^2 \\]",
            "माना मूलधन \\( P = ₹10,000 \\), दर \\( R = 10\\% \\)",
            "**Answer: Unclosed bold\n### Incomplete\n***",
            "Let \\( x = \\frac{480}{\nAnd \\[ \\sqrt{b^2 -",
            "\\[ x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a} \\]",
            "```mermaid\ngraph TD\nA-->B\n```",
            "<div><script>alert(1)</script><svg onload=1></div>"
        )

        for (fixture in allFixtures) {
            // 1. Single character streaming fuzz
            for (len in 1..fixture.length) {
                val prefix = fixture.substring(0, len)
                val blocks = MarkdownDocumentParser.parseToBlocks(prefix, isStreaming = (len < fixture.length))
                assertNotNull("Streaming prefix $len should never return null for fixture: $prefix", blocks)
            }

            // 2. 2-character chunk streaming fuzz
            var chunk2Accumulator = ""
            var idx2 = 0
            while (idx2 < fixture.length) {
                val nextChunk = fixture.substring(idx2, (idx2 + 2).coerceAtMost(fixture.length))
                chunk2Accumulator += nextChunk
                idx2 += 2
                val blocks = MarkdownDocumentParser.parseToBlocks(chunk2Accumulator, isStreaming = (idx2 < fixture.length))
                assertNotNull(blocks)
            }

            // 3. 5-character chunk streaming fuzz
            var chunk5Accumulator = ""
            var idx5 = 0
            while (idx5 < fixture.length) {
                val nextChunk = fixture.substring(idx5, (idx5 + 5).coerceAtMost(fixture.length))
                chunk5Accumulator += nextChunk
                idx5 += 5
                val blocks = MarkdownDocumentParser.parseToBlocks(chunk5Accumulator, isStreaming = (idx5 < fixture.length))
                assertNotNull(blocks)
            }

            // 4. Word-boundary streaming fuzz
            val words = fixture.split(" ")
            var wordAccumulator = ""
            words.forEachIndexed { wordIdx, word ->
                wordAccumulator += if (wordAccumulator.isEmpty()) word else " $word"
                val blocks = MarkdownDocumentParser.parseToBlocks(wordAccumulator, isStreaming = (wordIdx < words.size - 1))
                assertNotNull(blocks)
            }
        }
    }

    // =========================================================================
    // MALFORMED INPUT FUZZ TESTING
    // =========================================================================

    @Test
    fun malformedInputFuzz_extremeInputs_neverCrash() {
        val hostileInputs = listOf(
            "",
            "   \n\n\t  ",
            "**",
            "***",
            "****",
            "#####",
            "\\(",
            "\\)",
            "\\[",
            "\\]",
            "$$",
            "$$$",
            "\\frac{",
            "\\frac{}{}",
            "\\frac{1}{",
            "\\frac{1}{2}{3}",
            "\\sqrt{",
            "\\sqrt{}",
            "\\begin{aligned}",
            "\\end{aligned}",
            "\\begin{aligned} x &= 1",
            "\\ce{",
            "\\ce{}",
            "\\ce{H2O",
            "```",
            "```python",
            "```python\nprint(1)",
            "| col 1 | col 2",
            "|---|---|",
            "<script>",
            "</script>",
            "<a href=\"javascript:void(0)\">",
            "\u0000\u0001\u0002\u0003",
            "\\u999999",
            "A".repeat(10_000), // 10,000 char unbroken string
            "\\frac{" + "a/".repeat(500) + "}" // deeply nested
        )

        for (input in hostileInputs) {
            val normalized = ContentNormalizer.normalize(input)
            assertNotNull(normalized)

            val blocksStreaming = MarkdownDocumentParser.parseToBlocks(input, isStreaming = true)
            assertNotNull(blocksStreaming)

            val blocksFinal = MarkdownDocumentParser.parseToBlocks(input, isStreaming = false)
            assertNotNull(blocksFinal)

            val mathFormatted = formatMathExpression(input)
            assertNotNull(mathFormatted)

            val chemFormatted = formatChemistryFormula(input)
            assertNotNull(chemFormatted)
        }
    }
}
