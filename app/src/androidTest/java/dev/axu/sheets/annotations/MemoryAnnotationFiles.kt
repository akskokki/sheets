package dev.axu.sheets.annotations

class MemoryAnnotationFiles : AnnotationFiles {
    val files = HashMap<String, ByteArray>()
    override fun read(key: String) = files[key]
    override fun write(key: String, bytes: ByteArray) {
        files[key] = bytes
    }
    override fun keys() = files.keys.toSet()
    override fun moveAside(key: String) {
        files.remove(key)
    }
}
