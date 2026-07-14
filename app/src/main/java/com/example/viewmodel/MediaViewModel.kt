package com.company.wholetv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.company.wholetv.data.Comment
import com.company.wholetv.data.MediaItem
import com.company.wholetv.data.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

class MediaViewModel(private val context: android.content.Context, private val repository: MediaRepository) : ViewModel() {

    // Bottom Tab navigation: "Home", "Streaming", "Categories", "Promotion", "Me"
    private val _currentTab = MutableStateFlow("Home")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // Selected category filter
    private val _selectedCategory = MutableStateFlow("Recommend")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Dynamically generated categories from uploaded items in real-time
    val categories: StateFlow<List<String>> = repository.allMediaItems
        .map { items ->
            val list = items.map { it.category.trim() }.distinct().filter { it.isNotBlank() }.sorted()
            listOf("Recommend") + list
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            listOf("Recommend")
        )

    // Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Active Media Item for detailed view
    private val _selectedMediaId = MutableStateFlow<Int?>(null)
    val selectedMediaId: StateFlow<Int?> = _selectedMediaId.asStateFlow()

    // Expose all media items as a StateFlow
    val allMediaItems: StateFlow<List<MediaItem>> = repository.allMediaItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reactive selection of media item
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedMediaItem: StateFlow<MediaItem?> = _selectedMediaId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.getMediaItemById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Comments for the selected media item
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentComments: StateFlow<List<Comment>> = _selectedMediaId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.getCommentsForMedia(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered media items matching category and search queries
    @OptIn(ExperimentalCoroutinesApi::class)
    val mediaItems: StateFlow<List<MediaItem>> = combine(
        _selectedCategory,
        _searchQuery,
        repository.allMediaItems
    ) { category, query, allItems ->
        var items = allItems
        
        // Filter by category (if category is "Recommend", show all or customized, let's show all items for Recommend)
        if (category != "Recommend") {
            items = items.filter { it.category.equals(category, ignoreCase = true) }
        }
        
        // Filter by search query if non-empty (smart keywords matching across title, description, hashtags, category, genre, languages, and uploaderName)
        if (query.isNotBlank()) {
            val keywords = query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (keywords.isNotEmpty()) {
                items = items.filter { item ->
                    keywords.any { keyword ->
                        item.title.contains(keyword, ignoreCase = true) ||
                        item.description.contains(keyword, ignoreCase = true) ||
                        item.hashtags.contains(keyword, ignoreCase = true) ||
                        item.category.contains(keyword, ignoreCase = true) ||
                        item.genre.contains(keyword, ignoreCase = true) ||
                        item.languages.contains(keyword, ignoreCase = true) ||
                        item.uploaderName.contains(keyword, ignoreCase = true)
                    }
                }
            }
        }
        items
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic Search Page Flows
    private val _searchQueryText = MutableStateFlow("")
    val searchQueryText: StateFlow<String> = _searchQueryText.asStateFlow()

    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()

    fun setSearchQueryText(query: String) {
        _searchQueryText.value = query
        if (query.isNotBlank()) {
            viewModelScope.launch {
                _isSearchLoading.value = true
                delay(300) // smooth typing loading effect
                _isSearchLoading.value = false
            }
        }
    }

    val searchResults: StateFlow<List<MediaItem>> = combine(
        _searchQueryText,
        repository.allMediaItems
    ) { query, allItems ->
        if (query.isBlank()) {
            return@combine emptyList()
        }
        val keywords = query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (keywords.isEmpty()) return@combine emptyList()

        allItems.filter { item ->
            keywords.any { keyword ->
                item.title.contains(keyword, ignoreCase = true) ||
                item.description.contains(keyword, ignoreCase = true) ||
                item.hashtags.contains(keyword, ignoreCase = true) ||
                item.category.contains(keyword, ignoreCase = true) ||
                item.genre.contains(keyword, ignoreCase = true) ||
                item.languages.contains(keyword, ignoreCase = true) ||
                item.uploaderName.contains(keyword, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Recommendation Sections
    val trendingItems: StateFlow<List<MediaItem>> = repository.allMediaItems
        .map { items -> items.sortedByDescending { it.views }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestUploads: StateFlow<List<MediaItem>> = repository.allMediaItems
        .map { items -> items.sortedByDescending { it.timestamp }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recommendedItems: StateFlow<List<MediaItem>> = repository.allMediaItems
        .map { items -> items.sortedByDescending { it.rating }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val popularThisWeek: StateFlow<List<MediaItem>> = repository.allMediaItems
        .map { items -> items.sortedByDescending { it.likes }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<MediaItem>> = repository.allMediaItems
        .map { items -> items.filter { it.watchTime > 0 || it.views > 200 }.sortedByDescending { it.timestamp }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Slides banner flow (isSlide = 1)
    @OptIn(ExperimentalCoroutinesApi::class)
    val slides: StateFlow<List<MediaItem>> = combine(_selectedCategory, repository.slides) { category, allSlides ->
        // Return slides of the current category, or all slides if "Recommend" is selected
        if (category == "Recommend") {
            allSlides
        } else {
            allSlides.filter { it.category.equals(category, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Platform in streaming view: "Netflix", "Disney+", "Prime Video", "All"
    private val _selectedPlatform = MutableStateFlow("All")
    val selectedPlatform: StateFlow<String> = _selectedPlatform.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val streamingItems: StateFlow<List<MediaItem>> = combine(
        _selectedPlatform,
        repository.allMediaItems
    ) { platform, allItems ->
        if (platform == "All") {
            allItems.filter { it.streamingPlatform != "None" }
        } else {
            allItems.filter { it.streamingPlatform.equals(platform, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Simulated ultra-fast download status & progress
    // ID -> Progress (0.0 to 1.0)
    private val _downloadProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, Float>> = _downloadProgress.asStateFlow()

    // ID -> Status ("None", "Downloading", "Completed")
    private val _downloadStatus = MutableStateFlow<Map<Int, String>>(emptyMap())
    val downloadStatus: StateFlow<Map<Int, String>> = _downloadStatus.asStateFlow()

    // Persistent User Identity using SharedPreferences
    private val sharedPrefs = context.getSharedPreferences("wholetv_prefs", android.content.Context.MODE_PRIVATE)
    val currentUserId: String
        get() = auth?.currentUser?.uid ?: (sharedPrefs.getString("user_id", null) ?: run {
            val newId = java.util.UUID.randomUUID().toString()
            sharedPrefs.edit().putString("user_id", newId).apply()
            newId
        })

    // Firebase Authentication
    val auth: com.google.firebase.auth.FirebaseAuth? by lazy {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance()
        } catch (t: Throwable) {
            android.util.Log.e("FirebaseInit", "FirebaseAuth could not be initialized", t)
            null
        }
    }

    val currentUser: com.google.firebase.auth.FirebaseUser?
        get() = auth?.currentUser

    private val _isUserLoggedIn = MutableStateFlow(auth?.currentUser != null)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

    fun checkUserLoggedIn() {
        _isUserLoggedIn.value = auth?.currentUser != null
    }

    fun loginUser(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val a = auth ?: run {
            onFailure("Firebase Authentication is not available.")
            return
        }
        viewModelScope.launch {
            try {
                a.signInWithEmailAndPassword(email, password).await()
                _isUserLoggedIn.value = true
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Failed to sign in.")
            }
        }
    }

    fun signUpUser(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val a = auth ?: run {
            onFailure("Firebase Authentication is not available.")
            return
        }
        viewModelScope.launch {
            try {
                a.createUserWithEmailAndPassword(email, password).await()
                _isUserLoggedIn.value = true
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Failed to sign up.")
            }
        }
    }

    fun logoutUser() {
        auth?.signOut()
        _isUserLoggedIn.value = false
        _userBio.value = "Love watching amazing content on wholeTV!"
        _userUsername.value = "@streamer"
    }

    // Customizable User Profile states
    private val _userBio = MutableStateFlow(sharedPrefs.getString("user_bio", "Love watching amazing content on wholeTV!") ?: "")
    val userBio: StateFlow<String> = _userBio.asStateFlow()

    private val _userUsername = MutableStateFlow(sharedPrefs.getString("user_username", "@streamer") ?: "")
    val userUsername: StateFlow<String> = _userUsername.asStateFlow()

    private fun getFileFromUri(context: android.content.Context, uri: android.net.Uri): File? {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "video/mp4"
            val extension = when {
                mimeType.contains("video/mp4") -> "mp4"
                mimeType.contains("video/mkv") -> "mkv"
                mimeType.contains("video/webm") -> "webm"
                mimeType.contains("image/jpeg") -> "jpg"
                mimeType.contains("image/jpg") -> "jpg"
                mimeType.contains("image/png") -> "png"
                else -> "bin"
            }
            val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}.$extension")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("UploadMedia", "Failed to copy URI to temp file", e)
            null
        }
    }

    fun updateUserProfile(displayName: String, username: String, bio: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val u = auth?.currentUser
        viewModelScope.launch {
            try {
                if (u != null) {
                    val updates = com.google.firebase.auth.userProfileChangeRequest {
                        this.displayName = displayName
                    }
                    u.updateProfile(updates).await()
                }

                sharedPrefs.edit()
                    .putString("user_display_name", displayName)
                    .putString("user_username", username)
                    .putString("user_bio", bio)
                    .apply()
                _userBio.value = bio
                _userUsername.value = username
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Failed to update user profile.")
            }
        }
    }

    fun uploadProfilePicture(uri: android.net.Uri, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        val u = auth?.currentUser
        viewModelScope.launch {
            try {
                val imageFile = getFileFromUri(context, uri)
                if (imageFile == null || !imageFile.exists() || !imageFile.canRead()) {
                    onFailure("Failed to access or read the selected image file.")
                    return@launch
                }
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val okHttpClient = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                android.util.Log.i("UploadProfile", "Starting robust profile picture upload...")
                val result = withContext(Dispatchers.IO) {
                    performCloudinaryUpload(
                        okHttpClient = okHttpClient,
                        file = imageFile,
                        mimeType = mimeType,
                        resourceType = "image",
                        onProgress = null
                    )
                }
                val downloadUrl = result.first
                imageFile.delete()

                if (u != null) {
                    val updates = com.google.firebase.auth.userProfileChangeRequest {
                        this.photoUri = android.net.Uri.parse(downloadUrl)
                    }
                    u.updateProfile(updates).await()
                }
                
                sharedPrefs.edit().putString("user_photo_url", downloadUrl).apply()
                onSuccess(downloadUrl)
            } catch (e: Exception) {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                android.util.Log.e("UploadProfile", "Failed to upload profile picture:\n$sw")
                onFailure(e.localizedMessage ?: "Failed to upload profile image.")
            }
        }
    }

    // Dynamic Watch Time Tracking
    fun addWatchTime(id: Int, seconds: Long) {
        viewModelScope.launch {
            val media = repository.getMediaItemById(id).firstOrNull()
            if (media != null) {
                val updated = media.copy(watchTime = media.watchTime + seconds)
                repository.insertMediaItem(updated)
            }
        }
    }

    // Upload state variables (delegated to WorkManager-backed UploadManager)
    private val _isUploading = MutableStateFlow(false)
    private val _uploadProgressValue = MutableStateFlow(0f)
    val isUploading: StateFlow<Boolean> = com.example.data.UploadManager.isUploading
    val uploadProgressValue: StateFlow<Float> = com.example.data.UploadManager.progress
    val uploadSpeed: StateFlow<String> = com.example.data.UploadManager.speedString
    val uploadedSize: StateFlow<String> = com.example.data.UploadManager.uploadedSizeString
    val totalSize: StateFlow<String> = com.example.data.UploadManager.totalSizeString
    val remainingSize: StateFlow<String> = com.example.data.UploadManager.remainingSizeString
    val uploadEta: StateFlow<String> = com.example.data.UploadManager.etaString
    val uploadTitle: StateFlow<String> = com.example.data.UploadManager.uploadTitle
    val uploadStatus: StateFlow<String> = com.example.data.UploadManager.uploadStatus
    val uploadError: StateFlow<String?> = com.example.data.UploadManager.error

    init {
        // Seed database locally if empty
        viewModelScope.launch {
            if (repository.getCount() == 0) {
                seedDatabase()
            }
            // Active deletion of older demo items that have empty firestoreId
            try {
                repository.deleteByFirestoreId("")
                android.util.Log.i("MediaViewModel", "Cleared old demo/seeded items successfully.")
            } catch (e: Exception) {
                android.util.Log.e("MediaViewModel", "Failed to clear old demo items", e)
            }
        }
    }

    fun setTab(tab: String) {
        _currentTab.value = tab
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectMedia(id: Int?) {
        _selectedMediaId.value = id
        if (id != null) {
            viewModelScope.launch {
                repository.incrementViews(id)
            }
        }
    }

    fun selectPlatform(platform: String) {
        _selectedPlatform.value = platform
    }

    fun likeMedia(id: Int) {
        viewModelScope.launch {
            repository.incrementLikes(id)
        }
    }

    fun shareMedia(id: Int, onShareText: (String) -> Unit) {
        viewModelScope.launch {
            repository.incrementShares(id)
            val media = repository.getMediaItemById(id).firstOrNull()
            if (media != null) {
                val shareText = "🎥 Check out this amazing show on wholeTV!\n\n" +
                        "Title: ${media.title}\n" +
                        "Description: ${media.description}\n" +
                        "Rating: ⭐ ${media.rating}\n" +
                        "Audio Languages: ${media.languages}\n" +
                        "Quality: ${media.qualities}\n" +
                        "Hashtags: ${media.hashtags}\n\n" +
                        "Watch now on wholeTV - Ultra Legendary Fast Streaming!"
                onShareText(shareText)
            }
        }
    }

    fun submitComment(mediaItemId: Int, userName: String, text: String) {
        if (userName.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            val comment = Comment(
                mediaItemId = mediaItemId,
                userName = userName.trim(),
                text = text.trim()
            )
            repository.insertComment(comment)
        }
    }

    // Real Range-Request Pauseable & Resumeable Download Manager
    private val downloadJobs = mutableMapOf<Int, Job>()
    private val downloadedBytesMap = mutableMapOf<Int, Long>()
    private val totalBytesMap = mutableMapOf<Int, Long>()

    fun startUltraFastDownload(id: Int) {
        val currentStatus = _downloadStatus.value[id] ?: "None"
        if (currentStatus == "Downloading" || currentStatus == "Completed") return

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                _downloadStatus.value = _downloadStatus.value + (id to "Downloading")
                _downloadProgress.value = _downloadProgress.value + (id to 0f)
                
                val media = repository.getMediaItemById(id).firstOrNull() ?: throw Exception("Media item not found")
                val videoUrl = media.videoUrl
                if (videoUrl.isBlank()) throw Exception("No video URL found for this content")

                val downloadsDir = File(context.filesDir, "downloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val localFile = File(downloadsDir, "$id.mp4")
                var startBytes = 0L
                if (localFile.exists() && currentStatus == "Paused") {
                    startBytes = localFile.length()
                } else if (localFile.exists()) {
                    localFile.delete()
                }

                val url = URL(videoUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 20000

                if (startBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$startBytes-")
                }

                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                    throw Exception("Server returned HTTP response: $responseCode")
                }

                val contentLength = connection.contentLengthLong
                val totalBytes = if (startBytes > 0) {
                    startBytes + contentLength
                } else {
                    contentLength
                }

                totalBytesMap[id] = totalBytes
                downloadedBytesMap[id] = startBytes

                val randomAccessFile = RandomAccessFile(localFile, "rw")
                randomAccessFile.seek(startBytes)

                val inputStream = connection.inputStream
                val buffer = ByteArray(16384)
                var bytesRead: Int
                var currentBytes = startBytes

                while (true) {
                    if (_downloadStatus.value[id] == "Paused") {
                        break
                    }

                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    randomAccessFile.write(buffer, 0, bytesRead)
                    currentBytes += bytesRead
                    downloadedBytesMap[id] = currentBytes

                    if (totalBytes > 0) {
                        val progress = currentBytes.toFloat() / totalBytes
                        _downloadProgress.value = _downloadProgress.value + (id to progress)
                    }
                }

                randomAccessFile.close()
                inputStream.close()
                connection.disconnect()

                if (_downloadStatus.value[id] == "Paused") {
                    android.util.Log.i("DownloadManager", "Download paused for media ID: $id at $currentBytes bytes")
                } else {
                    _downloadStatus.value = _downloadStatus.value + (id to "Completed")
                    _downloadProgress.value = _downloadProgress.value + (id to 1.0f)
                    
                    val updated = media.copy(
                        localFilePath = localFile.absolutePath,
                        isDownloaded = true
                    )
                    repository.insertMediaItem(updated)
                    repository.incrementDownloads(id)
                    android.util.Log.i("DownloadManager", "Download completed successfully for media ID: $id")
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadManager", "Download failed for media ID: $id", e)
                _downloadStatus.value = _downloadStatus.value + (id to "Failed")
            }
        }
        downloadJobs[id] = job
    }

    fun pauseDownload(id: Int) {
        _downloadStatus.value = _downloadStatus.value + (id to "Paused")
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)
    }

    fun resumeDownload(id: Int) {
        startUltraFastDownload(id)
    }

    fun deleteDownload(id: Int) {
        downloadJobs[id]?.cancel()
        downloadJobs.remove(id)
        
        _downloadStatus.value = _downloadStatus.value + (id to "None")
        _downloadProgress.value = _downloadProgress.value + (id to 0f)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = File(context.filesDir, "downloads")
                val localFile = File(downloadsDir, "$id.mp4")
                if (localFile.exists()) {
                    localFile.delete()
                }
                val media = repository.getMediaItemById(id).firstOrNull()
                if (media != null) {
                    val updated = media.copy(
                        localFilePath = null,
                        isDownloaded = false
                    )
                    repository.insertMediaItem(updated)
                }
                android.util.Log.i("DownloadManager", "Deleted download for media ID: $id")
            } catch (e: Exception) {
                android.util.Log.e("DownloadManager", "Failed to delete download for media ID: $id", e)
            }
        }
    }

    private fun prepareUploadFile(context: android.content.Context, uri: android.net.Uri, isVideo: Boolean): File? {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: (if (isVideo) "video/mp4" else "image/jpeg")
            val ext = if (isVideo) "mp4" else "jpg"
            
            val dir = File(context.cacheDir, "uploads").apply { mkdirs() }
            val tempFile = File(dir, "up_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.$ext")
            
            resolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            android.util.Log.e("UploadMedia", "Failed to cache file for background upload", e)
            null
        }
    }

    // Modern background upload enqueuer using WorkManager and safe scoped caching
    fun uploadMedia(
        title: String,
        description: String,
        category: String,
        hashtags: String,
        qualities: String,
        languages: String,
        rating: Double,
        isSlide: Boolean,
        badge: String,
        streamingPlatform: String,
        videoUri: android.net.Uri?,
        coverUri: android.net.Uri?,
        genre: String = "Action",
        uploaderName: String = "Anonymous",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (videoUri == null) {
            onFailure("Please select a video file.")
            return
        }
        
        viewModelScope.launch {
            try {
                val context = context
                // Cache files first to ensure background accessibility
                val videoFile = withContext(Dispatchers.IO) {
                    prepareUploadFile(context, videoUri, true)
                }
                if (videoFile == null || !videoFile.exists()) {
                    onFailure("Failed to cache video file. Make sure it is readable.")
                    return@launch
                }
                
                val coverFile = if (coverUri != null) {
                    withContext(Dispatchers.IO) {
                        prepareUploadFile(context, coverUri, false)
                    }
                } else null
                
                val sharedPrefs = context.getSharedPreferences("wholetv_prefs", android.content.Context.MODE_PRIVATE)
                val backendUrl = sharedPrefs.getString("backend_url", "https://wholetv-backend.onrender.com/api") ?: "https://wholetv-backend.onrender.com/api"
                
                val uploadId = java.util.UUID.randomUUID().toString()
                
                // Reset states
                com.example.data.UploadManager.reset()
                com.example.data.UploadManager.updateProgress(
                    isUploading = true,
                    progress = 0f,
                    speed = "0 KB/s",
                    uploadedSize = "0 MB",
                    totalSize = "Calculating...",
                    remainingSize = "Calculating...",
                    eta = "Calculating...",
                    title = title,
                    status = "Starting background worker..."
                )
                
                // Build WorkManager inputs
                val uploadData = androidx.work.workDataOf(
                    "videoPath" to videoFile.absolutePath,
                    "coverPath" to coverFile?.absolutePath,
                    "title" to title,
                    "description" to description,
                    "category" to category,
                    "hashtags" to hashtags,
                    "qualities" to qualities,
                    "languages" to languages,
                    "rating" to rating,
                    "isSlide" to isSlide,
                    "badge" to badge,
                    "streamingPlatform" to streamingPlatform,
                    "genre" to genre,
                    "uploaderName" to uploaderName,
                    "currentUserId" to currentUserId,
                    "backendUrl" to backendUrl,
                    "uploadId" to uploadId
                )
                
                val uploadWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.company.wholetv.data.UploadWorker>()
                    .setInputData(uploadData)
                    .addTag("upload_work_$uploadId")
                    .build()
                
                androidx.work.WorkManager.getInstance(context).enqueue(uploadWorkRequest)
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Failed to start background upload.")
            }
        }
    }

    private suspend fun executeUploadPipelineWithJson(
        uploadId: String,
        json: org.json.JSONObject,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        _isUploading.value = true
        _uploadProgressValue.value = 0f

        val filePath = json.getString("filePath")
        val coverFilePath = json.optString("coverFilePath", "")
        val title = json.getString("title")
        val description = json.getString("description")
        val category = json.getString("category")
        val hashtags = json.getString("hashtags")
        val qualities = json.getString("qualities")
        val languages = json.getString("languages")
        val rating = json.getDouble("rating")
        val isSlide = json.getBoolean("isSlide")
        val badge = json.getString("badge")
        val streamingPlatform = json.getString("streamingPlatform")
        val genre = json.getString("genre")
        val uploaderName = json.getString("uploaderName")
        val currentUserId = json.getString("currentUserId")
        var startByte = json.optLong("startByte", 0L)

        val videoFile = File(filePath)
        val coverFile = if (coverFilePath.isNotEmpty()) File(coverFilePath) else null

        try {
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // 1. Upload Video
            android.util.Log.i("UploadMedia", "Executing upload pipeline for $title (ID: $uploadId). File: ${videoFile.absolutePath}, Start: $startByte")
            val totalSize = videoFile.length()
            val chunkSize = 6 * 1024 * 1024 // 6 MB chunk size
            var lastSecureUrl = ""
            var lastPublicId = ""

            if (totalSize < 5 * 1024 * 1024) {
                // Short file - direct single upload
                val result = withContext(Dispatchers.IO) {
                    performSingleDirectUpload(okHttpClient, videoFile, "video/mp4", "video")
                }
                lastSecureUrl = result.first
                lastPublicId = result.second
                _uploadProgressValue.value = 0.8f
            } else {
                // Large file - chunked/resumable upload
                while (startByte < totalSize) {
                    val endByte = minOf(startByte + chunkSize, totalSize)
                    val currentChunkSize = (endByte - startByte).toInt()
                    val chunkBytes = ByteArray(currentChunkSize)

                    withContext(Dispatchers.IO) {
                        java.io.RandomAccessFile(videoFile, "r").use { raf ->
                            raf.seek(startByte)
                            raf.readFully(chunkBytes)
                        }
                    }

                    // Retry chunk upload if it fails
                    var chunkSuccess = false
                    var attempt = 0
                    val maxAttempts = 5
                    var responseBody = ""
                    var lastChunkEx: Exception? = null

                    while (attempt < maxAttempts && !chunkSuccess) {
                        attempt++
                        try {
                            val url = "https://api.cloudinary.com/v1_1/wholetv/auto/upload"
                            val octetType = "application/octet-stream".toMediaTypeOrNull()
                            val requestBody = okhttp3.RequestBody.create(octetType, chunkBytes, 0, currentChunkSize)

                            val multipartBody = okhttp3.MultipartBody.Builder()
                                .setType(okhttp3.MultipartBody.FORM)
                                .addFormDataPart("file", videoFile.name, requestBody)
                                .addFormDataPart("upload_preset", "wholetv_upload")
                                .build()

                            val contentRangeHeader = "bytes $startByte-${endByte - 1}/$totalSize"
                            val request = okhttp3.Request.Builder()
                                .url(url)
                                .header("Accept", "application/json")
                                .header("X-Unique-Upload-Id", uploadId)
                                .header("Content-Range", contentRangeHeader)
                                .post(multipartBody)
                                .build()

                            android.util.Log.i("UploadMedia", "Uploading chunk $contentRangeHeader (Attempt $attempt)...")
                            val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
                            responseBody = response.body?.string() ?: ""

                            if (response.isSuccessful) {
                                chunkSuccess = true
                                val jsonRes = org.json.JSONObject(responseBody)
                                lastSecureUrl = jsonRes.optString("secure_url", "")
                                lastPublicId = jsonRes.optString("public_id", "")
                                android.util.Log.i("UploadMedia", "Chunk uploaded successfully! Content-Range: $contentRangeHeader")
                            } else {
                                android.util.Log.e("UploadMedia", "Chunk upload attempt $attempt failed with status ${response.code}. Response: $responseBody")
                                lastChunkEx = Exception("HTTP ${response.code}: $responseBody")
                                delay(2000L * attempt)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("UploadMedia", "IOException/Exception on chunk upload attempt $attempt", e)
                            lastChunkEx = e
                            delay(2000L * attempt)
                        }
                    }

                    if (!chunkSuccess) {
                        throw Exception("Failed to upload chunk $startByte-${endByte - 1} after $maxAttempts attempts. Error: ${lastChunkEx?.localizedMessage}")
                    }

                    startByte = endByte
                    updatePendingUploadProgress(uploadId, startByte)
                    _uploadProgressValue.value = (startByte.toFloat() / totalSize) * 0.8f
                }
            }

            // 2. Upload Cover Artwork (if exists)
            var posterUrl = ""
            if (coverFile != null && coverFile.exists()) {
                android.util.Log.i("UploadMedia", "Uploading cover artwork: ${coverFile.absolutePath}")
                val result = withContext(Dispatchers.IO) {
                    performSingleDirectUpload(okHttpClient, coverFile, "image/jpeg", "image")
                }
                posterUrl = result.first
                _uploadProgressValue.value = 0.95f
            }

            if (posterUrl.isEmpty()) {
                posterUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500"
            }

            _uploadProgressValue.value = 1.0f

            // 3. Create MediaItem and save to Room database
            val newItem = MediaItem(
                title = title.trim(),
                description = description.trim(),
                category = category.trim(),
                hashtags = hashtags.trim(),
                qualities = qualities.trim(),
                languages = languages.trim(),
                rating = rating,
                isSlide = isSlide,
                badge = badge.trim(),
                streamingPlatform = streamingPlatform,
                videoUrl = lastSecureUrl,
                posterUrl = posterUrl,
                uploaderId = currentUserId,
                firestoreId = lastPublicId,
                views = (100..500).random(),
                likes = 0,
                shares = 0,
                downloads = 0,
                timestamp = System.currentTimeMillis(),
                genre = genre.trim(),
                uploaderName = uploaderName.trim(),
                watchTime = 0L
            )

            withContext(Dispatchers.IO) {
                repository.insertMediaItem(newItem)
                // Delete local temp files
                try {
                    videoFile.delete()
                    coverFile?.delete()
                } catch (e: Exception) {
                    // Ignore file delete errors
                }
            }

            removePendingUpload(uploadId)
            android.util.Log.i("UploadMedia", "Upload pipeline successfully finished for $title!")

            withContext(Dispatchers.Main) {
                _isUploading.value = false
                onSuccess?.invoke()
            }
        } catch (e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            android.util.Log.e("UploadMedia", "Pipeline failure for $title:\n$sw")
            withContext(Dispatchers.Main) {
                _isUploading.value = false
                onFailure?.invoke(e.localizedMessage ?: "Failed to upload to Cloudinary.")
            }
        }
    }

    private suspend fun performSingleDirectUpload(
        okHttpClient: okhttp3.OkHttpClient,
        file: java.io.File,
        mimeType: String,
        resourceType: String
    ): Pair<String, String> {
        val cloudName = "wholetv"
        val preset = "wholetv_upload"
        val url = "https://api.cloudinary.com/v1_1/$cloudName/auto/upload"

        val mediaType = mimeType.toMediaTypeOrNull()
        val requestBody = okhttp3.RequestBody.create(mediaType, file)

        val multipartBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", file.name, requestBody)
            .addFormDataPart("upload_preset", preset)
            .build()

        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(multipartBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (response.isSuccessful) {
            val json = org.json.JSONObject(responseBody)
            return Pair(json.getString("secure_url"), json.optString("public_id", ""))
        } else {
            throw Exception("HTTP ${response.code}: $responseBody")
        }
    }

    private fun updatePendingUploadProgress(uploadId: String, startByte: Long) {
        val prefs = context.getSharedPreferences("wholetv_pending_uploads", android.content.Context.MODE_PRIVATE)
        val existingString = prefs.getString(uploadId, null) ?: return
        try {
            val json = org.json.JSONObject(existingString)
            json.put("startByte", startByte)
            prefs.edit().putString(uploadId, json.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("MediaViewModel", "Error updating progress for $uploadId", e)
        }
    }

    private fun removePendingUpload(uploadId: String) {
        val prefs = context.getSharedPreferences("wholetv_pending_uploads", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(uploadId).apply()
    }

    private fun resumePendingUploads() {
        val prefs = context.getSharedPreferences("wholetv_pending_uploads", android.content.Context.MODE_PRIVATE)
        val allEntries = prefs.all
        if (allEntries.isEmpty()) return
        
        android.util.Log.i("MediaViewModel", "Found ${allEntries.size} pending uploads to resume.")
        for ((uploadId, jsonStr) in allEntries) {
            if (jsonStr !is String) continue
            try {
                val json = org.json.JSONObject(jsonStr)
                val filePath = json.getString("filePath")
                val file = File(filePath)
                if (!file.exists() || file.length() == 0L) {
                    android.util.Log.w("MediaViewModel", "Pending upload file $filePath does not exist. Removing.")
                    prefs.edit().remove(uploadId).apply()
                    continue
                }
                
                // Only run one active resume pipeline if nothing is currently uploading
                if (!_isUploading.value) {
                    android.util.Log.i("MediaViewModel", "Resuming pending upload with ID $uploadId...")
                    viewModelScope.launch {
                        executeUploadPipelineWithJson(uploadId, json)
                    }
                    break // Since we only do one at a time
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaViewModel", "Error resuming pending upload $uploadId", e)
            }
        }
    }

    private suspend fun performCloudinaryUpload(
        okHttpClient: okhttp3.OkHttpClient,
        file: java.io.File,
        mimeType: String,
        resourceType: String,
        onProgress: ((Float) -> Unit)?
    ): Pair<String, String> {
        val cloudName = "wholetv"
        val preset = "wholetv_upload"
        val url = "https://api.cloudinary.com/v1_1/$cloudName/auto/upload"

        android.util.Log.i("UploadMedia", "Starting Cloudinary upload to url: $url with preset: $preset")

        val requestBody = if (onProgress != null) {
            ProgressRequestBody(file, mimeType) { progress ->
                onProgress(progress)
            }
        } else {
            file.asRequestBody(mimeType.toMediaTypeOrNull())
        }

        val multipartBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", file.name, requestBody)
            .addFormDataPart("upload_preset", preset)
            .build()

        val request = okhttp3.Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(multipartBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val statusCode = response.code
            val headers = response.headers
            val xCldError = headers["X-Cld-Error"]
            val responseBody = response.body?.string() ?: ""

            android.util.Log.i("UploadMedia", "Cloudinary Response. Status Code: $statusCode")
            android.util.Log.i("UploadMedia", "Response Headers: $headers")
            if (!xCldError.isNullOrBlank()) {
                android.util.Log.e("UploadMedia", "X-Cld-Error Header: $xCldError")
            }
            android.util.Log.i("UploadMedia", "Response Body:\n$responseBody")

            if (response.isSuccessful) {
                val json = org.json.JSONObject(responseBody)
                val secureUrl = json.getString("secure_url")
                val publicId = json.optString("public_id", "")
                android.util.Log.i("UploadMedia", "Successful Cloudinary upload! URL: $secureUrl, Public ID: $publicId")
                return Pair(secureUrl, publicId)
            } else {
                val extractedError = extractErrorMessageFromCloudinary(responseBody, statusCode, headers)
                val fullDetail = "Cloudinary upload failed (HTTP $statusCode). X-Cld-Error: ${xCldError ?: "none"}. Error Detail: $extractedError. Full Response: $responseBody"
                android.util.Log.e("UploadMedia", fullDetail)
                throw Exception(fullDetail)
            }
        } catch (e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            android.util.Log.e("UploadMedia", "Network/IO Exception during Cloudinary upload:\n$sw")
            throw e
        }
    }

    private fun extractErrorMessageFromCloudinary(responseBody: String, statusCode: Int, headers: okhttp3.Headers): String {
        val xCldError = headers["X-Cld-Error"]
        if (!xCldError.isNullOrBlank()) {
            return "Cloudinary Error (X-Cld-Error): $xCldError (Status: $statusCode)"
        }

        val trimmed = responseBody.trim()
        if (trimmed.startsWith("<") || trimmed.contains("<html", ignoreCase = true)) {
            val titleMatch = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(trimmed)
            val h1Match = Regex("<h1>(.*?)</h1>", RegexOption.IGNORE_CASE).find(trimmed)
            val bodyMatch = Regex("<body>(.*?)</body>", RegexOption.DOT_MATCHES_ALL).find(trimmed)

            val title = titleMatch?.groupValues?.get(1)?.trim() ?: ""
            val h1 = h1Match?.groupValues?.get(1)?.trim() ?: ""
            val body = bodyMatch?.groupValues?.get(1)?.trim()?.replace(Regex("<[^>]*>"), " ")?.trim() ?: ""

            val detail = when {
                title.isNotEmpty() && h1.isNotEmpty() -> "$title - $h1"
                title.isNotEmpty() -> title
                h1.isNotEmpty() -> h1
                body.isNotEmpty() -> {
                    if (body.length > 200) body.substring(0, 197) + "..." else body
                }
                else -> "HTML response"
            }
            return "HTML response (Status: $statusCode): $detail"
        }

        try {
            val json = org.json.JSONObject(trimmed)
            if (json.has("error")) {
                val errorObj = json.getJSONObject("error")
                if (errorObj.has("message")) {
                    return errorObj.getString("message")
                }
            }
        } catch (e: Exception) {
            // Non-JSON format or missing expected keys
        }

        return if (trimmed.length > 300) trimmed.substring(0, 297) + "..." else trimmed
    }

    fun deleteMedia(firestoreId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (firestoreId.isBlank()) return
        viewModelScope.launch {
            try {
                repository.deleteByFirestoreId(firestoreId)
                onSuccess()
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Failed to delete video.")
            }
        }
    }

    private suspend fun seedDatabase() {
        // Clear pre-seeded demo data as requested by user
    }
}

class MediaViewModelFactory(private val context: android.content.Context, private val repository: MediaRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MediaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MediaViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ProgressRequestBody(
    private val file: File,
    private val contentType: String,
    private val onProgress: (progress: Float) -> Unit
) : okhttp3.RequestBody() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun contentType(): okhttp3.MediaType? {
        return contentType.toMediaTypeOrNull()
    }

    override fun contentLength(): Long {
        return file.length()
    }

    override fun writeTo(sink: okio.BufferedSink) {
        val fileLength = file.length()
        val buffer = ByteArray(4096)
        var uploaded: Long = 0
        var lastProgressUpdate = 0L

        file.inputStream().use { fileInputStream ->
            var read: Int
            while (fileInputStream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploaded += read
                val progress = if (fileLength > 0) uploaded.toFloat() / fileLength else 0f
                
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate > 100 || progress == 1.0f) {
                    lastProgressUpdate = now
                    handler.post { onProgress(progress) }
                }
            }
        }
    }
}
