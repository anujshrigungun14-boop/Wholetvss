package com.company.wholetv.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val category: String, // "Recommend", "Reality-TV", "Movies", "TV Shows", "Anime"
    val hashtags: String = "", // e.g., "#fantasy #action"
    val qualities: String = "1080p HD", // e.g., "720p HD, 1080p HD, 4K Ultra HD"
    val languages: String = "English", // e.g., "English, Multilingual (Hindi, Spanish)"
    val posterUrl: String = "", // Can be custom URL or fallback identifier
    val rating: Double = 8.5,
    val isSlide: Boolean = false, // If true, appears in the sliding carousel
    val badge: String = "", // e.g., "New episode", "Update to 12"
    val views: Int = 0,
    val likes: Int = 0,
    val shares: Int = 0,
    val downloads: Int = 0,
    val streamingPlatform: String = "None", // "Netflix", "Disney+", "Prime Video", "None"
    val videoUrl: String = "",
    val firestoreId: String = "",
    val uploaderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val genre: String = "Action",
    val uploaderName: String = "Anonymous",
    val watchTime: Long = 0,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false
)
