package com.example.foiltracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackFile::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FoilTrackerDatabase :
    RoomDatabase() {

    abstract fun trackFileDao():
        TrackFileDao

    companion object {

        @Volatile
        private var INSTANCE:
            FoilTrackerDatabase? = null

        fun getInstance(
            context: Context
        ): FoilTrackerDatabase {

            return INSTANCE
                ?: synchronized(this) {

                    INSTANCE
                        ?: Room.databaseBuilder(
                            context.applicationContext,
                            FoilTrackerDatabase::class.java,
                            "foiltracker.db"
                        )
                        .build()
                        .also {
                            INSTANCE = it
                        }
                }
        }
    }
}
