package com.example.foiltracker.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WearFileReceiverService :
    WearableListenerService() {

    companion object {
        private const val TAG = "FoilTrackerSync"
    }

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    override fun onCreate() {
        super.onCreate()

        Log.i(
            TAG,
            "WearFileReceiverService CREATED"
        )
    }

    override fun onDataChanged(
        events: DataEventBuffer
    ) {
        Log.i(
            TAG,
            "onDataChanged: ${events.count} events"
        )

        for (event in events) {

            if (
                event.type !=
                DataEvent.TYPE_CHANGED
            ) {
                continue
            }

            val path =
                event.dataItem.uri.path
                    ?: continue

            if (
                !path.startsWith(
                    "/foiltracker/file/"
                )
            ) {
                continue
            }

            Log.i(
                TAG,
                "GPX DataItem received: $path"
            )

            val dataItem =
                event.dataItem.freeze()

            scope.launch {

                try {

                    WearFileSync.process(
                        applicationContext,
                        dataItem
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Failed importing $path",
                        e
                    )
                }
            }
        }
    }

    override fun onDestroy() {

        scope.cancel()

        super.onDestroy()
    }
}
