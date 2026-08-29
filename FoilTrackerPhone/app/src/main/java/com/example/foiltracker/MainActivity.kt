package com.example.foiltracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.foiltracker.ui.FoilTrackerApp

// TODO remove block
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.foiltracker.sync.WearFileSync

class MainActivity :
    ComponentActivity() {

        // TODO remove
        private fun diagnoseWearDataLayer() {

            lifecycleScope.launch {
                WearFileSync.sync(this@MainActivity)
            }
        }
    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface {
                    FoilTrackerApp()
                }
            }
        }
        // TODO remove
        diagnoseWearDataLayer()
    }
}
