package dev.axu.sheets.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.axu.sheets.R
import dev.axu.sheets.appContainer

@Composable
fun LibraryScreen(onOpenSheet: (Sheet) -> Unit, onOpenSettings: () -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { LibraryViewModel(container.library) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    // Pick up files added to the folder while the app was in the background.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            if (viewModel.state != LibraryState.NoFolder) {
                TextButton(onClick = { pickFolder.launch(null) }) {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Change folder")
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
            }
        }

        if (viewModel.notesAtRisk) {
            // Picking the same folder again grants write access to it.
            NotesAtRiskBanner(onAllow = { pickFolder.launch(viewModel.folder) })
        }

        when (val state = viewModel.state) {
            LibraryState.Loading -> Centered { CircularProgressIndicator() }
            LibraryState.NoFolder -> Centered {
                Message("Choose the folder where your sheet music PDFs live.")
                Button(onClick = { pickFolder.launch(null) }) { Text("Choose folder") }
            }
            LibraryState.Unavailable -> Centered {
                Message("The sheet music folder can't be opened anymore.")
                Button(onClick = { pickFolder.launch(null) }) { Text("Choose folder") }
            }
            is LibraryState.Loaded ->
                if (state.sheets.isEmpty()) {
                    Centered { Message("No PDFs in this folder yet.") }
                } else {
                    SheetList(state.sheets, onOpenSheet)
                }
        }
    }
}

@Composable
private fun NotesAtRiskBanner(onAllow: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your notes are only stored inside the app, so uninstalling it would delete them. " +
                    "Allow keeping them in your sheet music folder, then pick the same folder.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            Button(onClick = onAllow) { Text("Allow") }
        }
    }
}

@Composable
private fun SheetList(sheets: List<Sheet>, onOpenSheet: (Sheet) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        items(sheets, key = { it.uri }) { sheet ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSheet(sheet) }
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(20.dp))
                Text(sheet.title, style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            content()
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
}
