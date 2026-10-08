package dev.axu.sheets.reader

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.axu.sheets.AppContainer
import dev.axu.sheets.pdf.PageBitmapCache
import dev.axu.sheets.pdf.PdfDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface ReaderState {
    data object Loading : ReaderState
    data object Failed : ReaderState
    class Ready(
        val document: PdfDocument,
        val pages: PageBitmapCache,
        val ink: DocumentInk,
        val annotationKey: String,
    ) : ReaderState
}

class ReaderViewModel(
    private val uri: Uri,
    private val container: AppContainer,
) : ViewModel() {
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
            return ReaderState.Ready(document, PageBitmapCache(document), ink, key)
        } catch (e: Exception) {
            document.close()
            throw e
        }
    }

    fun onStrokeFinished(page: Int, stroke: Stroke) {
        val ready = state as? ReaderState.Ready ?: return
        ready.ink.add(page, stroke)
        save(ready)
    }

    fun undo() {
        val ready = state as? ReaderState.Ready ?: return
        if (ready.ink.undo() != null) save(ready)
    }

    private fun save(ready: ReaderState.Ready) =
        container.annotations.save(ready.annotationKey, ready.ink.toPageStrokes())

    override fun onCleared() {
        val ready = state as? ReaderState.Ready ?: return
        // Rendering may still be in flight; close() waits for it.
        container.appScope.launch { ready.document.close() }
    }
}
