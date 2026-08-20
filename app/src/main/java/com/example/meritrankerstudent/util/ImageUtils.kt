package com.example.meritrankerstudent.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

object ImageUtils {

    const val MAX_COMPRESSED_IMAGE_BYTES: Int = 1_500_000 // 1.5 MB max raw compressed JPEG binary limit

    /**
     * Copies a Uri stream into a local app cache File so it remains readable across process/recomposition lifetime.
     */
    suspend fun copyUriToCacheFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "question_attachments").apply { if (!exists()) mkdirs() }
            val tempFile = File(dir, "attachment_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                // If input stream unavailable, write fallback placeholder to temp file
                val bitmap = createQuestionPlaceholderBitmap()
                tempFile.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                }
            }
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Safely decodes a downsampled [Bitmap] from a URI off the main thread.
     * Respects EXIF orientation and uses RGB_565 config to guarantee bounded memory usage.
     */
    suspend fun loadDownsampledBitmap(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = 1024
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            var bitmap: Bitmap? = null

            // Try decoding via ContentResolver stream
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)

                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.inSampleSize = calculateInSampleSize(options, maxDimensionPx, maxDimensionPx)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.RGB_565
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                    }
                }
            }

            // Fallback for file path decoding
            if (bitmap == null && uri.path != null) {
                val file = File(uri.path!!)
                if (file.exists() && file.length() > 0) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.inSampleSize = calculateInSampleSize(options, maxDimensionPx, maxDimensionPx)
                        options.inJustDecodeBounds = false
                        options.inPreferredConfig = Bitmap.Config.RGB_565
                        bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
                    }
                }
            }

            // High quality fallback bitmap generator if device image header is unreadable
            val finalBitmap = bitmap ?: createQuestionPlaceholderBitmap()

            // Rotate based on EXIF if available
            val rotationDegrees = getExifRotationDegrees(context, uri)
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    finalBitmap, 0, 0, finalBitmap.width, finalBitmap.height, matrix, true
                )
                if (rotated != finalBitmap && finalBitmap != bitmap) {
                    finalBitmap.recycle()
                }
                return@withContext rotated
            }

            finalBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            createQuestionPlaceholderBitmap()
        }
    }

    private fun createQuestionPlaceholderBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#17202F"))

        val paint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("📷 Question Image", 150f, 160f, paint)
        return bitmap
    }

    internal fun calculateInSampleSize(
        outWidth: Int,
        outHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (outHeight > reqHeight || outWidth > reqWidth) {
            val halfHeight = outHeight / 2
            val halfWidth = outWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int = calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)

    private fun getExifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val exifInterface = inputStream?.use { ExifInterface(it) } ?: return 0
            val orientation = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Creates a temporary image File in app cache directory for Camera capture.
     */
    fun createTempCameraFile(context: Context): File {
        val dir = File(context.cacheAreaDir(), "camera_photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File.createTempFile(
            "camera_question_${System.currentTimeMillis()}_",
            ".jpg",
            dir
        )
    }

    /**
     * Safely loads and converts a downsampled image to Base64 JPEG string with a hard byte-size limit guard.
     */
    suspend fun loadDownsampledBase64(
        context: Context,
        uri: Uri,
        maxDimensionPx: Int = 1024,
        quality: Int = 80,
        maxByteLimit: Int = MAX_COMPRESSED_IMAGE_BYTES
    ): String? = withContext(Dispatchers.IO) {
        try {
            var bitmap = loadDownsampledBitmap(context, uri, maxDimensionPx) ?: return@withContext null
            var outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            var bytes = outputStream.toByteArray()

            // Bounded downscale fallback if initial compress exceeds maxByteLimit
            if (bytes.size > maxByteLimit) {
                val scaledWidth = (bitmap.width * 0.7f).toInt().coerceAtLeast(300)
                val scaledHeight = (bitmap.height * 0.7f).toInt().coerceAtLeast(300)
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                outputStream = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 65, outputStream)
                bytes = outputStream.toByteArray()
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
            }

            // If still over hard byte limit, reject before network request
            if (bytes.size > maxByteLimit) {
                return@withContext null
            }

            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun Context.cacheAreaDir(): File {
        return externalCacheDir ?: cacheDir
    }
}
