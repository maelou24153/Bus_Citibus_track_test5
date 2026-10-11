package com.cadence.songs.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * theme : 0 = automatique, 1 = clair, 2 = sombre, 3 = scène (noir pur)
 * chordStyle : 0 = lettres (C, Am), 1 = numéros (Nashville), 2 = solfège (Do, Lam)
 * accentColor / backgroundColor / textColor / chordColorCustom : couleurs ARGB, 0 = valeur par défaut du thème
 */
data class Settings(
    val theme: Int = 0,
    val fontSize: Float = 22f,
    val chordStyle: Int = 0,
    val columns: Int = 1,
    val chordColor: Int = 0,
    val showChords: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** 0 = guitare, 1 = ukulélé, 2 = piano */
    val instrument: Int = 0,
    val accentColor: Int = 0,
    val backgroundColor: Int = 0,
    val textColor: Int = 0,
    val chordColorCustom: Int = 0,
    /** Partitions : true = Do fixe, false = Do mobile (la tonique est toujours Do). */
    val solfegeFixed: Boolean = true,
    /** Partitions : afficher les accords (symboles) au-dessus des notes. */
    val scoreShowChords: Boolean = true,
    /** Partitions : instrument de lecture par défaut (voir Synth.INSTRUMENTS). */
    val scoreInstrument: Int = 0
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val THEME = intPreferencesKey("theme")
        val FONT = floatPreferencesKey("font_size")
        val STYLE = intPreferencesKey("chord_style")
        val COLUMNS = intPreferencesKey("columns")
        val CHORD_COLOR = intPreferencesKey("chord_color")
        val SHOW_CHORDS = booleanPreferencesKey("show_chords")
        val KEEP_ON = booleanPreferencesKey("keep_screen_on")
        val INSTRUMENT = intPreferencesKey("instrument")
        val ACCENT = intPreferencesKey("accent_color")
        val BACKGROUND = intPreferencesKey("background_color")
        val TEXT = intPreferencesKey("text_color")
        val CHORD_CUSTOM = intPreferencesKey("chord_color_custom")
        val SOL_FIXED = booleanPreferencesKey("solfege_fixed")
        val SCORE_CHORDS = booleanPreferencesKey("score_show_chords")
        val SCORE_INST = intPreferencesKey("score_instrument")
    }

    private fun read(p: Preferences) = Settings(
        theme = p[Keys.THEME] ?: 0,
        fontSize = p[Keys.FONT] ?: 22f,
        chordStyle = p[Keys.STYLE] ?: 0,
        columns = p[Keys.COLUMNS] ?: 1,
        chordColor = p[Keys.CHORD_COLOR] ?: 0,
        showChords = p[Keys.SHOW_CHORDS] ?: true,
        keepScreenOn = p[Keys.KEEP_ON] ?: true,
        instrument = p[Keys.INSTRUMENT] ?: 0,
        accentColor = p[Keys.ACCENT] ?: 0,
        backgroundColor = p[Keys.BACKGROUND] ?: 0,
        textColor = p[Keys.TEXT] ?: 0,
        chordColorCustom = p[Keys.CHORD_CUSTOM] ?: 0,
        solfegeFixed = p[Keys.SOL_FIXED] ?: true,
        scoreShowChords = p[Keys.SCORE_CHORDS] ?: true,
        scoreInstrument = p[Keys.SCORE_INST] ?: 0
    )

    val flow: Flow<Settings> = context.dataStore.data.map { read(it) }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(read(p))
            p[Keys.THEME] = s.theme
            p[Keys.FONT] = s.fontSize
            p[Keys.STYLE] = s.chordStyle
            p[Keys.COLUMNS] = s.columns
            p[Keys.CHORD_COLOR] = s.chordColor
            p[Keys.SHOW_CHORDS] = s.showChords
            p[Keys.KEEP_ON] = s.keepScreenOn
            p[Keys.INSTRUMENT] = s.instrument
            p[Keys.ACCENT] = s.accentColor
            p[Keys.BACKGROUND] = s.backgroundColor
            p[Keys.TEXT] = s.textColor
            p[Keys.CHORD_CUSTOM] = s.chordColorCustom
            p[Keys.SOL_FIXED] = s.solfegeFixed
            p[Keys.SCORE_CHORDS] = s.scoreShowChords
            p[Keys.SCORE_INST] = s.scoreInstrument
        }
    }
}
