plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set(
        "com.example.foiltracker.core.FoilTrackerCliKt"
    )
}
