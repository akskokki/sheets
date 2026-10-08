package dev.axu.sheets.library

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface LibraryState {
    data object NoFolder : LibraryState
    data object Loading : LibraryState
    data class Loaded(val sheets: List<Sheet>) : LibraryState
    data object Unavailable : LibraryState
}

class LibraryViewModel(private val library: LibraryRepository) : ViewModel() {
    var state: LibraryState by mutableStateOf(LibraryState.Loading)
        private set

    /** Whether notes are only kept in app storage, so uninstalling would lose them. */
    var notesAtRisk by mutableStateOf(false)
        private set

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            library.folder.collect { refresh() }
        }
        viewModelScope.launch {
            library.notesFolder.collect { notesAtRisk = library.folder.value != null && it == null }
        }
    }

    val folder: Uri? get() = library.folder.value

    fun onFolderPicked(uri: Uri) = library.setFolder(uri)

    fun refresh() {
        val folder = library.folder.value
        if (folder == null) {
            state = LibraryState.NoFolder
            return
        }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            state = try {
                LibraryState.Loaded(library.listSheets(folder))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Typically SecurityException/FileNotFoundException when the folder went away.
                Log.w("Library", "Can't list $folder", e)
                LibraryState.Unavailable
            }
        }
    }
}
