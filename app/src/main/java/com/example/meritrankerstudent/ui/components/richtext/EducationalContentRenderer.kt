package com.example.meritrankerstudent.ui.components.richtext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface RichBlock {
    data class Header(val level: Int, val text: String) : RichBlock
    data class Paragraph(val elements: List<InlineSpan>) : RichBlock
    data class ListGroup(val items: List<List<InlineSpan>>, val isOrdered: Boolean) : RichBlock
    data class Code(val language: String?, val code: String) : RichBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : RichBlock
    data class Math(val formula: String) : RichBlock
    data class Chemistry(val formula: String) : RichBlock
    data class Timeline(val items: List<TimelineItem>) : RichBlock
    data class Diagram(val type: String, val content: String, val steps: List<String>) : RichBlock
    data class Fallback(val rawText: String, val errorReason: String? = null) : RichBlock
}

data class TimelineItem(val dateOrYear: String, val title: String, val detail: String? = null)

sealed interface InlineSpan {
    data class Text(val content: String, val isBold: Boolean = false, val isItalic: Boolean = false) : InlineSpan
    data class Math(val formula: String) : InlineSpan
    data class Chemistry(val formula: String) : InlineSpan
}

object MarkdownDocumentParser {

    fun parseToBlocks(rawContent: String, isStreaming: Boolean): List<RichBlock> {
        val content = ContentNormalizer.normalize(rawContent)
        if (content.isBlank()) return emptyList()

        val blocks = mutableListOf<RichBlock>()
        val lines = content.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            // Fenced code, timeline, or diagram blocks
            if (line.trimStart().startsWith("```")) {
                val lang = line.trimStart().removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // skip closing fence

                if (lang.equals("timeline", ignoreCase = true)) {
                    val items = codeLines.mapNotNull { tLine ->
                        val parts = tLine.split("|").map { it.trim() }
                        if (parts.size >= 2) {
                            TimelineItem(dateOrYear = parts[0], title = parts[1], detail = parts.getOrNull(2))
                        } else null
                    }
                    if (items.isNotEmpty()) {
                        blocks.add(RichBlock.Timeline(items))
                    } else {
                        blocks.add(RichBlock.Code(language = "timeline", code = codeLines.joinToString("\n")))
                    }
                } else if (lang.equals("mermaid", ignoreCase = true) || lang.equals("diagram", ignoreCase = true)) {
                    val rawDiagram = codeLines.joinToString("\n")
                    val steps = codeLines
                        .filter { !it.trim().startsWith("flowchart") && !it.trim().startsWith("graph") }
                        .map { it.replace(Regex("[\\[\\]]|\\-\\->"), " ").trim() }
                        .filter { it.isNotBlank() }
                    blocks.add(RichBlock.Diagram(type = lang, content = rawDiagram, steps = steps.ifEmpty { listOf(rawDiagram) }))
                } else {
                    blocks.add(RichBlock.Code(language = lang.ifEmpty { null }, code = codeLines.joinToString("\n")))
                }
                continue
            }

            // Aligned equation block: \begin{aligned} ... \end{aligned} or \begin{equation}
            if (line.contains("\\begin{aligned}") || line.contains("\\begin{equation}") || line.contains("\\begin{matrix}")) {
                val alignedLines = mutableListOf<String>()
                var currentLine = line
                while (i < lines.size && !currentLine.contains("\\end{aligned}") && !currentLine.contains("\\end{equation}") && !currentLine.contains("\\end{matrix}")) {
                    alignedLines.add(lines[i])
                    i++
                    if (i < lines.size) currentLine = lines[i]
                }
                if (i < lines.size) {
                    alignedLines.add(lines[i])
                    i++
                }
                val fullAlignedFormula = alignedLines.joinToString("\n")
                blocks.add(RichBlock.Math(fullAlignedFormula))
                continue
            }

            // Block Math $$...$$ or \[...\]
            if (line.trim().startsWith("$$") || line.trim().startsWith("\\[")) {
                val isSquareBracket = line.trim().startsWith("\\[")
                val startDelimiter = if (isSquareBracket) "\\[" else "$$"
                val endDelimiter = if (isSquareBracket) "\\]" else "$$"

                val mathLines = mutableListOf<String>()
                val firstLineText = line.trim().removePrefix(startDelimiter)
                if (firstLineText.endsWith(endDelimiter) && firstLineText.length > 2) {
                    blocks.add(RichBlock.Math(firstLineText.removeSuffix(endDelimiter).trim()))
                    i++
                    continue
                }
                if (firstLineText.isNotBlank()) mathLines.add(firstLineText)
                i++
                while (i < lines.size && !lines[i].trim().endsWith(endDelimiter) && !lines[i].trim().startsWith(endDelimiter)) {
                    mathLines.add(lines[i])
                    i++
                }
                if (i < lines.size) {
                    val lastLineText = lines[i].trim().removeSuffix(endDelimiter)
                    if (lastLineText.isNotBlank()) mathLines.add(lastLineText)
                    i++
                }
                blocks.add(RichBlock.Math(mathLines.joinToString(" ").trim()))
                continue
            }

            // Standalone Chemistry Block \ce{...}
            if (line.trim().startsWith("\\ce{") && line.trim().endsWith("}")) {
                val chemFormula = line.trim().removePrefix("\\ce{").removeSuffix("}").trim()
                blocks.add(RichBlock.Chemistry(chemFormula))
                i++
                continue
            }

            // Headers (# ## ###)
            if (line.startsWith("#")) {
                val level = line.takeWhile { it == '#' }.length
                val title = line.drop(level).trim()
                if (title.isNotBlank()) {
                    blocks.add(RichBlock.Header(level = level.coerceIn(1, 4), text = title))
                    i++
                    continue
                }
            }

            // Table (| col1 | col2 |)
            if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                val parsedTable = parseMarkdownTable(tableLines)
                if (parsedTable != null) {
                    blocks.add(parsedTable)
                } else {
                    tableLines.forEach { blocks.add(RichBlock.Paragraph(parseInlineSpans(it))) }
                }
                continue
            }

            // List items (* - 1.)
            if (isListLine(line)) {
                val listSpans = mutableListOf<List<InlineSpan>>()
                val isOrdered = line.trim().firstOrNull()?.isDigit() == true
                while (i < lines.size && isListLine(lines[i])) {
                    val itemText = lines[i].trim()
                        .replaceFirst(Regex("^([*\\-]|\\[[ xX]\\]|\\d+\\.)\\s*"), "")
                    listSpans.add(parseInlineSpans(itemText))
                    i++
                }
                blocks.add(RichBlock.ListGroup(items = listSpans, isOrdered = isOrdered))
                continue
            }

            // Regular paragraph
            if (line.isNotBlank()) {
                blocks.add(RichBlock.Paragraph(parseInlineSpans(line.trim())))
            }
            i++
        }

        return blocks
    }

    private fun isListLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("* ") ||
                trimmed.startsWith("- ") ||
                trimmed.matches(Regex("^\\d+\\.\\s.*"))
    }

    private fun parseMarkdownTable(lines: List<String>): RichBlock.Table? {
        if (lines.size < 2) return null
        val headers = lines[0].split("|").map { it.trim() }.filter { it.isNotEmpty() }
        val bodyLines = lines.drop(if (lines.getOrNull(1)?.contains("---") == true) 2 else 1)
        val rows = bodyLines.map { rowLine ->
            rowLine.split("|").map { it.trim() }.filterIndexed { index, _ -> index in headers.indices }
        }
        return RichBlock.Table(headers = headers, rows = rows)
    }

    fun parseInlineSpans(text: String): List<InlineSpan> {
        if (text.isBlank()) return emptyList()
        val spans = mutableListOf<InlineSpan>()

        // Comprehensive regex matching inline chemistry \ce{...}, block math $$...$$, \[...\], inline math \(...\), and $...$
        val pattern = Regex("(\\\\(?:ce)\\{[^}]+\\}|\\$\\$[\\s\\S]+?\\$\\$|\\\\\\[[\\s\\S]+?\\\\\\]|\\\\\\([\\s\\S]+?\\\\\\)|\\$[^\\$\\n]+?\\$)")
        var lastIdx = 0

        for (match in pattern.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1

            if (start > lastIdx) {
                spans.addAll(parseFormattedTextSpans(text.substring(lastIdx, start)))
            }

            val rawSpan = match.value
            when {
                rawSpan.startsWith("\\ce{") -> {
                    val chemContent = rawSpan.removePrefix("\\ce{").removeSuffix("}")
                    spans.add(InlineSpan.Chemistry(chemContent))
                }
                rawSpan.startsWith("\\[") -> {
                    val mathContent = rawSpan.removePrefix("\\[").removeSuffix("\\]")
                    spans.add(InlineSpan.Math(mathContent.trim()))
                }
                rawSpan.startsWith("$$") -> {
                    val mathContent = rawSpan.removePrefix("$$").removeSuffix("$$")
                    spans.add(InlineSpan.Math(mathContent.trim()))
                }
                rawSpan.startsWith("\\(") -> {
                    val mathContent = rawSpan.removePrefix("\\(").removeSuffix("\\)")
                    spans.add(InlineSpan.Math(mathContent.trim()))
                }
                rawSpan.startsWith("$") -> {
                    val mathContent = rawSpan.removePrefix("$").removeSuffix("$")
                    spans.add(InlineSpan.Math(mathContent.trim()))
                }
            }

            lastIdx = end
        }

        if (lastIdx < text.length) {
            spans.addAll(parseFormattedTextSpans(text.substring(lastIdx)))
        }

        return spans.ifEmpty { listOf(InlineSpan.Text(cleanStrayLatexMacros(text))) }
    }

    private fun parseFormattedTextSpans(text: String): List<InlineSpan> {
        val cleanedText = cleanStrayLatexMacros(text)
        val spans = mutableListOf<InlineSpan>()
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        var lastIdx = 0

        for (match in boldRegex.findAll(cleanedText)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > lastIdx) {
                val plainPart = cleanedText.substring(lastIdx, start)
                if (plainPart.isNotEmpty()) {
                    spans.add(InlineSpan.Text(plainPart))
                }
            }
            spans.add(InlineSpan.Text(match.groupValues[1], isBold = true))
            lastIdx = end
        }

        if (lastIdx < cleanedText.length) {
            val trailingPart = cleanedText.substring(lastIdx)
            if (trailingPart.isNotEmpty()) {
                spans.add(InlineSpan.Text(trailingPart))
            }
        }

        return spans
    }

    /**
     * Safety pass to normalize stray TeX macros that might appear in plain text.
     */
    private fun cleanStrayLatexMacros(input: String): String {
        var str = input
        if (str.contains("\\frac") || str.contains("\\sqrt") || str.contains("^") || str.contains("_")) {
            str = formatMathExpression(str)
        }
        return str
            .replace("\\infty", "∞")
            .replace("\\subseteq", "⊆")
            .replace("\\supseteq", "⊇")
            .replace("\\subset", "⊂")
            .replace("\\supset", "⊃")
            .replace("\\notin", "∉")
            .replace("\\in", "∈")
            .replace("\\cup", "∪")
            .replace("\\cap", "∩")
            .replace("\\implies", "⟹")
            .replace("\\iff", "⟺")
            .replace("\\therefore", "∴")
            .replace("\\because", "∵")
            .replace("\\Rightarrow", "⟹")
            .replace("\\Leftarrow", "⟸")
            .replace("\\Leftrightarrow", "⟺")
            .replace("\\longrightarrow", "⟶")
            .replace("\\longleftarrow", "⟵")
            .replace("\\times", "×")
            .replace("\\div", "÷")
            .replace("\\pm", "±")
            .replace("\\mp", "∓")
            .replace("\\cdot", "·")
            .replace("\\approx", "≈")
            .replace("\\neq", "≠")
            .replace("\\ne", "≠")
            .replace("\\leq", "≤")
            .replace("\\le", "≤")
            .replace("\\geq", "≥")
            .replace("\\ge", "≥")
            .replace("\\rightarrow", "→")
            .replace("\\to", "→")
            .replace("\\leftarrow", "←")
            .replace("\\degree", "°")
            .replace("\\theta", "θ")
            .replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\pi", "π")
            .replace("\\Delta", "Δ")
            .replace("\\delta", "δ")
            .replace("\\lambda", "λ")
            .replace("\\mu", "μ")
            .replace("\\sigma", "σ")
            .replace("\\omega", "ω")
            .replace("\\phi", "ϕ")
            .replace("\\%", "%")
            .replace("\\(", "")
            .replace("\\)", "")
            .replace(Regex("\\\\text\\{([^}]+)\\}")) { it.groupValues[1] }
            .replace(Regex("\\\\mathrm\\{([^}]+)\\}")) { it.groupValues[1] }
            .replace(Regex("\\\\mathbf\\{([^}]+)\\}")) { it.groupValues[1] }
    }
}

@Composable
fun EducationalInlineText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    val spans = remember(text) {
        val normalized = ContentNormalizer.normalize(text)
        MarkdownDocumentParser.parseInlineSpans(normalized)
    }
    val mathColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
    val mathBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val chemColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF34D399) else Color(0xFF059669)
    val chemBgColor = chemColor.copy(alpha = 0.12f)

    Text(
        text = buildInlineAnnotatedString(
            spans = spans,
            mathColor = mathColor,
            mathBgColor = mathBgColor,
            chemColor = chemColor,
            chemBgColor = chemBgColor
        ),
        modifier = modifier,
        style = if (fontWeight != null) style.copy(fontWeight = fontWeight, color = color, lineHeight = lineHeight) else style.copy(color = color, lineHeight = lineHeight)
    )
}

@Composable
fun EducationalContentRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val blocks = remember(content, isStreaming) {
        MarkdownDocumentParser.parseToBlocks(content, isStreaming)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is RichBlock.Header -> RenderHeaderBlock(block)
                is RichBlock.Paragraph -> RenderParagraphBlock(block.elements)
                is RichBlock.ListGroup -> RenderListGroupBlock(block)
                is RichBlock.Code -> RenderCodeBlock(block)
                is RichBlock.Table -> RenderTableBlock(block)
                is RichBlock.Math -> RenderMathBlock(block)
                is RichBlock.Chemistry -> RenderChemistryBlock(block)
                is RichBlock.Timeline -> RenderTimelineBlock(block)
                is RichBlock.Diagram -> RenderDiagramBlock(block)
                is RichBlock.Fallback -> RenderFallbackBlock(block)
            }
        }
    }
}

@Composable
private fun RenderHeaderBlock(header: RichBlock.Header) {
    val textStyle = when (header.level) {
        1 -> MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        2 -> MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        else -> MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
    Text(
        text = header.text,
        style = textStyle,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun RenderParagraphBlock(elements: List<InlineSpan>) {
    val mathColor = MaterialTheme.colorScheme.onSurface
    val mathBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val chemColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF34D399) else Color(0xFF059669)
    val chemBgColor = chemColor.copy(alpha = 0.12f)

    Text(
        text = buildInlineAnnotatedString(
            spans = elements,
            mathColor = mathColor,
            mathBgColor = mathBgColor,
            chemColor = chemColor,
            chemBgColor = chemBgColor
        ),
        style = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun RenderListGroupBlock(listGroup: RichBlock.ListGroup) {
    val mathColor = MaterialTheme.colorScheme.onSurface
    val mathBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val chemColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF34D399) else Color(0xFF059669)
    val chemBgColor = chemColor.copy(alpha = 0.12f)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        listGroup.items.forEachIndexed { index, itemSpans ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (listGroup.isOrdered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.width(20.dp)
                )
                Text(
                    text = buildInlineAnnotatedString(
                        spans = itemSpans,
                        mathColor = mathColor,
                        mathBgColor = mathBgColor,
                        chemColor = chemColor,
                        chemBgColor = chemBgColor
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RenderCodeBlock(codeBlock: RichBlock.Code) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = codeBlock.language?.uppercase() ?: "CODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Code snippet", codeBlock.code)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Copy code",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = codeBlock.code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun RenderTableBlock(table: RichBlock.Table) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            // Headers
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(vertical = 8.dp)
            ) {
                table.headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .widthIn(min = 100.dp)
                            .padding(horizontal = 12.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Rows
            table.rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .background(if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .widthIn(min = 100.dp)
                                .padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderMathBlock(math: RichBlock.Math) {
    val formattedMath = remember(math.formula) { formatMathExpression(math.formula) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = formattedMath,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )
            )
        }
    }
}

@Composable
private fun RenderChemistryBlock(chem: RichBlock.Chemistry) {
    val formattedChem = remember(chem.formula) { formatChemistryFormula(chem.formula) }
    val chemColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF34D399) else Color(0xFF059669)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = chemColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, chemColor.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🧪", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formattedChem,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
private fun RenderFallbackBlock(fallback: RichBlock.Fallback) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = fallback.rawText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Raw expression", fallback.rawText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Expression copied!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Copy raw text",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun RenderTimelineBlock(timeline: RichBlock.Timeline) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "⏳", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Historical Timeline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            timeline.items.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = item.dateOrYear,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!item.detail.isNullOrBlank()) {
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (idx < timeline.items.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderDiagramBlock(diagram: RichBlock.Diagram) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📊", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Process Flowchart / Diagram",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            diagram.steps.forEachIndexed { idx, step ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (idx < diagram.steps.size - 1) {
                    Text(
                        text = "   ↓",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

fun buildInlineAnnotatedString(
    spans: List<InlineSpan>,
    mathColor: Color = Color.Unspecified,
    mathBgColor: Color = Color.Transparent,
    chemColor: Color = Color(0xFF059669),
    chemBgColor: Color = Color(0xFF059669).copy(alpha = 0.12f)
): AnnotatedString {
    return buildAnnotatedString {
        spans.forEach { span ->
            when (span) {
                is InlineSpan.Text -> {
                    val weight = if (span.isBold) FontWeight.Bold else FontWeight.Normal
                    val fontStyle = if (span.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                    withStyle(SpanStyle(fontWeight = weight, fontStyle = fontStyle)) {
                        append(span.content)
                    }
                }
                is InlineSpan.Math -> {
                    val formatted = formatMathExpression(span.formula)
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = if (mathColor != Color.Unspecified) mathColor else Color.Unspecified,
                            background = mathBgColor
                        )
                    ) {
                        append(formatted)
                    }
                }
                is InlineSpan.Chemistry -> {
                    val formatted = formatChemistryFormula(span.formula)
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = chemColor,
                            background = chemBgColor
                        )
                    ) {
                        append(" $formatted ")
                    }
                }
            }
        }
    }
}

/**
 * Robust, recursive LaTeX math expression formatter.
 * Handles nested braces for fractions \frac{-b \pm \sqrt{b^2 - 4ac}}{2a},
 * roots \sqrt{x}, superscripts ^2, subscripts _1, aligned calculations, and TeX symbols.
 */
fun formatMathExpression(input: String): String {
    var str = input.trim()

    // Handle aligned blocks: \begin{aligned}... \end{aligned}
    if (str.contains("\\begin{aligned}")) {
        val body = str.substringAfter("\\begin{aligned}").substringBefore("\\end{aligned}").trim()
        val lines = body.split("\\\\", "\n")
        return lines.map { line ->
            val cleanedLine = line.replace("&=", "=").replace("&", "").trim()
            formatMathExpression(cleanedLine)
        }.filter { it.isNotBlank() }.joinToString("\n")
    }

    // Step 1: Unwrap text and font tags: \text{...}, \mathrm{...}, \mathbf{...}, etc.
    str = str
        .replace(Regex("\\\\text\\{([^}]+)\\}")) { it.groupValues[1] }
        .replace(Regex("\\\\mathrm\\{([^}]+)\\}")) { it.groupValues[1] }
        .replace(Regex("\\\\mathbf\\{([^}]+)\\}")) { it.groupValues[1] }
        .replace(Regex("\\\\mathit\\{([^}]+)\\}")) { it.groupValues[1] }
        .replace(Regex("\\\\mathtt\\{([^}]+)\\}")) { it.groupValues[1] }

    // Step 2: Recursively parse \frac{Num}{Den} with nested brace depth matching
    str = parseLaTeXFractions(str)

    // Step 3: Parse \sqrt{x}
    str = parseLaTeXRoots(str)

    // Step 4: Format superscripts (x^2 -> x², a^{m+n} -> aᵐ⁺ⁿ) & subscripts (x_1 -> x₁)
    str = formatSuperscriptsAndSubscripts(str)

    // Step 5: Format common LaTeX symbols
    str = str
        .replace("\\implies", "⟹")
        .replace("\\iff", "⟺")
        .replace("\\therefore", "∴")
        .replace("\\because", "∵")
        .replace("\\Rightarrow", "⟹")
        .replace("\\Leftarrow", "⟸")
        .replace("\\Leftrightarrow", "⟺")
        .replace("\\longrightarrow", "⟶")
        .replace("\\longleftarrow", "⟵")
        .replace("\\pm", "±")
        .replace("\\mp", "∓")
        .replace("\\times", "×")
        .replace("\\div", "÷")
        .replace("\\cdot", "·")
        .replace("\\approx", "≈")
        .replace("\\neq", "≠")
        .replace("\\ne", "≠")
        .replace("\\leq", "≤")
        .replace("\\le", "≤")
        .replace("\\geq", "≥")
        .replace("\\ge", "≥")
        .replace("\\infty", "∞")
        .replace("\\rightarrow", "→")
        .replace("\\to", "→")
        .replace("\\leftarrow", "←")
        .replace("\\gets", "←")
        .replace("\\left(", "(")
        .replace("\\right)", ")")
        .replace("\\left[", "[")
        .replace("\\right]", "]")
        .replace("\\left\\{", "{")
        .replace("\\right\\}", "}")
        .replace("\\left|", "|")
        .replace("\\right|", "|")
        .replace("\\left.", "")
        .replace("\\right.", "")
        .replace("\\%", "%")
        .replace("\\alpha", "α")
        .replace("\\beta", "β")
        .replace("\\gamma", "γ")
        .replace("\\delta", "δ")
        .replace("\\epsilon", "ε")
        .replace("\\theta", "θ")
        .replace("\\lambda", "λ")
        .replace("\\mu", "μ")
        .replace("\\pi", "π")
        .replace("\\sigma", "σ")
        .replace("\\tau", "τ")
        .replace("\\phi", "φ")
        .replace("\\omega", "ω")
        .replace("\\Delta", "Δ")
        .replace("\\Sigma", "Σ")
        .replace("\\Omega", "Ω")
        .replace("\\sum", "Σ")
        .replace("\\int", "∫")
        .replace("\\degree", "°")
        .replace("\\angle", "∠")
        .replace("\\triangle", "△")
        .replace("\\parallel", "∥")
        .replace("\\perp", "⊥")
        .replace("\\cup", "∪")
        .replace("\\cap", "∩")
        .replace("\\in", "∈")
        .replace("\\notin", "∉")
        .replace("\\subset", "⊂")
        .replace("\\subseteq", "⊆")
        .replace("\\emptyset", "∅")
        .replace("\\empty", "∅")
        .replace("\\{", "{")
        .replace("\\}", "}")

    return str
}

/**
 * Recursive \frac{Num}{Den} parser using brace depth matching for nested expressions.
 */
fun parseLaTeXFractions(input: String): String {
    val target = "\\frac{"
    val startIdx = input.indexOf(target)
    if (startIdx == -1) return input

    val numStart = startIdx + target.length
    val numEnd = findMatchingBraceEnd(input, numStart) ?: return input

    val numText = input.substring(numStart, numEnd)

    // Denominator starts with '{' immediately after numEnd
    if (numEnd + 1 >= input.length || input[numEnd + 1] != '{') {
        return input.substring(0, startIdx) + "($numText)" + parseLaTeXFractions(input.substring(numEnd + 1))
    }

    val denStart = numEnd + 2
    val denEnd = findMatchingBraceEnd(input, denStart) ?: return input
    val denText = input.substring(denStart, denEnd)

    val formattedNum = parseLaTeXFractions(numText)
    val formattedDen = parseLaTeXFractions(denText)

    val numWrapped = if (formattedNum.contains("+") || formattedNum.contains("-") || formattedNum.contains(" ") || formattedNum.contains("/")) "($formattedNum)" else formattedNum
    val denWrapped = if (formattedDen.contains("+") || formattedDen.contains("-") || formattedDen.contains(" ") || formattedDen.contains("/")) "($formattedDen)" else formattedDen
    val replacement = "$numWrapped / $denWrapped"
    val nextSegment = parseLaTeXFractions(input.substring(denEnd + 1))

    return input.substring(0, startIdx) + replacement + nextSegment
}

/**
 * Recursive \sqrt{x} parser.
 */
fun parseLaTeXRoots(input: String): String {
    val target = "\\sqrt{"
    val startIdx = input.indexOf(target)
    if (startIdx == -1) return input

    val contentStart = startIdx + target.length
    val contentEnd = findMatchingBraceEnd(input, contentStart) ?: return input
    val contentText = input.substring(contentStart, contentEnd)

    val replacement = "√(${parseLaTeXRoots(contentText)})"
    val nextSegment = parseLaTeXRoots(input.substring(contentEnd + 1))

    return input.substring(0, startIdx) + replacement + nextSegment
}

private fun findMatchingBraceEnd(str: String, startIdx: Int): Int? {
    var depth = 1
    for (i in startIdx until str.length) {
        when (str[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return i
            }
        }
    }
    return null
}

fun formatSuperscriptsAndSubscripts(input: String): String {
    var result = input

    // Handle ^{exp}
    result = Regex("\\^\\{([^}]+)\\}").replace(result) { match ->
        toSuperscript(match.groupValues[1])
    }
    // Handle single ^digit or ^char
    result = Regex("\\^([0-9a-n])").replace(result) { match ->
        toSuperscript(match.groupValues[1])
    }

    // Handle _{sub}
    result = Regex("_\\{([^}]+)\\}").replace(result) { match ->
        toSubscript(match.groupValues[1])
    }
    // Handle single _digit or _char
    result = Regex("_([0-9a-z])").replace(result) { match ->
        toSubscript(match.groupValues[1])
    }

    return result
}

private fun toSuperscript(str: String): String {
    val map = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'm' to 'ᵐ', 'x' to 'ˣ', 'y' to 'ʸ', 'a' to 'ᵃ', 'b' to 'ᵇ'
    )
    return str.map { map[it] ?: it }.joinToString("")
}

private fun toSubscript(str: String): String {
    val map = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'n' to 'ₙ'
    )
    return str.map { map[it] ?: it }.joinToString("")
}

fun formatChemistryFormula(chem: String): String {
    var result = chem.trim()
        .replace("->", " ⟶ ")
        .replace("<->", " ⇄ ")

    // Subscript numbers in chemical formulas e.g. H2O -> H₂O, H2SO4 -> H₂SO₄, 2H2 + O2 -> 2H₂ + O₂
    result = Regex("([A-Za-z\\)])(\\d+)").replace(result) { match ->
        match.groupValues[1] + toSubscript(match.groupValues[2])
    }

    // Superscript charges e.g. SO4^2- -> SO₄²⁻
    result = Regex("\\^([0-9]+[+-]?)").replace(result) { match ->
        toSuperscript(match.groupValues[1])
    }

    return result.replace(Regex("\\s+"), " ")
}
