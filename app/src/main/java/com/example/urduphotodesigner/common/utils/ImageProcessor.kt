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
import java.io.FileOutputStream
import java.io.IOException

object ImageProcessor {

    fun saveBitmapToFile(bitmap: Bitmap, options: ExportOptions, path: String) {
        val file = File(path)
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        FileOutputStream(file).use { stream ->
            bitmap.compress(options.format.format, options.quality.quality, stream)
        }
    }

    fun bitmapToFilePath(context: Context, bitmap: Bitmap, fileName: String = "img_${System.currentTimeMillis()}.jpg"): String {
        val dir = File(context.filesDir, "images")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return file.absolutePath
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun filePathToBase64(filePath: String): String {
        val file = File(filePath)
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun base64ToFilePath(context: Context, base64: String, fileName: String = "img_${System.currentTimeMillis()}.jpg"): String {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val dir = File(context.filesDir, "images")
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            out.write(bytes)
        }

        return file.absolutePath
    }

    fun copyUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            // Decode and compress the bitmap from the URI
            val bitmap = getBitmapFromUri(context, uri)

            val dir = File(context.filesDir, "images")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun copyUriToTempFile(context: Context, uri: Uri, path: String): File? {
        return try {
            // Decode and compress the bitmap from the URI
            val bitmap = getBitmapFromUri(context, uri)

            val dir = File(context.filesDir, "images")
            if (!dir.exists()) dir.mkdirs()

            val file = File(path)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
        return try {
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}