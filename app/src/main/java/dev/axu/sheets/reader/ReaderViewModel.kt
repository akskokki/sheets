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
    class Ready(val document: PdfDocument, val pages: PageBitmapCache) : ReaderState
}

class ReaderViewModel(
    private val uri: Uri,
    private val container: AppContainer,
) : ViewModel() {
    var state: ReaderState by mutableStateOf(ReaderState.Loading)
        private set

    val ink = DocumentInk()

    init {
        viewModelScope.launch {
            state = try {
                val document = PdfDocument.open(container.contentResolver, uri)
                ReaderState.Ready(document, PageBitmapCache(document))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Reader", "Can't open $uri", e)
                ReaderState.Failed
            }
        }
    }

    fun onStrokeFinished(page: Int, stroke: Stroke) = ink.add(page, stroke)

    fun undo() {
        ink.undo()
    }

    override fun onCleared() {
        val ready = state as? ReaderState.Ready ?: return
        // Rendering may still be in flight; close() waits for it.
        container.appScope.launch { ready.document.close() }
    }
}
