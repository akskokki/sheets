package dev.axu.sheets.reader

import android.content.Context
import androidx.core.content.edit

/** The last page viewed in each document, keyed like annotations so it survives renames. */
class ReadingPositions(context: Context) {
    private val prefs = context.getSharedPreferences("positions", Context.MODE_PRIVATE)

    fun pageOf(documentKey: String): Int = prefs.getInt(documentKey, 0)

    fun save(documentKey: String, page: Int) = prefs.edit { putInt(documentKey, page) }
}
