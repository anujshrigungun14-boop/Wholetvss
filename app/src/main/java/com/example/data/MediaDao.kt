package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY timestamp DESC")
    fun getAllMediaItems(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE category = :category ORDER BY timestamp DESC")
    fun getMediaItemsByCategory(category: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isSlide = 1 ORDER BY timestamp DESC")
    fun getSlides(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun getMediaItemById(id: Int): Flow<MediaItem?>

    @Query("SELECT * FROM media_items WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR hashtags LIKE '%' || :query || '%'")
    fun searchMediaItems(query: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE streamingPlatform = :platform ORDER BY timestamp DESC")
    fun getMediaItemsByStreamingPlatform(platform: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM comments WHERE mediaItemId = :mediaItemId ORDER BY timestamp DESC")
    fun getCommentsForMedia(mediaItemId: Int): Flow<List<Comment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(item: MediaItem): Long

    @Query("DELETE FROM media_items WHERE firestoreId = :firestoreId")
    suspend fun deleteByFirestoreId(firestoreId: String)

    @Query("SELECT * FROM media_items WHERE firestoreId = :firestoreId LIMIT 1")
    suspend fun getMediaItemByFirestoreId(firestoreId: String): MediaItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: Comment)

    @Query("UPDATE media_items SET views = views + 1 WHERE id = :id")
    suspend fun incrementViews(id: Int)

    @Query("UPDATE media_items SET likes = likes + 1 WHERE id = :id")
    suspend fun incrementLikes(id: Int)

    @Query("UPDATE media_items SET shares = shares + 1 WHERE id = :id")
    suspend fun incrementShares(id: Int)

    @Query("UPDATE media_items SET downloads = downloads + 1 WHERE id = :id")
    suspend fun incrementDownloads(id: Int)

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun getCount(): Int
}
