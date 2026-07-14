package com.company.wholetv

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.company.wholetv.data.AppDatabase
import com.company.wholetv.data.Comment
import com.company.wholetv.data.MediaItem
import com.company.wholetv.data.MediaRepository
import com.company.wholetv.ui.theme.*
import com.company.wholetv.viewmodel.MediaViewModel
import com.company.wholetv.viewmodel.MediaViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseInit", "Manual Firebase initialization skipped or failed", e)
        }
        
        // Build Database & Repository
        val database = AppDatabase.getDatabase(this)
        val repository = MediaRepository(database.mediaDao())
        val factory = MediaViewModelFactory(applicationContext, repository)

        setContent {
            MyApplicationTheme {
                val viewModel: MediaViewModel = viewModel(factory = factory)
                val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
                
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PremiumDarkBg),
                    bottomBar = {
                        WholeTVBottomBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.setTab(it) }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Display correct screen based on active tab
                        when (currentTab) {
                            "Home" -> HomeScreen(viewModel = viewModel)
                            "Streaming" -> StreamingScreen(viewModel = viewModel)
                            "Categories" -> CategoriesScreen(viewModel = viewModel)
                            "Search" -> SearchScreen(viewModel = viewModel)
                            "Me" -> MeProfileScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// BOTTOM NAVIGATION BAR
// ==========================================
@Composable
fun WholeTVBottomBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    val items = listOf(
        NavigationItem("Home", Icons.Filled.Home),
        NavigationItem("Streaming", Icons.Filled.PlayArrow),
        NavigationItem("Categories", Icons.Filled.Folder),
        NavigationItem("Search", Icons.Filled.Search),
        NavigationItem("Me", Icons.Filled.Person)
    )

    NavigationBar(
        containerColor = DarkSurface,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier.testTag("bottom_nav_bar")
    ) {
        items.forEach { item ->
            val selected = currentTab == item.title
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(item.title) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = if (selected) MovieRed else MutedSlate
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        color = if (selected) SoftWhite else MutedSlate,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MovieRed.copy(alpha = 0.15f)
                ),
                modifier = Modifier.testTag("nav_tab_${item.title.lowercase()}")
            )
        }
    }
}

data class NavigationItem(val title: String, val icon: ImageVector)

// ==========================================
// HOME SCREEN
// ==========================================
@Composable
fun HomeScreen(viewModel: MediaViewModel) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val slides by viewModel.slides.collectAsStateWithLifecycle()
    val selectedMediaItem by viewModel.selectedMediaItem.collectAsStateWithLifecycle()
    
    val trendingItems by viewModel.trendingItems.collectAsStateWithLifecycle()
    val latestUploads by viewModel.latestUploads.collectAsStateWithLifecycle()
    val recommendedItems by viewModel.recommendedItems.collectAsStateWithLifecycle()
    val popularThisWeek by viewModel.popularThisWeek.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    
    var isUploadOpen by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(PremiumDarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header Search Bar
            HomeSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onHistoryClick = { /* No-op or notification */ },
                onDownloadClick = { viewModel.setTab("Me") },
                onFocusChanged = { searchFocused = it }
            )

            // Category Tab Bar
            CategoryTabBar(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = { viewModel.setCategory(it) }
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Large Featured Slide Banner
                if (slides.isNotEmpty() && searchQuery.isBlank()) {
                    item {
                        FeaturedSlidesCarousel(
                            slides = slides,
                            onSlideClick = { viewModel.selectMedia(it.id) }
                        )
                    }
                }

                // Show Search Results if query is non-blank
                if (searchQuery.isNotBlank()) {
                    item {
                        SectionHeader(title = "Search Results")
                    }

                    if (mediaItems.isEmpty()) {
                        item {
                            EmptyStatePlaceholder(
                                message = "No search results found for \"$searchQuery\""
                            )
                        }
                    } else {
                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 1200.dp)
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                userScrollEnabled = false
                            ) {
                                items(mediaItems) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { viewModel.selectMedia(item.id) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Display Premium Recommendations & Category List

                    // 1. Continue Watching
                    if (continueWatching.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Continue Watching")
                        }
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(continueWatching) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { viewModel.selectMedia(item.id) }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Trending Now Section
                    item {
                        SectionHeader(title = "Trending Now")
                    }
                    item {
                        val activeTrending = if (trendingItems.isNotEmpty()) trendingItems else mediaItems
                        if (activeTrending.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(activeTrending) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { viewModel.selectMedia(item.id) }
                                    )
                                }
                            }
                        } else {
                            EmptyStatePlaceholder(message = "No content available. Upload one now!")
                        }
                    }

                    // 3. Recommended For You
                    if (recommendedItems.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Recommended For You")
                        }
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(recommendedItems) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { viewModel.selectMedia(item.id) }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Latest Uploads
                    if (latestUploads.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Latest Uploads")
                        }
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(latestUploads) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { viewModel.selectMedia(item.id) }
                                    )
                                }
                            }
                        }
                    }

                    // 5. Popular This Week
                    if (popularThisWeek.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Popular This Week")
                        }
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(popularThisWeek) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { viewModel.selectMedia(item.id) }
                                    )
                                }
                            }
                        }
                    }

                    // 6. Editor's Choice Specials
                    item {
                        SectionHeader(title = "Editor's Choice Special")
                    }
                    item {
                        val editorsChoice = mediaItems.filter { it.rating >= 8.5 }
                        if (editorsChoice.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(editorsChoice) { item ->
                                    MediaCard(
                                        item = item,
                                        onClick = { viewModel.selectMedia(item.id) }
                                    )
                                }
                            }
                        } else {
                            EmptyStatePlaceholder(message = "No high-rated items available yet.")
                        }
                    }
                }
            }
        }

        // Legendary Fast Upload FAB
        FloatingActionButton(
            onClick = { isUploadOpen = true },
            containerColor = MovieRed,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("upload_fab"),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Upload Content",
                modifier = Modifier.size(28.dp)
            )
        }

        // Details Modal/Dialog
        if (selectedMediaItem != null) {
            MediaDetailsDialog(
                media = selectedMediaItem!!,
                viewModel = viewModel,
                onDismiss = { viewModel.selectMedia(null) }
            )
        }

        // Upload Screen/Dialog
        if (isUploadOpen) {
            UploadDialog(
                viewModel = viewModel,
                onDismiss = { isUploadOpen = false }
            )
        }
    }
}

// ==========================================
// SEARCH BAR COMPONENT
// ==========================================
@Composable
fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onHistoryClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Unique stylized wholeTV logo
        Text(
            text = "whole",
            color = SoftWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 2.dp)
        )
        Box(
            modifier = Modifier
                .border(1.dp, MovieRed, RoundedCornerShape(4.dp))
                .background(MovieRed.copy(alpha = 0.15f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = "TV",
                color = MovieRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Search Input Box
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = "Search movies, shows...",
                    color = MutedSlate,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MutedSlate,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = MutedSlate,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                disabledContainerColor = DarkSurface,
                focusedIndicatorColor = MovieRed,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = SoftWhite,
                unfocusedTextColor = SoftWhite
            ),
            shape = RoundedCornerShape(30.dp),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("search_text_input")
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Timer/History icon
        IconButton(
            onClick = onHistoryClick,
            modifier = Modifier.testTag("history_button")
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = "History",
                tint = SoftWhite,
                modifier = Modifier.size(24.dp)
            )
        }

        // Download Manager icon
        IconButton(
            onClick = onDownloadClick,
            modifier = Modifier.testTag("download_manager_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Downloads",
                tint = SoftWhite,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ==========================================
// CATEGORY TAB BAR (MATCHING SCREENSHOTS)
// ==========================================
@Composable
fun CategoryTabBar(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
        containerColor = Color.Transparent,
        contentColor = SoftWhite,
        edgePadding = 16.dp,
        divider = {},
        indicator = {},
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_tab_row")
    ) {
        categories.forEachIndexed { index, category ->
            val isSelected = selectedCategory == category
            Tab(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                modifier = Modifier
                    .padding(vertical = 12.dp, horizontal = 4.dp)
                    .testTag("category_tab_$category")
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = category,
                        color = if (isSelected) SoftWhite else MutedSlate,
                        fontSize = if (isSelected) 18.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(2.dp)
                                .background(MovieRed)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SLIDES/CAROUSEL BANNER (MATCHING SCREENSHOTS)
// ==========================================
@Composable
fun FeaturedSlidesCarousel(
    slides: List<MediaItem>,
    onSlideClick: (MediaItem) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    
    // Auto-scroll banner timer
    LaunchedEffect(slides) {
        while (slides.isNotEmpty()) {
            delay(5000)
            currentIndex = (currentIndex + 1) % slides.size
        }
    }

    if (slides.isEmpty()) return
    val currentSlide = slides.getOrNull(currentIndex) ?: slides.first()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clickable { onSlideClick(currentSlide) }
                .testTag("slides_carousel")
        ) {
            // Draw Poster background fallback or load URL
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, PremiumDarkBg)
                        )
                    )
            ) {
                if (currentSlide.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = currentSlide.posterUrl,
                        contentDescription = currentSlide.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Unique fallback graphic
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(MovieRed.copy(alpha = 0.35f), PremiumDarkBg),
                                    radius = 500f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = null,
                                tint = MovieRed,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentSlide.title,
                                color = SoftWhite,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }

                // Shadows Overlay to blend beautifully
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    PremiumDarkBg.copy(alpha = 0.3f),
                                    PremiumDarkBg
                                )
                            )
                        )
                )

                // Featured labels
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    if (currentSlide.badge.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(MovieRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentSlide.badge.uppercase(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = currentSlide.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = currentSlide.description,
                        color = MutedSlate,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )
                }
            }
        }

        // Indicators dots (matching screens)
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slides.forEachIndexed { idx, _ ->
                Box(
                    modifier = Modifier
                        .size(width = if (idx == currentIndex) 16.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (idx == currentIndex) MovieRed else DeepGrey)
                )
            }
        }
    }
}

// ==========================================
// MEDIA POSTER / CARD COMPONENT (RESPONSIVE)
// ==========================================
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(115.dp)
            .clickable { onClick() }
            .testTag("media_card_${item.title.replace(" ", "_").lowercase()}")
    ) {
        Box(
            modifier = Modifier
                .width(115.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
        ) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Creative Fallback
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(DeepGrey, PremiumDarkBg)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        val icon = when (item.category) {
                            "Movies" -> Icons.Filled.Movie
                            "TV Shows" -> Icons.Filled.Tv
                            "Reality-TV" -> Icons.Filled.TheaterComedy
                            "Anime" -> Icons.Filled.Animation
                            else -> Icons.Filled.PlayCircle
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MovieRed.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.title,
                            color = SoftWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Top-left/right badges (e.g. New episode, rating)
            if (item.badge.isNotBlank()) {
                val isRating = item.badge.toDoubleOrNull() != null
                Box(
                    modifier = Modifier
                        .align(if (isRating) Alignment.BottomEnd else Alignment.TopStart)
                        .background(
                            if (isRating) Color.Transparent else MovieRed,
                            shape = if (isRating) RoundedCornerShape(0.dp) else RoundedCornerShape(bottomEnd = 8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.badge,
                        color = if (isRating) CinemaGold else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            color = SoftWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==========================================
// STREAMING PLATFORMS TAB (SCREEN 3 MATCHING)
// ==========================================
@Composable
fun StreamingScreen(viewModel: MediaViewModel) {
    val platforms = listOf("All", "Netflix", "Disney+", "Prime Video")
    val selectedPlatform by viewModel.selectedPlatform.collectAsStateWithLifecycle()
    val streamingItems by viewModel.streamingItems.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumDarkBg)
    ) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Streaming Hub",
                color = SoftWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.LiveTv,
                contentDescription = null,
                tint = MovieRed
            )
        }

        // Platform Selectors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            platforms.forEach { platform ->
                val selected = platform == selectedPlatform
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (selected) MovieRed else DarkSurface)
                        .border(
                            width = 1.dp,
                            color = if (selected) MovieRed else DeepGrey,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable { viewModel.selectPlatform(platform) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("platform_tab_${platform.lowercase().replace("+", "_plus")}")
                ) {
                    Text(
                        text = platform.uppercase(),
                        color = if (selected) Color.White else MutedSlate,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SectionHeader(title = "Today's New Streaming")

        if (streamingItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EmptyStatePlaceholder(message = "No streaming content found under this platform.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(streamingItems) { item ->
                    MediaCard(
                        item = item,
                        onClick = { viewModel.selectMedia(item.id) }
                    )
                }
            }
        }
    }
    
    // Connect dialog details
    val selectedMediaItem by viewModel.selectedMediaItem.collectAsStateWithLifecycle()
    if (selectedMediaItem != null) {
        MediaDetailsDialog(
            media = selectedMediaItem!!,
            viewModel = viewModel,
            onDismiss = { viewModel.selectMedia(null) }
        )
    }
}

// ==========================================
// CATEGORIES BROWSE SCREEN
// ==========================================
fun getIconForCategory(category: String): ImageVector {
    return when (category.lowercase().trim()) {
        "movies" -> Icons.Filled.Movie
        "tv shows" -> Icons.Filled.Tv
        "reality-tv", "reality" -> Icons.Filled.TheaterComedy
        "anime" -> Icons.Filled.Animation
        "action" -> Icons.Filled.FlashOn
        "fantasy" -> Icons.Filled.AutoAwesome
        "romance" -> Icons.Filled.Favorite
        "mystery" -> Icons.Filled.ManageSearch
        "comedy" -> Icons.Filled.SentimentSatisfiedAlt
        "sci-fi" -> Icons.Filled.RocketLaunch
        "music" -> Icons.Filled.MusicNote
        "animation" -> Icons.Filled.Toys
        else -> Icons.Filled.Folder
    }
}

@Composable
fun CategoriesScreen(viewModel: MediaViewModel) {
    val categoriesList by viewModel.categories.collectAsStateWithLifecycle()
    val dynamicCategories = remember(categoriesList) {
        categoriesList.filter { it != "Recommend" }
    }

    var activeQuery by remember { mutableStateOf("") }
    val allMediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    
    val filteredBrowseItems = remember(activeQuery, allMediaItems) {
        if (activeQuery.isEmpty()) {
            allMediaItems
        } else {
            allMediaItems.filter {
                it.category.equals(activeQuery, ignoreCase = true) ||
                it.hashtags.contains(activeQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumDarkBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Discover Categories",
                color = SoftWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Grid of category filter blocks
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dynamicCategories) { genre ->
                val isActive = activeQuery.equals(genre, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) MovieRed else DarkSurface)
                        .border(1.dp, if (isActive) MovieRed else DeepGrey, RoundedCornerShape(8.dp))
                        .clickable {
                            activeQuery = if (isActive) "" else genre
                        }
                        .padding(12.dp)
                        .testTag("genre_block_${genre.lowercase().replace(" ", "_")}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = getIconForCategory(genre),
                            contentDescription = genre,
                            tint = if (isActive) Color.White else MovieRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = genre,
                            color = if (isActive) Color.White else SoftWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SectionHeader(
            title = if (activeQuery.isEmpty()) "All Mix Shows" else "$activeQuery Collection"
        )

        if (filteredBrowseItems.isEmpty()) {
            EmptyStatePlaceholder(message = "No items matching this category yet. Upload some!")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredBrowseItems) { item ->
                    MediaCard(
                        item = item,
                        onClick = { viewModel.selectMedia(item.id) }
                    )
                }
            }
        }
    }
    
    // Connect dialog details
    val selectedMediaItem by viewModel.selectedMediaItem.collectAsStateWithLifecycle()
    if (selectedMediaItem != null) {
        MediaDetailsDialog(
            media = selectedMediaItem!!,
            viewModel = viewModel,
            onDismiss = { viewModel.selectMedia(null) }
        )
    }
}

// ==========================================
// COMMUNITY SCREEN (FREE & OPEN PLATFORM)
// ==========================================
@Composable
fun CommunityScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumDarkBg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "wholeTV Community Hub",
                color = SoftWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, MovieRed)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .background(MovieRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "100% FREE COMMUNITY PLATFORM",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ultra Legendary Free Speed Access",
                    color = SoftWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Experience true raw 4K HDR streams at download speeds exceeding 120MB/s! Your offline media downloads complete under 2 seconds. No paywalls, no subscriptions, no ads, ever.",
                    color = MutedSlate,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = MovieRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Explore Shared Catalog", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = CinemaGold,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Legendary Upload Mode",
                        color = SoftWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add your custom movies, set languages, qualities, and sliders instantly. Your content is streamed in multilingual instantly.",
                        color = MutedSlate,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.TheaterComedy,
                    contentDescription = null,
                    tint = MovieRed,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "User-Driven Community Library",
                    color = SoftWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This application strictly functions using user-driven uploads and community sharing. Feel free to use the bottom '+' button at any time to expand wholeTV's database with custom categories!",
                    color = MutedSlate,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ==========================================
// PROFILE SCREEN / OFFLINE DOWNLOADS LIST
// ==========================================
@Composable
fun MeProfileScreen(viewModel: MediaViewModel) {
    val context = LocalContext.current
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsStateWithLifecycle()
    val allMediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    // Auth input states
    var isSignUpMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf("") }

    // User Profile customizable states
    val userBio by viewModel.userBio.collectAsStateWithLifecycle()
    val userUsername by viewModel.userUsername.collectAsStateWithLifecycle()
    
    val authUser = viewModel.auth?.currentUser
    val displayNick = remember(authUser, isUserLoggedIn) {
        authUser?.displayName ?: context.getSharedPreferences("wholetv_prefs", android.content.Context.MODE_PRIVATE)
            .getString("user_display_name", "Legendary Streamer") ?: "Legendary Streamer"
    }
    val displayPhotoUrl = remember(authUser, isUserLoggedIn) {
        authUser?.photoUrl?.toString() ?: context.getSharedPreferences("wholetv_prefs", android.content.Context.MODE_PRIVATE)
            .getString("user_photo_url", "") ?: ""
    }

    // Profile editing states
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editDisplayName by remember { mutableStateOf(displayNick) }
    var editUsername by remember { mutableStateOf(userUsername) }
    var editBio by remember { mutableStateOf(userBio) }

    // Selected offline media to play
    var activeOfflineVideoUrl by remember { mutableStateOf<String?>(null) }

    // Profile Image Picker Launcher
    val profileImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Uploading profile picture...", Toast.LENGTH_SHORT).show()
            viewModel.uploadProfilePicture(uri, { newUrl ->
                Toast.makeText(context, "Profile picture updated successfully!", Toast.LENGTH_SHORT).show()
            }, { err ->
                Toast.makeText(context, "Upload failed: $err", Toast.LENGTH_LONG).show()
            })
        }
    }

    // Filter items showing in Download list (Completed, Downloading, Paused, Failed)
    val downloadList = remember(allMediaItems, downloadStatus) {
        allMediaItems.filter { 
            val status = downloadStatus[it.id] ?: "None"
            status == "Completed" || status == "Downloading" || status == "Paused" || status == "Failed"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumDarkBg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "My wholeTV",
                color = SoftWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (isUserLoggedIn) {
                TextButton(onClick = { viewModel.logoutUser() }) {
                    Text("Logout", color = MovieRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isUserLoggedIn) {
            // Modern Authentication Card UI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, DeepGrey)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSignUpMode) "Create Account" else "Welcome Back",
                        color = SoftWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isSignUpMode) "Sign up to upload, stream & download offline" else "Log in to access your streaming feed",
                        color = MutedSlate,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    if (authError.isNotEmpty()) {
                        Text(
                            text = authError,
                            color = MovieRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address", color = MutedSlate) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password", color = MutedSlate) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (emailInput.isBlank() || passwordInput.isBlank()) {
                                authError = "Please fill in all credentials."
                                return@Button
                            }
                            authError = ""
                            if (isSignUpMode) {
                                viewModel.signUpUser(emailInput, passwordInput, {
                                    Toast.makeText(context, "Signed up successfully!", Toast.LENGTH_SHORT).show()
                                }, { err ->
                                    authError = err
                                })
                            } else {
                                viewModel.loginUser(emailInput, passwordInput, {
                                    Toast.makeText(context, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                                }, { err ->
                                    authError = err
                                })
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MovieRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSignUpMode) "Sign Up" else "Log In", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { isSignUpMode = !isSignUpMode }) {
                        Text(
                            text = if (isSignUpMode) "Already have an account? Log In" else "Don't have an account? Sign Up",
                            color = CinemaGold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            // Interactive User Profile Card UI
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Editable Profile Avatar
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(DarkSurface)
                        .border(2.dp, MovieRed, CircleShape)
                        .clickable {
                            profileImagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (displayPhotoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = displayPhotoUrl,
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Change profile picture",
                            tint = MovieRed,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                    
                    // Small Edit Camera overlay icon
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.BottomEnd)
                            .background(MovieRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = "Edit photo",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = displayNick,
                    color = SoftWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = userUsername,
                    color = CinemaGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = userBio,
                    color = MutedSlate,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        editDisplayName = displayNick
                        editUsername = userUsername
                        editBio = userBio
                        showEditProfileDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, DeepGrey)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = SoftWhite, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Profile Details", color = SoftWhite, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Stats Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${allMediaItems.size}",
                            color = SoftWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Mix Shows", color = MutedSlate, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${downloadList.filter { downloadStatus[it.id] == "Completed" }.size}",
                            color = SoftWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Downloaded", color = MutedSlate, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "100%",
                            color = CinemaGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Sync Health", color = MutedSlate, fontSize = 12.sp)
                    }
                }
            }
        }

        val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
        val uploadProgress by viewModel.uploadProgressValue.collectAsStateWithLifecycle()

        if (isUploading) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, MovieRed.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        progress = { uploadProgress },
                        color = MovieRed,
                        trackColor = DeepGrey,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Uploading to Cloud...",
                            color = SoftWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val pct = (uploadProgress * 100).toInt().coerceIn(0, 100)
                        Text(
                            text = "Progress: $pct%",
                            color = CinemaGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader(title = "My Offline Downloads")

        if (downloadList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(150.dp)
                    .background(DarkSurface, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = MutedSlate,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No offline media downloaded yet.",
                        color = MutedSlate,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Click download inside any video to store offline.",
                        color = MutedSlate.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                downloadList.forEach { item ->
                    val status = downloadStatus[item.id] ?: "None"
                    val progress = downloadProgress[item.id] ?: 0f

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, DeepGrey)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Movie,
                                    contentDescription = null,
                                    tint = MovieRed,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = SoftWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Status: $status | ${item.qualities}",
                                        color = if (status == "Failed") MovieRed else CinemaGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Interactive Action Buttons based on state
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (status == "Downloading") {
                                        IconButton(onClick = { viewModel.pauseDownload(item.id) }) {
                                            Icon(imageVector = Icons.Filled.Pause, contentDescription = "Pause", tint = SoftWhite)
                                        }
                                    } else if (status == "Paused" || status == "Failed") {
                                        IconButton(onClick = { viewModel.resumeDownload(item.id) }) {
                                            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Resume", tint = SoftWhite)
                                        }
                                    } else if (status == "Completed") {
                                        IconButton(onClick = {
                                            // Play offline local file directly!
                                            val localFile = File(item.localFilePath ?: "")
                                            if (localFile.exists()) {
                                                activeOfflineVideoUrl = localFile.absolutePath
                                            } else {
                                                Toast.makeText(context, "Local file not found! Re-downloading required.", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(imageVector = Icons.Filled.PlayCircle, contentDescription = "Play Offline", tint = MovieRed, modifier = Modifier.size(28.dp))
                                        }
                                    }

                                    // Delete / cancel button
                                    IconButton(onClick = { viewModel.deleteDownload(item.id) }) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete download", tint = MutedSlate)
                                    }
                                }
                            }

                            // Progress Bar for downloading/paused states
                            if (status == "Downloading" || status == "Paused") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        color = MovieRed,
                                        trackColor = Color.White.copy(alpha = 0.1f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        color = SoftWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }

    // Edit Profile Details Dialog
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile Info", color = SoftWhite, fontWeight = FontWeight.Bold) },
            containerColor = PremiumDarkBg,
            text = {
                Column {
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Display Nickname") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("Username handle") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Bio description") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUserProfile(
                            displayName = editDisplayName,
                            username = editUsername,
                            bio = editBio,
                            onSuccess = {
                                showEditProfileDialog = false
                                Toast.makeText(context, "Profile details updated!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MovieRed)
                ) {
                    Text("Save Changes", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = MutedSlate)
                }
            }
        )
    }

    // Overlay dialog to play offline download videos
    if (activeOfflineVideoUrl != null) {
        Dialog(
            onDismissRequest = { activeOfflineVideoUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoPlayer(
                    videoUrl = activeOfflineVideoUrl!!,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = { activeOfflineVideoUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 40.dp, start = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close player", tint = Color.White)
                }
            }
        }
    }
}

// ==========================================
// MEDIA DETAILS MODAL/BOTTOM DIALOG
// ==========================================
@Composable
fun MediaDetailsDialog(
    media: MediaItem,
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    val comments by viewModel.currentComments.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val allItems by viewModel.allMediaItems.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    var inputCommentName by remember { mutableStateOf("") }
    var inputCommentText by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }

    val activeProgress = downloadProgress[media.id] ?: 0f
    val activeStatus = downloadStatus[media.id] ?: "None"

    val recommendations = remember(media, allItems) {
        val scored = allItems
            .filter { it.id != media.id }
            .map { item ->
                var score = 0
                if (item.category.equals(media.category, ignoreCase = true)) {
                    score += 5
                }
                val mediaTags = media.hashtags.split('#', ' ', ',').filter { it.isNotBlank() }.map { it.lowercase() }
                val itemTags = item.hashtags.split('#', ' ', ',').filter { it.isNotBlank() }.map { it.lowercase() }
                val commonTags = mediaTags.intersect(itemTags.toSet())
                score += commonTags.size * 3

                val mediaWords = media.title.split("\\s+".toRegex()).filter { it.length > 3 }.map { it.lowercase() }
                val itemWords = item.title.split("\\s+".toRegex()).filter { it.length > 3 }.map { it.lowercase() }
                val commonWords = mediaWords.intersect(itemWords.toSet())
                score += commonWords.size * 2

                item to score
            }

        val filtered = scored.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        if (filtered.isNotEmpty()) {
            filtered.take(6)
        } else {
            allItems.filter { it.id != media.id }.take(6)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumDarkBg)
                .testTag("media_detail_dialog")
        ) {
            // Blurred backdrop or plain background representation
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(Color.Black)
                    ) {
                        VideoPlayer(
                            videoUrl = media.videoUrl.ifBlank { "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Overlaid Close Player Button
                        IconButton(
                            onClick = { isPlaying = false },
                            modifier = Modifier
                                .padding(top = 40.dp, start = 16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .align(Alignment.TopStart)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Player",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        if (media.posterUrl.isNotBlank()) {
                            AsyncImage(
                                model = media.posterUrl,
                                contentDescription = media.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(DeepGrey, PremiumDarkBg)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Movie,
                                    contentDescription = null,
                                    tint = MovieRed,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }

                        // Centered Play Button Overlay
                        IconButton(
                            onClick = { isPlaying = true },
                            modifier = Modifier
                                .size(64.dp)
                                .background(MovieRed.copy(alpha = 0.9f), CircleShape)
                                .align(Alignment.Center)
                                .testTag("play_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Shadow Gradient
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, PremiumDarkBg)
                                    )
                                )
                        )

                        // Exit action
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .padding(top = 40.dp, start = 16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .align(Alignment.TopStart)
                                .testTag("exit_detail_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Metadata Details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = media.hashtags,
                        color = MovieRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row metadata indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Rating",
                            tint = CinemaGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = " ${media.rating}  |  ",
                            color = SoftWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .border(1.dp, MovieRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = media.category.uppercase(),
                                color = MovieRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (media.streamingPlatform != "None") {
                            Text(text = "  |  Platform: ", color = MutedSlate, fontSize = 12.sp)
                            Text(
                                text = media.streamingPlatform,
                                color = CinemaGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Audio & Quality Tags (Requested: Multilingual & Qualities)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Hd,
                                    contentDescription = null,
                                    tint = CinemaGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = media.qualities, color = SoftWhite, fontSize = 11.sp)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Translate,
                                    contentDescription = null,
                                    tint = MovieRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = media.languages, color = SoftWhite, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = media.description,
                        color = SoftWhite,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Row stats: views, likes, downloads
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = MutedSlate
                            )
                            Text(
                                text = formatCount(media.views),
                                color = SoftWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { viewModel.likeMedia(media.id) }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.ThumbUp,
                                    contentDescription = "Like",
                                    tint = MovieRed
                                )
                                Text(
                                    text = formatCount(media.likes),
                                    color = SoftWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(onClick = {
                            viewModel.shareMedia(media.id) { text ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Show"))
                            }
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share",
                                    tint = SoftWhite
                                )
                                Text(
                                    text = formatCount(media.shares),
                                    color = SoftWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Divider(color = DeepGrey, thickness = 1.dp)

                    // Legendary Max Fast Download System Section
                    SectionHeader(title = "Legendary Download System (Max 120MB/s)")
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (activeStatus) {
                            "None", "Idle" -> {
                                Button(
                                    onClick = { viewModel.startUltraFastDownload(media.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MovieRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("fast_download_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = CinemaGold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Legendary Max Fast Download", fontWeight = FontWeight.Bold)
                                }
                            }
                            "Downloading" -> {
                                Text(
                                    text = "Downloading at 108.4 MB/s...",
                                    color = CinemaGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { activeProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MovieRed,
                                    trackColor = DeepGrey
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${(activeProgress * 100).toInt()}% Complete",
                                    color = SoftWhite,
                                    fontSize = 11.sp
                                )
                            }
                            "Completed" -> {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF2E7D32), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Downloaded Offline Successfully!",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Delete Button (only for uploader of this content)
                    if (media.uploaderId == viewModel.currentUserId && media.firestoreId.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.deleteMedia(
                                    firestoreId = media.firestoreId,
                                    onSuccess = {
                                        Toast.makeText(context, "🗑️ Video deleted successfully!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(context, "Failed to delete: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("delete_media_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete Video",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Uploaded Video", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // More Like This (Recommendations) Section
                    if (recommendations.isNotEmpty()) {
                        Divider(color = DeepGrey, thickness = 1.dp)
                        SectionHeader(title = "More Like This (Recommended)")
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recommendations) { item ->
                                MediaCard(
                                    item = item,
                                    onClick = { viewModel.selectMedia(item.id) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Divider(color = DeepGrey, thickness = 1.dp)

                    // Comments Section
                    SectionHeader(title = "Reviews & Discussion (${comments.size})")

                    // Add comment form
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Add Your Review",
                            color = SoftWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = inputCommentName,
                            onValueChange = { inputCommentName = it },
                            placeholder = { Text("Your Screen Name (e.g. MovieLover99)", color = MutedSlate, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MovieRed,
                                unfocusedBorderColor = DeepGrey,
                                focusedTextColor = SoftWhite,
                                unfocusedTextColor = SoftWhite
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("comment_name_field")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = inputCommentText,
                            onValueChange = { inputCommentText = it },
                            placeholder = { Text("Write your review or thoughts...", color = MutedSlate, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MovieRed,
                                unfocusedBorderColor = DeepGrey,
                                focusedTextColor = SoftWhite,
                                unfocusedTextColor = SoftWhite
                            ),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth().testTag("comment_text_field")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (inputCommentName.isNotBlank() && inputCommentText.isNotBlank()) {
                                    viewModel.submitComment(media.id, inputCommentName, inputCommentText)
                                    inputCommentText = ""
                                    Toast.makeText(context, "Review submitted successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Please fill out both fields.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MovieRed),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.align(Alignment.End).testTag("submit_comment_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Submit", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Comments display list
                    if (comments.isEmpty()) {
                        Text(
                            text = "No reviews yet. Be the first to add yours!",
                            color = MutedSlate,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 80.dp)
                        ) {
                            comments.forEach { comment ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    border = BorderStroke(1.dp, DeepGrey),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.AccountCircle,
                                                contentDescription = null,
                                                tint = MovieRed,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = comment.userName,
                                                color = SoftWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = comment.text,
                                            color = SoftWhite,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// UPLOAD DIALOG (ULTRA LEGENDARY FAST UPLOAD)
// ==========================================
@Composable
fun UploadDialog(
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("Movies") }
    var hashtags by remember { mutableStateOf("") }
    var qualities by remember { mutableStateOf("1080p HD") }
    var languages by remember { mutableStateOf("English") }
    var ratingString by remember { mutableStateOf("8.5") }
    var badge by remember { mutableStateOf("") }
    var isSlide by remember { mutableStateOf(false) }
    var platform by remember { mutableStateOf("None") }
    var genreInput by remember { mutableStateOf("Action") }
    val loggedInUser = viewModel.currentUser
    var uploaderNameInput by remember { mutableStateOf(loggedInUser?.displayName ?: loggedInUser?.email?.substringBefore("@") ?: "Legendary Streamer") }

    var selectedVideoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedCoverUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var videoName by remember { mutableStateOf("") }
    var coverName by remember { mutableStateOf("") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        selectedVideoUri = uri
        videoName = uri?.let { getFileName(context, it) } ?: ""
    }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        selectedCoverUri = uri
        coverName = uri?.let { getFileName(context, it) } ?: ""
    }

    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgressValue.collectAsStateWithLifecycle()

    val existingCategories by viewModel.categories.collectAsStateWithLifecycle()
    val dynamicCategoriesList = remember(existingCategories) {
        existingCategories.filter { it != "Recommend" }
    }
    val platformsList = listOf("None", "Netflix", "Disney+", "Prime Video")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumDarkBg)
                .testTag("upload_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Custom Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("upload_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SoftWhite
                        )
                    }
                    Text(
                        text = "Legendary Fast Upload",
                        color = SoftWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Filled.RocketLaunch,
                        contentDescription = null,
                        tint = MovieRed
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Upload custom videos and cover artwork directly into the wholeTV database.",
                        color = MutedSlate,
                        fontSize = 13.sp
                    )

                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Movie/Show Title *") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = MovieRed
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("upload_title_field")
                    )

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description *") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = MovieRed
                        ),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("upload_desc_field")
                    )

                    // VIDEO FILE PICKER CARD
                    Text(text = "Select Video File *", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!isUploading) videoPickerLauncher.launch("video/*") },
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, if (selectedVideoUri != null) MovieRed else DeepGrey)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectedVideoUri != null) Icons.Filled.CheckCircle else Icons.Filled.VideoFile,
                                contentDescription = null,
                                tint = if (selectedVideoUri != null) Color(0xFF2E7D32) else MovieRed,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (videoName.isNotEmpty()) videoName else "Tap to Choose Video",
                                    color = SoftWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (selectedVideoUri != null) "Video selected from device" else "Supports MP4, MKV, 3GP",
                                    color = MutedSlate,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // COVER IMAGE PICKER CARD
                    Text(text = "Select Cover Image (Optional)", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isUploading) {
                                    coverPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, if (selectedCoverUri != null) MovieRed else DeepGrey)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectedCoverUri != null) Icons.Filled.CheckCircle else Icons.Filled.Image,
                                contentDescription = null,
                                tint = if (selectedCoverUri != null) Color(0xFF2E7D32) else MovieRed,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (coverName.isNotEmpty()) coverName else "Tap to Choose Cover Image",
                                    color = SoftWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (selectedCoverUri != null) "Cover selected from device" else "Supports JPG, PNG, WEBP",
                                    color = MutedSlate,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Custom Category Input TextField
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = { categoryInput = it },
                        label = { Text("Category * (Type custom or choose from existing below)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = MovieRed
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("upload_custom_category_field")
                    )

                    // Category scroll row
                    if (dynamicCategoriesList.isNotEmpty()) {
                        Text(text = "Or Choose Existing Category:", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dynamicCategoriesList.forEach { cat ->
                                val selected = cat.lowercase().trim() == categoryInput.lowercase().trim()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) MovieRed else DarkSurface)
                                        .border(1.dp, if (selected) MovieRed else DeepGrey, RoundedCornerShape(20.dp))
                                        .clickable { categoryInput = cat }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (selected) Color.White else SoftWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Platform selection row
                    Text(text = "Streaming Hub Provider", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        platformsList.forEach { plat ->
                            val selected = plat == platform
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) MovieRed else DarkSurface)
                                    .border(1.dp, if (selected) MovieRed else DeepGrey, RoundedCornerShape(20.dp))
                                    .clickable { platform = plat }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = plat,
                                    color = if (selected) Color.White else SoftWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Multilingual languages
                    OutlinedTextField(
                        value = languages,
                        onValueChange = { languages = it },
                        label = { Text("Multilingual Languages (e.g. English, Hindi, Spanish)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = MovieRed
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("upload_languages_field")
                    )

                    // Video quality
                    OutlinedTextField(
                        value = qualities,
                        onValueChange = { qualities = it },
                        label = { Text("Qualities (e.g. 1080p HD, 4K Ultra HD)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = MovieRed
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("upload_qualities_field")
                    )

                    // Hashtags
                    OutlinedTextField(
                        value = hashtags,
                        onValueChange = { hashtags = it },
                        label = { Text("Hashtags (e.g. #thriller #fantasy)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MovieRed,
                            unfocusedBorderColor = DeepGrey,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = MovieRed
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("upload_hashtags_field")
                    )

                    // Genre and Uploader Name Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = genreInput,
                            onValueChange = { genreInput = it },
                            label = { Text("Genre * (e.g. Action, Comedy)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MovieRed,
                                unfocusedBorderColor = DeepGrey,
                                focusedTextColor = SoftWhite,
                                unfocusedTextColor = SoftWhite,
                                focusedLabelColor = MovieRed
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("upload_genre_field")
                        )

                        OutlinedTextField(
                            value = uploaderNameInput,
                            onValueChange = { uploaderNameInput = it },
                            label = { Text("Uploader Name") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MovieRed,
                                unfocusedBorderColor = DeepGrey,
                                focusedTextColor = SoftWhite,
                                unfocusedTextColor = SoftWhite,
                                focusedLabelColor = MovieRed
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("upload_uploader_field")
                        )
                    }

                    // Rating & Badge Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = ratingString,
                            onValueChange = { ratingString = it },
                            label = { Text("Rating (0-10)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MovieRed,
                                unfocusedBorderColor = DeepGrey,
                                focusedTextColor = SoftWhite,
                                unfocusedTextColor = SoftWhite,
                                focusedLabelColor = MovieRed
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("upload_rating_field")
                        )

                        OutlinedTextField(
                            value = badge,
                            onValueChange = { badge = it },
                            label = { Text("Badge (e.g. Hot, New)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MovieRed,
                                unfocusedBorderColor = DeepGrey,
                                focusedTextColor = SoftWhite,
                                unfocusedTextColor = SoftWhite,
                                focusedLabelColor = MovieRed
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("upload_badge_field")
                        )
                    }

                    // Is Slide Carousel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Feature in Sliding Banner Carousel", color = SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Appears in high quality banner slides at top.", color = MutedSlate, fontSize = 11.sp)
                        }
                        Switch(
                            checked = isSlide,
                            onCheckedChange = { isSlide = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MovieRed,
                                checkedTrackColor = MovieRed.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("upload_slide_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Rocket Legendary Upload trigger
                    Button(
                        enabled = !isUploading,
                        onClick = {
                            if (isUploading) return@Button
                            val r = ratingString.toDoubleOrNull() ?: 8.5
                            val finalCategory = categoryInput.trim().ifBlank { "Movies" }
                            if (title.isBlank() || description.isBlank()) {
                                Toast.makeText(context, "Please fill out required title and description fields.", Toast.LENGTH_SHORT).show()
                            } else if (selectedVideoUri == null) {
                                Toast.makeText(context, "Please select a video file to upload.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.uploadMedia(
                                    title = title,
                                    description = description,
                                    category = finalCategory,
                                    hashtags = hashtags,
                                    qualities = qualities,
                                    languages = languages,
                                    rating = r,
                                    isSlide = isSlide,
                                    badge = badge,
                                    streamingPlatform = platform,
                                    videoUri = selectedVideoUri,
                                    coverUri = selectedCoverUri,
                                    genre = genreInput,
                                    uploaderName = uploaderNameInput,
                                    onSuccess = {
                                        Toast.makeText(context, "🚀 Video uploaded successfully into wholeTV database!", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    },
                                    onFailure = { error ->
                                        Toast.makeText(context, "❌ Upload failed: $error", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MovieRed,
                            disabledContainerColor = MovieRed.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_upload_button")
                    ) {
                        Icon(imageVector = Icons.Filled.RocketLaunch, contentDescription = null, tint = CinemaGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LEGENDARY UPLOAD", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(60.dp))
                }
            }

            // Real-time circular progress indicator overlay dialog when uploading
            if (isUploading) {
                Dialog(onDismissRequest = {}) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                progress = { uploadProgress },
                                color = MovieRed,
                                trackColor = DeepGrey
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Uploading to Cloud...",
                                color = SoftWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${(uploadProgress * 100).toInt()}% Complete",
                                color = CinemaGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// DECORATIVE / ASSISTANCE LAYOUTS
// ==========================================
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = SoftWhite,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
    )
}

@Composable
fun EmptyStatePlaceholder(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.TvOff,
            contentDescription = null,
            tint = MutedSlate,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = MutedSlate,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000f)
        count >= 1_000 -> String.format("%.1fK", count / 1_000f)
        else -> count.toString()
    }
}

@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }

    val safeVideoUrl = remember(videoUrl) {
        if (videoUrl.isBlank() || videoUrl.startsWith("http://placeholder") || (!videoUrl.startsWith("http") && !videoUrl.startsWith("content"))) {
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        } else {
            videoUrl
        }
    }

    // Custom States
    var isPlayingState by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var resizeMode by remember { mutableStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }
    
    // Gestural Overlays
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var brightnessValue by remember { mutableStateOf(0.5f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var volumeValue by remember { mutableStateOf(0.5f) }
    var doubleTapText by remember { mutableStateOf("") }
    var showDoubleTapOverlay by remember { mutableStateOf(false) }

    // Popup settings
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("Auto") }

    val exoPlayer = remember(safeVideoUrl) {
        try {
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                val mediaItem = androidx.media3.common.MediaItem.fromUri(safeVideoUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                
                // Seek to resume if there's any last saved position
                // Let's check shared preferences
                val prefs = context.getSharedPreferences("wholetv_playback_resume", android.content.Context.MODE_PRIVATE)
                val savedPos = prefs.getLong(safeVideoUrl, 0L)
                if (savedPos > 0L) {
                    seekTo(savedPos)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayer", "Failed to initialize ExoPlayer safely", e)
            null
        }
    }

    // Save playback position periodically and on dispose
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer?.let {
                val prefs = context.getSharedPreferences("wholetv_playback_resume", android.content.Context.MODE_PRIVATE)
                prefs.edit().putLong(safeVideoUrl, it.currentPosition).apply()
                it.release()
            }
            // Reset orientation to portrait on exit
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Sync play state
    LaunchedEffect(exoPlayer) {
        while (exoPlayer != null) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlayingState = exoPlayer.isPlaying
            delay(250)
        }
    }

    // Auto-hide controls
    LaunchedEffect(isControlsVisible, isLocked) {
        if (isControlsVisible && !isLocked) {
            delay(3500)
            isControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = {
                        isControlsVisible = !isControlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (isLocked) return@detectTapGestures
                        exoPlayer?.let { player ->
                            val width = size.width
                            if (offset.x < width / 2) {
                                // Rewind 10s
                                val target = (player.currentPosition - 10000).coerceAtLeast(0)
                                player.seekTo(target)
                                doubleTapText = "-10s"
                            } else {
                                // Fast-forward 10s
                                val target = (player.currentPosition + 10000).coerceAtMost(player.duration)
                                player.seekTo(target)
                                doubleTapText = "+10s"
                            }
                            showDoubleTapOverlay = true
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (isLocked) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        if (offset.x < width / 2) {
                            showBrightnessOverlay = true
                            showVolumeOverlay = false
                            // Get current brightness
                            val attrs = activity?.window?.attributes
                            brightnessValue = attrs?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                        } else {
                            showVolumeOverlay = true
                            showBrightnessOverlay = false
                            // Get volume
                            val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                            val cur = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
                            volumeValue = if (max > 0) cur / max else 0f
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        val width = size.width
                        val isLeft = change.position.x < width / 2
                        val delta = -dragAmount / 500f // scroll up increases, scroll down decreases
                        
                        if (isLeft) {
                            brightnessValue = (brightnessValue + delta).coerceIn(0.01f, 1.0f)
                            activity?.let { act ->
                                val lp = act.window.attributes
                                lp.screenBrightness = brightnessValue
                                act.window.attributes = lp
                            }
                        } else {
                            volumeValue = (volumeValue + delta).coerceIn(0f, 1.0f)
                            val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                            val targetVol = (volumeValue * max).toInt().coerceIn(0, max)
                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
                        }
                    },
                    onDragEnd = {
                        showBrightnessOverlay = false
                        showVolumeOverlay = false
                    }
                )
            }
    ) {
        if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Animated double tap visual indicator overlay
        AnimatedVisibility(
            visible = showDoubleTapOverlay,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .size(90.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (doubleTapText.startsWith("-")) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = doubleTapText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            LaunchedEffect(showDoubleTapOverlay) {
                if (showDoubleTapOverlay) {
                    delay(800)
                    showDoubleTapOverlay = false
                }
            }
        }

        // Gesture overlays for brightness and volume
        if (showBrightnessOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Brightness5, contentDescription = null, tint = Color.Yellow)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(100.dp)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(brightnessValue)
                                .align(Alignment.BottomStart)
                                .background(MovieRed, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }

        if (showVolumeOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = if (volumeValue == 0f) Icons.Default.VolumeMute else Icons.Default.VolumeUp, contentDescription = null, tint = MovieRed)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(100.dp)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(volumeValue)
                                .align(Alignment.BottomStart)
                                .background(MovieRed, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }

        // Custom controller overlay
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                if (isLocked) {
                    // Lock Screen Icon Mode
                    IconButton(
                        onClick = { isLocked = false; isControlsVisible = true },
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(60.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlock screen controls",
                            tint = MovieRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    // Full Premium Controls Overlay
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    // Fullscreen toggle
                                    isFullscreen = !isFullscreen
                                    activity?.requestedOrientation = if (isFullscreen) {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    } else {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    }
                                },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Premium Cinema Player",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        // Right Top actions (Speed, Quality, Lock)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Aspect ratio button
                            IconButton(
                                onClick = {
                                    resizeMode = when (resizeMode) {
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                    val modeName = when (resizeMode) {
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom (Crop)"
                                        else -> "Stretch"
                                    }
                                    Toast.makeText(context, "Aspect Ratio: $modeName", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))

                            // Speed selection
                            Box {
                                IconButton(
                                    onClick = { showSpeedMenu = true },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Playback speed",
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false },
                                    modifier = Modifier.background(PremiumDarkBg)
                                ) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                        DropdownMenuItem(
                                            text = { Text("${speed}x", color = if (currentSpeed == speed) MovieRed else SoftWhite) },
                                            onClick = {
                                                currentSpeed = speed
                                                exoPlayer?.setPlaybackSpeed(speed)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))

                            // Quality selection
                            Box {
                                IconButton(
                                    onClick = { showQualityMenu = true },
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Quality",
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = showQualityMenu,
                                    onDismissRequest = { showQualityMenu = false },
                                    modifier = Modifier.background(PremiumDarkBg)
                                ) {
                                    listOf("Auto", "1080p HD", "720p HD", "480p").forEach { quality ->
                                        DropdownMenuItem(
                                            text = { Text(quality, color = if (selectedQuality == quality) MovieRed else SoftWhite) },
                                            onClick = {
                                                selectedQuality = quality
                                                showQualityMenu = false
                                                Toast.makeText(context, "Streaming quality: $quality", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))

                            // Lock screen button
                            IconButton(
                                onClick = { isLocked = true },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Lock controls",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Middle playback actions (Previous, Rewind, Play/Pause, FastForward, Next)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        IconButton(
                            onClick = {
                                exoPlayer?.let {
                                    val target = (it.currentPosition - 10000).coerceAtLeast(0L)
                                    it.seekTo(target)
                                }
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play/Pause Large central button
                        IconButton(
                            onClick = {
                                exoPlayer?.let {
                                    if (it.isPlaying) {
                                        it.pause()
                                    } else {
                                        it.play()
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(MovieRed, CircleShape)
                                .size(68.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlayingState) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingState) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                exoPlayer?.let {
                                    val target = (it.currentPosition + 10000).coerceAtMost(it.duration)
                                    it.seekTo(target)
                                }
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Bottom progress bar and controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        // Progress Slider
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            onValueChange = { progress ->
                                exoPlayer?.let {
                                    val target = (progress * duration).toLong()
                                    it.seekTo(target)
                                    currentPosition = target
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MovieRed,
                                activeTrackColor = MovieRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Duration labels and speed/mute details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPosition) + " / " + formatTime(duration),
                                color = SoftWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Mute option
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        exoPlayer?.volume = if (isMuted) 0f else 1.0f
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "Auto Quality",
                                    color = SoftWhite,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .background(MovieRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Utility to format ExoPlayer position (ms) to string "00:00"
fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun getFileName(context: android.content.Context, uri: android.net.Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Unknown file"
}

// ==========================================
// PREMIUM SEARCH SCREEN COMPONENT
// ==========================================
@Composable
fun SearchScreen(viewModel: MediaViewModel) {
    val queryText by viewModel.searchQueryText.collectAsStateWithLifecycle()
    val isSearchLoading by viewModel.isSearchLoading.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val suggestions = listOf("Action", "Comedy", "Anime", "Sci-Fi", "Netflix", "Recommend", "Disney+", "4K Ultra HD")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumDarkBg)
            .padding(top = 40.dp, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = "Discover Content",
            color = SoftWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Large Premium Modern Search Bar
        OutlinedTextField(
            value = queryText,
            onValueChange = { viewModel.setSearchQueryText(it) },
            placeholder = {
                Text(
                    text = "Search by title, genre, hashtags, language...",
                    color = MutedSlate,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MovieRed,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (queryText.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQueryText("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = SoftWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MovieRed,
                unfocusedBorderColor = DeepGrey,
                focusedTextColor = SoftWhite,
                unfocusedTextColor = SoftWhite,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            ),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("premium_search_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearchLoading) {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MovieRed)
            }
        } else if (queryText.isBlank()) {
            // Empty State: Show recommendations, popular tags, and suggestions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = "Popular Searches",
                    color = SoftWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Suggestions Scroll Row
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    items(suggestions) { tag ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, MovieRed.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(20.dp),
                            onClick = { viewModel.setSearchQueryText(tag) },
                            modifier = Modifier.testTag("search_suggest_$tag")
                        ) {
                            Text(
                                text = tag,
                                color = SoftWhite,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = MovieRed.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Instant real-time search across wholeTV database",
                            color = MutedSlate,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (searchResults.isEmpty()) {
            // No Results State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.SearchOff,
                        contentDescription = null,
                        tint = MutedSlate,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No results found for \"$queryText\"",
                        color = SoftWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Try searching for categories, hashtags, genres, or languages.",
                        color = MutedSlate,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            // Results State: Beautiful Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = "${searchResults.size} matches found",
                    color = MutedSlate,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchResults) { item ->
                        MediaCard(
                            item = item,
                            onClick = { viewModel.selectMedia(item.id) }
                        )
                    }
                }
            }
        }
    }
}
