package com.webscare.urducanvas.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.drawable.PictureDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.webscare.urducanvas.common.canvas.model.ExportOptions
import com.webscare.urducanvas.common.canvas.sealed.ImageFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    private const val DIR_IMAGES = "images"
    private const val DIR_EXPORTS_IMAGES = "exports/images"
    private const val DIR_EXPORTS_JSON = "exports/json"
    private const val DIR_FONTS = "fonts"
    private const val DIR_FONT_PREVIEWS = "fonts/previews"

    // --- Public directory getters ---
    fun imagesDir(context: Context) = File(context.filesDir, DIR_IMAGES)
    fun exportsImagesDir(context: Context) = File(context.filesDir, DIR_EXPORTS_IMAGES)
    fun exportsJsonDir(context: Context) = File(context.filesDir, DIR_EXPORTS_JSON)
    fun fontsDir(context: Context) = File(context.filesDir, DIR_FONTS)
    fun fontPreviewsDir(context: Context) = File(context.filesDir, DIR_FONT_PREVIEWS)

    private fun ensureDir(dir: File) {
        if (!dir.exists()) dir.mkdirs()
    }

    fun downsampleIfNeeded(bitmap: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        // GPU hard limit: DisplayListCanvas rejects any single drawBitmap call
        // that exceeds 100 MB. We use 96 MB as a safe margin.
        // For ARGB_8888 (4 bytes/px): 96 MB / 4 = 24,000,000 pixels max.
        val MAX_GPU_PIXELS = 24_000_000
        val gpuCapW = 4899 // sqrt(24_000_000) rounded down, used to derive capped dims
        val gpuCapH = 4899

        // Final target = most restrictive of caller's requested max and GPU limit
        val targetW = minOf(maxW, gpuCapW)
        val targetH = minOf(maxH, gpuCapH)

        // Already within bounds — return as-is, no copy made
        if (w <= targetW && h <= targetH && w * h <= MAX_GPU_PIXELS) return bitmap

        val scale = minOf(targetW.toFloat() / w, targetH.toFloat() / h)
        val newW = (w * scale).roundToInt().coerceAtLeast(1)
        val newH = (h * scale).roundToInt().coerceAtLeast(1)

        val scaled = bitmap.scale(newW, newH)
        bitmap.recycle()
        return scaled
    }

    // ← New overload for thumbnails — always PNG to preserve transparency
    fun saveBitmapToFile(bitmap: Bitmap, path: String) {
        val file = File(path)
        ensureDir(file.parentFile!!)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 30, stream)
            stream.flush()
            stream.fd.sync()
        }
    }

    // --- Images ---
    fun bitmapToFilePath(
        context: Context, bitmap: Bitmap, fileName: String = "img_${System.currentTimeMillis()}.png"
    ): String {
        val dir = imagesDir(context)
        ensureDir(dir)
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.fd.sync()
        }
        return file.absolutePath
    }

    fun copyUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val extension = getFileExtension(context, uri).lowercase()
            val dir = imagesDir(context)
            ensureDir(dir)
            val file = File(dir, "img_${System.currentTimeMillis()}.$extension")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output) // ✅ direct copy, no recompression
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun copyPdfUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val dir = context.cacheDir
            val file = File(dir, "pdf_${System.currentTimeMillis()}.pdf")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    fun copyUriToTempFile(context: Context, uri: Uri, path: String): File? {
        return try {
            val bitmap = getBitmapFromUri(context, uri)
            val extension = when (getFileExtension(context, uri).lowercase()) {
                "png" -> "png"
                "webp" -> "webp"
                else -> "jpg"
            }
            val format = when (extension) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.JPEG
            }
            val dir = imagesDir(context)
            ensureDir(dir)
            val file = File(path)
            FileOutputStream(file).use { out ->
                bitmap.compress(format, 100, out)
                out.flush()
                out.fd.sync()
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decodeUriSafely(contentResolver: android.content.ContentResolver, uri: Uri, maxW: Int, maxH: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            options.inSampleSize = calculateInSampleSize(options, maxW, maxH)
            options.inJustDecodeBounds = false

            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }


    fun filePathToBase64(filePath: String): String {
        return try {
            val file = File(filePath)
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun base64ToFilePath(
        context: Context, base64: String, fileName: String = "img_${System.currentTimeMillis()}.jpg"
    ): String {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val file = File(getImagesDir(context), fileName)  // use same strategy
            if (!file.parentFile!!.exists()) file.parentFile!!.mkdirs()

            FileOutputStream(file).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Centralized folder access so all methods use the same path
     */
    fun getImagesDir(context: Context): File {
        val dir = File(context.filesDir, "images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // --- Exports ---
    fun newExportImageFile(
        context: Context, baseName: String = "exp_${System.currentTimeMillis()}.png"
    ): File {
        val dir = exportsImagesDir(context)
        ensureDir(dir)
        return File(dir, baseName)
    }

    fun newExportJsonFile(
        context: Context, baseName: String = "exp_${System.currentTimeMillis()}.json"
    ): File {
        val dir = exportsJsonDir(context)
        ensureDir(dir)
        return File(dir, baseName)
    }

    // --- Fonts ---
    fun newFontFile(context: Context, originalName: String): File {
        val dir = fontsDir(context)
        ensureDir(dir)
        return File(dir, "${System.currentTimeMillis()}_$originalName")
    }

    fun newFontPreviewFile(context: Context, originalName: String): File {
        val dir = fontPreviewsDir(context)
        ensureDir(dir)
        return File(dir, "${System.currentTimeMillis()}_$originalName")
    }

    // --- Images ---
    fun newImageFile(context: Context, originalName: String): File {
        val dir = getImagesDir(context)
        return File(dir, "${System.currentTimeMillis()}_$originalName")
    }

    // --- Copy / Utility ---
    fun copyFile(src: File, dest: File): Boolean {
        return try {
            ensureDir(dest.parentFile!!)
            FileInputStream(src).channel.use { inChannel ->
                FileOutputStream(dest).channel.use { outChannel ->
                    outChannel.transferFrom(inChannel, 0, inChannel.size())
                    outChannel.force(true)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Throws(IOException::class)
    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(resolver, uri)
        }
    }

    fun filePathToBitmap(filePath: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            null
        }
    }

    fun bitmapCompress(image: Bitmap, canvasWidth: Int, canvasHeight: Int): Bitmap {
        // Calculate ratios
        val widthRatio = canvasWidth.toFloat() / image.width
        val heightRatio = canvasHeight.toFloat() / image.height
        val scale = minOf(widthRatio, heightRatio)

        // Optional: limit the scale factor (to avoid extremely huge bitmaps)
        val maxScale = 3f  // allow up to 3× enlargement
        val finalScale = scale.coerceAtMost(maxScale)

        val newWidth = (image.width * finalScale).toInt().coerceAtLeast(1)
        val newHeight = (image.height * finalScale).toInt().coerceAtLeast(1)

        return image.scale(newWidth, newHeight)
    }

    fun Bitmap.trimTransparentEdges(): Bitmap {
        if (isRecycled || width <= 0 || height <= 0) return this
        val w = this.width
        val h = this.height

        try {
            var top = h
            var bottom = -1
            var left = w
            var right = -1

            val rowBuffer = IntArray(w)

            // Scan top-down for top edge
            for (y in 0 until h) {
                getPixels(rowBuffer, 0, w, 0, y, w, 1)
                var hasOpaque = false
                for (x in 0 until w) {
                    if ((rowBuffer[x] ushr 24 and 0xff) > 0) {
                        hasOpaque = true
                        if (x < left) left = x
                        if (x > right) right = x
                    }
                }
                if (hasOpaque) {
                    top = y
                    break
                }
            }

            if (top >= h) return this // Completely transparent

            // Scan bottom-up for bottom edge
            for (y in h - 1 downTo top) {
                getPixels(rowBuffer, 0, w, 0, y, w, 1)
                var hasOpaque = false
                for (x in 0 until w) {
                    if ((rowBuffer[x] ushr 24 and 0xff) > 0) {
                        hasOpaque = true
                        if (x < left) left = x
                        if (x > right) right = x
                    }
                }
                if (hasOpaque) {
                    bottom = y
                    break
                }
            }

            // Fine-scan left and right edges between top and bottom rows
            for (y in (top + 1) until bottom) {
                getPixels(rowBuffer, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    if ((rowBuffer[x] ushr 24 and 0xff) > 0) {
                        if (x < left) left = x
                        if (x > right) right = x
                    }
                }
            }

            if (left > right || top > bottom) return this

            val cropW = right - left + 1
            val cropH = bottom - top + 1

            if (cropW == w && cropH == h) return this

            return Bitmap.createBitmap(this, left, top, cropW, cropH)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return this
        } catch (e: Throwable) {
            e.printStackTrace()
            return this
        }
    }


    fun PictureDrawable.trimTransparentEdges(): PictureDrawable {
        try {
            val origW = intrinsicWidth.takeIf { it > 0 } ?: picture.width
            val origH = intrinsicHeight.takeIf { it > 0 } ?: picture.height
            if (origW <= 0 || origH <= 0) return this

            setBounds(0, 0, origW, origH)

            // Cap sample size to max 512px on either dimension to prevent OOM
            val maxSample = 512f
            val maxDim = maxOf(origW, origH).toFloat()
            val sampleScale = if (maxDim > maxSample) maxSample / maxDim else 1f

            val sampleW = (origW * sampleScale).toInt().coerceAtLeast(1)
            val sampleH = (origH * sampleScale).toInt().coerceAtLeast(1)

            val tempBitmap = createBitmap(sampleW, sampleH)
            val canvas = Canvas(tempBitmap)
            canvas.scale(sampleScale, sampleScale)
            draw(canvas)

            val sampleBounds = tempBitmap.findNonTransparentBounds()
            tempBitmap.recycle()

            if (sampleBounds == null) return this

            // Map sample bounds back to original vector dimensions
            val invScale = 1f / sampleScale
            val left = (sampleBounds.left * invScale).toInt().coerceIn(0, origW - 1)
            val top = (sampleBounds.top * invScale).toInt().coerceIn(0, origH - 1)
            val right = (sampleBounds.right * invScale).toInt().coerceIn(left + 1, origW)
            val bottom = (sampleBounds.bottom * invScale).toInt().coerceIn(top + 1, origH)

            val newW = right - left
            val newH = bottom - top

            if (newW <= 0 || newH <= 0 || (newW == origW && newH == origH)) return this

            val trimmedPicture = Picture()
            val trimCanvas = trimmedPicture.beginRecording(newW, newH)
            trimCanvas.translate(-left.toFloat(), -top.toFloat())
            trimCanvas.drawPicture(picture)
            trimmedPicture.endRecording()

            return PictureDrawable(trimmedPicture).also {
                it.setBounds(0, 0, newW, newH)
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return this
        } catch (e: Throwable) {
            e.printStackTrace()
            return this
        }
    }

    private fun Bitmap.findNonTransparentBounds(alphaThreshold: Int = 10): Rect? {
        if (isRecycled || width <= 0 || height <= 0) return null
        val w = width
        val h = height

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        val rowBuffer = IntArray(w)

        for (y in 0 until h) {
            getPixels(rowBuffer, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val alpha = rowBuffer[x] ushr 24 and 0xff
                if (alpha > alphaThreshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        return if (maxX >= minX && maxY >= minY) Rect(minX, minY, maxX + 1, maxY + 1) else null
    }

    fun getFileExtension(context: Context, uri: Uri): String {
        var extension: String? = null
        try {
            val type = context.contentResolver.getType(uri)
            if (type != null) {
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
            }
            if (extension == null) {
                val path = uri.path ?: ""
                val dot = path.lastIndexOf('.')
                if (dot != -1) extension = path.substring(dot + 1)
            }
        } catch (_: Exception) {
        }
        return extension ?: "jpg"
    }

    /**
     * Convert Bitmap -> Base64 (PNG encoding by default).
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        if (bitmap.isRecycled) return ""
        return try {
            val maxDimension = 1920
            val scaled = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                bitmap.scale((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
            } else bitmap

            val stream = ByteArrayOutputStream()

            if (bitmap.hasAlpha()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, stream)
                } else {
                    @Suppress("DEPRECATION")
                    scaled.compress(Bitmap.CompressFormat.WEBP, 80, stream)
                }
            } else {
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            }

            val result = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

            stream.reset()
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()

            result
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun bitmapToBase64Lossless(bitmap: Bitmap): String {
        if (bitmap.isRecycled) return ""
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val result = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            stream.reset()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Convert Base64 -> Bitmap.
     */
    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun applyFilter(src: Bitmap, filter: ImageFilter?): Bitmap {
        if (filter == null || filter is ImageFilter.None) return src

        val output = createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        paint.colorFilter = when (filter) {
            ImageFilter.Grayscale -> ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })

            ImageFilter.Sepia -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.393f,
                        0.769f,
                        0.189f,
                        0f,
                        0f,
                        0.349f,
                        0.686f,
                        0.168f,
                        0f,
                        0f,
                        0.272f,
                        0.534f,
                        0.131f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Invert -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        -1f,
                        0f,
                        0f,
                        0f,
                        255f,
                        0f,
                        -1f,
                        0f,
                        0f,
                        255f,
                        0f,
                        0f,
                        -1f,
                        0f,
                        255f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.CoolTint -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.1f,
                        0f,
                        0f,
                        0f,
                        -20f,
                        0f,
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1.3f,
                        0f,
                        20f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.WarmTint -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.3f,
                        0f,
                        0f,
                        0f,
                        30f,
                        0f,
                        1f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0.8f,
                        0f,
                        -20f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Vintage -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.9f,
                        0.3f,
                        0.1f,
                        0f,
                        5f,
                        0.2f,
                        0.8f,
                        0.2f,
                        0f,
                        5f,
                        0.1f,
                        0.2f,
                        0.7f,
                        0f,
                        -10f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Film -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f,
                        0.1f,
                        0.1f,
                        0f,
                        15f,
                        0.1f,
                        1.2f,
                        0.1f,
                        0f,
                        10f,
                        0.1f,
                        0.1f,
                        0.9f,
                        0f,
                        -10f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.TealOrange -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f,
                        0f,
                        0f,
                        0f,
                        20f,
                        0f,
                        1.0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0.8f,
                        0f,
                        -10f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.HighContrast -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.5f,
                        0f,
                        0f,
                        0f,
                        -50f,
                        0f,
                        1.5f,
                        0f,
                        0f,
                        -50f,
                        0f,
                        0f,
                        1.5f,
                        0f,
                        -50f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.BlackWhite -> ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
                val contrast = ColorMatrix().apply {
                    set(
                        floatArrayOf(
                            1.4f,
                            0f,
                            0f,
                            0f,
                            -50f,
                            0f,
                            1.4f,
                            0f,
                            0f,
                            -50f,
                            0f,
                            0f,
                            1.4f,
                            0f,
                            -50f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f
                        )
                    )
                }
                postConcat(contrast)
            })

            ImageFilter.BrightnessBoost -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f,
                        0f,
                        0f,
                        0f,
                        30f,
                        0f,
                        1.2f,
                        0f,
                        0f,
                        30f,
                        0f,
                        0f,
                        1.2f,
                        0f,
                        30f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Sharpen -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        2f,
                        -1f,
                        -1f,
                        0f,
                        0f,
                        -1f,
                        2f,
                        -1f,
                        0f,
                        0f,
                        -1f,
                        -1f,
                        2f,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Sketch -> ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })

            ImageFilter.Cartoon -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.5f,
                        0f,
                        0f,
                        0f,
                        -30f,
                        0f,
                        1.5f,
                        0f,
                        0f,
                        -30f,
                        0f,
                        0f,
                        1.5f,
                        0f,
                        -30f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.HDR -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.3f,
                        0f,
                        0f,
                        0f,
                        -20f,
                        0f,
                        1.3f,
                        0f,
                        0f,
                        -20f,
                        0f,
                        0f,
                        1.3f,
                        0f,
                        -20f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Lomo -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f,
                        0.2f,
                        0.1f,
                        0f,
                        10f,
                        0.1f,
                        1.0f,
                        0f,
                        0f,
                        5f,
                        0.1f,
                        0.1f,
                        1.2f,
                        0f,
                        -10f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Pastel -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1f,
                        0f,
                        0f,
                        0f,
                        20f,
                        0f,
                        1f,
                        0f,
                        0f,
                        20f,
                        0f,
                        0f,
                        1f,
                        0f,
                        20f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Dramatic -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.5f,
                        0f,
                        0f,
                        0f,
                        -40f,
                        0f,
                        1.5f,
                        0f,
                        0f,
                        -40f,
                        0f,
                        0f,
                        1.5f,
                        0f,
                        -40f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.GoldenHour -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        1.2f,
                        0.2f,
                        0f,
                        0f,
                        30f,
                        0.1f,
                        1.1f,
                        0f,
                        0f,
                        20f,
                        0f,
                        0f,
                        0.8f,
                        0f,
                        -10f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            ImageFilter.Cyberpunk -> ColorMatrixColorFilter(ColorMatrix().apply {
                set(
                    floatArrayOf(
                        0.9f,
                        0.2f,
                        0.6f,
                        0f,
                        30f,
                        0.1f,
                        0.8f,
                        0.5f,
                        0f,
                        10f,
                        0.2f,
                        0.3f,
                        1.5f,
                        0f,
                        -20f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f
                    )
                )
            })

            else -> null
        }
        val scale = min(
            output.width.toFloat() / src.width.toFloat(),
            output.height.toFloat() / src.height.toFloat()
        )
        val dx = (output.width - src.width * scale) / 2f
        val dy = (output.height - src.height * scale) / 2f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(src, matrix, paint)
        if (filter == ImageFilter.SoftBlur) {
            val blurPaint = Paint().apply {
                maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawBitmap(output, 0f, 0f, blurPaint)
        } else if (filter == ImageFilter.Glow) {
            val glowPaint = Paint().apply {
                maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.OUTER)
                color = Color.argb(120, 255, 255, 200)
            }
            canvas.drawBitmap(output, 0f, 0f, glowPaint)
        }

        return output
    }

}