package dev.axu.sheets

import android.app.Application
import android.content.Context
import dev.axu.sheets.library.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SheetsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-wired dependencies shared across the app. */
class AppContainer(context: Context) {
    /** Outlives screens, for work that must finish even if the user navigates away (e.g. saving). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val contentResolver = context.contentResolver
    val library = LibraryRepository(context)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SheetsApplication).container
