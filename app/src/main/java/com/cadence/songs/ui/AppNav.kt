package com.cadence.songs.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cadence.songs.ui.editor.EditorScreen
import com.cadence.songs.ui.home.HomeScreen
import com.cadence.songs.ui.importer.WebImportScreen
import com.cadence.songs.ui.score.ScoreScreen
import androidx.compose.runtime.CompositionLocalProvider
import com.cadence.songs.ui.setlists.SetDetailScreen
import com.cadence.songs.ui.viewer.SongViewerScreen

@Composable
fun AppNav(vm: AppViewModel, wide: Boolean) {
    val nav = rememberNavController()
    CompositionLocalProvider(LocalOpenScore provides { id: Long -> nav.navigate("score/$id") }) {
    NavHost(navController = nav, startDestination = "home") {
        composable(
            route = "score/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            ScoreScreen(
                vm = vm,
                songId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() }
            )
        }
        composable("home") {
            HomeScreen(
                vm = vm,
                wide = wide,
                onOpenSong = { id -> nav.navigate("song/$id") },
                onEditSong = { id -> nav.navigate("edit/$id") },
                onOpenSet = { id -> nav.navigate("set/$id") },
                onImportWeb = { nav.navigate("webimport") }
            )
        }
        composable("webimport") {
            WebImportScreen(
                vm = vm,
                onClose = { nav.popBackStack() },
                onImported = { id ->
                    nav.popBackStack()
                    nav.navigate("song/$id")
                }
            )
        }
        composable(
            route = "song/{id}?set={set}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("set") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val set = entry.arguments?.getLong("set") ?: -1L
            SongViewerScreen(
                vm = vm,
                songId = id,
                setId = set,
                onBack = { nav.popBackStack() },
                onEdit = { songId -> nav.navigate("edit/$songId") }
            )
        }
        composable(
            route = "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            EditorScreen(
                vm = vm,
                songId = entry.arguments?.getLong("id") ?: 0L,
                onClose = { nav.popBackStack() }
            )
        }
        composable(
            route = "set/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            SetDetailScreen(
                vm = vm,
                setId = id,
                onBack = { nav.popBackStack() },
                onOpenSong = { songId -> nav.navigate("song/$songId?set=$id") }
            )
        }
    }
    }
}
