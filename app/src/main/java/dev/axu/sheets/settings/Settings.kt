package dev.axu.sheets.settings

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** User preferences, observable from Compose and remembered across sessions. */
@Stable
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var scratchOutToErase by setting("scratch_out_to_erase", default = true)
    var showRecentPens by setting("show_recent_pens", default = true)
    var ignorePalm by setting("ignore_palm", default = true)
    var multiFingerTapUndo by setting("multi_finger_tap_undo", default = true)
    var tapEdgesToTurnPages by setting("tap_edges_to_turn_pages", default = true)
    var cropMargins by setting("crop_margins", default = true)
    var fullScreen by setting("full_screen", default = true)
    var keepScreenOn by setting("keep_screen_on", default = false)

    private fun setting(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        private var value by mutableStateOf(prefs.getBoolean(key, default))

        override fun getValue(thisRef: Any?, property: KProperty<*>) = value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            this.value = value
            prefs.edit { putBoolean(key, value) }
        }
    }
}
