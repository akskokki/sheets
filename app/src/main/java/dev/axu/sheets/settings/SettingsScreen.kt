package dev.axu.sheets.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.axu.sheets.R
import dev.axu.sheets.annotations.FolderAnnotationFiles
import dev.axu.sheets.appContainer

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val settings = container.settings
    val notesFolder by container.library.notesFolder.collectAsState()
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
            Spacer(Modifier.width(8.dp))
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .widthIn(max = 720.dp)
                .padding(horizontal = 32.dp),
        ) {
            Section("Pen")
            Toggle(
                "Scratch out to erase",
                "Scribble back and forth over your notes to erase them.",
                settings.scratchOutToErase,
            ) { settings.scratchOutToErase = it }
            Toggle(
                "Ignore palm while writing",
                "Ignore touches that start while the pen is touching or hovering over the screen.",
                settings.ignorePalm,
            ) { settings.ignorePalm = it }

            Section("Gestures")
            Toggle(
                "Two-finger tap to undo",
                "Tap with three fingers to redo.",
                settings.multiFingerTapUndo,
            ) { settings.multiFingerTapUndo = it }
            Toggle(
                "Tap page edges to turn pages",
                "Tap the left or right third of the page. When off, any tap shows or hides the toolbar.",
                settings.tapEdgesToTurnPages,
            ) { settings.tapEdgesToTurnPages = it }

            Section("Reading")
            Toggle(
                "Full screen",
                "Hide the status and navigation bars while reading.",
                settings.fullScreen,
            ) { settings.fullScreen = it }
            Toggle(
                "Keep screen on",
                "Don't let the screen turn off while a sheet is open.",
                settings.keepScreenOn,
            ) { settings.keepScreenOn = it }

            Section("Notes")
            Text(
                if (notesFolder != null) {
                    "Your notes are saved in the app and in your sheet music folder, in a hidden " +
                        "\"${FolderAnnotationFiles.DIRECTORY_NAME}\" folder. If you reinstall the app, " +
                        "choose the same folder to get them back."
                } else {
                    "Your notes are only saved inside the app. Allow access to your sheet music folder " +
                        "from the library to keep a copy there."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(24.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
