package com.example.foiltracker.sync

import android.content.Context
import android.util.Log
import com.example.foiltracker.FoilTrackerApplication
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import java.io.File
import com.google.android.gms.wearable.PutDataMapRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WearFileSync {

    private const val TAG =
        "FoilTrackerSync"

    private const val PATH_PREFIX =
        "/foiltracker/file/"

    private const val ASSET_KEY =
        "file"

    suspend fun deleteFileDataItem(
        context: Context,
        syncId: String
    ) = withContext(Dispatchers.IO) {

        try {

            val uri =
                android.net.Uri.parse(syncId)

            Wearable
                .getDataClient(context)
                .deleteDataItems(uri)
                .await()

            Log.i(
                TAG,
                "Deleted Wear DataItem: $syncId"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed deleting Wear DataItem: $syncId",
                e
            )
        }
    }
    suspend fun sendDeleteCommand(
        context: Context,
        filename: String
    ) = withContext(Dispatchers.IO) {

        val safeFilename = File(filename).name

        val path =
            "/foiltracker/delete/$safeFilename"

        try {

            val request =
                PutDataMapRequest
                    .create(path)
                    .apply {

                        dataMap.putString(
                            "operation",
                            "delete"
                        )

                        dataMap.putString(
                            "filename",
                            safeFilename
                        )

                        dataMap.putLong(
                            "timestamp",
                            System.currentTimeMillis()
                        )
                    }
                    .asPutDataRequest()
                    .setUrgent()

            Wearable
                .getDataClient(context)
                .putDataItem(request)
                .await()

            Log.i(
                "FoilTrackerSync",
                "DELETE sent to watch: $safeFilename"
            )

        } catch (e: Exception) {

            Log.e(
                "FoilTrackerSync",
                "Failed to send DELETE: $safeFilename",
                e
            )
        }
    }

    suspend fun sync(
        context: Context
    ) {

        Log.i(
            TAG,
            "Starting Data Layer sync"
        )

        val application =
            context.applicationContext
                as FoilTrackerApplication

        val dataItems =
            Wearable
                .getDataClient(context)
                .dataItems
                .await()

        try {

            Log.i(
                TAG,
                "Found ${dataItems.count} DataItems"
            )

            for (item in dataItems) {

                try {

                    process(
                        context,
                        item
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Failed processing ${item.uri}",
                        e
                    )
                }
            }

        } finally {

            dataItems.release()
        }

        Log.i(
            TAG,
            "Data Layer sync complete"
        )
    }

    suspend fun process(
        context: Context,
        dataItem: DataItem
    ) {

        val application =
            context.applicationContext
                as FoilTrackerApplication

        val path =
            dataItem.uri.path
                ?: return

        if (
            !path.startsWith(
                PATH_PREFIX
            )
        ) {
            return
        }

        val syncId =
            dataItem.uri.toString()

        if (
            application.repository
                .findBySyncId(syncId)
                != null
        ) {

            Log.d(
                TAG,
                "Already imported: $syncId"
            )

            return
        }

        val dataMap =
            DataMapItem
                .fromDataItem(dataItem)
                .dataMap

        val filename =
            dataMap.getString("filename")
                ?: File(path).name

        val modifiedTime =
            dataMap.getLong(
                "modified",
                System.currentTimeMillis()
            )

        val mimeType =
            dataMap.getString("mime_type")
                ?: "application/gpx+xml"

        val asset =
            dataMap.getAsset(ASSET_KEY)
                ?: throw IllegalStateException(
                    "Missing Asset"
                )

        val tempDir =
            File(
                context.cacheDir,
                "wear_assets"
            )

        tempDir.mkdirs()

        val tempFile =
            File(
                tempDir,
                "${System.nanoTime()}.gpx"
            )

        try {

            val response =
                Wearable
                    .getDataClient(context)
                    .getFdForAsset(asset)
                    .await()

            try {

                response
                    .getInputStream()
                    .use { input ->

                        tempFile
                            .outputStream()
                            .use { output ->

                            input.copyTo(
                                output,
                                64 * 1024
                            )
                        }
                    }

            } finally {

                response.release()
            }

            if (
                !tempFile.exists() ||
                tempFile.length() == 0L
            ) {

                throw IllegalStateException(
                    "Downloaded file is empty"
                )
            }

            application.repository.addTrack(

                syncId =
                    syncId,

                filename =
                    filename,

                sourceFile =
                    tempFile,

                modifiedTime =
                    modifiedTime,

                mimeType =
                    mimeType
            )

            Log.i(
                TAG,
                "IMPORTED GPX: " +
                    "$filename " +
                    "(${tempFile.length()} bytes)"
            )

        } finally {

            tempFile.delete()
        }
    }
}
