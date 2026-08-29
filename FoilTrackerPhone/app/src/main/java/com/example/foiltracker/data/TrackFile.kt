package com.example.foiltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_files"
)
data class TrackFile(

    @PrimaryKey
    val syncId: String,

    val filename: String,

    val sizeBytes: Long,

    val modifiedTime: Long,

    val receivedTime: Long,

    val mimeType: String,

    val localPath: String
)
