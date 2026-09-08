plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set(
        "org.piouz.pumpfoil.core.FoilTrackerCliKt"
    )
}
