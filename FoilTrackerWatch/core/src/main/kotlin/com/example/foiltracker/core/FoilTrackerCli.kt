package com.example.foiltracker.core

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


fun main(
    args: Array<String>
) {
    if (args.size < 1) {
        System.err.println(
            "Usage: foiltracker-cli <file.gpx>"
        )
        System.exit(2)
    }

    val file =
        File(args[0])

    val content : String? = FoilTrackerReporter().getReport(file, args.size == 1).toString()

    if(content == null) {
        System.exit(1)
    } else {
        println(content)
    }

}
