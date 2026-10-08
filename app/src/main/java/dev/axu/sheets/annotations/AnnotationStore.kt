package dev.axu.sheets.annotations

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Stores each document's annotations, never touching the PDF itself.
 *
 * Annotations are keyed by a hash of the PDF's contents, so they follow the music when the file is
 * renamed or moved and don't get mixed up when two files share a name.
 *
 * Every save goes to two places: app storage, which is always there, and the sheet music folder
 * (when the app may write to it), which survives uninstalling. Saves are timestamped; loading uses
 * the newest copy and brings the other up to date.
 */
class AnnotationStore(
    private val resolver: ContentResolver,
    private val local: AnnotationFiles,
    notesFolder: StateFlow<Uri?>,
    private val scope: CoroutineScope,
) {
    /** Serializes all file access so saves land in order and loads never see a half-written file. */
    private val io = Dispatchers.IO.limitedParallelism(1)

    /** Only touched on [io]. */
    private var folder: AnnotationFiles? = null

    /**
     * Documents with a copy written by a newer version of the app. This version can't read it, so
     * it must never write over it either: that would lose the notes in it. Only touched on [io].
     */
    private val readOnly = HashSet<String>()

    private val stores get() = listOfNotNull(local, folder)

    init {
        scope.launch(io) {
            notesFolder.collect { uri ->
                folder = uri?.let { FolderAnnotationFiles(resolver, it) }
                folder?.let(::backUpTo)
            }
        }
    }

    suspend fun keyOf(pdf: Uri): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val stream = resolver.openInputStream(pdf) ?: throw FileNotFoundException(pdf.toString())
        stream.use {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun load(key: String): Annotations = withContext(io) {
        val copies = stores.mapNotNull { readCopy(it, key) }.sortedByDescending { it.savedAtMillis }
        for (copy in copies) {
            val saved = try {
                copy.bytes.inputStream().use(AnnotationCodec::decode)
            } catch (e: NewerFormatException) {
                Log.w(TAG, "Annotations $key are from a newer version; leaving them alone", e)
                readOnly += key
                continue
            } catch (e: Exception) {
                Log.e(TAG, "Can't read annotations $key", e)
                runCatching { copy.store.moveAside(key) }
                continue
            }
            for (store in stores) {
                val stale = copies.none { it.store === store && it.savedAtMillis >= copy.savedAtMillis }
                if (stale) writeSafely(store, key, copy.bytes)
            }
            return@withContext saved
        }
        Annotations.Empty
    }

    /** Saves in the background; the write completes even if the caller goes away. */
    fun save(key: String, annotations: Annotations) {
        scope.launch(io) {
            val bytes = ByteArrayOutputStream()
                .also { AnnotationCodec.encode(annotations, System.currentTimeMillis(), it) }
                .toByteArray()
            for (store in stores) writeSafely(store, key, bytes)
        }
    }

    private class Copy(val store: AnnotationFiles, val bytes: ByteArray, val savedAtMillis: Long)

    /**
     * A store's copy of [key], or null if it has none or can't read it. Unreadable copies are set
     * aside, except ones from a newer version, which are left alone.
     */
    private fun readCopy(store: AnnotationFiles, key: String): Copy? = try {
        store.read(key)?.let { Copy(store, it, it.inputStream().use(AnnotationCodec::savedAtMillis)) }
    } catch (e: NewerFormatException) {
        Log.w(TAG, "Annotations $key are from a newer version; leaving them alone", e)
        readOnly += key
        null
    } catch (e: Exception) {
        Log.e(TAG, "Can't read annotations $key", e)
        runCatching { store.moveAside(key) }
        null
    }

    /** Copies local annotations that are missing or outdated in [folder], e.g. once it becomes writable. */
    private fun backUpTo(folder: AnnotationFiles) {
        try {
            for (key in local.keys()) {
                val newer = readCopy(local, key) ?: continue
                val existing = readCopy(folder, key)
                if (existing == null || existing.savedAtMillis < newer.savedAtMillis) {
                    writeSafely(folder, key, newer.bytes)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Can't back up annotations", e)
        }
    }

    private fun writeSafely(store: AnnotationFiles, key: String, bytes: ByteArray) {
        if (key in readOnly) {
            Log.w(TAG, "Not writing annotations $key over a newer version's")
            return
        }
        try {
            store.write(key, bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Can't save annotations $key to ${store.javaClass.simpleName}", e)
        }
    }

    private companion object {
        const val TAG = "AnnotationStore"
    }
}
