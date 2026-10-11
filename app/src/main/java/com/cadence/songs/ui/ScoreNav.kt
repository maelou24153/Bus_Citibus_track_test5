package com.cadence.songs.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Ouvre l'écran « partition solfège » d'un morceau (fourni par AppNav ; sans effet ailleurs). */
val LocalOpenScore = staticCompositionLocalOf<(Long) -> Unit> { {} }
