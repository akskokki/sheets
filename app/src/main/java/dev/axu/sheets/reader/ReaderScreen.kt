package dev.axu.sheets.reader

import android.net.Uri
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.axu.sheets.R
import dev.axu.sheets.appContainer
import dev.axu.sheets.ink.LocalInkHost
import dev.axu.sheets.pdf.size
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Backdrop = Color(0xFFE6E6EA)
private val ToolbarKey = Any()
private val EraserReminderKey = Any()
private const val ERASE_FADE_MILLIS = 180
private const val MESSAGE_MILLIS = 900L

@Composable
fun ReaderScreen(uri: Uri, title: String, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    // Scoped to this visit rather than the activity, so leaving releases the document and its pages.
    val owner = remember(uri) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val viewModel = viewModel(owner) { ReaderViewModel(uri, container) }

    val settings = container.settings

    BackHandler(onBack = onBack)
    if (settings.fullScreen) ImmersiveMode()
    KeepScreenOn(settings.keepScreenOn)

    Box(
        Modifier
            .fillMaxSize()
            .background(Backdrop)
            .then(if (settings.fullScreen) Modifier else Modifier.systemBarsPadding()),
        contentAlignment = Alignment.Center,
    ) {
        when (val state = viewModel.state) {
            ReaderState.Loading -> CircularProgressIndicator()

            ReaderState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't open $title", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onBack) { Text("Back") }
            }

            is ReaderState.Ready -> Reader(state, viewModel, title, onBack)
        }
    }
}

@Composable
private fun Reader(state: ReaderState.Ready, viewModel: ReaderViewModel, title: String, onBack: () -> Unit) {
    val document = state.document
    val ink = state.ink
    val pagerState = rememberPagerState(initialPage = state.initialPage) { document.pageCount }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var message by remember { mutableStateOf<Message?>(null) }
    val zoom = remember { PageZoom() }
    val scope = rememberCoroutineScope()
    val strokeRenderer = remember { CanvasStrokeRenderer.create() }

    val container = LocalContext.current.appContainer
    val pen = container.pen
    val settings = container.settings
    // Only the pen finally chosen in the menu counts as used, not every color tried on the way.
    val penMenu = remember { PenMenuState(onOpen = pen::startChoosing, onClose = pen::doneChoosing) }
    var erasing by rememberSaveable { mutableStateOf(false) }
    val inkTargets = remember(document) {
        PageInkTargets(
            state.crops,
            brush = { pen.brush },
            erasing = { erasing },
            // Writing on the page is the natural way to be done with the pen menu.
            onStrokeStarted = penMenu::close,
            onStrokeFinished = viewModel::onStrokeFinished,
            startErasing = viewModel::startErasing,
        )
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            zoom.reset()
            penMenu.close()
            viewModel.onPageSettled(page)
        }
    }

    LaunchedEffect(chromeVisible) { if (!chromeVisible) penMenu.close() }

    val inkHost = LocalInkHost.current
    DisposableEffect(inkHost, inkTargets) {
        inkHost.targetResolver = inkTargets
        onDispose { if (inkHost.targetResolver === inkTargets) inkHost.targetResolver = null }
    }

    for (erasure in viewModel.erasures) {
        key(erasure) {
            LaunchedEffect(Unit) {
                erasure.alpha.animateTo(0f, tween(ERASE_FADE_MILLIS))
                viewModel.onErasureFaded(erasure)
            }
        }
    }

    /** Undo or redo on the page in view, so the change is never out of sight. */
    fun edit(name: String, action: (page: Int) -> Boolean) {
        val done = action(pagerState.currentPage)
        message = Message(if (done) name else "Nothing to ${name.lowercase()}")
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { it },
            userScrollEnabled = !zoom.isZoomed,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoom) { detectZoom(zoom) }
                .pointerInput(pagerState) {
                    detectTaps(
                        // Sheet-music style tap zones: edges turn pages, the middle toggles the toolbar.
                        onTap = { offset ->
                            val third = size.width / 3f
                            when {
                                !settings.tapEdgesToTurnPages -> chromeVisible = !chromeVisible
                                offset.x < third -> scope.launch { pagerState.turnPage(-1) }
                                offset.x > 2 * third -> scope.launch { pagerState.turnPage(+1) }
                                else -> chromeVisible = !chromeVisible
                            }
                        },
                        onMultiFingerTap = { fingers ->
                            if (settings.multiFingerTapUndo) {
                                when (fingers) {
                                    2 -> edit("Undo", viewModel::undo)
                                    3 -> edit("Redo", viewModel::redo)
                                }
                            }
                        },
                    )
                },
        ) { index ->
            DisposableEffect(index) { onDispose { inkTargets.onPageRemoved(index) } }
            PdfPage(
                index = index,
                crop = state.crops[index],
                pages = state.pages,
                zoom = zoom.takeIf { index == pagerState.currentPage },
                modifier = Modifier.onGloballyPositioned { inkTargets.onPagePositioned(index, it) },
            ) { pageToCanvas ->
                drawStrokes(ink.strokesOn(index), pageToCanvas, strokeRenderer)
                for (erasure in viewModel.erasures) {
                    if (erasure.page == index) {
                        drawStrokes(erasure.strokes, pageToCanvas, strokeRenderer, erasure.alpha.value)
                    }
                }
            }
        }

        val toolbarState = ToolbarState(
            title = title,
            page = pagerState.currentPage + 1,
            pageCount = document.pageCount,
            canUndo = ink.canUndo(pagerState.currentPage),
            canRedo = ink.canRedo(pagerState.currentPage),
        )
        val toolbarActions = remember(viewModel) {
            ToolbarActions(
                onBack = onBack,
                onUndo = { edit("Undo", viewModel::undo) },
                onRedo = { edit("Redo", viewModel::redo) },
            )
        }
        // Prefer the margin beside the pages (landscape), so the toolbar never covers the music.
        // Pages are cropped differently, so go by the widest to keep the toolbar put while paging.
        val widestPage = remember(state.crops, constraints) {
            state.crops.maxOf {
                it.width() *
                    it.size.scaleToFit(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            }
        }
        val useRail = (maxWidth - with(LocalDensity.current) { widestPage.toDp() }) / 2 >= ToolbarRailWidth
        val toolbarModifier = Modifier.onGloballyPositioned {
            inkTargets.onExclusionPositioned(ToolbarKey, it)
            penMenu.toolbar = it.boundsInRoot()
        }
        if (penMenu.isOpen) ClosePenMenuOnTouch(penMenu)
        AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut()) {
            DisposableEffect(Unit) { onDispose { inkTargets.onExclusionRemoved(ToolbarKey) } }
            if (useRail) {
                ReaderSideRail(toolbarState, toolbarActions, toolbarModifier) {
                    DrawingTools(pen, penMenu, settings.showRecentPens, erasing, { erasing = it }, vertical = true)
                }
            } else {
                ReaderTopBar(toolbarState, toolbarActions, toolbarModifier) {
                    DrawingTools(pen, penMenu, settings.showRecentPens, erasing, { erasing = it }, vertical = false)
                }
            }
        }
        if (chromeVisible) PenMenu(penMenu, pen, sideways = useRail, inkTargets)
        // With the toolbar hidden, there'd be no sign the pen erases.
        AnimatedVisibility(
            erasing && !chromeVisible,
            Modifier.align(Alignment.TopEnd).padding(16.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            DisposableEffect(Unit) { onDispose { inkTargets.onExclusionRemoved(EraserReminderKey) } }
            EraserReminder(
                onClick = { erasing = false },
                Modifier.onGloballyPositioned { inkTargets.onExclusionPositioned(EraserReminderKey, it) },
            )
        }

        TransientMessage(message, onDismiss = { message = null }, Modifier.align(Alignment.BottomCenter))
    }
}

/** Shows the eraser is on while the toolbar is hidden; tapping it switches back to the pen. */
@Composable
private fun EraserReminder(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = "Erasing. Switch back to the pen" },
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_eraser), contentDescription = null, Modifier.size(22.dp))
        }
    }
}

/** Identity matters: showing the same text again restarts the timeout. */
private class Message(val text: String)

/** A brief confirmation, e.g. for edits made by gesture while the toolbar is hidden. */
@Composable
private fun TransientMessage(message: Message?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Keep showing the last text while fading out.
    var shown by remember { mutableStateOf("") }
    if (message != null) shown = message.text
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_MILLIS)
            onDismiss()
        }
    }
    AnimatedVisibility(message != null, modifier.padding(bottom = 48.dp), enter = fadeIn(), exit = fadeOut()) {
        Text(
            shown,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .background(Color(0xCC1F2937), RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

private suspend fun PagerState.turnPage(delta: Int) {
    val target = (currentPage + delta).coerceIn(0, pageCount - 1)
    if (target != currentPage) animateScrollToPage(target)
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
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
