package org.piouz.pumpfoil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale


class MainActivity : ComponentActivity() {

    @Composable
    fun SlideToConfirm(
        text: String,
        onConfirmed: () -> Unit
    ) {
        var offsetX by remember { mutableFloatStateOf(0f) }
        var maxOffset by remember { mutableFloatStateOf(0f) }

        val thumbSize = 40.dp
        val density = LocalDensity.current
        val thumbSizePx = with(density) { thumbSize.toPx() }

        Box(
            modifier = Modifier
            .width(100.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .onSizeChanged { size ->
                maxOffset = (size.width - thumbSizePx).coerceAtLeast(0f)
            }
        ) {
            Text(
                text = text,
                modifier = Modifier.align(Alignment.Center)
            )

            Box(
                modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(maxOffset) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount)
                            .coerceIn(0f, maxOffset)
                        },
                        onDragEnd = {
                            if (offsetX >= maxOffset * 0.9f) {
                                onConfirmed()
                            }
                            offsetX = 0f
                        }
                    )
                },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null
                )
            }
        }
    } 
    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
    }

    private lateinit var ambientObserver:
            AmbientLifecycleObserver

    private val ambientCallback =
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {

            override fun onEnterAmbient(
                ambientDetails:
                AmbientLifecycleObserver.AmbientDetails
            ) {
                // Keep current behaviour.
            }

            override fun onExitAmbient() {
                // Back to normal interactive mode.
            }

            override fun onUpdateAmbient() {
                // Called when ambient display should update.
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        if (LocationService.recording.value) {
            setKeepScreenOn(true)
        }

        ambientObserver =
            AmbientLifecycleObserver(
                this,
                ambientCallback
            )

        lifecycle.addObserver(
            ambientObserver
        )

        requestRequiredPermissions()

        setContent {
            TrackerScreen()
        }
    }

    private fun setKeepScreenOn(
        enabled: Boolean
    ) {
        if (enabled) {

            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )

        } else {

            window.clearFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun requestRequiredPermissions() {

        val permissionsList =
            mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION,
                "android.permission.health.READ_HEART_RATE"
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            permissionsList.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        val missingPermissions =
            permissionsList.filter {

                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

        if (missingPermissions.isNotEmpty()) {

            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun startTracking() {

        setKeepScreenOn(true)

        val intent =
            Intent(
                this,
                LocationService::class.java
            ).setAction(
                LocationService.ACTION_START
            )

        ContextCompat.startForegroundService(
            this,
            intent
        )
    }

    private fun stopTracking() {

        setKeepScreenOn(false)

        val intent =
            Intent(
                this,
                LocationService::class.java
            ).setAction(
                LocationService.ACTION_STOP
            )

        startService(intent)
    }

    @Composable
    fun TrackerScreen() {

        val distanceMeters by
        LocationService.totalDistanceMeters
            .collectAsState()

        val speedKmh by
        LocationService.speedKmh
            .collectAsState()

        val runDurationSeconds by
        LocationService.runDurationSeconds
            .collectAsState()

        val runDistanceMeters by
        LocationService.runDistanceMeters
            .collectAsState()

        val heartRateBpm by
        LocationService.heartRateBpm
            .collectAsState()

        val recording by
        LocationService.recording
            .collectAsState()

        /*
         * ---------------------------------------------------------
         * CURRENT TIME
         * ---------------------------------------------------------
         */

        var currentTime by remember {

            mutableStateOf(
                LocalTime.now().format(
                    DateTimeFormatter.ofPattern(
                        "HH:mm"
                    )
                )
            )
        }

        LaunchedEffect(Unit) {

            while (true) {

                currentTime =
                    LocalTime.now().format(
                        DateTimeFormatter.ofPattern(
                            "HH:mm"
                        )
                    )

                delay(10000)
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.SpaceEvenly
            ) {

                /*
                 * CLOCK
                 */
                Text(
                    text = currentTime,
                    fontWeight = FontWeight.Bold
                )

                /*
                 * DISTANCE
                 */
                val distanceText =
                    if (distanceMeters < 1000f) {

                        "${distanceMeters.toInt()} m"

                    } else {

                        String.format(
                            Locale.US,
                            "%.2f km",
                            distanceMeters / 1000f
                        )
                    }

                Text(
                    text = distanceText
                )

                /*
                 * SPEED
                 */
                Text(
                    text = String.format(
                        Locale.US,
                        "%.1f km/h",
                        speedKmh
                    )
                )

                /*
                 * DURATION ABOVE 7 KM/H
                 */
                Text(
                    text =
                        formatDuration(
                            runDurationSeconds
                        ),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = runDistanceMeters?.let {
                        "${it.toInt()} m"
                    } ?: "-- m"
                )

                /*
                 * HEART RATE
                 */
                Text(
                    text = heartRateBpm?.let {
                        "${it.toInt()} bpm"
                    } ?: "-- bpm"
                )

                Text(
                    text =
                        "build: ${BuildConfig.BUILD_DATE}"
                )

                SlideToConfirm(
                    text = if (recording) "STOP" else "START",
                    onConfirmed = {
                        if (recording) {
                            stopTracking()
                        } else {
                            startTracking()
                        }
                    }
                )
            }
        }
    }

    private fun formatDuration(
        seconds: Long
    ): String {

        val minutes =
            seconds / 60

        val remainingSeconds =
            seconds % 60

        return String.format(
            Locale.US,
            "%02d:%02d",
            minutes,
            remainingSeconds
        )
    }

    override fun onDestroy() {

        if (::ambientObserver.isInitialized) {

            lifecycle.removeObserver(
                ambientObserver
            )
        }

        super.onDestroy()
    }
}
