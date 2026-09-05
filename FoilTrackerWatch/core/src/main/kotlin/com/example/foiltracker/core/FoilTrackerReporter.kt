package com.example.foiltracker.core

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class FoilTrackerReporter {
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

    fun getReportString(file: File, all: Boolean) : String? {

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

        var output = "Single Attempts\n\n"
        output += "time -> duration"
        var previousRunDurationSeconds = 0L
        var pointTime = 0L
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
                output +=
                "point=${index + 1} " +
                "reltime=${formatDuration((point.timeMs - startTimeMs)/1000)} " +
                "time=$time " +
                "lat=${point.latitude} " +
                "lon=${point.longitude} " +
                "distanceMeters=${"%.3f".format(result.distanceMeters)} " +
                "speedKmh=${"%.3f".format(result.speedKmh)} " +
                "acceptedForGpx=${result.acceptedForGpx} " +
                "runDuration=${formatDuration(result.runDurationSeconds)}"
                output += "\n"
            } else {

                if(result.runDurationSeconds < previousRunDurationSeconds || index + 1 == points.size) {
                    output += "\n\n" +
                    "${formatDuration((pointTime - startTimeMs)/1000)} -> " +
                    "${formatDuration(previousRunDurationSeconds)}"
                }

                if(previousRunDurationSeconds != result.runDurationSeconds) {
                    pointTime = point.timeMs
                }
                previousRunDurationSeconds = result.runDurationSeconds
            }
        }
        return output
    }
}
