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
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

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

    fun updateUserProfile(displayName: String, username: String, bio: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val u = auth?.currentUser
        if (u == null) {
            // Local mode fallback
            sharedPrefs.edit()
                .putString("user_display_name", displayName)
                .putString("user_username", username)
                .putString("user_bio", bio)
                .apply()
            _userBio.value = bio
            _userUsername.value = username
            onSuccess()
            return
        }

        viewModelScope.launch {
            try {
                // 1. Update Display Name in Firebase Auth
                val updates = com.google.firebase.auth.userProfileChangeRequest {
                    this.displayName = displayName
                }
                u.updateProfile(updates).await()

                // 2. Save username and bio in Firestore and SharedPreferences
                sharedPrefs.edit()
                    .putString("user_username", username)
                    .putString("user_bio", bio)
                    .apply()
                _userBio.value = bio
                _userUsername.value = username

                val db = firestore
                if (db != null) {
                    val userMeta = hashMapOf(
                        "displayName" to displayName,
                        "username" to username,
                        "bio" to bio,
                        "email" to (u.email ?: "")
                    )
                    db.collection("users").document(u.uid).set(userMeta).await()
                }

                onSuccess()
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Failed to update user profile.")
            }
        }
    }

    fun uploadProfilePicture(uri: android.net.Uri, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        val u = auth?.currentUser
        val st = storage
        if (u == null || st == null) {
            // Local fallback
            sharedPrefs.edit().putString("user_photo_url", uri.toString()).apply()
            onSuccess(uri.toString())
            return
        }

        viewModelScope.launch {
            try {
                val ref = st.reference.child("profiles/${u.uid}.jpg")
                val uploadTask = ref.putFile(uri)
                uploadTask.await()
                
                val downloadUrl = ref.downloadUrl.await().toString()
                
                val updates = com.google.firebase.auth.userProfileChangeRequest {
                    this.photoUri = android.net.Uri.parse(downloadUrl)
                }
                u.updateProfile(updates).await()
                
                sharedPrefs.edit().putString("user_photo_url", downloadUrl).apply()
                
                // Sync to user doc in Firestore
                val db = firestore
                if (db != null) {
                    db.collection("users").document(u.uid).update("photoUrl", downloadUrl).await()
                }

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

                val db = firestore
                if (media.firestoreId.isNotEmpty() && db != null) {
                    try {
                        db.collection("media_items").document(media.firestoreId)
                            .update("watchTime", com.google.firebase.firestore.FieldValue.increment(seconds))
                    } catch (e: Exception) {
                        android.util.Log.e("WatchTime", "Failed to increment watchTime in Firestore", e)
                    }
                }
            }
        }
    }

    // Firebase references initialized safely with lazy try-catch to prevent crashes on startup if missing configuration
    private val firestore: com.google.firebase.firestore.FirebaseFirestore? by lazy {
        try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
        } catch (t: Throwable) {
            android.util.Log.e("FirebaseInit", "Firestore could not be initialized, running in Local Offline Mode", t)
            null
        }
    }

    private val storage: com.google.firebase.storage.FirebaseStorage? by lazy {
        try {
            com.google.firebase.storage.FirebaseStorage.getInstance()
        } catch (t: Throwable) {
            android.util.Log.e("FirebaseInit", "FirebaseStorage could not be initialized, running in Local Offline Mode", t)
            null
        }
    }

    val isFirebaseAvailable: Boolean
        get() = firestore != null && storage != null

    // Upload state variables
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgressValue = MutableStateFlow(0f)
    val uploadProgressValue: StateFlow<Float> = _uploadProgressValue.asStateFlow()

    init {
        if (isFirebaseAvailable) {
            // Start Firebase sync to Room database in real-time
            syncFromFirestore()
        } else {
            // Seed database locally if empty since we are running in Local Offline Mode
            viewModelScope.launch {
                if (repository.getCount() == 0) {
                    seedDatabase()
                }
            }
        }
    }

    private fun syncFromFirestore() {
        val db = firestore ?: return
        db.collection("media_items")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseSync", "Listen failed.", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    viewModelScope.launch {
                        val snapshotDocIds = snapshot.documents.map { it.id }.toSet()

                        for (doc in snapshot.documents) {
                            try {
                                val docId = doc.id
                                val title = doc.getString("title") ?: ""
                                val description = doc.getString("description") ?: ""
                                val category = doc.getString("category") ?: "Movies"
                                val hashtags = doc.getString("hashtags") ?: ""
                                val qualities = doc.getString("qualities") ?: "1080p HD"
                                val languages = doc.getString("languages") ?: "English"
                                val posterUrl = doc.getString("posterUrl") ?: ""
                                val videoUrl = doc.getString("videoUrl") ?: ""
                                val uploaderId = doc.getString("uploaderId") ?: ""
                                val rating = doc.getDouble("rating") ?: 8.5
                                val isSlide = doc.getBoolean("isSlide") ?: false
                                val badge = doc.getString("badge") ?: ""
                                val views = doc.getLong("views")?.toInt() ?: 0
                                val likes = doc.getLong("likes")?.toInt() ?: 0
                                val shares = doc.getLong("shares")?.toInt() ?: 0
                                val downloads = doc.getLong("downloads")?.toInt() ?: 0
                                val streamingPlatform = doc.getString("streamingPlatform") ?: "None"
                                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                val genre = doc.getString("genre") ?: "Action"
                                val uploaderName = doc.getString("uploaderName") ?: "Anonymous"
                                val watchTime = doc.getLong("watchTime") ?: 0L

                                val existing = repository.getMediaItemByFirestoreId(docId)
                                if (existing != null) {
                                    val updated = existing.copy(
                                        title = title,
                                        description = description,
                                        category = category,
                                        hashtags = hashtags,
                                        qualities = qualities,
                                        languages = languages,
                                        posterUrl = posterUrl,
                                        videoUrl = videoUrl,
                                        uploaderId = uploaderId,
                                        rating = rating,
                                        isSlide = isSlide,
                                        badge = badge,
                                        views = views,
                                        likes = likes,
                                        shares = shares,
                                        downloads = downloads,
                                        streamingPlatform = streamingPlatform,
                                        timestamp = timestamp,
                                        genre = genre,
                                        uploaderName = uploaderName,
                                        watchTime = watchTime
                                    )
                                    repository.insertMediaItem(updated)
                                } else {
                                    val newItem = MediaItem(
                                        title = title,
                                        description = description,
                                        category = category,
                                        hashtags = hashtags,
                                        qualities = qualities,
                                        languages = languages,
                                        posterUrl = posterUrl,
                                        videoUrl = videoUrl,
                                        uploaderId = uploaderId,
                                        rating = rating,
                                        isSlide = isSlide,
                                        badge = badge,
                                        views = views,
                                        likes = likes,
                                        shares = shares,
                                        downloads = downloads,
                                        streamingPlatform = streamingPlatform,
                                        firestoreId = docId,
                                        timestamp = timestamp,
                                        genre = genre,
                                        uploaderName = uploaderName,
                                        watchTime = watchTime
                                    )
                                    repository.insertMediaItem(newItem)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FirebaseSync", "Error parsing doc: ${doc.id}", e)
                            }
                        }

                        val allLocalItems = repository.allMediaItems.firstOrNull() ?: emptyList()
                        for (localItem in allLocalItems) {
                            if (localItem.firestoreId.isNotEmpty() && !snapshotDocIds.contains(localItem.firestoreId)) {
                                repository.deleteByFirestoreId(localItem.firestoreId)
                            }
                        }
                    }
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

    private var commentsListenerReg: com.google.firebase.firestore.ListenerRegistration? = null

    private fun listenForComments(mediaItemId: Int, firestoreId: String) {
        commentsListenerReg?.remove()
        val db = firestore ?: return
        if (firestoreId.isBlank()) return

        commentsListenerReg = db.collection("media_items")
            .document(firestoreId)
            .collection("comments")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CommentsSync", "Listen for comments failed.", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    viewModelScope.launch {
                        for (doc in snapshot.documents) {
                            try {
                                val userName = doc.getString("userName") ?: "Anonymous"
                                val text = doc.getString("text") ?: ""
                                val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                                // Check if comment already exists locally
                                val existingComments = repository.getCommentsForMedia(mediaItemId).firstOrNull() ?: emptyList()
                                val exists = existingComments.any { it.userName.trim() == userName.trim() && it.text.trim() == text.trim() }
                                if (!exists) {
                                    val newComment = Comment(
                                        mediaItemId = mediaItemId,
                                        userName = userName,
                                        text = text,
                                        timestamp = timestamp
                                    )
                                    repository.insertComment(newComment)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CommentsSync", "Error parsing comment doc", e)
                            }
                        }
                    }
                }
            }
    }

    fun selectMedia(id: Int?) {
        _selectedMediaId.value = id
        if (id != null) {
            viewModelScope.launch {
                repository.incrementViews(id)
                val media = repository.getMediaItemById(id).firstOrNull()
                if (media != null) {
                    val db = firestore
                    if (media.firestoreId.isNotEmpty() && db != null) {
                        try {
                            db.collection("media_items").document(media.firestoreId)
                                .update("views", com.google.firebase.firestore.FieldValue.increment(1))
                        } catch (e: Exception) {
                            android.util.Log.e("selectMedia", "Failed to increment views in Firestore", e)
                        }
                        listenForComments(id, media.firestoreId)
                    }
                }
            }
        } else {
            commentsListenerReg?.remove()
            commentsListenerReg = null
        }
    }

    fun selectPlatform(platform: String) {
        _selectedPlatform.value = platform
    }

    fun likeMedia(id: Int) {
        viewModelScope.launch {
            repository.incrementLikes(id)
            val media = repository.getMediaItemById(id).firstOrNull()
            if (media != null) {
                val db = firestore
                if (media.firestoreId.isNotEmpty() && db != null) {
                    try {
                        db.collection("media_items").document(media.firestoreId)
                            .update("likes", com.google.firebase.firestore.FieldValue.increment(1))
                    } catch (e: Exception) {
                        android.util.Log.e("likeMedia", "Failed to increment likes in Firestore", e)
                    }
                }
            }
        }
    }

    fun shareMedia(id: Int, onShareText: (String) -> Unit) {
        viewModelScope.launch {
            repository.incrementShares(id)
            val media = repository.getMediaItemById(id).firstOrNull()
            if (media != null) {
                val db = firestore
                if (media.firestoreId.isNotEmpty() && db != null) {
                    try {
                        db.collection("media_items").document(media.firestoreId)
                            .update("shares", com.google.firebase.firestore.FieldValue.increment(1))
                    } catch (e: Exception) {
                        android.util.Log.e("shareMedia", "Failed to increment shares in Firestore", e)
                    }
                }
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

            val media = repository.getMediaItemById(mediaItemId).firstOrNull()
            if (media != null) {
                val db = firestore
                if (media.firestoreId.isNotEmpty() && db != null) {
                    try {
                        val commentData = hashMapOf(
                            "userName" to userName.trim(),
                            "text" to text.trim(),
                            "timestamp" to System.currentTimeMillis()
                        )
                        db.collection("media_items")
                            .document(media.firestoreId)
                            .collection("comments")
                            .add(commentData)
                    } catch (e: Exception) {
                        android.util.Log.e("submitComment", "Failed to add comment to Firestore", e)
                    }
                }
            }
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

    // Custom "Ultra Legendary Fast Upload System"
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
        android.util.Log.i("UploadMedia", "[STEP 1: File Selection & URI Validation] Starting upload process...")
        android.util.Log.d("UploadMedia", "Selected Video URI: $videoUri")
        android.util.Log.d("UploadMedia", "Selected Cover URI: $coverUri")

        if (videoUri == null) {
            val errorMsg = "URI validation failed: selected video URI is null."
            android.util.Log.e("UploadMedia", errorMsg)
            onFailure(errorMsg)
            return
        }

        android.util.Log.i("UploadMedia", "[STEP 2: URI Validation Succeeded] Input fields: title='$title', genre='$genre', uploaderName='$uploaderName'")

        viewModelScope.launch {
            _isUploading.value = true
            _uploadProgressValue.value = 0f

            val db = firestore
            val st = storage

            if (db == null || st == null) {
                android.util.Log.w("UploadMedia", "Firebase services are null! Running local fallback mode.")
                try {
                    delay(300)
                    _uploadProgressValue.value = 0.5f
                    delay(300)
                    _uploadProgressValue.value = 1.0f

                    val videoUrl = videoUri.toString()
                    val posterUrl = coverUri?.toString() ?: "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500"

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
                        firestoreId = "local_" + java.util.UUID.randomUUID().toString(),
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
                    _isUploading.value = false
                    android.util.Log.i("UploadMedia", "Successfully added media locally!")
                    onSuccess()
                } catch (e: Exception) {
                    _isUploading.value = false
                    android.util.Log.e("UploadMedia", "Failed to upload locally", e)
                    onFailure(e.localizedMessage ?: "Failed to upload locally.")
                }
                return@launch
            }

            try {
                val uploadId = java.util.UUID.randomUUID().toString()
                android.util.Log.i("UploadMedia", "[STEP 3: Upload Start] Generated upload ID: $uploadId")

                // 1. Upload Video to Firebase Storage
                val videoRef = st.reference.child("videos/$uploadId.mp4")
                android.util.Log.i("UploadMedia", "[STEP 4: StorageReference Path] Video path: ${videoRef.path} (Bucket: ${st.reference.bucket})")
                
                val videoUploadTask = videoRef.putFile(videoUri)

                videoUploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = (taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()) * 0.8f
                    _uploadProgressValue.value = progress
                    android.util.Log.d("UploadMedia", "[STEP 5: Video Upload Progress] ${(progress * 100).toInt()}% uploaded (${taskSnapshot.bytesTransferred}/${taskSnapshot.totalByteCount} bytes)")
                }

                android.util.Log.i("UploadMedia", "Waiting for video upload to complete...")
                val videoSnapshot = videoUploadTask.await()
                android.util.Log.i("UploadMedia", "[STEP 6: Video Upload Completion] Video uploaded successfully to Storage.")

                android.util.Log.i("UploadMedia", "[STEP 7: Download URL Generation] Getting video download URL directly from StorageReference...")
                val videoUrl = videoRef.downloadUrl.await().toString()
                android.util.Log.i("UploadMedia", "Video download URL generated successfully: $videoUrl")

                // 2. Upload Cover Image (if selected) to Firebase Storage
                var posterUrl = ""
                if (coverUri != null) {
                    val coverRef = st.reference.child("covers/$uploadId.jpg")
                    android.util.Log.i("UploadMedia", "Cover StorageReference path: ${coverRef.path}")
                    
                    val coverUploadTask = coverRef.putFile(coverUri)

                    coverUploadTask.addOnProgressListener { taskSnapshot ->
                        val progress = 0.8f + ((taskSnapshot.bytesTransferred.toFloat() / taskSnapshot.totalByteCount.toFloat()) * 0.2f)
                        _uploadProgressValue.value = progress
                        android.util.Log.d("UploadMedia", "Cover upload progress: ${(progress * 100).toInt()}%")
                    }

                    android.util.Log.i("UploadMedia", "Waiting for cover image upload to complete...")
                    coverUploadTask.await()
                    android.util.Log.i("UploadMedia", "Cover uploaded successfully to Storage.")

                    android.util.Log.i("UploadMedia", "Getting cover download URL directly from StorageReference...")
                    posterUrl = coverRef.downloadUrl.await().toString()
                    android.util.Log.i("UploadMedia", "Cover download URL generated: $posterUrl")
                } else {
                    posterUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500"
                    android.util.Log.i("UploadMedia", "No cover image selected. Using fallback poster URL: $posterUrl")
                }

                _uploadProgressValue.value = 1.0f

                // 3. Save Metadata to Cloud Firestore
                val meta = hashMapOf(
                    "title" to title.trim(),
                    "description" to description.trim(),
                    "category" to category.trim(),
                    "hashtags" to hashtags.trim(),
                    "qualities" to qualities.trim(),
                    "languages" to languages.trim(),
                    "rating" to rating,
                    "isSlide" to isSlide,
                    "badge" to badge.trim(),
                    "streamingPlatform" to streamingPlatform,
                    "videoUrl" to videoUrl,
                    "posterUrl" to posterUrl,
                    "uploaderId" to currentUserId,
                    "views" to (100..500).random(),
                    "likes" to 0,
                    "shares" to 0,
                    "downloads" to 0,
                    "timestamp" to System.currentTimeMillis(),
                    "genre" to genre.trim(),
                    "uploaderName" to uploaderName.trim(),
                    "watchTime" to 0L
                )

                android.util.Log.i("UploadMedia", "[STEP 8: Firestore Write] Writing metadata document to 'media_items/$uploadId'...")
                db.collection("media_items").document(uploadId).set(meta).await()
                android.util.Log.i("UploadMedia", "[STEP 9: Firestore Write Success] Metadata written successfully.")

                _isUploading.value = false
                android.util.Log.i("UploadMedia", "[STEP 10: Upload System Success] Entire upload flow completed 100% successfully!")
                onSuccess()
            } catch (e: Exception) {
                _isUploading.value = false
                android.util.Log.e("UploadMedia", "[UPLOAD SYSTEM FAILURE] Direct Firebase Exception encountered:", e)
                val errorMessage = "Upload failed: " + (e.localizedMessage ?: "Unknown Firebase error")
                onFailure(errorMessage)
            }
        }
    }

    fun deleteMedia(firestoreId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (firestoreId.isBlank()) return
        viewModelScope.launch {
            val db = firestore
            val st = storage

            if (db == null || st == null) {
                // LOCAL ONLY FALLBACK MODE
                try {
                    val existing = repository.getMediaItemByFirestoreId(firestoreId)
                    if (existing != null) {
                        if (existing.uploaderId != currentUserId) {
                            onFailure("You are not authorized to delete this video.")
                            return@launch
                        }
                        repository.deleteByFirestoreId(firestoreId)
                        onSuccess()
                    } else {
                        onFailure("Video not found.")
                    }
                } catch (e: Exception) {
                    onFailure(e.localizedMessage ?: "Failed to delete video.")
                }
                return@launch
            }

            try {
                val docRef = db.collection("media_items").document(firestoreId)
                val doc = docRef.get().await()

                if (doc.exists()) {
                    val uploader = doc.getString("uploaderId") ?: ""
                    if (uploader != currentUserId) {
                        onFailure("You are not authorized to delete this video.")
                        return@launch
                    }

                    docRef.delete().await()

                    try {
                        val videoRef = st.reference.child("videos/$firestoreId.mp4")
                        videoRef.delete()
                    } catch (e: Exception) {
                        android.util.Log.e("DeleteMedia", "Failed to delete video file", e)
                    }

                    try {
                        val coverRef = st.reference.child("covers/$firestoreId.jpg")
                        coverRef.delete()
                    } catch (e: Exception) {
                        android.util.Log.e("DeleteMedia", "Failed to delete cover file", e)
                    }

                    onSuccess()
                } else {
                    onFailure("Video not found.")
                }
            } catch (e: Exception) {
                android.util.Log.e("DeleteMedia", "Failed to delete", e)
                onFailure(e.localizedMessage ?: "Failed to delete video.")
            }
        }
    }

    private suspend fun seedDatabase() {
        // Seeding database with original show contents as seen in the screenshots
        val seedItems = listOf(
            MediaItem(
                title = "House of the Dragon",
                description = "The rise and fall of the Targaryen dynasty. High-stakes political war and epic dragon flights set 200 years before Game of Thrones.",
                category = "TV Shows",
                hashtags = "#HBOOriginal #Fantasy #WarOfDragons",
                qualities = "1080p Full HD, 4K Ultra HD",
                languages = "English, Hindi, Spanish, Multilingual Audio",
                rating = 8.9,
                isSlide = true,
                badge = "New episode",
                views = 4520380,
                likes = 890200,
                shares = 340110,
                downloads = 560000,
                streamingPlatform = "Disney+" // HBO original but streaming platforms represent in screenshots
            ),
            MediaItem(
                title = "Your Fault (London)",
                description = "Nick and Noah's intense love story faces collegiate challenges, career-ending fractures, and family interventions in London.",
                category = "Movies",
                hashtags = "#Romance #YourFault #Teens #London",
                qualities = "1080p HD, 4K Ultra HD",
                languages = "English, Hindi, Spanish, French, Multilingual",
                rating = 8.7,
                isSlide = true,
                badge = "Trending",
                views = 2120400,
                likes = 410200,
                shares = 150000,
                downloads = 210000,
                streamingPlatform = "Prime Video"
            ),
            MediaItem(
                title = "Enola Holmes 3",
                description = "Enola Holmes starts her own agency in London and takes on a major conspiracy involving missing girls and industrial espionage.",
                category = "Movies",
                hashtags = "#Mystery #Detective #EnolaHolmes",
                qualities = "1080p HD, 4K Ultra HD",
                languages = "English, Hindi, Spanish, German",
                rating = 8.3,
                isSlide = false,
                badge = "8.3",
                views = 1820300,
                likes = 320100,
                shares = 98000,
                downloads = 145000,
                streamingPlatform = "Netflix"
            ),
            MediaItem(
                title = "Toy Story 5",
                description = "Woody, Buzz, and their toy community find themselves pitted against the ultimate threat of tablets, gaming, and cyber toys.",
                category = "Movies",
                hashtags = "#Disney #Animation #Family #Adventure",
                qualities = "1080p HD, 4K Ultra HD, 3D HD",
                languages = "English, Hindi, Spanish, Japanese",
                rating = 8.8,
                isSlide = false,
                badge = "8.8",
                views = 3920000,
                likes = 750000,
                shares = 280000,
                downloads = 480000,
                streamingPlatform = "Disney+"
            ),
            MediaItem(
                title = "Avatar: The Last Airbender",
                description = "A live-action reimagining of the beloved animated series following Aang, the young Avatar, as he master elements to defeat Fire Nation.",
                category = "TV Shows",
                hashtags = "#Fantasy #LiveAction #Elements #Action",
                qualities = "1080p HD, 4K Ultra HD",
                languages = "English, Hindi, Spanish, Japanese, Korean",
                rating = 8.5,
                isSlide = false,
                badge = "Update to 7",
                views = 5120000,
                likes = 980000,
                shares = 420000,
                downloads = 650000,
                streamingPlatform = "Netflix"
            ),
            MediaItem(
                title = "Strung",
                description = "Four extremely talented violinists from diverse backgrounds clash in a high-intensity orchestral reality show battle in Vienna.",
                category = "Reality-TV",
                hashtags = "#RealityTV #Violin #MusicDrama",
                qualities = "1080p HD, 720p HD",
                languages = "English, Spanish",
                rating = 8.1,
                isSlide = false,
                badge = "Exclusive",
                views = 980000,
                likes = 120000,
                shares = 45000,
                downloads = 67000,
                streamingPlatform = "None"
            ),
            MediaItem(
                title = "Wardriver",
                description = "A brilliant hacker who steals online accounts gets blackmailed by an intelligence agent to perform high-risk terminal intrusions.",
                category = "Movies",
                hashtags = "#Thriller #Cyberpunk #Wardriver",
                qualities = "1080p HD, 4K Ultra HD",
                languages = "English, German, French",
                rating = 8.7,
                isSlide = false,
                badge = "8.7",
                views = 1420000,
                likes = 290000,
                shares = 110000,
                downloads = 180000,
                streamingPlatform = "Netflix"
            ),
            MediaItem(
                title = "Trying - Season 5",
                description = "A comedic look at Nikki and Jason as they raise adoption teenagers, manage full-time jobs, and try to keep their sanity intact.",
                category = "TV Shows",
                hashtags = "#Comedy #Drama #British #Adoption",
                qualities = "1080p HD",
                languages = "English, Multilingual Subtitles",
                rating = 8.6,
                isSlide = false,
                badge = "Update to 1",
                views = 890000,
                likes = 150000,
                shares = 50000,
                downloads = 72000,
                streamingPlatform = "Disney+"
            ),
            MediaItem(
                title = "Jeff Arcuri: Nice to Meet You",
                description = "Stand-up comedy star Jeff Arcuri delivers razor-sharp spontaneous crowd work and observational humor in his debut special.",
                category = "Reality-TV",
                hashtags = "#Comedy #StandUp #CrowdWork",
                qualities = "1080p HD, 4K Ultra HD",
                languages = "English",
                rating = 8.9,
                isSlide = false,
                badge = "8.9",
                views = 1220000,
                likes = 210000,
                shares = 85000,
                downloads = 95000,
                streamingPlatform = "Netflix"
            ),
            MediaItem(
                title = "Better Late Than Single",
                description = "Ten energetic single seniors move into an elite beachfront resort to rewrite the rules of modern romantic partnerships.",
                category = "Reality-TV",
                hashtags = "#DatingShow #Comedy #Seniors",
                qualities = "1080p HD",
                languages = "English, Spanish",
                rating = 7.9,
                isSlide = false,
                badge = "Update to 4",
                views = 740000,
                likes = 95000,
                shares = 23000,
                downloads = 31000,
                streamingPlatform = "Prime Video"
            ),
            MediaItem(
                title = "Nothing to Lose",
                description = "High school sprinter gets transported into a world of celestial warriors and has to run the race of his life to return home.",
                category = "Anime",
                hashtags = "#Anime #SciFi #Isekai #Action",
                qualities = "1080p HD, 4K Ultra HD",
                languages = "Japanese (Original), English, Hindi, Spanish",
                rating = 8.7,
                isSlide = false,
                badge = "8.7",
                views = 2150000,
                likes = 480000,
                shares = 190000,
                downloads = 320000,
                streamingPlatform = "Prime Video"
            ),
            MediaItem(
                title = "Salcedo: Leather & Boogaloo",
                description = "Cyberpunk street dancers combine neon gravity boots and high stakes robbery in the gritty underground slums of Neo-Manila.",
                category = "Anime",
                hashtags = "#Anime #Mecha #Cyberpunk #Dance",
                qualities = "1080p HD",
                languages = "Spanish, Japanese (Original), English",
                rating = 8.2,
                isSlide = false,
                badge = "Update to 12",
                views = 610000,
                likes = 84000,
                shares = 19000,
                downloads = 22000,
                streamingPlatform = "Netflix"
            )
        )

        for (item in seedItems) {
            val id = repository.insertMediaItem(item)
            
            // Seed 2-3 standard cinematic comments for each movie/show
            repository.insertComment(
                Comment(
                    mediaItemId = id.toInt(),
                    userName = "CinematicPro",
                    text = "This looks absolutely stunning in 4K Ultra HD quality! Streaming speed is incredibly stable."
                )
            )
            repository.insertComment(
                Comment(
                    mediaItemId = id.toInt(),
                    userName = "DramaQueen",
                    text = "Oh my goodness, the cast is exceptional. Easily one of the best releases this season! 🔥"
                )
            )
        }
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
