package com.example.foiltracker

import android.app.Application
import com.example.foiltracker.data.FoilTrackerDatabase
import com.example.foiltracker.data.TrackRepository

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
