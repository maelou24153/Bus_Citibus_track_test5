package com.cadence.songs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cadence.songs.ui.AppViewModel
import com.cadence.songs.ui.AppNav
import com.cadence.songs.ui.theme.CadenceTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
            val settings by vm.settings.collectAsState()
            CadenceTheme(settings.theme, settings.accentColor, settings.backgroundColor, settings.textColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val size = calculateWindowSizeClass(this@MainActivity)
                    // Interface « tablette » (rail + liste/détail) quand l'écran est large ET haut.
                    val wide = size.widthSizeClass != WindowWidthSizeClass.Compact &&
                        size.heightSizeClass != WindowHeightSizeClass.Compact
                    AppNav(vm, wide)
                }
            }
        }
    }
}
