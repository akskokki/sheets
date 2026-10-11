package dev.axu.sheets.reader

import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.axu.sheets.AppContainer
import dev.axu.sheets.ink.ScratchOut
import dev.axu.sheets.ink.StrokeEraser
import dev.axu.sheets.pdf.PageRenderer
import dev.axu.sheets.pdf.PdfDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ReaderState {
    data object Loading : ReaderState
    data object Failed : ReaderState
    class Ready(
        val document: PdfDocument,
        val pages: PageRenderer,
        val ink: DocumentInk,
        /** Identifies the document's contents; see [dev.axu.sheets.annotations.AnnotationStore]. */
        val documentKey: String,
        /** The part of each page shown, in points. */
        val crops: List<RectF>,
        val initialPage: Int,
    ) : ReaderState
}

class ReaderViewModel(private val uri: Uri, private val container: AppContainer) : ViewModel() {
    var state: ReaderState by mutableStateOf(ReaderState.Loading)
        private set

    /** The size pages are fitted into, once laid out. */
    private val viewport = MutableStateFlow<IntSize?>(null)

    fun onViewportChanged(size: IntSize) {
        viewport.value = size
    }

    init {
        viewModelScope.launch {
            state = try {
                open()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Reader", "Can't open $uri", e)
                ReaderState.Failed
            }
        }
    }

    /**
     * Opens the document with the page to show already rendered, so it appears complete. Steps
     * that don't depend on each other overlap.
     */
    private suspend fun open(): ReaderState.Ready = coroutineScope {
        // Hashing reads the whole file, so it runs while the document opens.
        val keying = async { container.annotations.keyOf(uri) }
        val document = PdfDocument.open(container.contentResolver, uri)
        try {
            val key = keying.await()
            val loading = async { container.annotations.load(key) }
            val music = if (container.settings.cropMargins) container.crops.music(document, key) else null
            fun crop(index: Int, notes: List<Stroke>): RectF =
                music?.let { cropPage(document.pageSizes[index], it[index], notes) }
                    ?: document.pageSizes[index].toRect()

            val page = container.positions.pageOf(key).coerceIn(0, document.pageCount - 1)
            val pages = PageRenderer(document)
            val viewport = viewport.filterNotNull().first()
            suspend fun render(crop: RectF) =
                fittedSize(crop, viewport).let { pages.page(page, crop, it.width, it.height) }
            // Render while the notes load. Only notes beside the music change the crop, and then
            // the page is rendered again.
            val bare = crop(page, notes = emptyList())
            val rendering = async { render(bare) }

            val ink = DocumentInk(loading.await())
            val crops = List(document.pageCount) { crop(it, ink.strokesOn(it)) }
            if (crops[page] == bare) {
                rendering.await()
            } else {
                rendering.cancel()
                render(crops[page])
            }
            ReaderState.Ready(document, pages, ink, key, crops, page)
        } catch (e: Exception) {
            document.close()
            throw e
        }
    }

    /** Scratched-out ink that's still fading out on screen. */
    val erasures = mutableStateListOf<Erasure>()

    fun onStrokeFinished(page: Int, stroke: Stroke) {
        val ready = state as? ReaderState.Ready ?: return
        val erased = if (container.settings.scratchOutToErase) {
            ScratchOut.erasedBy(stroke, ready.ink.strokesOn(page))
        } else {
            emptyList()
        }
        if (erased.isEmpty()) {
            ready.ink.add(page, stroke)
        } else {
            ready.ink.erase(page, erased)
            erasures += Erasure(page, erased + stroke)
        }
        save(ready)
    }

    /** Starts erasing whole strokes on [page] wherever the eraser passes; see [StrokeEraser]. */
    fun startErasing(page: Int): PageEraser? {
        val ready = state as? ReaderState.Ready ?: return null
        val gesture = ready.ink.startErasing(page)
        return object : PageEraser {
            private var lastX = Float.NaN
            private var lastY = Float.NaN
            private var erasedAny = false

            override fun moveTo(x: Float, y: Float) {
                val touched = if (lastX.isNaN()) {
                    StrokeEraser.touched(x, y, x, y, ready.ink.strokesOn(page))
                } else {
                    StrokeEraser.touched(lastX, lastY, x, y, ready.ink.strokesOn(page))
                }
                lastX = x
                lastY = y
                if (touched.isEmpty()) return
                gesture.erase(touched)
                erasures += Erasure(page, touched)
                erasedAny = true
            }

            override fun finish() {
                if (erasedAny) save(ready)
            }
        }
    }

    fun onErasureFaded(erasure: Erasure) {
        erasures -= erasure
    }

    /** Undoes the latest edit on [page]; returns false if there was nothing to undo. */
    fun undo(page: Int): Boolean {
        val ready = state as? ReaderState.Ready ?: return false
        return ready.ink.undo(page).also { if (it) save(ready) }
    }

    /** Redoes the latest undone edit on [page]; returns false if there was nothing to redo. */
    fun redo(page: Int): Boolean {
        val ready = state as? ReaderState.Ready ?: return false
        return ready.ink.redo(page).also { if (it) save(ready) }
    }

    fun onPageSettled(page: Int) {
        val ready = state as? ReaderState.Ready ?: return
        container.positions.save(ready.documentKey, page)
    }

    private fun save(ready: ReaderState.Ready) =
        container.annotations.save(ready.documentKey, ready.ink.toAnnotations())

    override fun onCleared() {
        val ready = state as? ReaderState.Ready ?: return
        // Rendering may still be in flight; close() waits for it.
        container.appScope.launch { ready.document.close() }
    }
}
