package com.example.foiltracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.concurrent.futures.await
import androidx.core.app.NotificationCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import com.example.foiltracker.core.RunDurationEvent
import com.example.foiltracker.core.RunDurationCalculator
import com.example.foiltracker.core.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LocationService : Service() {

    companion object {

        private val _recording =
            MutableStateFlow(false)

        val recording: StateFlow<Boolean> =
            _recording.asStateFlow()

        const val ACTION_START =
            "com.example.foiltracker.START"

        const val ACTION_STOP =
            "com.example.foiltracker.STOP"

        private const val CHANNEL_ID =
            "foiltracker_gps"

        private const val NOTIFICATION_ID =
            1

        private val _totalDistanceMeters =
            MutableStateFlow(0f)

        val totalDistanceMeters:
            StateFlow<Float> =
            _totalDistanceMeters.asStateFlow()

        private val _speedKmh =
            MutableStateFlow(0.0)

        val speedKmh:
            StateFlow<Double> =
            _speedKmh.asStateFlow()

        private val _runDurationSeconds =
            MutableStateFlow(0L)

        val runDurationSeconds:
            StateFlow<Long> =
            _runDurationSeconds.asStateFlow()
    }

    private val serviceScope =
        CoroutineScope(
            Dispatchers.IO + Job()
        )

    private val exerciseClient by lazy {
        HealthServices
            .getClient(this)
            .exerciseClient
    }

    private val toneGenerator =
        ToneGenerator(
            AudioManager.STREAM_NOTIFICATION,
            100
        )

    private var gpxWriter: FileWriter? = null

    private var currentFile: File? = null

    private var accumulatedDistance =
        0f

    private var calculator =
        RunDurationCalculator()

    private val exerciseUpdateCallback =
        object : ExerciseUpdateCallback {

            override fun onExerciseUpdateReceived(
                update: ExerciseUpdate
            ) {

                val locationDataPoints =
                    update.latestMetrics.getData(
                        DataType.LOCATION
                    )

                if (locationDataPoints.isEmpty()) {
                    return
                }

                /*
                 * Convert Health Services boot-relative timestamps
                 * to Unix timestamps.
                 */
                val bootTimeMs =
                    System.currentTimeMillis() -
                        SystemClock.elapsedRealtime()

                locationDataPoints.forEach { dataPoint ->

                    val lat =
                        dataPoint.value.latitude

                    val lon =
                        dataPoint.value.longitude

                    val locationTimeMs =
                        bootTimeMs +
                            dataPoint
                                .timeDurationFromBoot
                                .toMillis()

                    val point =
                        TrackPoint(
                            latitude = lat,
                            longitude = lon,
                            timeMs = locationTimeMs
                        )

                    val result =
                        calculator.processPoint(point)

                    _speedKmh.value =
                        result.speedKmh

                    /*
                     * Distance.
                     */
                    if (result.acceptedForGpx) {

                        accumulatedDistance +=
                            result.distanceMeters

                        _totalDistanceMeters.value =
                            accumulatedDistance

                        /*
                         * Write accepted point to GPX.
                         */
                        writePoint(
                            lat = lat,
                            lon = lon,
                            timeMs = locationTimeMs
                        )
                    }

                    /*
                     * This is now the sole source of truth for the
                     * run duration.
                     */
                    _runDurationSeconds.value =
                        result.runDurationSeconds

                    handleEvents(
                        result.events
                    )
                }
            }

            override fun onAvailabilityChanged(
                dataType: DataType<*, *>,
                availability: Availability
            ) {
            }

            override fun onLapSummaryReceived(
                lapSummary: ExerciseLapSummary
            ) {
            }

            override fun onRegistered() {
            }

            override fun onRegistrationFailed(
                throwable: Throwable
            ) {
                android.util.Log.e(
                    "FoilTracker",
                    "Exercise callback registration failed",
                    throwable
                )
            }
        }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                startServiceForeground()
                startTracking()
            }

            ACTION_STOP -> {
                stopTracking()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleEvents(
        events: List<RunDurationEvent>
    ) {

        events.forEach { event ->

            when (event) {

                RunDurationEvent.QualifyingPeriodStarted -> {

                    toneGenerator.startTone(
                        ToneGenerator.TONE_PROP_BEEP,
                        200
                    )

                    android.util.Log.d(
                        "FoilTracker",
                        "Started qualifying >7 km/h period"
                    )
                }

                is RunDurationEvent.QualifyingPeriodEnded -> {

                    toneGenerator.startTone(
                        ToneGenerator.TONE_PROP_BEEP2,
                        200
                    )

                    android.util.Log.d(
                        "FoilTracker",
                        "Ended >7 km/h period: " +
                            "${event.durationSeconds}s"
                    )
                }
            }
        }
    }

    private fun startServiceForeground() {

        val notification =
            createNotification()

        if (Build.VERSION.SDK_INT >= 34) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun startTracking() {

        /*
         * Reset session.
         */
        _recording.value = true

        accumulatedDistance = 0f

        _totalDistanceMeters.value =
            0f

        _speedKmh.value =
            0.0

        _runDurationSeconds.value =
            0L

        /*
         * Create a NEW calculator for every recording.
         *
         * This is important because RunDurationCalculator is a
         * state machine.
         */
        resetCalculator()

        createGpxFile()

        serviceScope.launch {

            try {

                val config =
                    ExerciseConfig(
                        exerciseType =
                            ExerciseType.BIKING,

                        dataTypes =
                            setOf(
                                DataType.LOCATION
                            ),

                        isAutoPauseAndResumeEnabled =
                            false,

                        isGpsEnabled =
                            true
                    )

                exerciseClient
                    .setUpdateCallback(
                        exerciseUpdateCallback
                    )

                exerciseClient
                    .startExerciseAsync(config)
                    .await()

                android.util.Log.d(
                    "FoilTracker",
                    "WHS démarré"
                )

            } catch (e: Exception) {

                android.util.Log.e(
                    "FoilTracker",
                    "Erreur WHS",
                    e
                )

                stopSelf()
            }
        }
    }

    private fun resetCalculator() {
        calculator = RunDurationCalculator()
        /*
         * RunDurationCalculator intentionally has no reset()
         * method. Creating one per recording makes its lifecycle
         * explicit and avoids accidentally retaining GPS state.
         *
         * The property itself therefore needs to be replaceable.
         */
    }

    private fun stopTracking() {

        _recording.value = false

        serviceScope.launch {

            try {

                exerciseClient
                    .endExerciseAsync()
                    .await()

                exerciseClient
                    .clearUpdateCallbackAsync(
                        exerciseUpdateCallback
                    )

            } catch (_: Exception) {
            }

            /*
             * Finish any pending qualifying period.
             */
            val events =
                calculator.finish(
                    System.currentTimeMillis()
                )

            handleEvents(events)

            _runDurationSeconds.value =
                calculator.currentRunDurationSeconds

            closeGpxFile()

            stopForeground(
                STOP_FOREGROUND_REMOVE
            )

            stopSelf()
        }
    }

    private fun getCurrentRunDuration(): Long {
        /*
         * The calculator's processPoint() results are the public
         * state used during recording. At this point there is no
         * new point to process, so this method is only needed if
         * finish() ended an active period.
         *
         * The service can instead retain the last value.
         */
        return _runDurationSeconds.value
    }

    private fun createGpxFile() {

        try {

            val timestamp =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(Date())

            currentFile =
                File(
                    filesDir,
                    "foiltrack_$timestamp.gpx"
                )

            gpxWriter =
                FileWriter(
                    currentFile,
                    false
                )

            gpxWriter?.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1"
                     creator="FoilTracker"
                     xmlns="http://www.topografix.com/GPX/1/1">
                    <trk>
                        <name>FoilTracker WHS</name>
                        <trkseg>
                """.trimIndent()
            )

            gpxWriter?.flush()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun writePoint(
        lat: Double,
        lon: Double,
        timeMs: Long
    ) {

        val writer =
            gpxWriter ?: return

        try {

            val formatter =
                SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    Locale.US
                )

            formatter.timeZone =
                TimeZone.getTimeZone("UTC")

            val time =
                formatter.format(
                    Date(timeMs)
                )

            writer.write(
                """
                
                <trkpt lat="$lat" lon="$lon">
                    <time>$time</time>
                    <extensions>
                        <provider>whs</provider>
                    </extensions>
                </trkpt>
                """.trimIndent()
            )

            writer.flush()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun closeGpxFile() {

        try {

            gpxWriter?.write(
                "\n        </trkseg>\n" +
                    "    </trk>\n" +
                    "</gpx>"
            )

            gpxWriter?.flush()
            gpxWriter?.close()

        } catch (_: Exception) {
        }

        gpxWriter = null
        currentFile = null
    }

    private fun createNotificationChannel() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "FoilTracker GPS",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun createNotification(): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("FoilTracker")
            .setContentText(
                "Enregistrement GPS en cours..."
            )
            .setSmallIcon(
                android.R.drawable.ic_menu_mylocation
            )
            .setOngoing(true)
            .build()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    override fun onDestroy() {

        toneGenerator.release()

        serviceScope.cancel()

        super.onDestroy()
    }
}
