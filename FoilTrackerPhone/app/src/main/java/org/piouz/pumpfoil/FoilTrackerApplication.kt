package org.piouz.pumpfoil

import android.app.Application
import org.piouz.pumpfoil.data.FoilTrackerDatabase
import org.piouz.pumpfoil.data.TrackRepository

class FoilTrackerApplication :
    Application() {

    val database by lazy {
        FoilTrackerDatabase.getInstance(this)
    }

    val repository by lazy {
        TrackRepository(
            context = this,
            dao = database.trackFileDao()
        )
    }
}
