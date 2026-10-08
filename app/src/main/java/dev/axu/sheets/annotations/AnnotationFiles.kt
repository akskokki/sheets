package dev.axu.sheets.annotations

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.AtomicFile
import dev.axu.sheets.BuildConfig
import java.io.File
import java.io.FileNotFoundException

/** A place where encoded annotation files are kept, one per document key. Calls block. */
interface AnnotationFiles {
    fun read(key: String): ByteArray?
    fun write(key: String, bytes: ByteArray)
    fun keys(): Set<String>

    /** Renames an unreadable file so it's kept for inspection but no longer used. */
    fun moveAside(key: String)
}

private const val EXTENSION = ".ink"

/** Files in app-private storage: always available, but deleted when the app is uninstalled. */
class LocalAnnotationFiles(private val directory: File) : AnnotationFiles {
    override fun read(key: String): ByteArray? = try {
        file(key).readFully()
    } catch (_: FileNotFoundException) {
        null
    }

    override fun write(key: String, bytes: ByteArray) {
        directory.mkdirs()
        val file = file(key)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }

    override fun keys(): Set<String> =
        directory.list()?.filter { it.endsWith(EXTENSION) }?.map { it.removeSuffix(EXTENSION) }?.toSet().orEmpty()

    override fun moveAside(key: String) {
        file(key).baseFile.renameTo(File(directory, "$key.corrupt-${System.currentTimeMillis()}"))
    }

    private fun file(key: String) = AtomicFile(File(directory, key + EXTENSION))
}

/**
 * Files in a hidden folder inside the user's sheet music folder, accessed through the Storage
 * Access Framework. They outlive the app, and choosing the same folder after reinstalling brings
 * them back.
 */
class FolderAnnotationFiles(private val resolver: ContentResolver, private val tree: Uri) : AnnotationFiles {
    /** Document IDs by display name, so lookups don't query the provider every time. */
    private var directoryId: String? = null
    private var files: MutableMap<String, String>? = null

    override fun read(key: String): ByteArray? {
        val id = fileId(key) ?: return null
        return try {
            resolver.openInputStream(documentUri(id))?.use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            forgetListing()
            null
        }
    }

    override fun write(key: String, bytes: ByteArray) {
        try {
            writeTo(fileId(key) ?: create(key), bytes)
        } catch (_: FileNotFoundException) {
            // Deleted behind our back; start over with a fresh listing.
            forgetListing()
            writeTo(fileId(key) ?: create(key), bytes)
        }
    }

    override fun keys(): Set<String> =
        listing().keys.filter { it.endsWith(EXTENSION) }.map { it.removeSuffix(EXTENSION) }.toSet()

    override fun moveAside(key: String) {
        val id = fileId(key) ?: return
        DocumentsContract.renameDocument(resolver, documentUri(id), "$key.corrupt-${System.currentTimeMillis()}")
        forgetListing()
    }

    private fun writeTo(id: String, bytes: ByteArray) {
        // "wt" truncates in place; the local copy covers the tiny window where this is partial.
        val stream = resolver.openOutputStream(documentUri(id), "wt") ?: throw FileNotFoundException(id)
        stream.use { it.write(bytes) }
    }

    private fun create(key: String): String {
        val name = key + EXTENSION
        val uri = DocumentsContract.createDocument(resolver, documentUri(directory()), "application/octet-stream", name)
            ?: throw FileNotFoundException("Can't create $name")
        val id = DocumentsContract.getDocumentId(uri)
        listing()[name] = id
        return id
    }

    private fun fileId(key: String): String? = listing()[key + EXTENSION]

    private fun listing(): MutableMap<String, String> =
        files ?: children(directory()).toMutableMap().also { files = it }

    private fun forgetListing() {
        directoryId = null
        files = null
    }

    /** The hidden annotations folder's document ID, created if needed. */
    private fun directory(): String {
        directoryId?.let { return it }
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val id = children(rootId)[DIRECTORY_NAME]
            ?: DocumentsContract.createDocument(resolver, documentUri(rootId), Document.MIME_TYPE_DIR, DIRECTORY_NAME)
                ?.let(DocumentsContract::getDocumentId)
            ?: throw FileNotFoundException("Can't create $DIRECTORY_NAME")
        directoryId = id
        return id
    }

    private fun children(parentId: String): Map<String, String> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        val children = HashMap<String, String>()
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) children[cursor.getString(1)] = cursor.getString(0)
        }
        return children
    }

    private fun documentUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    companion object {
        const val DIRECTORY_NAME = BuildConfig.NOTES_DIRECTORY
    }
}
