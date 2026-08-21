package com.example.meritrankerstudent.ui.components.richtext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Reusable high-performance renderer that converts assistant responses into a clean,
 * crisp, density-aware PNG image for Native Android Clipboard (Copy) and Share Sheet (Share).
 *
 * Excludes response action toolbars, AI disclaimers, composer, and surrounding chat UI.
 * Renders full response content (even if taller than screen viewport) in a readable
 * light-neutral export theme with proper math, chemistry, tables, Hindi, and subtle branding.
 */
object ResponseShareRenderer {

    private const val EXPORT_WIDTH = 1080
    private const val HORIZONTAL_PADDING = 54
    private const val VERTICAL_PADDING = 48
    private const val MAX_BITMAP_HEIGHT = 10000

    /**
     * Generates a rendered PNG file of the given response and returns its FileProvider content URI.
     */
    suspend fun generateResponseImageUri(
        context: Context,
        rawContent: String,
        subject: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val normalized = ContentNormalizer.normalize(rawContent)
            if (normalized.isBlank()) return@withContext null

            val blocks = MarkdownDocumentParser.parseToBlocks(normalized, isStreaming = false)
            val bitmap = renderResponseToBitmap(blocks, subject) ?: return@withContext null

            val shareDir = File(context.cacheDir, "share").apply { if (!exists()) mkdirs() }
            cleanOldShareFiles(shareDir)

            val imageFile = File(shareDir, "meritranker_solution_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            bitmap.recycle()

            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, imageFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copies the rendered answer image to the Android system clipboard.
     * Shows a confirmation toast upon success or fallback text on clipboard limitations.
     */
    suspend fun copyResponseAsImage(context: Context, rawContent: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val uri = generateResponseImageUri(context, rawContent)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard == null) {
                Toast.makeText(context, "Couldn't copy answer", Toast.LENGTH_SHORT).show()
                return@withContext false
            }

            if (uri != null) {
                val clip = ClipData.newUri(context.contentResolver, "MeritRanker Solution", uri)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Answer copied as image", Toast.LENGTH_SHORT).show()
                true
            } else {
                // Fallback to formatted plain text copy if image generation failed
                val cleanText = ContentNormalizer.normalize(rawContent)
                val clip = ClipData.newPlainText("MeritRanker Solution", cleanText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Answer copied", Toast.LENGTH_SHORT).show()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Couldn't copy answer", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Opens the native Android Share sheet with the rendered response PNG.
     */
    suspend fun shareResponseAsImage(context: Context, rawContent: String, subject: String? = null) = withContext(Dispatchers.Main) {
        try {
            val uri = generateResponseImageUri(context, rawContent, subject)
            if (uri != null) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "MeritRanker Solution")
                    putExtra(Intent.EXTRA_TEXT, "Shared from MeritRanker Smart Tutor")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "Share solution").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } else {
                // Fallback to text sharing if bitmap generation failed
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, ContentNormalizer.normalize(rawContent))
                }
                val chooser = Intent.createChooser(sendIntent, "Share solution").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Couldn't share solution", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Renders parsed blocks into a crisp off-screen Bitmap with light neutral background.
     */
    private fun renderResponseToBitmap(blocks: List<RichBlock>, subject: String?): Bitmap? {
        val contentWidth = EXPORT_WIDTH - (HORIZONTAL_PADDING * 2)

        // Setup Paints
        val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        val textPaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 38f
            isAntiAlias = true
            isSubpixelText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val boldTextPaint = TextPaint(textPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val h1Paint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 52f
            isAntiAlias = true
            isSubpixelText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val h2Paint = TextPaint(h1Paint).apply { textSize = 44f }
        val h3Paint = TextPaint(h1Paint).apply { textSize = 40f }

        val mathBoxPaint = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val mathTextPaint = TextPaint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 38f
            isAntiAlias = true
            isSubpixelText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // Measure heights dynamically
        var totalHeight = VERTICAL_PADDING + 80 /* Header */ + 30 /* Header spacing */

        data class RenderItem(
            val block: RichBlock,
            val layout: StaticLayout?,
            val extraHeight: Int
        )

        val renderItems = mutableListOf<RenderItem>()

        for (block in blocks) {
            when (block) {
                is RichBlock.Header -> {
                    val paint = when (block.level) {
                        1 -> h1Paint
                        2 -> h2Paint
                        else -> h3Paint
                    }
                    val layout = createStaticLayout(block.text, paint, contentWidth)
                    val h = layout.height + 24
                    renderItems.add(RenderItem(block, layout, h))
                    totalHeight += h
                }
                is RichBlock.Paragraph -> {
                    val text = buildSpannableFromSpans(block.elements)
                    val layout = createStaticLayout(text, textPaint, contentWidth)
                    val h = layout.height + 20
                    renderItems.add(RenderItem(block, layout, h))
                    totalHeight += h
                }
                is RichBlock.ListGroup -> {
                    var listTotalH = 0
                    for ((idx, item) in block.items.withIndex()) {
                        val prefix = if (block.isOrdered) "${idx + 1}.  " else "•   "
                        val itemText = SpannableStringBuilder(prefix).apply {
                            append(buildSpannableFromSpans(item))
                        }
                        val layout = createStaticLayout(itemText, textPaint, contentWidth - 20)
                        listTotalH += layout.height + 12
                    }
                    renderItems.add(RenderItem(block, null, listTotalH + 12))
                    totalHeight += listTotalH + 12
                }
                is RichBlock.Math -> {
                    val formattedMath = formatMathExpression(block.formula)
                    val layout = createStaticLayout(formattedMath, mathTextPaint, contentWidth - 48)
                    val h = layout.height + 48 /* Box padding */ + 20
                    renderItems.add(RenderItem(block, layout, h))
                    totalHeight += h
                }
                is RichBlock.Chemistry -> {
                    val formattedChem = formatChemistryFormula(block.formula)
                    val layout = createStaticLayout(formattedChem, mathTextPaint, contentWidth - 48)
                    val h = layout.height + 48 + 20
                    renderItems.add(RenderItem(block, layout, h))
                    totalHeight += h
                }
                is RichBlock.Table -> {
                    val rowHeight = 60
                    val tableH = (block.rows.size + 1) * rowHeight + 30
                    renderItems.add(RenderItem(block, null, tableH))
                    totalHeight += tableH
                }
                is RichBlock.Code -> {
                    val layout = createStaticLayout(block.code, TextPaint().apply {
                        color = Color.parseColor("#F8FAFC")
                        textSize = 32f
                        typeface = Typeface.MONOSPACE
                        isAntiAlias = true
                    }, contentWidth - 48)
                    val h = layout.height + 60 + 20
                    renderItems.add(RenderItem(block, layout, h))
                    totalHeight += h
                }
                else -> {
                    val text = when (block) {
                        is RichBlock.Fallback -> block.rawText
                        is RichBlock.Timeline -> block.items.joinToString("\n") { "${it.dateOrYear}: ${it.title}" }
                        is RichBlock.Diagram -> block.steps.joinToString(" ➔ ")
                        else -> ""
                    }
                    val layout = createStaticLayout(text, textPaint, contentWidth)
                    val h = layout.height + 20
                    renderItems.add(RenderItem(block, layout, h))
                    totalHeight += h
                }
            }
        }

        totalHeight += 70 /* Footer */ + VERTICAL_PADDING
        val boundedHeight = totalHeight.coerceIn(400, MAX_BITMAP_HEIGHT)

        val bitmap = Bitmap.createBitmap(EXPORT_WIDTH, boundedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background and border
        val cardRect = RectF(6f, 6f, EXPORT_WIDTH - 6f, boundedHeight - 6f)
        canvas.drawRoundRect(cardRect, 24f, 24f, bgPaint)
        canvas.drawRoundRect(cardRect, 24f, 24f, borderPaint)

        // Draw Header
        var currentY = VERTICAL_PADDING.toFloat()

        // Brand Icon / Pill
        val badgePaint = Paint().apply { color = Color.parseColor("#2563EB"); style = Paint.Style.FILL; isAntiAlias = true }
        canvas.drawRoundRect(RectF(HORIZONTAL_PADDING.toFloat(), currentY, (HORIZONTAL_PADDING + 28).toFloat(), currentY + 28), 8f, 8f, badgePaint)

        val headerBrandPaint = TextPaint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("MERITRANKER", (HORIZONTAL_PADDING + 40).toFloat(), currentY + 24, headerBrandPaint)

        val headerTutorPaint = TextPaint().apply {
            color = Color.parseColor("#64748B")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        canvas.drawText("•  SMART TUTOR", (HORIZONTAL_PADDING + 270).toFloat(), currentY + 24, headerTutorPaint)

        if (!subject.isNullOrBlank()) {
            val subjectPaint = TextPaint().apply {
                color = Color.parseColor("#475569")
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(subject, (EXPORT_WIDTH - HORIZONTAL_PADDING).toFloat(), currentY + 24, subjectPaint)
        }

        currentY += 46f
        canvas.drawLine(HORIZONTAL_PADDING.toFloat(), currentY, (EXPORT_WIDTH - HORIZONTAL_PADDING).toFloat(), currentY, dividerPaint)
        currentY += 26f

        // Draw Blocks
        for (item in renderItems) {
            when (item.block) {
                is RichBlock.Header, is RichBlock.Paragraph, is RichBlock.Fallback -> {
                    item.layout?.let { layout ->
                        canvas.save()
                        canvas.translate(HORIZONTAL_PADDING.toFloat(), currentY)
                        layout.draw(canvas)
                        canvas.restore()
                        currentY += layout.height + 20f
                    }
                }
                is RichBlock.ListGroup -> {
                    for ((idx, listSpan) in item.block.items.withIndex()) {
                        val prefix = if (item.block.isOrdered) "${idx + 1}.  " else "•   "
                        val itemText = SpannableStringBuilder(prefix).apply {
                            append(buildSpannableFromSpans(listSpan))
                        }
                        val layout = createStaticLayout(itemText, textPaint, contentWidth - 20)
                        canvas.save()
                        canvas.translate((HORIZONTAL_PADDING + 10).toFloat(), currentY)
                        layout.draw(canvas)
                        canvas.restore()
                        currentY += layout.height + 12f
                    }
                    currentY += 12f
                }
                is RichBlock.Math -> {
                    item.layout?.let { layout ->
                        val boxH = layout.height + 36f
                        val boxRect = RectF(
                            HORIZONTAL_PADDING.toFloat(),
                            currentY,
                            (EXPORT_WIDTH - HORIZONTAL_PADDING).toFloat(),
                            currentY + boxH
                        )
                        canvas.drawRoundRect(boxRect, 16f, 16f, mathBoxPaint)
                        canvas.drawRoundRect(boxRect, 16f, 16f, borderPaint)

                        canvas.save()
                        canvas.translate((HORIZONTAL_PADDING + 24).toFloat(), currentY + 18f)
                        layout.draw(canvas)
                        canvas.restore()
                        currentY += boxH + 20f
                    }
                }
                is RichBlock.Chemistry -> {
                    item.layout?.let { layout ->
                        val chemBoxPaint = Paint().apply {
                            color = Color.parseColor("#F0FDFA")
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        val chemBorderPaint = Paint().apply {
                            color = Color.parseColor("#99F6E4")
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                            isAntiAlias = true
                        }
                        val boxH = layout.height + 36f
                        val boxRect = RectF(
                            HORIZONTAL_PADDING.toFloat(),
                            currentY,
                            (EXPORT_WIDTH - HORIZONTAL_PADDING).toFloat(),
                            currentY + boxH
                        )
                        canvas.drawRoundRect(boxRect, 16f, 16f, chemBoxPaint)
                        canvas.drawRoundRect(boxRect, 16f, 16f, chemBorderPaint)

                        canvas.save()
                        canvas.translate((HORIZONTAL_PADDING + 24).toFloat(), currentY + 18f)
                        layout.draw(canvas)
                        canvas.restore()
                        currentY += boxH + 20f
                    }
                }
                is RichBlock.Table -> {
                    val table = item.block
                    val numCols = table.headers.size.coerceAtLeast(1)
                    val colW = contentWidth / numCols.toFloat()
                    val rowH = 56f

                    // Header Row Background
                    val tableHeaderPaint = Paint().apply { color = Color.parseColor("#F1F5F9"); style = Paint.Style.FILL }
                    val tableCellBorder = Paint().apply { color = Color.parseColor("#E2E8F0"); style = Paint.Style.STROKE; strokeWidth = 2f }

                    canvas.drawRoundRect(
                        RectF(HORIZONTAL_PADDING.toFloat(), currentY, (EXPORT_WIDTH - HORIZONTAL_PADDING).toFloat(), currentY + rowH),
                        8f, 8f, tableHeaderPaint
                    )

                    // Draw Headers
                    for (c in 0 until numCols) {
                        val headerText = table.headers.getOrElse(c) { "" }
                        canvas.drawText(
                            headerText,
                            (HORIZONTAL_PADDING + c * colW + 16),
                            currentY + 38f,
                            boldTextPaint
                        )
                    }
                    currentY += rowH

                    // Draw Rows
                    for (row in table.rows) {
                        for (c in 0 until numCols) {
                            val cellText = row.getOrElse(c) { "" }
                            canvas.drawText(
                                cellText,
                                (HORIZONTAL_PADDING + c * colW + 16),
                                currentY + 38f,
                                textPaint
                            )
                        }
                        canvas.drawLine(HORIZONTAL_PADDING.toFloat(), currentY + rowH, (EXPORT_WIDTH - HORIZONTAL_PADDING).toFloat(), currentY + rowH, tableCellBorder)
                        currentY += rowH
                    }
                    currentY += 20f
                }
                is RichBlock.Code -> {
                    item.layout?.let { layout ->
                        val codeBoxPaint = Paint().apply { color = Color.parseColor("#1E293B"); style = Paint.Style.FILL; isAntiAlias = true }
                        val boxH = layout.height + 40f
                        val boxRect = RectF(
                            HORIZONTAL_PADDING.toFloat(),
                            currentY,
                            (EXPORT_WIDTH - HORIZONTAL_PADDING).toFloat(),
                            currentY + boxH
                        )
                        canvas.drawRoundRect(boxRect, 16f, 16f, codeBoxPaint)

                        canvas.save()
                        canvas.translate((HORIZONTAL_PADDING + 24).toFloat(), currentY + 20f)
                        layout.draw(canvas)
                        canvas.restore()
                        currentY += boxH + 20f
                    }
                }
                else -> {
                    item.layout?.let { layout ->
                        canvas.save()
                        canvas.translate(HORIZONTAL_PADDING.toFloat(), currentY)
                        layout.draw(canvas)
                        canvas.restore()
                        currentY += layout.height + 20f
                    }
                }
            }
        }

        // Draw Footer
        currentY += 10f
        canvas.drawLine(HORIZONTAL_PADDING.toFloat(), currentY, (EXPORT_WIDTH - HORIZONTAL_PADDING).toFloat(), currentY, dividerPaint)
        currentY += 34f

        val footerTextPaint = TextPaint().apply {
            color = Color.parseColor("#64748B")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generated with MeritRanker Smart Tutor • Learn • Practice • Excel", (EXPORT_WIDTH / 2).toFloat(), currentY, footerTextPaint)

        return bitmap
    }

    private fun buildSpannableFromSpans(spans: List<InlineSpan>): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        for (span in spans) {
            when (span) {
                is InlineSpan.Text -> {
                    val start = ssb.length
                    ssb.append(span.content)
                    if (span.isBold && span.isItalic) {
                        ssb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, ssb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else if (span.isBold) {
                        ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else if (span.isItalic) {
                        ssb.setSpan(StyleSpan(Typeface.ITALIC), start, ssb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                is InlineSpan.Math -> {
                    val formatted = formatMathExpression(span.formula)
                    ssb.append(formatted)
                }
                is InlineSpan.Chemistry -> {
                    val formatted = formatChemistryFormula(span.formula)
                    ssb.append(formatted)
                }
            }
        }
        return ssb
    }

    private fun createStaticLayout(source: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        val safeWidth = width.coerceAtLeast(100)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(source, 0, source.length, paint, safeWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.22f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(source, paint, safeWidth, Layout.Alignment.ALIGN_NORMAL, 1.22f, 0f, true)
        }
    }

    private fun cleanOldShareFiles(shareDir: File) {
        try {
            val files = shareDir.listFiles() ?: return
            if (files.size > 5) {
                files.sortedBy { it.lastModified() }
                    .take(files.size - 5)
                    .forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
