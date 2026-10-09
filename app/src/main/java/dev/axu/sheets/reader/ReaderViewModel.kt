package dev.axu.sheets.reader

import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.axu.sheets.AppContainer
import dev.axu.sheets.fingering.FingeringCleanup
import dev.axu.sheets.fingering.FingeringCleanup.Companion.A4_WIDTH
import dev.axu.sheets.ink.ScratchOut
import dev.axu.sheets.pdf.PageRenderer
import dev.axu.sheets.pdf.PdfDocument
import kotlinx.coroutines.CancellationException
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

    private suspend fun open(): ReaderState.Ready {
        val document = PdfDocument.open(container.contentResolver, uri)
        try {
            val key = container.annotations.keyOf(uri)
            val ink = DocumentInk(container.annotations.load(key))
            val crops = if (container.settings.cropMargins) {
                container.crops.of(document, key, ink)
            } else {
                document.pageSizes.map { it.toRect() }
            }
            val page = container.positions.pageOf(key).coerceIn(0, document.pageCount - 1)
            return ReaderState.Ready(document, PageRenderer(document), ink, key, crops, page)
        } catch (e: Exception) {
            document.close()
            throw e
        }
    }

    /** Scratched-out or tidied-up ink that's still fading out on screen. */
    val erasures = mutableStateListOf<Erasure>()

    private val fingerings = FingeringCleanup(
        viewModelScope,
        strokesOn = { page -> (state as? ReaderState.Ready)?.ink?.strokesOn(page).orEmpty() },
        pageWidth = { page -> (state as? ReaderState.Ready)?.document?.pageSizes?.get(page)?.width ?: A4_WIDTH },
        replace = { page, handwriting, clean ->
            val ready = state as? ReaderState.Ready
            if (ready != null) {
                ready.ink.replace(page, handwriting, clean)
                erasures += Erasure(page, handwriting)
                save(ready)
            }
        },
    )

    fun onPenDown() = fingerings.onPenDown()

    fun onPenUp() = fingerings.onPenUp()

    fun onStrokeFinished(page: Int, stroke: Stroke) {
        val ready = state as? ReaderState.Ready ?: return
        val erased = if (container.settings.scratchOutToErase) {
            ScratchOut.erasedBy(stroke, ready.ink.strokesOn(page))
        } else {
            emptyList()
        }
        if (erased.isEmpty()) {
            ready.ink.add(page, stroke)
            if (container.settings.cleanUpFingerings) fingerings.onStrokeAdded(page, stroke)
        } else {
            ready.ink.erase(page, erased)
            erasures += Erasure(page, erased + stroke)
        }
        save(ready)
    }

    fun onErasureFaded(erasure: Erasure) {
        erasures -= erasure
    }

    /** Returns the page of the undone edit, or null if there was nothing to undo. */
    fun undo(): Int? {
        val ready = state as? ReaderState.Ready ?: return null
        return ready.ink.undo()?.also { save(ready) }
    }

    /** Returns the page of the redone edit, or null if there was nothing to redo. */
    fun redo(): Int? {
        val ready = state as? ReaderState.Ready ?: return null
        return ready.ink.redo()?.also { save(ready) }
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
