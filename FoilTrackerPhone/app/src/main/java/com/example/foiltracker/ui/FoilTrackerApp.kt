package com.example.foiltracker.ui

import androidx.compose.runtime.*
import com.example.foiltracker.data.TrackFile

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
