package dev.axu.sheets.reader

import android.net.Uri
import android.graphics.Matrix
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import dev.axu.sheets.ink.LocalInkHost
import dev.axu.sheets.ink.Pens
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.axu.sheets.R
import dev.axu.sheets.appContainer
import kotlinx.coroutines.launch

private val Backdrop = Color(0xFFE6E6EA)
private val ToolbarKey = Any()

@Composable
fun ReaderScreen(uri: Uri, title: String, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel(key = uri.toString()) { ReaderViewModel(uri, container) }

    BackHandler(onBack = onBack)
    ImmersiveMode()

    Box(Modifier.fillMaxSize().background(Backdrop), contentAlignment = Alignment.Center) {
        when (val state = viewModel.state) {
            ReaderState.Loading -> CircularProgressIndicator()
            ReaderState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't open $title", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onBack) { Text("Back") }
            }
            is ReaderState.Ready -> Reader(
                state = state,
                title = title,
                onStrokeFinished = viewModel::onStrokeFinished,
                onUndo = viewModel::undo,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun Reader(
    state: ReaderState.Ready,
    title: String,
    onStrokeFinished: (page: Int, stroke: Stroke) -> Unit,
    onUndo: () -> Unit,
    onBack: () -> Unit,
) {
    val document = state.document
    val ink = state.ink
    val pagerState = rememberPagerState { document.pageCount }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val strokeRenderer = remember { CanvasStrokeRenderer.create() }

    val currentOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val inkTargets = remember(document) {
        PageInkTargets(document.pageSizes, brush = { Pens.Default }) { page, stroke ->
            currentOnStrokeFinished(page, stroke)
        }
    }
    val inkHost = LocalInkHost.current
    DisposableEffect(inkHost, inkTargets) {
        inkHost.targetResolver = inkTargets
        onDispose { if (inkHost.targetResolver === inkTargets) inkHost.targetResolver = null }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pagerState) {
                    // Sheet-music style tap zones: edges turn pages, the middle toggles the toolbar.
                    detectTapGestures { offset ->
                        val third = size.width / 3f
                        when {
                            offset.x < third -> scope.launch { pagerState.turnPage(-1) }
                            offset.x > 2 * third -> scope.launch { pagerState.turnPage(+1) }
                            else -> chromeVisible = !chromeVisible
                        }
                    }
                },
        ) { index ->
            DisposableEffect(index) { onDispose { inkTargets.onPageRemoved(index) } }
            PdfPage(
                index = index,
                pageSize = document.pageSizes[index],
                pages = state.pages,
                modifier = Modifier.onGloballyPositioned { inkTargets.onPagePositioned(index, it) },
            ) { pageToPx ->
                val strokes = ink.strokesOn(index)
                if (strokes.isNotEmpty()) {
                    val pageToCanvas = Matrix().apply { setScale(pageToPx, pageToPx) }
                    drawIntoCanvas {
                        val canvas = it.nativeCanvas
                        val checkpoint = canvas.save()
                        // The renderer only uses the matrix for level of detail; the canvas has to apply it.
                        canvas.concat(pageToCanvas)
                        for (stroke in strokes) strokeRenderer.draw(canvas, stroke, pageToCanvas)
                        canvas.restoreToCount(checkpoint)
                    }
                }
            }
        }

        AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut()) {
            DisposableEffect(Unit) { onDispose { inkTargets.onExclusionRemoved(ToolbarKey) } }
            ReaderToolbar(
                title = title,
                page = pagerState.currentPage + 1,
                pageCount = document.pageCount,
                canUndo = ink.canUndo,
                onUndo = onUndo,
                onBack = onBack,
                modifier = Modifier.onGloballyPositioned { inkTargets.onExclusionPositioned(ToolbarKey, it) },
            )
        }
    }
}

@Composable
private fun ReaderToolbar(
    title: String,
    page: Int,
    pageCount: Int,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color.White.copy(alpha = 0.94f), shadowElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = "Undo")
            }
            Text(
                "$page / $pageCount",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp, end = 16.dp),
            )
        }
    }
}

private suspend fun PagerState.turnPage(delta: Int) {
    val target = (currentPage + delta).coerceIn(0, pageCount - 1)
    if (target != currentPage) animateScrollToPage(target)
}

/** Hides the system bars while reading; they come back with a swipe from the edge. */
@Composable
private fun ImmersiveMode() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val controller = window.insetsController
        controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsets.Type.systemBars())
        onDispose { controller?.show(WindowInsets.Type.systemBars()) }
    }
}
