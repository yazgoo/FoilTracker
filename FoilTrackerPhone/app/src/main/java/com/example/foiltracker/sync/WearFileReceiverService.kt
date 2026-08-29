package com.example.foiltracker.sync

import android.content.Context
import android.util.Log
import com.example.foiltracker.FoilTrackerApplication
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

object WearFileSync {

    private const val TAG =
        "FoilTrackerSync"

    private const val PATH_PREFIX =
        "/foiltracker/file/"

    private const val ASSET_KEY =
        "file"

    suspend fun sync(
        context: Context
    ) = withContext(Dispatchers.IO) {

        Log.i(
            TAG,
            "Starting Data Layer sync"
        )

        try {

            val application =
                context.applicationContext
                    as FoilTrackerApplication

            val dataItems =
                Wearable
                    .getDataClient(context)
                    .dataItems
                    .await()

            Log.i(
                TAG,
                "Found ${dataItems.count} DataItems"
            )

            for (item in dataItems) {

                try {

                    processItem(
                        context,
                        application,
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

            dataItems.release()

            Log.i(
                TAG,
                "Data Layer sync complete"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Data Layer sync failed",
                e
            )
        }
    }

    private suspend fun processItem(
        context: Context,
        application: FoilTrackerApplication,
        dataItem: DataItem
    ) {

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

        /*
         * Already imported?
         */
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
            dataMap.getString(
                "filename"
            )
                ?: File(path).name

        val modifiedTime =
            dataMap.getLong(
                "modified",
                System.currentTimeMillis()
            )

        val mimeType =
            dataMap.getString(
                "mime_type"
            )
                ?: "application/gpx+xml"

        val asset =
            dataMap.getAsset(
                ASSET_KEY
            )
                ?: throw IllegalStateException(
                    "Missing Asset"
                )

        val tempDir =
            File(
                context.cacheDir,
                "wear_assets"
            )

        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val tempFile =
            File(
                tempDir,
                "${System.nanoTime()}.gpx"
            )

        try {

            /*
             * Download Asset.
             */
            val response =
                Wearable
                    .getDataClient(context)
                    .getFdForAsset(asset)
                    .await()

            try {

                val input =
                    response.getInputStream()

                input.use { stream ->

                    tempFile.outputStream()
                        .use { output ->

                        stream.copyTo(
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

            /*
             * Store permanently.
             */
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
