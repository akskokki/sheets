package dev.axu.sheets.reader

import dev.axu.sheets.annotations.AnnotationStore

/**
 * Each document's ink for as long as the app runs, so a sheet's undo history survives leaving it
 * and coming back. Only touched on the main thread.
 */
class InkSessions(private val store: AnnotationStore) {
    private val sessions = HashMap<String, DocumentInk>()

    /**
     * The ink of the document with [key]: this session's, unless its notes were changed elsewhere
     * meanwhile. Then they're loaded afresh and the history dropped, so they're never written over.
     */
    suspend fun open(key: String): DocumentInk {
        sessions[key]?.let { if (!store.changedElsewhere(key)) return it }
        return DocumentInk(store.load(key)).also { sessions[key] = it }
    }
}
