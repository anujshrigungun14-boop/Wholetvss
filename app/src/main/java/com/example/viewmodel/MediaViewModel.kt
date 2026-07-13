package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Comment
import com.example.data.MediaItem
import com.example.data.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MediaViewModel(private val repository: MediaRepository) : ViewModel() {

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
        
        // Filter by search query if non-empty (smart keywords matching across title, description, hashtags, and category)
        if (query.isNotBlank()) {
            val keywords = query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (keywords.isNotEmpty()) {
                items = items.filter { item ->
                    keywords.any { keyword ->
                        item.title.contains(keyword, ignoreCase = true) ||
                        item.description.contains(keyword, ignoreCase = true) ||
                        item.hashtags.contains(keyword, ignoreCase = true) ||
                        item.category.contains(keyword, ignoreCase = true)
                    }
                }
            }
        }
        items
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    init {
        // Run seed check
        viewModelScope.launch {
            // Seeding disabled to remove all fake/demo content and support purely real, user-driven catalog
            /*
            if (repository.getCount() == 0) {
                seedDatabase()
            }
            */
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
            repository.getMediaItemById(id).firstOrNull()?.let { media ->
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

    // Legendary Ultra-Fast Download System
    fun startUltraFastDownload(id: Int) {
        val currentStatus = _downloadStatus.value[id] ?: "None"
        if (currentStatus == "Downloading" || currentStatus == "Completed") return

        viewModelScope.launch {
            _downloadStatus.value = _downloadStatus.value + (id to "Downloading")
            _downloadProgress.value = _downloadProgress.value + (id to 0f)

            // Simulate ultra fast download (e.g. 100MB/s progress bars)
            val steps = 10
            for (i in 1..steps) {
                delay(200) // 2 seconds total legendary download
                val progress = i.toFloat() / steps
                _downloadProgress.value = _downloadProgress.value + (id to progress)
            }

            _downloadStatus.value = _downloadStatus.value + (id to "Completed")
            repository.incrementDownloads(id)
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
        streamingPlatform: String
    ) {
        viewModelScope.launch {
            val newItem = MediaItem(
                title = title.trim(),
                description = description.trim(),
                category = category,
                hashtags = hashtags.trim(),
                qualities = qualities.trim(),
                languages = languages.trim(),
                rating = rating,
                isSlide = isSlide,
                badge = badge.trim(),
                streamingPlatform = streamingPlatform,
                views = (1000..15000).random(),
                likes = (100..2000).random(),
                shares = (10..500).random(),
                downloads = (50..3000).random()
            )
            val insertedId = repository.insertMediaItem(newItem)
            
            // Add initial welcome comment for newly uploaded content
            repository.insertComment(
                Comment(
                    mediaItemId = insertedId.toInt(),
                    userName = "wholeTV Moderator",
                    text = "Welcome! This is newly uploaded under the Legendary Fast Upload System. Feel free to download at max speed and comment!"
                )
            )
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

class MediaViewModelFactory(private val repository: MediaRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MediaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MediaViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
