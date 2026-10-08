package dev.axu.sheets.annotations

import android.content.ContentResolver
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Stores each document's annotations in app storage, never touching the PDF itself.
 *
 * Annotations are keyed by a hash of the PDF's contents, so they follow the music when the file is
 * renamed or moved and don't get mixed up when two files share a name.
 */
class AnnotationStore(
    private val resolver: ContentResolver,
    private val directory: File,
    private val scope: CoroutineScope,
) {
    /** Serializes disk access so saves land in order and loads never see a half-written file. */
    private val io = Dispatchers.IO.limitedParallelism(1)

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

    suspend fun load(key: String): PageStrokes = withContext(io) {
        val file = fileFor(key)
        try {
            file.openRead().use(AnnotationCodec::decode)
        } catch (_: FileNotFoundException) {
            emptyMap()
        } catch (e: Exception) {
            // Keep the unreadable file aside rather than overwriting it with the next save.
            Log.e(TAG, "Can't read annotations $key", e)
            file.baseFile.renameTo(File(directory, "$key.corrupt-${System.currentTimeMillis()}"))
            emptyMap()
        }
    }

    /** Saves in the background; the write completes even if the caller goes away. */
    fun save(key: String, pages: PageStrokes) {
        scope.launch(io) {
            directory.mkdirs()
            val file = fileFor(key)
            val output = file.startWrite()
            try {
                AnnotationCodec.encode(pages, output)
                file.finishWrite(output)
            } catch (e: Exception) {
                Log.e(TAG, "Can't save annotations $key", e)
                file.failWrite(output)
            }
        }
    }

    private fun fileFor(key: String) = AtomicFile(File(directory, "$key.ink"))

    private companion object {
        const val TAG = "AnnotationStore"
    }
}
