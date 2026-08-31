package com.example.foiltracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackFileDao {
    @Query("SELECT * FROM track_files")
    suspend fun getAll(): List<TrackFile>

    @Query(
        """
        SELECT *
        FROM track_files
        ORDER BY modifiedTime DESC
        """
    )
    fun observeAll():
        Flow<List<TrackFile>>


    @Query(
        """
        SELECT *
        FROM track_files
        WHERE syncId = :syncId
        LIMIT 1
        """
    )
    suspend fun findBySyncId(
        syncId: String
    ): TrackFile?


    @Insert(
        onConflict = OnConflictStrategy.REPLACE
    )
    suspend fun insert(
        track: TrackFile
    )


    @Query(
        """
        DELETE FROM track_files
        WHERE syncId = :syncId
        """
    )
    suspend fun delete(
        syncId: String
    )
}
