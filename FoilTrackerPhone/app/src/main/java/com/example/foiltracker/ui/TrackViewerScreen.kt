package com.example.foiltracker.ui

import com.example.foiltracker.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.foiltracker.data.TrackFile
import com.example.foiltracker.sharing.FileSharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import androidx.compose.ui.unit.sp

private fun formatDuration(
    seconds: Long
): String {

    val minutes =
        seconds / 60

    val remainingSeconds =
        seconds % 60

    if(minutes > 59) {
        val hours = minutes / 60
        val minutes2 = minutes % 60
        return String.format(
            "%02d:%02d:%02d",
            hours,
            minutes2,
            remainingSeconds
        )
    }

    return String.format(
        "%02d:%02d",
        minutes,
        remainingSeconds
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackViewerScreen(
    track: TrackFile,
    onBack: () -> Unit
) {

    val context =
        LocalContext.current


    var text by
        remember {
            mutableStateOf<String?>(null)
        }


    LaunchedEffect(track.localPath) {

        text =
            withContext(Dispatchers.IO) {

                var content = "Runs\n\n"
                content += "Start    Duration    Distance    Speed"
                try {
                    val file = File(track.localPath)
                    FoilTrackerReporter().getReport(file, false)!!.runs.forEach { run ->
                        content += "\n\n${run.startTime}    ${run.duration}    ${run.distance}    ${run.speed}"
                    }
                } catch (exception: Exception) {

                    "Unable to read GPX:\n\n" +
                        exception.message + "\n\n" +
                        exception.stackTraceToString()
                }
                content
            }
    }


    Scaffold(

        topBar = {

            TopAppBar(

                title = {
                    Text(track.filename)
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription =
                                "Back"
                        )
                    }
                },

                actions = {

                    IconButton(

                        onClick = {

                            FileSharing.share(
                                context,
                                track
                            )
                        }
                    ) {

                        Icon(
                            Icons.Default.Share,
                            contentDescription =
                                "Share"
                        )
                    }
                }
            )
        }

    ) { padding ->

        Text(

            text =
                text ?: "Loading...",

            fontSize = 22.sp,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),

            style =
                MaterialTheme
                    .typography
                    .bodySmall
        )
    }
}
