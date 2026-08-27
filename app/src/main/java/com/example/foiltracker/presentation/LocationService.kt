package com.example.foiltracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
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
        private val _recording = MutableStateFlow(false)
        val recording: StateFlow<Boolean> = _recording.asStateFlow()

        const val ACTION_START =
            "com.example.foiltracker.START"

        const val ACTION_STOP =
            "com.example.foiltracker.STOP"

        private const val CHANNEL_ID = "foiltracker_gps"
        private const val NOTIFICATION_ID = 1

        private const val SPEED_THRESHOLD_KMH = 7.0

        private const val MIN_SPEED_KMH = 10.0
        private const val MAX_SPEED_KMH = 30.0
        private const val MAX_TIME_STEP_SECONDS = 6.0
        private const val MAX_ACCEL_MPS2 = 5.0
        private const val MIN_TIME_SECONDS = 20.0

        private val _totalDistanceMeters =
            MutableStateFlow(0f)

        val totalDistanceMeters: StateFlow<Float> =
            _totalDistanceMeters.asStateFlow()

        private val _speedKmh =
            MutableStateFlow(0.0)

        val speedKmh: StateFlow<Double> =
            _speedKmh.asStateFlow()

        private val _runDurationSeconds =
            MutableStateFlow(0L)

        val runDurationSeconds: StateFlow<Long> =
            _runDurationSeconds.asStateFlow()
    }

    private val serviceScope =
        CoroutineScope(Dispatchers.IO + Job())

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

    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastLocationTimeMs: Long = 0L

    private var accumulatedDistance = 0f

    private val minMovementMeters = 2.0f

    /*
     * Samples used to reproduce the Ruby filtering algorithm.
     *
     * Each sample corresponds to one calculated speed:
     *
     *   timeMs
     *   speedKmh
     */
    private data class SpeedSample(
        val timeMs: Long,
        val speedKmh: Double
    )

    private val speedSamples =
        ArrayDeque<SpeedSample>()

    /*
     * A candidate period starts when we get a valid filtered
     * speed sample.
     *
     * It only becomes a real >7 km/h period once it survives:
     *
     *   - speed filter
     *   - acceleration filter
     *   - time-gap grouping
     *   - minimum duration
     */
    private var candidateStartMs: Long? = null
    private var candidateLastMs: Long? = null

    private var above7SinceMs: Long? = null

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
                            android.os.SystemClock.elapsedRealtime()

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

                    /*
                     * First GPS point.
                     */
                    if (lastLat == null ||
                        lastLon == null ||
                        lastLocationTimeMs == 0L
                    ) {
                        lastLat = lat
                        lastLon = lon
                        lastLocationTimeMs =
                            locationTimeMs

                        _speedKmh.value = 0.0

                        writePoint(
                            lat,
                            lon,
                            locationTimeMs
                        )

                        return@forEach
                    }

                    /*
                     * Calculate distance between GPS points.
                     */
                    val results =
                        FloatArray(1)

                    Location.distanceBetween(
                        lastLat!!,
                        lastLon!!,
                        lat,
                        lon,
                        results
                    )

                    val distanceMeters =
                        results[0]

                    /*
                     * Calculate speed.
                     *
                     * GPS distance is metres.
                     * Time is seconds.
                     * Result is converted to km/h.
                     */
                    val elapsedSeconds =
                        (
                                locationTimeMs -
                                        lastLocationTimeMs
                                ) / 1000.0

                    val calculatedSpeedKmh =
                        if (elapsedSeconds > 0.0) {
                            distanceMeters /
                                    elapsedSeconds *
                                    3.6
                        } else {
                            0.0
                        }

                    _speedKmh.value =
                        calculatedSpeedKmh

                    /*
                     * -----------------------------------------------
                     * ABOVE 7 KM/H / FILTERED PERIOD
                     * -----------------------------------------------
                     *
                     * This reproduces the Ruby logic:
                     *
                     *   speed > 10 && speed < 30
                     *
                     *   abs(
                     *       (nextSpeed - speed) /
                     *       (nextTime - time)
                     *   ) < 5
                     *
                     *   consecutive samples <= 6 seconds apart
                     *
                     *   total period > 20 seconds
                     */

                    processSpeedSample(
                        locationTimeMs,
                        calculatedSpeedKmh
                    )

                    /*
                     * -----------------------------------------------
                     * DISTANCE
                     * -----------------------------------------------
                     *
                     * Ignore GPS movements smaller than 2 metres.
                     */
                    if (distanceMeters >= minMovementMeters) {

                        accumulatedDistance +=
                            distanceMeters

                        _totalDistanceMeters.value =
                            accumulatedDistance

                        /*
                         * Write accepted point to GPX.
                         */
                        writePoint(
                            lat,
                            lon,
                            locationTimeMs
                        )

                        /*
                         * Use this point as the reference for the
                         * next distance/speed calculation.
                         */
                        lastLat = lat
                        lastLon = lon
                        lastLocationTimeMs =
                            locationTimeMs
                    }
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

    /*
     * Add a speed sample and update the detected period.
     *
     * The important difference from the old implementation is that
     * crossing 7 km/h does NOT immediately start the timer.
     *
     * The timer only starts once we have a qualifying sequence.
     */
    private fun processSpeedSample(
        timeMs: Long,
        speedKmh: Double
    ) {
        /*
         * The Ruby algorithm first removes speeds outside
         * MIN_SPEED..MAX_SPEED.
         */
        if (speedKmh <= MIN_SPEED_KMH ||
            speedKmh >= MAX_SPEED_KMH
        ) {
            finishCandidate(timeMs)
            return
        }

        val sample =
            SpeedSample(
                timeMs,
                speedKmh
            )

        /*
         * If there is a previous sample, calculate acceleration.
         */
        val previous =
            speedSamples.lastOrNull()

        if (previous != null) {

            val dt =
                (timeMs - previous.timeMs) / 1000.0

            if (dt <= 0.0) {
                return
            }

            /*
             * Equivalent to:
             *
             * ((nxt.speed - step.speed) /
             *  (nxt.time - step.time)).abs < MAX_ACCEL
             */
            val acceleration =
                (speedKmh - previous.speedKmh) / dt

            if (kotlin.math.abs(acceleration) >=
                MAX_ACCEL_MPS2
            ) {
                finishCandidate(timeMs)
                speedSamples.clear()
                speedSamples.addLast(sample)
                return
            }

            /*
             * Equivalent to chunk_while:
             *
             *   b.time - a.time <= MAX_TIME_STEP
             */
            if (dt > MAX_TIME_STEP_SECONDS) {
                finishCandidate(timeMs)
                speedSamples.clear()
            }
        }

        speedSamples.addLast(sample)

        /*
         * We have a valid filtered sequence.
         */
        if (candidateStartMs == null) {
            candidateStartMs = timeMs

            /*
             * Start beep only once the candidate eventually
             * becomes a valid period. For now don't beep.
             */
        }

        candidateLastMs = timeMs

        val durationSeconds =
            (
                    candidateLastMs!! -
                            candidateStartMs!!
                    ) / 1000.0

        /*
         * Ruby:
         *
         *   .filter { |x|
         *     x.last.time - x.first.time > MIN_TIME_S
         *   }
         */
        if (durationSeconds > MIN_TIME_SECONDS) {

            if (above7SinceMs == null) {

                above7SinceMs =
                    candidateStartMs

                /*
                 * START BEEP
                 */
                toneGenerator.startTone(
                    ToneGenerator.TONE_PROP_BEEP,
                    200
                )

                android.util.Log.d(
                    "FoilTracker",
                    "Started qualifying >7 km/h period"
                )
            }

            _runDurationSeconds.value =
                durationSeconds.toLong()
        }
    }

    /*
     * Finish the current candidate sequence.
     */
    private fun finishCandidate(
        timeMs: Long
    ) {
        /*
         * If we already have a qualifying period,
         * publish its final duration.
         */
        above7SinceMs?.let { startMs ->

            val endMs =
                candidateLastMs ?: timeMs

            val duration =
                (
                        endMs -
                                startMs
                        ) / 1000

            /*
             * STOP BEEP
             */
            toneGenerator.startTone(
                ToneGenerator.TONE_PROP_BEEP2,
                200
            )

            android.util.Log.d(
                "FoilTracker",
                "Ended >7 km/h period: ${duration}s"
            )

            _runDurationSeconds.value =
                duration

            above7SinceMs = null
        }

        candidateStartMs = null
        candidateLastMs = null
        speedSamples.clear()
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

        _totalDistanceMeters.value = 0f

        _speedKmh.value = 0.0

        _runDurationSeconds.value = 0L

        above7SinceMs = null
        candidateStartMs = null
        candidateLastMs = null
        speedSamples.clear()

        lastLat = null
        lastLon = null
        lastLocationTimeMs = 0L

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
            finishCandidate(
                System.currentTimeMillis()
            )

            closeGpxFile()

            stopForeground(
                STOP_FOREGROUND_REMOVE
            )

            stopSelf()
        }
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
