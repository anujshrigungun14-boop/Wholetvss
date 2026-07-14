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
                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("file", imageFile.name, imageFile.asRequestBody(mimeType.toMediaTypeOrNull()))
                    .addFormDataPart("upload_preset", "wholetv_upload")
                    .build()
                val request = okhttp3.Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/wholetv/image/upload")
                    .post(requestBody)
                    .build()
                val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
                if (!response.isSuccessful) {
                    val errorMsg = response.body?.string() ?: "Unknown error"
                    onFailure("Cloudinary upload failed: $errorMsg")
                    imageFile.delete()
                    return@launch
                }
                val resBody = response.body?.string() ?: ""
                val json = org.json.JSONObject(resBody)
                val downloadUrl = json.getString("secure_url")
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

    // Upload state variables
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgressValue = MutableStateFlow(0f)
    val uploadProgressValue: StateFlow<Float> = _uploadProgressValue.asStateFlow()

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

    // Custom "Ultra Legendary Fast Upload System" using Cloudinary
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
        if (_isUploading.value) {
            android.util.Log.w("UploadMedia", "An upload is already in progress. Ignoring duplicate trigger.")
            onFailure("An upload is already in progress.")
            return
        }
        android.util.Log.i("UploadMedia", "[STEP 1: File Selection & URI Validation] Starting upload process...")
        if (videoUri == null) {
            onFailure("selected video URI is null.")
            return
        }

        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgressValue.value = 0f

            try {
                withContext(Dispatchers.IO) {
                    val context = context
                    val mimeType = try {
                        context.contentResolver.getType(videoUri) ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                    val isVideoMime = mimeType.startsWith("video/") || 
                            videoUri.toString().endsWith(".mp4", ignoreCase = true) || 
                            videoUri.toString().endsWith(".mkv", ignoreCase = true) || 
                            videoUri.toString().endsWith(".webm", ignoreCase = true)
                    
                    if (!isVideoMime) {
                        withContext(Dispatchers.Main) {
                            _isUploading.value = false
                            onFailure("MIME type validation failed: Selected file is not a video.")
                        }
                        return@withContext
                    }

                    val videoFile = getFileFromUri(context, videoUri)
                    if (videoFile == null || !videoFile.exists() || !videoFile.canRead()) {
                        withContext(Dispatchers.Main) {
                            _isUploading.value = false
                            onFailure("Failed to access or read the selected video file.")
                        }
                        return@withContext
                    }

                    // Initialize OkHttpClient
                    val okHttpClient = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    // Upload Video to Cloudinary
                    val videoProgressBody = ProgressRequestBody(videoFile, mimeType) { progress ->
                        _uploadProgressValue.value = progress * 0.8f
                    }
                    val videoMultipartBody = okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("file", videoFile.name, videoProgressBody)
                        .addFormDataPart("upload_preset", "wholetv_upload")
                        .build()
                    val videoRequest = okhttp3.Request.Builder()
                        .url("https://api.cloudinary.com/v1_1/wholetv/video/upload")
                        .post(videoMultipartBody)
                        .build()

                    android.util.Log.i("UploadMedia", "[STEP 3: OkHttp Request Creation] Creating video upload request to Cloudinary...")
                    android.util.Log.i("UploadMedia", "Endpoint: https://api.cloudinary.com/v1_1/wholetv/video/upload")
                    
                    // Execute with up to 3 retries for temporary network failure
                    var videoAttempts = 0
                    val maxAttempts = 3
                    var videoResponse: okhttp3.Response? = null
                    var uploadSuccess = false
                    while (videoAttempts < maxAttempts && !uploadSuccess) {
                        videoAttempts++
                        try {
                            android.util.Log.i("UploadMedia", "Executing video upload attempt $videoAttempts of $maxAttempts...")
                            val activeCall = okHttpClient.newCall(videoRequest)
                            videoResponse = activeCall.execute()
                            android.util.Log.i("UploadMedia", "Video upload response received. Code: ${videoResponse.code}, Success: ${videoResponse.isSuccessful}")
                            if (videoResponse.isSuccessful) {
                                uploadSuccess = true
                            } else {
                                android.util.Log.w("UploadMedia", "Video upload attempt $videoAttempts failed with code: ${videoResponse.code}")
                                if (videoResponse.code >= 500) {
                                    delay(2000L * videoAttempts)
                                } else {
                                    break
                                }
                            }
                        } catch (e: java.io.IOException) {
                            android.util.Log.e("UploadMedia", "IOException during video upload attempt $videoAttempts", e)
                            if (videoAttempts >= maxAttempts) throw e
                            delay(2000L * videoAttempts)
                        }
                    }

                    if (videoResponse == null || !videoResponse.isSuccessful) {
                        val errorMsg = videoResponse?.body?.string() ?: "Network error or invalid response from Cloudinary."
                        android.util.Log.e("UploadMedia", "Video upload failed completely. Error: $errorMsg")
                        videoFile.delete()
                        withContext(Dispatchers.Main) {
                            _isUploading.value = false
                            onFailure("Cloudinary upload failed: $errorMsg")
                        }
                        return@withContext
                    }

                    val videoResBody = videoResponse.body?.string() ?: ""
                    val videoJson = org.json.JSONObject(videoResBody)
                    val videoUrl = videoJson.getString("secure_url")
                    val publicId = videoJson.getString("public_id")
                    videoFile.delete()

                    // Upload Cover Image (if selected) to Cloudinary
                    var posterUrl = ""
                    if (coverUri != null) {
                        val coverFile = getFileFromUri(context, coverUri)
                        if (coverFile != null && coverFile.exists() && coverFile.canRead()) {
                            val coverMimeType = try {
                                context.contentResolver.getType(coverUri) ?: "image/jpeg"
                            } catch (e: Exception) {
                                "image/jpeg"
                            }
                            val coverProgressBody = ProgressRequestBody(coverFile, coverMimeType) { progress ->
                                _uploadProgressValue.value = 0.8f + (progress * 0.2f)
                            }
                            val coverMultipartBody = okhttp3.MultipartBody.Builder()
                                .setType(okhttp3.MultipartBody.FORM)
                                .addFormDataPart("file", coverFile.name, coverProgressBody)
                                .addFormDataPart("upload_preset", "wholetv_upload")
                                .build()
                            val coverRequest = okhttp3.Request.Builder()
                                .url("https://api.cloudinary.com/v1_1/wholetv/image/upload")
                                .post(coverMultipartBody)
                                .build()

                            android.util.Log.i("UploadMedia", "[STEP 4: Cover Upload] Creating cover upload request to Cloudinary...")
                            var coverAttempts = 0
                            var coverResponse: okhttp3.Response? = null
                            var coverSuccess = false
                            while (coverAttempts < maxAttempts && !coverSuccess) {
                                coverAttempts++
                                try {
                                    android.util.Log.i("UploadMedia", "Executing cover upload attempt $coverAttempts of $maxAttempts...")
                                    val activeCoverCall = okHttpClient.newCall(coverRequest)
                                    coverResponse = activeCoverCall.execute()
                                    android.util.Log.i("UploadMedia", "Cover upload response received. Code: ${coverResponse.code}, Success: ${coverResponse.isSuccessful}")
                                    if (coverResponse.isSuccessful) {
                                        coverSuccess = true
                                    } else {
                                        android.util.Log.w("UploadMedia", "Cover upload attempt $coverAttempts failed with code: ${coverResponse.code}")
                                        if (coverResponse.code >= 500) {
                                            delay(2000L * coverAttempts)
                                        } else {
                                            break
                                        }
                                    }
                                } catch (e: java.io.IOException) {
                                    android.util.Log.e("UploadMedia", "IOException during cover upload attempt $coverAttempts", e)
                                    if (coverAttempts >= maxAttempts) throw e
                                    delay(2000L * coverAttempts)
                                }
                            }

                            if (coverResponse != null && coverResponse.isSuccessful) {
                                val coverResBody = coverResponse.body?.string() ?: ""
                                val coverJson = org.json.JSONObject(coverResBody)
                                posterUrl = coverJson.getString("secure_url")
                            }
                            coverFile.delete()
                        }
                    }

                    if (posterUrl.isEmpty()) {
                        posterUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500"
                    }

                    _uploadProgressValue.value = 1.0f

                    // Insert new MediaItem directly into Room database
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
                        videoUrl = videoUrl,
                        posterUrl = posterUrl,
                        uploaderId = currentUserId,
                        firestoreId = publicId, // Use Cloudinary public ID as unique identifier
                        views = (100..500).random(),
                        likes = 0,
                        shares = 0,
                        downloads = 0,
                        timestamp = System.currentTimeMillis(),
                        genre = genre.trim(),
                        uploaderName = uploaderName.trim(),
                        watchTime = 0L
                    )

                    repository.insertMediaItem(newItem)
                    withContext(Dispatchers.Main) {
                        _isUploading.value = false
                        onSuccess()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isUploading.value = false
                    onFailure(e.localizedMessage ?: "Failed to upload to Cloudinary.")
                }
            }
        }
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
