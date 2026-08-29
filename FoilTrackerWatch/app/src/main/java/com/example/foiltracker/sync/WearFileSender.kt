package com.example.foiltracker.sync

import android.content.Context
import android.os.ParcelFileDescriptor
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

import android.util.Log
import kotlinx.coroutines.tasks.await

object WearFileSender {

    private const val TAG =
        "FoilTrackerSync"

    private const val PATH_PREFIX =
        "/foiltracker/file/"

    suspend fun logConnectedNodes(context: Context) {
        try {
            val nodes =
                Wearable
                    .getNodeClient(context)
                    .connectedNodes
                    .await()

            Log.i(
                "FoilTrackerSync",
                "CONNECTED NODES: ${nodes.size}"
            )

            nodes.forEach { node ->
                Log.i(
                    "FoilTrackerSync",
                    "NODE: id=${node.id} " +
                        "displayName=${node.displayName} " +
                        "nearby=${node.isNearby}"
                )
            }

        } catch (e: Exception) {
            Log.e(
                "FoilTrackerSync",
                "Unable to get connected nodes",
                e
            )
        }
    }
    suspend fun sendFile(
        context: Context,
        file: File
    ) = withContext(Dispatchers.IO) {

        if (!file.exists()) {
            Log.e(
                TAG,
                "File does not exist: ${file.absolutePath}"
            )
            return@withContext
        }

        if (file.length() == 0L) {
            Log.e(
                TAG,
                "File is empty: ${file.name}"
            )
            return@withContext
        }

        var descriptor:
            ParcelFileDescriptor? = null

        try {

            descriptor =
                ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY
                )

            val asset =
                Asset.createFromFd(
                    descriptor
                )

            val path =
                PATH_PREFIX +
                    file.name

            val request =
                PutDataMapRequest
                    .create(path)
                    .apply {

                        dataMap.putAsset(
                            "file",
                            asset
                        )

                        dataMap.putString(
                            "filename",
                            file.name
                        )

                        dataMap.putLong(
                            "modified",
                            file.lastModified()
                        )

                        dataMap.putString(
                            "mime_type",
                            "application/gpx+xml"
                        )

                        dataMap.putLong(
                            "sync_timestamp",
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
                TAG,
                "GPX sent: ${file.name}"
            )

        } catch (
            exception: Exception
        ) {

            Log.e(
                TAG,
                "GPX send failed",
                exception
            )

        } finally {

            try {
                descriptor?.close()
            } catch (
                _: Exception
            ) {
            }
        }
    }
}
