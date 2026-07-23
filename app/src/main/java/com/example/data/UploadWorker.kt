package com.company.wholetv.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.company.wholetv.data.AppDatabase
import com.company.wholetv.data.MediaItem
import com.company.wholetv.data.MediaRepository
import com.example.data.UploadManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.TimeUnit

class UploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "UploadWorker"
        private const val CHANNEL_ID = "wholetv_upload_channel"
        private const val NOTIFICATION_ID = 4004
        private const val DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024 // 5 MB chunks
    }

    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val videoPath = inputData.getString("videoPath") ?: return Result.failure()
        val coverPath = inputData.getString("coverPath")
        val title = inputData.getString("title") ?: "Untitled Video"
        val description = inputData.getString("description") ?: ""
        val category = inputData.getString("category") ?: "Movies"
        val hashtags = inputData.getString("hashtags") ?: ""
        val qualities = inputData.getString("qualities") ?: "1080p HD"
        val languages = inputData.getString("languages") ?: "English"
        val rating = inputData.getDouble("rating", 8.5)
        val isSlide = inputData.getBoolean("isSlide", false)
        val badge = inputData.getString("badge") ?: ""
        val streamingPlatform = inputData.getString("streamingPlatform") ?: "None"
        val genre = inputData.getString("genre") ?: "Action"
        val uploaderName = inputData.getString("uploaderName") ?: "Anonymous"
        val currentUserId = inputData.getString("currentUserId") ?: "system"
        
        var backendUrl = inputData.getString("backendUrl") ?: ""
        if (backendUrl.isBlank() || backendUrl == "direct" || backendUrl.contains("wholetv-backend.onrender.com")) {
            backendUrl = "https://wholetvss.onrender.com/api" // fallback
        }
        if (backendUrl.endsWith("/")) {
            backendUrl = backendUrl.substring(0, backendUrl.length - 1)
        }
        if (!backendUrl.endsWith("/api") && !backendUrl.contains("/api/")) {
            backendUrl = "$backendUrl/api"
        }

        val uploadId = inputData.getString("uploadId") ?: UUID.randomUUID().toString()

        Log.i(TAG, "=============================================")
        Log.i(TAG, "🚀 UPLOAD WORKER STARTED")
        Log.i(TAG, "Video Path: $videoPath")
        Log.i(TAG, "Cover Path: $coverPath")
        Log.i(TAG, "Backend URL: $backendUrl")
        Log.i(TAG, "Upload ID: $uploadId")
        Log.i(TAG, "=============================================")
        
        // Setup foreground notification
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground info: ${e.message}")
        }

        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            val errorMsg = "Video file does not exist locally: $videoPath"
            Log.e(TAG, "❌ [ERROR] $errorMsg")
            UploadManager.updateProgress(false, 0f, "0 KB/s", "0 MB", "0 MB", "0 MB", "Error", title, errorMsg, errorMsg)
            return Result.failure()
        }

        val totalSize = videoFile.length()
        val totalSizeStr = formatSize(totalSize)
        Log.i(TAG, "Video selected & validated. File size: $totalSizeStr ($totalSize bytes)")

        try {
            // Step 1: Upload cover photo if present
            var finalCoverUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1" // Default movie poster
            coverPath?.let { path ->
                val coverFile = File(path)
                if (coverFile.exists()) {
                    Log.i(TAG, "Cover image selected. Uploading cover file (${coverFile.length()} bytes)...")
                    updateNotification("Uploading cover image...", 0)
                    UploadManager.updateProgress(
                        isUploading = true,
                        progress = 0.01f,
                        speed = "Calculating...",
                        uploadedSize = "0 MB",
                        totalSize = totalSizeStr,
                        remainingSize = totalSizeStr,
                        eta = "Calculating...",
                        title = title,
                        status = "Uploading cover artwork..."
                    )
                    
                    val uploadedUrl = uploadFileDirectly(coverFile, backendUrl)
                    if (!uploadedUrl.isNullOrBlank()) {
                        finalCoverUrl = uploadedUrl
                        Log.i(TAG, "Cover image uploaded successfully! URL: $finalCoverUrl")
                    } else {
                        Log.w(TAG, "Cover image upload returned null URL. Using default poster: $finalCoverUrl")
                    }
                }
            }

            // Step 2: Upload Video via Chunked API
            val prefs = applicationContext.getSharedPreferences("wholetv_upload_prefs", Context.MODE_PRIVATE)
            val savedStartByteKey = "upload_${uploadId}_byte"
            var startByte = prefs.getLong(savedStartByteKey, 0L)
            
            // Safety check in case total size changed or saved size is corrupted
            if (startByte >= totalSize) {
                startByte = 0L
            }

            Log.i(TAG, "Starting / Resuming chunked video upload. Offset: $startByte / $totalSize bytes")

            val chunkSize = DEFAULT_CHUNK_SIZE
            var lastTime = System.currentTimeMillis()
            var lastBytes = startByte

            var currentVideoUrl: String? = null

            while (startByte < totalSize) {
                val endByte = minOf(startByte + chunkSize, totalSize)
                val chunkLength = (endByte - startByte).toInt()

                Log.i(TAG, "Reading video chunk: bytes $startByte-${endByte - 1}/$totalSize ($chunkLength bytes)")
                val chunkData = readChunkBytes(videoFile, startByte, chunkLength)

                // Try to upload chunk with retries
                var attempt = 1
                var chunkUploaded = false
                var lastErr: Exception? = null

                while (attempt <= 3 && !chunkUploaded) {
                    try {
                        val contentRange = "bytes $startByte-${endByte - 1}/$totalSize"
                        
                        val progressFloat = startByte.toFloat() / totalSize.toFloat()
                        val percent = (progressFloat * 100).toInt()
                        updateNotification("Uploading video ($percent%)...", percent)

                        Log.i(TAG, "Sending API request for chunk attempt $attempt/3: $contentRange to $backendUrl")
                        // Upload the chunk
                        val responseJson = uploadChunk(
                            chunkBytes = chunkData,
                            uploadId = uploadId,
                            contentRange = contentRange,
                            backendUrl = backendUrl,
                            fileOriginalName = videoFile.name
                        )

                        if (responseJson != null) {
                            chunkUploaded = true
                            Log.i(TAG, "Chunk uploaded successfully! Response: $responseJson")
                            
                            // If this response contains a secure_url or url, save it
                            val returnedUrl = responseJson.optString("secure_url", null)
                                ?: responseJson.optString("url", null)
                            if (!returnedUrl.isNullOrBlank()) {
                                currentVideoUrl = returnedUrl
                                Log.i(TAG, "Received secure_url from Cloudinary / Backend: $currentVideoUrl")
                            }
                        } else {
                            attempt++
                            Log.w(TAG, "Chunk response was null. Retrying attempt $attempt/3...")
                            TimeUnit.SECONDS.sleep(2)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Chunk upload failed on attempt $attempt/3: ${e.message}", e)
                        lastErr = e
                        attempt++
                        TimeUnit.SECONDS.sleep(3) // Wait 3s before retry
                    }
                }

                if (!chunkUploaded) {
                    val finalEx = lastErr ?: Exception("Chunk upload failed after 3 attempts.")
                    Log.e(TAG, "❌ [FATAL] Video chunk upload failed permanently: ${finalEx.message}", finalEx)
                    throw finalEx
                }

                // Chunk upload success! Update startByte and persist it
                startByte = endByte
                prefs.edit().putLong(savedStartByteKey, startByte).apply()

                // Calculate Speed and update stats
                val now = System.currentTimeMillis()
                val duration = now - lastTime
                if (duration >= 500) {
                    val bytesUploadedThisInterval = startByte - lastBytes
                    val speedBytesPerSec = (bytesUploadedThisInterval.toFloat() / duration.toFloat()) * 1000f
                    val speedStr = formatSpeed(speedBytesPerSec)
                    val uploadedSizeStr = formatSize(startByte)
                    val remainingSizeStr = formatSize(totalSize - startByte)
                    
                    val etaSeconds = if (speedBytesPerSec > 0) {
                        ((totalSize - startByte) / speedBytesPerSec).toLong()
                    } else -1L
                    val etaStr = if (etaSeconds >= 0) "${etaSeconds}s" else "Calculating..."
                    val totalProgress = startByte.toFloat() / totalSize.toFloat()

                    UploadManager.updateProgress(
                        isUploading = true,
                        progress = totalProgress,
                        speed = speedStr,
                        uploadedSize = uploadedSizeStr,
                        totalSize = totalSizeStr,
                        remainingSize = remainingSizeStr,
                        eta = etaStr,
                        title = title,
                        status = "Uploading video chunk (${(totalProgress * 100).toInt()}%)..."
                    )

                    lastTime = now
                    lastBytes = startByte
                }
            }

            if (currentVideoUrl.isNullOrBlank()) {
                val err = "Backend upload finished but Cloudinary/Backend did not return a valid secure_url."
                Log.e(TAG, "❌ [ERROR] $err")
                throw Exception(err)
            }

            Log.i(TAG, "✅ Video upload finished successfully! Final secure_url: $currentVideoUrl")

            // Step 3: Insert item into local Room Database
            Log.i(TAG, "Saving video metadata to local Room Database...")
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = MediaRepository(database.mediaDao())

            val finalItem = MediaItem(
                title = title,
                description = description,
                videoUrl = currentVideoUrl,
                posterUrl = finalCoverUrl,
                category = category,
                hashtags = hashtags,
                views = (100..500).random(),
                likes = (10..50).random(),
                languages = languages,
                qualities = qualities,
                rating = rating,
                isSlide = isSlide,
                badge = badge,
                streamingPlatform = streamingPlatform,
                genre = genre,
                uploaderName = uploaderName,
                uploaderId = currentUserId,
                firestoreId = "user_uploaded_${UUID.randomUUID()}",
                timestamp = System.currentTimeMillis()
            )

            repository.insertMediaItem(finalItem)
            Log.i(TAG, "✅ Metadata saved to Room Database successfully! Item ID: ${finalItem.id}, Title: ${finalItem.title}")

            // Cleanup local temp files to save space
            try {
                videoFile.delete()
                coverPath?.let { File(it).delete() }
                prefs.edit().remove(savedStartByteKey).apply()
                Log.i(TAG, "Temporary local upload files cleaned up.")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up temporary files", e)
            }

            // Notify UI & System
            updateNotification("Upload Finished Successfully! 🚀", 100)
            UploadManager.updateProgress(
                isUploading = false,
                progress = 1.0f,
                speed = "0 KB/s",
                uploadedSize = totalSizeStr,
                totalSize = totalSizeStr,
                remainingSize = "0 MB",
                eta = "0s",
                title = title,
                status = "Completed"
            )
            
            Log.i(TAG, "🎉 UPLOAD WORKER COMPLETED SUCCESSFULLY!")

            return Result.success(workDataOf("videoUrl" to currentVideoUrl, "coverUrl" to finalCoverUrl))

        } catch (e: Exception) {
            Log.e(TAG, "❌ [FATAL ERROR] Upload system execution failed: ${e.message}", e)
            val fullError = "${e.javaClass.simpleName}: ${e.localizedMessage ?: e.message}"
            updateNotification("Upload Failed ❌", 0)
            UploadManager.updateProgress(
                isUploading = false,
                progress = 0f,
                speed = "0 KB/s",
                uploadedSize = "0 MB",
                totalSize = totalSizeStr,
                remainingSize = totalSizeStr,
                eta = "Error",
                title = title,
                status = "Failed",
                errorMsg = fullError
            )
            return Result.failure()
        }
    }

    private fun readChunkBytes(file: File, offset: Long, length: Int): ByteArray {
        val raf = RandomAccessFile(file, "r")
        raf.seek(offset)
        val data = ByteArray(length)
        raf.readFully(data)
        raf.close()
        return data
    }

    private fun uploadFileDirectly(file: File, backendUrl: String): String? {
        Log.i(TAG, "Uploading cover photo directly to $backendUrl/upload")
        val fileRequestBody = RequestBody.create("image/*".toMediaTypeOrNull(), file)
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, fileRequestBody)
            .addFormDataPart("image", file.name, fileRequestBody)
            .build()

        val request = Request.Builder()
            .url("$backendUrl/upload")
            .post(multipartBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val bodyStr = response.body?.string() ?: ""
                Log.i(TAG, "Cover upload response: HTTP $code - $bodyStr")
                if (!response.isSuccessful) {
                    Log.e(TAG, "Cover upload failed with HTTP $code: $bodyStr")
                    null
                } else {
                    val json = JSONObject(bodyStr)
                    val url = json.optString("secure_url", null) ?: json.optString("url", null)
                    Log.i(TAG, "Cover upload secure_url received: $url")
                    url
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cover upload exception: ${e.message}", e)
            null
        }
    }

    private fun uploadChunk(
        chunkBytes: ByteArray,
        uploadId: String,
        contentRange: String,
        backendUrl: String,
        fileOriginalName: String
    ): JSONObject? {
        Log.i(TAG, "Sending chunk to $backendUrl/upload-chunk (Upload-ID: $uploadId, Content-Range: $contentRange)")
        val mediaType = "application/octet-stream".toMediaTypeOrNull()
        val rawRequestBody = RequestBody.create(mediaType, chunkBytes)
        
        val request = Request.Builder()
            .url("$backendUrl/upload-chunk")
            .header("X-Unique-Upload-Id", uploadId)
            .header("Content-Range", contentRange)
            .post(rawRequestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val bodyStr = response.body?.string() ?: ""
                Log.i(TAG, "Backend response code: HTTP $code, body: $bodyStr")

                if (response.isSuccessful) {
                    return JSONObject(bodyStr)
                } else if (code != 404) {
                    val errorMsg = "HTTP $code: $bodyStr"
                    Log.e(TAG, "Backend API error on /upload-chunk: $errorMsg")
                    throw java.io.IOException(errorMsg)
                }
            }
        } catch (e: java.io.IOException) {
            if (e.message?.contains("404") != true) throw e
        }

        // Fallback to multipart /upload endpoint if /upload-chunk is 404
        Log.i(TAG, "/upload-chunk returned 404. Falling back to multipart $backendUrl/upload")
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileOriginalName, rawRequestBody)
            .addFormDataPart("video", fileOriginalName, rawRequestBody)
            .build()

        val fallbackRequest = Request.Builder()
            .url("$backendUrl/upload")
            .header("X-Unique-Upload-Id", uploadId)
            .header("Content-Range", contentRange)
            .post(multipartBody)
            .build()

        client.newCall(fallbackRequest).execute().use { response ->
            val code = response.code
            val bodyStr = response.body?.string() ?: ""
            Log.i(TAG, "Fallback /upload response code: HTTP $code, body: $bodyStr")

            if (!response.isSuccessful) {
                val errorMsg = "HTTP $code: $bodyStr"
                Log.e(TAG, "Backend API error on /upload: $errorMsg")
                throw java.io.IOException(errorMsg)
            }

            return JSONObject(bodyStr)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Uploading to WholeTV")
            .setContentText("Uploading video in background...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(100, 0, false)
            .setOnlyAlertOnce(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Uploads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for WholeTV background video uploads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(text: String, progressPercent: Int) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Uploading to WholeTV")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(progressPercent < 100)
            .setProgress(100, progressPercent, progressPercent == 0 && text.contains("Calculating"))
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024f
        val mb = kb / 1024f
        val gb = mb / 1024f
        return when {
            gb >= 1.0f -> String.format("%.2f GB", gb)
            mb >= 1.0f -> String.format("%.2f MB", mb)
            else -> String.format("%.2f KB", kb)
        }
    }

    private fun formatSpeed(bytesPerSec: Float): String {
        val kb = bytesPerSec / 1024f
        val mb = kb / 1024f
        return when {
            mb >= 1.0f -> String.format("%.2f MB/s", mb)
            else -> String.format("%.2f KB/s", kb)
        }
    }
}
class ProgressRequestBody(
    private val mediaType: okhttp3.MediaType?,
    private val inputStream: InputStream,
    private val contentLength: Long,
    private val onProgress: (bytesWritten: Long) -> Unit
) : RequestBody() {
    override fun contentType() = mediaType
    override fun contentLength() = contentLength

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(4096)
        var bytesWritten = 0L
        var read: Int
        inputStream.use { stream ->
            while (stream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                bytesWritten += read
                onProgress(bytesWritten)
            }
        }
    }
}
