package com.example.foiltracker.sync

import android.util.Log
import kotlinx.coroutines.cancel
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class WearDeleteReceiverService :
    WearableListenerService() {

    companion object {

        private const val TAG =
            "FoilTrackerSync"

        private const val DELETE_PREFIX =
            "/foiltracker/delete/"

        private const val FILE_PREFIX =
            "/foiltracker/file/"
    }

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    override fun onDataChanged(
        events: DataEventBuffer
    ) {

        for (event in events) {

            if (
                event.type !=
                DataEvent.TYPE_CHANGED
            ) {
                continue
            }

            val item =
                event.dataItem

            val path =
                item.uri.path
                    ?: continue

            if (
                !path.startsWith(
                    DELETE_PREFIX
                )
            ) {
                continue
            }

            val frozenItem =
                item.freeze()

            serviceScope.launch {

                processDelete(
                    frozenItem
                )
            }
        }
    }

    private suspend fun processDelete(
        item: com.google.android.gms.wearable.DataItem
    ) {

        try {

            val dataMap =
                DataMapItem
                    .fromDataItem(item)
                    .dataMap

            val filename =
                dataMap.getString(
                    "filename"
                )
                    ?: File(item.uri.path ?: "").name

            if (
                !isValidFilename(filename)
            ) {

                Log.e(
                    TAG,
                    "Rejected invalid filename: $filename"
                )

                return
            }

            /*
             * -------------------------------------------------
             * 1. DELETE LOCAL WATCH FILE
             * -------------------------------------------------
             */

            deleteLocalFile(
                filename
            )

            /*
             * -------------------------------------------------
             * 2. DELETE ORIGINAL FILE DATA ITEM
             * -------------------------------------------------
             *
             * This is the important part.
             *
             * Without this, the phone will see the old GPX
             * DataItem again the next time it synchronizes.
             */

            val filePath =
                FILE_PREFIX +
                    filename

            val deletedCount =
                Wearable
                    .getDataClient(this)
                    .deleteDataItems(
                        android.net.Uri.parse(
                            "wear:$filePath"
                        )
                    )
                    .await()

            Log.i(
                TAG,
                "Deleted file DataItem: " +
                    "$filename " +
                    "(count=$deletedCount)"
            )

            /*
             * -------------------------------------------------
             * 3. DELETE DELETE-COMMAND DATA ITEM
             * -------------------------------------------------
             *
             * The command itself is no longer needed.
             */

            Wearable
                .getDataClient(this)
                .deleteDataItems(
                    item.uri
                )
                .await()

            Log.i(
                TAG,
                "DELETE received and completed: $filename"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed processing delete command",
                e
            )
        }
    }

    private fun deleteLocalFile(
        filename: String
    ) {

        val file =
            File(
                filesDir,
                filename
            )

        if (file.exists()) {

            if (file.delete()) {

                Log.i(
                    TAG,
                    "Deleted watch file: $filename"
                )

            } else {

                Log.e(
                    TAG,
                    "Could not delete watch file: $filename"
                )
            }

        } else {

            Log.i(
                TAG,
                "Watch file already absent: $filename"
            )
        }
    }

    private fun isValidFilename(
        filename: String
    ): Boolean {

        return !filename.contains("/") &&
            !filename.contains("\\") &&
            !filename.contains("..") &&
            filename.isNotBlank()
    }

    override fun onDestroy() {

        serviceScope.cancel()

        super.onDestroy()
    }
}
