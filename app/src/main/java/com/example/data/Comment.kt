package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comments")
data class Comment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mediaItemId: Int,
    val userName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
