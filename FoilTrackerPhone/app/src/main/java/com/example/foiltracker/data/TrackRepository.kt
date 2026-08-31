package com.example.foiltracker.data

import android.content.Context
import com.example.foiltracker.sync.WearFileSync
import kotlinx.coroutines.flow.Flow
import java.io.File

class TrackRepository(
    private val context: Context,
    private val dao: TrackFileDao
) {

    fun observeTracks():
        Flow<List<TrackFile>> =
        dao.observeAll()

    suspend fun findBySyncId(
        syncId: String
    ): TrackFile? =
        dao.findBySyncId(syncId)

    suspend fun addTrack(
        syncId: String,
        filename: String,
        sourceFile: File,
        modifiedTime: Long,
        mimeType: String
    ): TrackFile {

        val tracksDirectory =
            File(
                context.filesDir,
                "tracks"
            )

        tracksDirectory.mkdirs()

        val safeFilename =
            File(filename).name

        val destination =
            File(
                tracksDirectory,
                safeFilename
            )

        val temporary =
            File(
                tracksDirectory,
                ".$safeFilename.tmp"
            )

        sourceFile.inputStream().use { input ->

            temporary.outputStream().use { output ->

                input.copyTo(
                    output,
                    bufferSize = 64 * 1024
                )
            }
        }

        if (
            !temporary.exists() ||
            temporary.length() == 0L
        ) {

            temporary.delete()

            throw IllegalStateException(
                "Received empty file"
            )
        }

        if (destination.exists()) {
            destination.delete()
        }

        if (
            !temporary.renameTo(destination)
        ) {

            temporary.delete()

            throw IllegalStateException(
                "Unable to finalize file"
            )
        }

        val track =
            TrackFile(
                syncId = syncId,
                filename = safeFilename,
                sizeBytes = destination.length(),
                modifiedTime = modifiedTime,
                receivedTime =
                    System.currentTimeMillis(),
                mimeType = mimeType,
                localPath =
                    destination.absolutePath
            )

        dao.insert(track)

        return track
    }

    suspend fun delete(
        track: TrackFile
    ) {

        // Tell watch to delete its local file.
        WearFileSync.sendDeleteCommand(
            context = context,
            filename = track.filename
        )

        // Delete the original Wear DataItem.
        WearFileSync.deleteFileDataItem(
            context = context,
            syncId = track.syncId
        )

        // Delete phone file.
        File(track.localPath).delete()

        // Delete phone database entry.
        dao.delete(track.syncId)
    }

    suspend fun deleteAll() {

        val tracks =
            dao.getAll()

        for (track in tracks) {

            delete(track)
        }
    }
}
