package org.piouz.pumpfoil.ui

import androidx.compose.runtime.*
import org.piouz.pumpfoil.data.TrackFile

@Composable
fun FoilTrackerApp() {

    var selectedTrack
        by remember {
            mutableStateOf<TrackFile?>(null)
        }


    if (selectedTrack == null) {

        TrackListScreen(
            onOpen = {
                selectedTrack = it
            }
        )

    } else {

        TrackViewerScreen(
            track = selectedTrack!!,
            onBack = {
                selectedTrack = null
            }
        )
    }
}
