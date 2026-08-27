package com.example.foiltracker.core

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun formatDuration(
    seconds: Long
): String {

    val minutes =
        seconds / 60

    val remainingSeconds =
        seconds % 60

    return String.format(
        "%02d:%02d",
        minutes,
        remainingSeconds
    )
}

fun main(
    args: Array<String>
) {
    if (args.size != 1) {
        System.err.println(
            "Usage: foiltracker-cli <file.gpx>"
        )
        System.exit(2)
    }

    val file =
        File(args[0])

    val points =
        try {
            GpxTrackReader.read(file)
        } catch (e: Exception) {
            System.err.println(
                "Failed to read GPX: ${e.message}"
            )
            System.exit(1)
            return
        }

    if (points.isEmpty()) {
        println("No track points found.")
        return
    }

    val calculator =
        RunDurationCalculator()

    val formatter =
        DateTimeFormatter.ISO_INSTANT
            .withZone(ZoneOffset.UTC)

    points.forEachIndexed { index, point ->

        val result =
            calculator.processPoint(point)

        val time =
            formatter.format(
                Instant.ofEpochMilli(
                    point.timeMs
                )
            )

        println(
            "point=${index + 1} " +
                "time=$time " +
                "lat=${point.latitude} " +
                "lon=${point.longitude} " +
                "distanceMeters=${"%.3f".format(result.distanceMeters)} " +
                "speedKmh=${"%.3f".format(result.speedKmh)} " +
                "acceptedForGpx=${result.acceptedForGpx} " +
                "runDuration=${formatDuration(result.runDurationSeconds)}"
        )
    }

    /*
     * This models the end of the recording. Normally there will be
     * nothing interesting here because the GPX has already ended,
     * but it makes the CLI state-machine semantics match the service.
     */
    val finalPoint =
        points.last()

    calculator.finish(
        finalPoint.timeMs
    )
}
