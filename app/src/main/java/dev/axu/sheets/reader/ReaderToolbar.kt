package dev.axu.sheets.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.axu.sheets.R
import dev.axu.sheets.ink.PenSettings

/** Width of the vertical toolbar, used when the page leaves enough room beside it. */
val ToolbarRailWidth: Dp = 72.dp

class ToolbarState(
    val title: String,
    val page: Int,
    val pageCount: Int,
    val canUndo: Boolean,
    val canRedo: Boolean,
)

class ToolbarActions(
    val onBack: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
)

/** A bar across the top of the screen. */
@Composable
fun ReaderTopBar(state: ToolbarState, actions: ToolbarActions, pen: PenSettings, modifier: Modifier = Modifier) {
    Surface(color = ToolbarColor, shadowElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(actions)
            Text(
                state.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            PenControls(pen, vertical = false)
            Spacer(Modifier.width(16.dp))
            EditButtons(state, actions)
            PageNumber(state, Modifier.padding(start = 8.dp, end = 16.dp))
        }
    }
}

/** A rail down the side of the screen, for the margin beside a page in landscape. */
@Composable
fun ReaderSideRail(state: ToolbarState, actions: ToolbarActions, pen: PenSettings, modifier: Modifier = Modifier) {
    Surface(color = ToolbarColor, shadowElevation = 2.dp, modifier = modifier.width(ToolbarRailWidth).fillMaxHeight()) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BackButton(actions)
            Spacer(Modifier.height(24.dp))
            EditButtons(state, actions)
            Spacer(Modifier.height(24.dp))
            PenControls(pen, vertical = true)
            Spacer(Modifier.weight(1f))
            PageNumber(state, Modifier.padding(bottom = 16.dp))
        }
    }
}

private val ToolbarColor = Color.White.copy(alpha = 0.94f)

@Composable
private fun BackButton(actions: ToolbarActions) {
    IconButton(onClick = actions.onBack) {
        Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
    }
}

@Composable
private fun EditButtons(state: ToolbarState, actions: ToolbarActions) {
    IconButton(onClick = actions.onUndo, enabled = state.canUndo) {
        Icon(painterResource(R.drawable.ic_undo), contentDescription = "Undo")
    }
    IconButton(onClick = actions.onRedo, enabled = state.canRedo) {
        Icon(painterResource(R.drawable.ic_redo), contentDescription = "Redo")
    }
}

@Composable
private fun PageNumber(state: ToolbarState, modifier: Modifier = Modifier) {
    Text("${state.page} / ${state.pageCount}", style = MaterialTheme.typography.labelLarge, modifier = modifier)
}
