package com.example.foiltracker.ui

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

                try {

                    File(
                        track.localPath
                    ).readText()

                } catch (exception: Exception) {

                    "Unable to read GPX:\n\n" +
                        exception.message
                }
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
