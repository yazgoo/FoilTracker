package com.example.foiltracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver

import java.util.Locale

class MainActivity : ComponentActivity() {

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
                /*
                 * The Activity remains in the foreground.
                 *
                 * FLAG_KEEP_SCREEN_ON prevents the normal
                 * screen timeout while tracking.
                 */
            }

            override fun onExitAmbient() {
                // Back to normal interactive mode.
            }

            override fun onUpdateAmbient() {
                // Called when the ambient display should update.
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        /*
         * If tracking was already active when Android recreated
         * the Activity, keep the screen awake.
         */
        if (LocationService.recording.value) {
            setKeepScreenOn(true)
        }

        /*
         * Enable Wear OS ambient mode support.
         */
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

    /*
     * Keep the watch display awake while tracking.
     */
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
                Manifest.permission.ACTIVITY_RECOGNITION
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

        /*
         * Prevent the display from turning off while tracking.
         */
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

        /*
         * Allow the normal screen timeout again.
         */
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

        val recording by
        LocationService.recording
            .collectAsState()

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
                        "Speed: %.1f km/h",
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
                    text = "FoilTracker"
                )

                Text(
                    text =
                        "Build: ${BuildConfig.BUILD_DATE}"
                )

                Button(
                    onClick = {

                        if (recording) {
                            stopTracking()
                        } else {
                            startTracking()
                        }
                    }
                ) {

                    Text(
                        text =
                            if (recording) {
                                "STOP"
                            } else {
                                "START"
                            }
                    )
                }
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
