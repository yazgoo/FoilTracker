package com.example.foiltracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foiltracker.FoilTrackerApplication
import com.example.foiltracker.data.TrackFile
import com.example.foiltracker.sharing.FileSharing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(
    onOpen: (TrackFile) -> Unit
) {

    val context =
        LocalContext.current


    val application =
        context.applicationContext
            as FoilTrackerApplication


    val tracks by
        application.repository
            .observeTracks()
            .collectAsStateWithLifecycle(
                initialValue =
                    emptyList()
            )


    Scaffold(

        topBar = {

            TopAppBar(
                title = {
                    Text("FoilTracker")
                }
            )
        }

    ) { padding ->

        if (tracks.isEmpty()) {

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    "No tracks yet"
                )
            }

        } else {

            LazyColumn(

                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),

                contentPadding =
                    PaddingValues(16.dp),

                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                items(
                    tracks,
                    key = {
                        it.syncId
                    }
                ) { track ->

                    TrackRow(
                        track = track,

                        onOpen = {
                            onOpen(track)
                        },

                        onShare = {
                            FileSharing.share(
                                context,
                                track
                            )
                        },

                        onDelete = {

                            CoroutineScope(
                                Dispatchers.IO
                            ).launch {

                                application
                                    .repository
                                    .delete(track)
                            }
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun TrackRow(
    track: TrackFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {

    Card(

        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onOpen()
                }
    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    track.filename,
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Spacer(
                    Modifier.height(4.dp)
                )

                Text(
                    formatDate(
                        track.modifiedTime
                    )
                )

                Text(
                    formatSize(
                        track.sizeBytes
                    ),

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }


            IconButton(
                onClick = onShare
            ) {

                Icon(
                    Icons.Default.Share,
                    contentDescription =
                        "Share"
                )
            }


            IconButton(
                onClick = onDelete
            ) {

                Icon(
                    Icons.Default.Delete,
                    contentDescription =
                        "Delete"
                )
            }
        }
    }
}


private fun formatDate(
    timestamp: Long
): String {

    return SimpleDateFormat(
        "dd MMM yyyy  HH:mm",
        Locale.getDefault()
    ).format(
        Date(timestamp)
    )
}


private fun formatSize(
    bytes: Long
): String {

    return when {

        bytes < 1024 ->
            "$bytes B"

        bytes < 1024 * 1024 ->
            "%.1f KB".format(
                bytes / 1024.0
            )

        else ->
            "%.1f MB".format(
                bytes / 1024.0 / 1024.0
            )
    }
}
