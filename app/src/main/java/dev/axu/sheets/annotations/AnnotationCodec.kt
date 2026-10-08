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

/** Ink strokes per page index. */
typealias PageStrokes = Map<Int, List<Stroke>>

/**
 * Binary format for a document's annotations:
 *
 * ```
 * magic, version
 * brush count, then per brush: family id, color, size, epsilon
 * page count, then per page: page index, stroke count, then per stroke: brush index, inputs (Ink proto)
 * ```
 *
 * Only stroke inputs are stored; shapes are regenerated on load. Brushes are stored once and
 * shared, as nearly every stroke uses one of a handful.
 */
internal object AnnotationCodec {
    private const val MAGIC = 0x53484E4B // "SHNK"
    private const val VERSION = 1

    fun encode(pages: PageStrokes, output: OutputStream) {
        val brushes = LinkedHashMap<Brush, Int>()
        for (strokes in pages.values) for (stroke in strokes) brushes.getOrPut(stroke.brush) { brushes.size }

        DataOutputStream(output).apply {
            writeInt(MAGIC)
            writeInt(VERSION)
            writeInt(brushes.size)
            for (brush in brushes.keys) {
                writeUTF(Pens.idOf(brush.family))
                writeInt(brush.colorIntArgb)
                writeFloat(brush.size)
                writeFloat(brush.epsilon)
            }
            val nonEmpty = pages.filterValues { it.isNotEmpty() }
            writeInt(nonEmpty.size)
            for ((page, strokes) in nonEmpty) {
                writeInt(page)
                writeInt(strokes.size)
                for (stroke in strokes) {
                    writeInt(brushes.getValue(stroke.brush))
                    writeBlob { stroke.inputs.encode(it) }
                }
            }
            flush()
        }
    }

    fun decode(input: InputStream): PageStrokes = with(DataInputStream(input)) {
        if (readInt() != MAGIC) throw IOException("Not an annotation file")
        val version = readInt()
        if (version != VERSION) throw IOException("Unsupported annotation version $version")

        val brushes = List(readInt()) {
            val familyId = readUTF()
            val family = Pens.familyOf(familyId) ?: throw IOException("Unknown brush family $familyId")
            Brush.createWithColorIntArgb(family, colorIntArgb = readInt(), size = readFloat(), epsilon = readFloat())
        }
        val pages = HashMap<Int, List<Stroke>>()
        repeat(readInt()) {
            val page = readInt()
            pages[page] = List(readInt()) {
                val brush = brushes[readInt()]
                Stroke(brush, StrokeInputBatch.decode(readBlob()))
            }
        }
        pages
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
