package dev.axu.sheets.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.axu.sheets.ink.PenSettings
import dev.axu.sheets.ink.PenWidth
import dev.axu.sheets.ink.Pens

/** Pen color swatches followed by width choices, laid out in a row or (for the side rail) a column. */
@Composable
fun PenControls(pen: PenSettings, vertical: Boolean) {
    val content: @Composable () -> Unit = {
        for (color in Pens.colors) {
            Choice(
                selected = pen.color == color,
                description = "${color.name} pen",
                onSelect = { pen.select(color) },
            ) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color.argb)))
            }
        }
        if (vertical) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.width(12.dp))
        for (width in PenWidth.entries) {
            Choice(
                selected = pen.width == width,
                description = "${width.name} line",
                onSelect = { pen.select(width) },
            ) {
                Box(Modifier.size((width.size * DOT_SIZE_PER_POINT).dp).clip(CircleShape).background(Color(pen.color.argb)))
            }
        }
    }
    if (vertical) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { content() }
    }
}

/** Width choices are shown as dots sized in proportion to the line. */
private const val DOT_SIZE_PER_POINT = 7f

/** A 44dp touch target with a ring around it when selected. */
@Composable
private fun Choice(selected: Boolean, description: String, onSelect: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .then(if (selected) Modifier.border(2.dp, Color(0xFF1F2937), CircleShape) else Modifier)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
