package dev.axu.sheets.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class Sheet(val uri: Uri, val title: String)

/** The user's sheet music folder, accessed through the Storage Access Framework. */
class LibraryRepository(context: Context) {
    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    private val _folder = MutableStateFlow(restoreFolder())
    val folder: StateFlow<Uri?> = _folder.asStateFlow()

    private val _notesFolder = MutableStateFlow(_folder.value?.takeIf(::isWritable))

    /** [folder] if the app may also keep its notes there, otherwise null. */
    val notesFolder: StateFlow<Uri?> = _notesFolder.asStateFlow()

    fun setFolder(uri: Uri) {
        val previous = _folder.value
        val writable = try {
            resolver.takePersistableUriPermission(uri, READ_WRITE)
            true
        } catch (_: SecurityException) {
            // Some providers only grant read access; notes then stay in app storage only.
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            false
        }
        if (previous != null && previous != uri) release(previous)
        prefs.edit { putString(KEY_FOLDER, uri.toString()) }
        _folder.value = uri
        _notesFolder.value = uri.takeIf { writable }
    }

    private fun isWritable(uri: Uri) =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    private fun release(uri: Uri) {
        val permission = resolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        resolver.releasePersistableUriPermission(uri, flags)
    }

    /** All PDFs in [folder] and its subfolders, sorted by title. */
    suspend fun listSheets(folder: Uri): List<Sheet> = withContext(Dispatchers.IO) {
        val sheets = mutableListOf<Sheet>()
        collectPdfs(folder, DocumentsContract.getTreeDocumentId(folder), sheets)
        sheets.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    private fun collectPdfs(tree: Uri, parentId: String, into: MutableList<Sheet>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
        )
        resolver.query(children, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)
                when {
                    mime == Document.MIME_TYPE_DIR -> if (!name.startsWith(".")) collectPdfs(tree, id, into)
                    mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) ->
                        into += Sheet(DocumentsContract.buildDocumentUriUsingTree(tree, id), titleOf(name))
                }
            }
        }
    }

    private fun restoreFolder(): Uri? {
        val uri = prefs.getString(KEY_FOLDER, null)?.toUri() ?: return null
        // The grant can disappear, e.g. if the folder was deleted.
        val granted = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        return uri.takeIf { granted }
    }

    private companion object {
        const val KEY_FOLDER = "folder"
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/** "Stella's+Departure.pdf" -> "Stella's Departure" */
internal fun titleOf(fileName: String): String =
    fileName.removeSuffix(".pdf").removeSuffix(".PDF").replace('+', ' ').replace('_', ' ').trim()
