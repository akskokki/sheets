package dev.axu.sheets

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import dev.axu.sheets.ink.InkHostLayout
import dev.axu.sheets.ink.LocalInkHost
import dev.axu.sheets.library.LibraryScreen
import dev.axu.sheets.reader.ReaderScreen
import dev.axu.sheets.settings.SettingsScreen
import dev.axu.sheets.ui.SheetsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val content = ComposeView(this)
        val settings = appContainer.settings
        val inkHost = InkHostLayout(this, content, ignorePalm = { settings.ignorePalm })
        content.setContent {
            CompositionLocalProvider(LocalInkHost provides inkHost) {
                SheetsTheme {
                    Surface { App() }
                }
            }
        }
        setContentView(inkHost)
    }
}

private sealed interface Screen {
    data object Library : Screen
    data object Settings : Screen
    data class Reader(val uri: Uri, val title: String) : Screen
}

private val ScreenSaver = listSaver<Screen, String>(
    save = {
        when (it) {
            Screen.Library -> listOf("library")
            Screen.Settings -> listOf("settings")
            is Screen.Reader -> listOf("reader", it.uri.toString(), it.title)
        }
    },
    restore = {
        when (it[0]) {
            "settings" -> Screen.Settings
            "reader" -> Screen.Reader(it[1].toUri(), it[2])
            else -> Screen.Library
        }
    },
)

@Composable
private fun App() {
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf(Screen.Library) }

    when (val current = screen) {
        Screen.Library -> LibraryScreen(
            onOpenSheet = { screen = Screen.Reader(it.uri, it.title) },
            onOpenSettings = { screen = Screen.Settings },
        )
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Library })
        is Screen.Reader -> ReaderScreen(current.uri, current.title, onBack = { screen = Screen.Library })
    }
}
