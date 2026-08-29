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
import com.example.foiltracker.sync.WearFileSender

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


        /*
         * ---------------------------------------------------------
         * HEART RATE
         * ---------------------------------------------------------
         *
         * Current heart rate in BPM.
         *
         * null means that Health Services has not supplied a
         * heart-rate value yet.
         */
        private val _heartRateBpm =
            MutableStateFlow<Double?>(null)

        val heartRateBpm:
            StateFlow<Double?> =
            _heartRateBpm.asStateFlow()
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


    private var gpxWriter: FileWriter? =
        null

    private var currentFile: File? =
        null


    private var accumulatedDistance =
        0f


    private var calculator =
        RunDurationCalculator()


    /*
     * -------------------------------------------------------------
     * HEALTH SERVICES EXERCISE CALLBACK
     * -------------------------------------------------------------
     */
    private val exerciseUpdateCallback =
        object : ExerciseUpdateCallback {


            override fun onExerciseUpdateReceived(
                update: ExerciseUpdate
            ) {

                /*
                 * -------------------------------------------------
                 * HEART RATE
                 * -------------------------------------------------
                 *
                 * Health Services supplies heart-rate samples
                 * through HEART_RATE_BPM.
                 */
                val heartRateDataPoints =
                    update.latestMetrics.getData(
                        DataType.HEART_RATE_BPM
                    )


                heartRateDataPoints
                    .lastOrNull()
                    ?.let { dataPoint ->

                        _heartRateBpm.value =
                            dataPoint.value
                    }


                /*
                 * -------------------------------------------------
                 * LOCATION
                 * -------------------------------------------------
                 */
                val locationDataPoints =
                    update.latestMetrics.getData(
                        DataType.LOCATION
                    )


                if (locationDataPoints.isEmpty()) {
                    return
                }


                /*
                 * Convert Health Services boot-relative
                 * timestamps to Unix timestamps.
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
                            latitude =
                                lat,

                            longitude =
                                lon,

                            timeMs =
                                locationTimeMs
                        )


                    val result =
                        calculator.processPoint(
                            point
                        )


                    _speedKmh.value =
                        result.speedKmh


                    /*
                     * -------------------------------------------------
                     * DISTANCE
                     * -------------------------------------------------
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
                     * -------------------------------------------------
                     * RUN DURATION
                     * -------------------------------------------------
                     *
                     * RunDurationCalculator is the sole source
                     * of truth for this value.
                     */
                    _runDurationSeconds.value =
                        result.runDurationSeconds


                    /*
                     * -------------------------------------------------
                     * EVENTS
                     * -------------------------------------------------
                     */
                    handleEvents(
                        result.events
                    )
                }
            }


            override fun onAvailabilityChanged(
                dataType: DataType<*, *>,
                availability: Availability
            ) {

                android.util.Log.d(
                    "FoilTracker",
                    "Availability: " +
                        "$dataType -> $availability"
                )
            }


            override fun onLapSummaryReceived(
                lapSummary: ExerciseLapSummary
            ) {
            }


            override fun onRegistered() {

                android.util.Log.d(
                    "FoilTracker",
                    "Exercise callback registered"
                )
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


    /*
     * -------------------------------------------------------------
     * RUN DURATION EVENTS
     * -------------------------------------------------------------
     */
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


    /*
     * -------------------------------------------------------------
     * FOREGROUND SERVICE
     * -------------------------------------------------------------
     */
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


    /*
     * -------------------------------------------------------------
     * START TRACKING
     * -------------------------------------------------------------
     */
    private fun startTracking() {

        /*
         * Reset session.
         */
        _recording.value =
            true


        accumulatedDistance =
            0f


        _totalDistanceMeters.value =
            0f


        _speedKmh.value =
            0.0


        _runDurationSeconds.value =
            0L


        /*
         * Reset heart rate.
         */
        _heartRateBpm.value =
            null


        /*
         * Create a NEW calculator for every recording.
         */
        resetCalculator()


        createGpxFile()


        serviceScope.launch {

            try {

                /*
                 * IMPORTANT:
                 *
                 * The Activity must have already obtained
                 * READ_HEART_RATE before this code is reached.
                 *
                 * DataType.HEART_RATE_BPM is protected by
                 * android.permission.health.READ_HEART_RATE on
                 * Android 16+.
                 */
                val config =
                    ExerciseConfig(

                        exerciseType =
                            ExerciseType.BIKING,


                        dataTypes =
                            setOf(
                                DataType.LOCATION,
                                DataType.HEART_RATE_BPM
                            ),


                        isAutoPauseAndResumeEnabled =
                            false,


                        isGpsEnabled =
                            true
                    )


                android.util.Log.d(
                    "FoilTracker",
                    "Registering WHS callback"
                )


                exerciseClient
                    .setUpdateCallback(
                        exerciseUpdateCallback
                    )


                android.util.Log.d(
                    "FoilTracker",
                    "Starting WHS exercise"
                )


                exerciseClient
                    .startExerciseAsync(
                        config
                    )
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

                /*
                 * Exercise could not be started.
                 *
                 * In particular, this will happen if
                 * READ_HEART_RATE has not been granted.
                 */
                _recording.value =
                    false

                stopSelf()
            }
        }
    }


    private fun resetCalculator() {

        calculator =
            RunDurationCalculator()
    }


    /*
     * -------------------------------------------------------------
     * STOP TRACKING
     * -------------------------------------------------------------
     */
    private fun stopTracking() {

        _recording.value =
            false


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


            handleEvents(
                events
            )


            _runDurationSeconds.value =
                calculator.currentRunDurationSeconds


            val completedFile =
                closeGpxFile()

            if (completedFile != null) {

                WearFileSender.logConnectedNodes(context = this@LocationService)
                WearFileSender.sendFile(
                    context = this@LocationService,
                    file = completedFile
                )
            }


            stopForeground(
                STOP_FOREGROUND_REMOVE
            )


            stopSelf()
        }
    }


    /*
     * -------------------------------------------------------------
     * GPX
     * -------------------------------------------------------------
     */
    private fun createGpxFile() {

        try {

            val timestamp =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(
                    Date()
                )


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

            android.util.Log.e(
                "FoilTracker",
                "Unable to create GPX",
                e
            )
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
                TimeZone.getTimeZone(
                    "UTC"
                )


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

            android.util.Log.e(
                "FoilTracker",
                "Unable to write GPX point",
                e
            )
        }
    }


    private fun closeGpxFile() : File? {

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

        val completedFile =
            currentFile

        currentFile = null

        return completedFile
    }


    /*
     * -------------------------------------------------------------
     * NOTIFICATION
     * -------------------------------------------------------------
     */
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
            .setContentTitle(
                "FoilTracker"
            )
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
