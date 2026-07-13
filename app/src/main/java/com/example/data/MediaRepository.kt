package com.company.wholetv.data

import kotlinx.coroutines.flow.Flow

class MediaRepository(private val mediaDao: MediaDao) {
    val allMediaItems: Flow<List<MediaItem>> = mediaDao.getAllMediaItems()
    val slides: Flow<List<MediaItem>> = mediaDao.getSlides()

    fun getMediaItemsByCategory(category: String): Flow<List<MediaItem>> {
        return mediaDao.getMediaItemsByCategory(category)
    }

    fun getMediaItemById(id: Int): Flow<MediaItem?> {
        return mediaDao.getMediaItemById(id)
    }

    fun searchMediaItems(query: String): Flow<List<MediaItem>> {
        return mediaDao.searchMediaItems(query)
    }

    fun getMediaItemsByStreamingPlatform(platform: String): Flow<List<MediaItem>> {
        return mediaDao.getMediaItemsByStreamingPlatform(platform)
    }

    fun getCommentsForMedia(mediaItemId: Int): Flow<List<Comment>> {
        return mediaDao.getCommentsForMedia(mediaItemId)
    }

    suspend fun insertMediaItem(item: MediaItem): Long {
        return mediaDao.insertMediaItem(item)
    }

    suspend fun deleteByFirestoreId(firestoreId: String) {
        mediaDao.deleteByFirestoreId(firestoreId)
    }

    suspend fun getMediaItemByFirestoreId(firestoreId: String): MediaItem? {
        return mediaDao.getMediaItemByFirestoreId(firestoreId)
    }

    suspend fun insertComment(comment: Comment) {
        mediaDao.insertComment(comment)
    }

    suspend fun incrementViews(id: Int) {
        mediaDao.incrementViews(id)
    }

    suspend fun incrementLikes(id: Int) {
        mediaDao.incrementLikes(id)
    }

    suspend fun incrementShares(id: Int) {
        mediaDao.incrementShares(id)
    }

    suspend fun incrementDownloads(id: Int) {
        mediaDao.incrementDownloads(id)
    }

    suspend fun getCount(): Int {
        return mediaDao.getCount()
    }
}
