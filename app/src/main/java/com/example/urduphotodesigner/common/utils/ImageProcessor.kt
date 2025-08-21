package com.example.urduphotodesigner.common.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import com.example.urduphotodesigner.common.canvas.model.ExportOptions
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

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

    // --- Save Bitmap with options ---
    fun saveBitmapToFile(bitmap: Bitmap, options: ExportOptions, path: String) {
        val file = File(path)
        ensureDir(file.parentFile!!)
        FileOutputStream(file).use { stream ->
            bitmap.compress(options.format.format, options.quality.quality, stream)
        }
    }

    // --- Images ---
    fun bitmapToFilePath(context: Context, bitmap: Bitmap, fileName: String = "img_${System.currentTimeMillis()}.png"): String {
        val dir = imagesDir(context)
        ensureDir(dir)
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    fun copyUriToTempFile(context: Context, uri: Uri): File? {
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
            val file = File(dir, "img_${System.currentTimeMillis()}.$extension")
            FileOutputStream(file).use { out -> bitmap.compress(format, 100, out) }
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
            FileOutputStream(file).use { out -> bitmap.compress(format, 100, out) }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
        context: Context,
        base64: String,
        fileName: String = "img_${System.currentTimeMillis()}.jpg"
    ): String {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val file = File(getImagesDir(context), fileName)  // use same strategy
            if (!file.parentFile!!.exists()) file.parentFile!!.mkdirs()

            FileOutputStream(file).use { out ->
                out.write(bytes)
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
    fun newExportImageFile(context: Context, baseName: String = "exp_${System.currentTimeMillis()}.png"): File {
        val dir = exportsImagesDir(context)
        ensureDir(dir)
        return File(dir, baseName)
    }

    fun newExportJsonFile(context: Context, baseName: String = "exp_${System.currentTimeMillis()}.json"): File {
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
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(resolver, uri)
        }
    }

    fun filePathToBitmap(filePath: String): Bitmap? {
        return try { BitmapFactory.decodeFile(filePath) } catch (e: Exception) { null }
    }

    fun getFileExtension(context: Context, uri: Uri): String {
        var extension: String? = null
        try {
            val type = context.contentResolver.getType(uri)
            if (type != null) {
                extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
            }
            if (extension == null) {
                val path = uri.path ?: ""
                val dot = path.lastIndexOf('.')
                if (dot != -1) extension = path.substring(dot + 1)
            }
        } catch (_: Exception) {}
        return extension ?: "jpg"
    }

    /**
     * Save (or overwrite) a bitmap at a specific path.
     */
    fun saveBitmapAtPath(bitmap: Bitmap, path: String): String {
        val file = File(path)
        if (!file.parentFile!!.exists()) file.parentFile!!.mkdirs()

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    /**
     * Convert Bitmap -> Base64 (PNG encoding by default).
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        return try {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
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
}