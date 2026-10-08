package dev.axu.sheets

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import dev.axu.sheets.library.LibraryScreen
import dev.axu.sheets.reader.ReaderScreen
import dev.axu.sheets.ui.SheetsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SheetsTheme {
                Surface { App() }
            }
        }
    }
}

/** The sheet currently open in the reader, or null for the library. */
private data class OpenSheet(val uri: Uri, val title: String)

private val OpenSheetSaver = listSaver<OpenSheet?, String>(
    save = { if (it == null) emptyList() else listOf(it.uri.toString(), it.title) },
    restore = { if (it.isEmpty()) null else OpenSheet(it[0].toUri(), it[1]) },
)

@Composable
private fun App() {
    var openSheet by rememberSaveable(stateSaver = OpenSheetSaver) { mutableStateOf(null) }

    when (val sheet = openSheet) {
        null -> LibraryScreen(onOpenSheet = { openSheet = OpenSheet(it.uri, it.title) })
        else -> ReaderScreen(sheet.uri, sheet.title, onBack = { openSheet = null })
    }
}
