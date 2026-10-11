package dev.axu.sheets.reader

import android.net.Uri
import androidx.lifecycle.ViewModelStore

/**
 * Keeps the reader of the last sheet opened loaded after leaving it, so going back to it is
 * instant. It's released when another sheet opens, or the same one with different settings that
 * shape how it loads.
 */
class LoadedSheet {
    private var loaded: Pair<Any, ViewModelStore>? = null

    /** Where the reader of [uri], loaded with [cropMargins], is kept; releases any other. */
    fun storeFor(uri: Uri, cropMargins: Boolean): ViewModelStore {
        val key = uri to cropMargins
        loaded?.let { (loadedKey, store) -> if (loadedKey == key) return store else store.clear() }
        return ViewModelStore().also { loaded = key to it }
    }

    /** Releases the sheet kept, e.g. one that couldn't be opened, so the next visit starts over. */
    fun release() {
        loaded?.second?.clear()
        loaded = null
    }
}
