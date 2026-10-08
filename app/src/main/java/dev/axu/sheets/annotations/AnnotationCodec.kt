package dev.axu.sheets.annotations

import androidx.ink.brush.Brush
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import dev.axu.sheets.ink.Pens
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.IdentityHashMap

/**
 * A document's ink: strokes per page index (in drawing order within the page, which is also their
 * z-order), and every stroke in the order it was drawn across the whole document, oldest first.
 */
class Annotations(val pages: Map<Int, List<Stroke>>, val drawingOrder: List<Stroke>) {
    companion object {
        val Empty = Annotations(emptyMap(), emptyList())
    }
}

/** Written by a newer version of the app: not broken, just not readable by this one. */
class NewerFormatException(message: String) : IOException(message)

/**
 * Binary format for a document's annotations:
 *
 * ```
 * magic, version, save time (since version 2)
 * brush count, then per brush: family id, color, size, epsilon
 * page count, then per page: page index, stroke count, then per stroke: brush index, inputs (Ink proto)
 * drawing order (since version 2): stroke count, then per stroke: page index, index within page
 * ```
 *
 * Only stroke inputs are stored; shapes are regenerated on load. Brushes are stored once and
 * shared, as nearly every stroke uses one of a handful.
 */
internal object AnnotationCodec {
    private const val MAGIC = 0x53484E4B // "SHNK"
    private const val VERSION = 2

    fun encode(annotations: Annotations, savedAtMillis: Long, output: OutputStream) {
        val pages = annotations.pages.filterValues { it.isNotEmpty() }
        val brushes = LinkedHashMap<Brush, Int>()
        val positions = IdentityHashMap<Stroke, Pair<Int, Int>>()
        for ((page, strokes) in pages) {
            strokes.forEachIndexed { index, stroke ->
                brushes.getOrPut(stroke.brush) { brushes.size }
                positions[stroke] = page to index
            }
        }

        DataOutputStream(output).apply {
            writeInt(MAGIC)
            writeInt(VERSION)
            writeLong(savedAtMillis)
            writeInt(brushes.size)
            for (brush in brushes.keys) {
                writeUTF(Pens.idOf(brush.family))
                writeInt(brush.colorIntArgb)
                writeFloat(brush.size)
                writeFloat(brush.epsilon)
            }
            writeInt(pages.size)
            for ((page, strokes) in pages) {
                writeInt(page)
                writeInt(strokes.size)
                for (stroke in strokes) {
                    writeInt(brushes.getValue(stroke.brush))
                    writeBlob { stroke.inputs.encode(it) }
                }
            }
            val order = annotations.drawingOrder.mapNotNull { positions[it] }
            writeInt(order.size)
            for ((page, index) in order) {
                writeInt(page)
                writeInt(index)
            }
            flush()
        }
    }

    /** When the annotations were saved (0 if unknown), without decoding the strokes. */
    fun savedAtMillis(input: InputStream): Long = DataInputStream(input).readHeader().second

    fun decode(input: InputStream): Annotations = with(DataInputStream(input)) {
        val (version, _) = readHeader()
        val brushes = List(readInt()) {
            val familyId = readUTF()
            val family = Pens.familyOf(familyId) ?: throw NewerFormatException("Unknown brush family $familyId")
            Brush.createWithColorIntArgb(
                family,
                colorIntArgb = readInt(),
                size = readFloat(),
                epsilon = readFloat(),
            )
        }
        val pages = HashMap<Int, List<Stroke>>()
        repeat(readInt()) {
            val page = readInt()
            pages[page] = List(readInt()) {
                val brush = brushes[readInt()]
                Stroke(brush, StrokeInputBatch.decode(readBlob()))
            }
        }
        val drawingOrder = if (version >= 2) {
            List(readInt()) {
                val page = readInt()
                val index = readInt()
                pages[page]?.getOrNull(index) ?: throw IOException("No stroke $index on page $page")
            }
        } else {
            // Not recorded yet: assume page by page.
            pages.toSortedMap().values.flatten()
        }
        Annotations(pages, drawingOrder)
    }

    /** Reads the header, returning the format version and the save time. */
    private fun DataInputStream.readHeader(): Pair<Int, Long> {
        if (readInt() != MAGIC) throw IOException("Not an annotation file")
        return when (val version = readInt()) {
            1 -> 1 to 0L
            2 -> 2 to readLong()
            else -> throw NewerFormatException("Unsupported annotation version $version")
        }
    }

    // Ink's encoders close the stream they write to and its decoders read to the end, so each
    // encoded value gets its own length-prefixed buffer.
    private inline fun DataOutputStream.writeBlob(encode: (OutputStream) -> Unit) {
        val bytes = ByteArrayOutputStream().also(encode).toByteArray()
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readBlob(): InputStream {
        val bytes = ByteArray(readInt())
        readFully(bytes)
        return ByteArrayInputStream(bytes)
    }
}
