package com.frxe.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.frxe.music.island.FrxeAppVisibility
import com.frxe.music.island.IslandHubPreferences
import com.frxe.music.save.FrxeDownloadService
import com.frxe.music.ui.FrxeApp
import com.frxe.music.ui.FrxeViewModel
import com.frxe.music.ui.theme.FrxeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrxeTheme {
                val viewModel: FrxeViewModel = viewModel()
                FrxeApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FrxeAppVisibility.isForeground = true
        IslandHubPreferences.refreshFloatingOverlay(this)
        FrxeDownloadService.kick(this)
    }

    override fun onPause() {
        FrxeAppVisibility.isForeground = false
        IslandHubPreferences.refreshFloatingOverlay(this)
        super.onPause()
    }
}
