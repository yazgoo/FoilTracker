package com.example.foiltracker.core

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class FoilTrackerReporter {
    data class RunReport(
        val all: String,
        val startTime: String,
        val duration: String,
        val distance: String,
        val speed: String,
    ) {
        override fun toString() : String {
            if(all != "") {
                return all
            }
            return "${startTime} -> ${duration} ${distance} ${speed}"
        }
    }
    data class Report(
        val runs : List<RunReport>,
    ) {
        override fun toString() : String {
            return runs.joinToString("\n")
        }
    }
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

    fun getReport(file: File, all: Boolean) : Report? {

        val points =
        try {
            GpxTrackReader.read(file)
        } catch (e: Exception) {
            System.err.println(
                "Failed to read GPX: ${e.message}"
            )
            System.exit(1)
            return null
        }

        if (points.isEmpty()) {
            println("No track points found.")
            return null
        }

        val calculator =
        RunDurationCalculator()

        val formatter =
        DateTimeFormatter.ISO_INSTANT
        .withZone(ZoneOffset.UTC)

        val startTimeMs = points.first().timeMs

        var previousRunDurationSeconds = 0L
        var previousRunDistanceMeters = 0L
        var pointTime = 0L
        val runs = mutableListOf<RunReport>()

        points.forEachIndexed { index, point ->

            val result =
            calculator.processPoint(point)

            val time =
            formatter.format(
                Instant.ofEpochMilli(
                    point.timeMs
                )
            )

            if(all) {
                runs.add(
                    RunReport(
                        all = 
                        "point=${index + 1} " +
                        "reltime=${formatDuration((point.timeMs - startTimeMs)/1000)} " +
                        "time=$time " +
                        "lat=${point.latitude} " +
                        "lon=${point.longitude} " +
                        "distanceMeters=${"%.3f".format(result.distanceMeters)} " +
                        "speedKmh=${"%.3f".format(result.speedKmh)} " +
                        "acceptedForGpx=${result.acceptedForGpx} " +
                        "runDuration=${formatDuration(result.runDurationSeconds)}",
                        startTime = "",
                        duration = "",
                        distance = "",
                        speed = "",
                    )
                )
            } else {

                if(result.runDurationSeconds < previousRunDurationSeconds || index + 1 == points.size) {
                    runs.add(
                        RunReport(
                            all = "",
                            startTime = formatDuration((pointTime - startTimeMs)/1000),
                            duration = formatDuration(previousRunDurationSeconds),
                            distance = "${previousRunDistanceMeters} m",
                            speed = String.format(
                                "%.1f km/h",
                                3.6 * previousRunDistanceMeters / previousRunDurationSeconds
                            ) 
                        )
                    )
                }

                if(previousRunDurationSeconds != result.runDurationSeconds) {
                    pointTime = point.timeMs
                }
                previousRunDurationSeconds = result.runDurationSeconds
                previousRunDistanceMeters = result.runDistanceMeters
            }
        }
        return Report(runs)
    }
}
