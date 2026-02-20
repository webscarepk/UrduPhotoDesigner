package com.webscare.urducanvas.domain.repo

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.urduphotodesigner.data.remote.EndPointsInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class DownloadRepo @Inject constructor(
    private val context: Context,
    private val client: OkHttpClient,
    private val api: com.webscare.urducanvas.data.remote.EndPointsInterface
) {
    private val fontsDir by lazy {
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "fonts").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun downloadTemplateById(
        templateId: String,
        fileName: String,
        totalSizeFromApi: Long,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {

        val outputFile = File(fontsDir, fileName)

        Log.d(TAG, "downloadTemplateById → Starting download for ID: $templateId")
        Log.d(TAG, "downloadTemplateById → totalSizeFromApi: $totalSizeFromApi")

        val response = api.getTemplateJson(id = templateId)

        Log.d(TAG, "downloadTemplateById → Response code: ${response.code()}")

        if (!response.isSuccessful || response.body() == null) {
            throw Exception("Failed to download template: ${response.code()}")
        }

        val body = response.body()!!

        val contentLengthFromHeader = body.contentLength()
        Log.d(TAG, "downloadTemplateById → contentLength header: $contentLengthFromHeader")

        val totalSize = if (totalSizeFromApi > 0) {
            totalSizeFromApi
        } else {
            contentLengthFromHeader
        }

        Log.d(TAG, "downloadTemplateById → Final totalSize used: $totalSize")

        var bytesDownloaded = 0L
        var lastProgress = -1   // important change

        body.byteStream().use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {

                    outputStream.write(buffer, 0, bytesRead)
                    bytesDownloaded += bytesRead

                    Log.d(TAG, "downloadTemplateById → bytesDownloaded: $bytesDownloaded")

                    if (totalSize > 0) {

                        val progress = ((bytesDownloaded * 100) / totalSize).toInt()

                        Log.d(TAG, "downloadTemplateById → Calculated progress: $progress")

                        if (progress != lastProgress) {
                            lastProgress = progress
                            Log.d(TAG, "downloadTemplateById → Triggering onProgress: $progress")
                            onProgress(progress)
                        }
                    }
                }
            }
        }

        Log.d(TAG, "downloadTemplateById → Download completed")

        onProgress(100) // force final update

        return@withContext outputFile
    }

    suspend fun downloadAssets(
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val outputFile = File(fontsDir, fileName)

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download font: ${response.code}")
            }

            val contentLength = response.body?.contentLength() ?: 0L
            var bytesDownloaded = 0L
            var lastProgress = 0

            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        Log.d(TAG, "downloadAssets: $contentLength")
                        if (contentLength > 0) {
                            val progress = ((bytesDownloaded * 100) / contentLength).toInt()
                            if (progress > lastProgress) {
                                Log.d(TAG, "downloadAssets: $progress")
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
        }
        return@withContext outputFile
    }
}