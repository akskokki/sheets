package dev.axu.sheets

import android.app.Application
import android.content.Context
import dev.axu.sheets.annotations.AnnotationStore
import dev.axu.sheets.annotations.LocalAnnotationFiles
import dev.axu.sheets.ink.PenSettings
import dev.axu.sheets.library.LibraryRepository
import dev.axu.sheets.reader.ReadingPositions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

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
    val positions = ReadingPositions(context)
    val pen = PenSettings(context)
    val annotations = AnnotationStore(
        contentResolver,
        local = LocalAnnotationFiles(File(context.filesDir, "annotations")),
        notesFolder = library.notesFolder,
        scope = appScope,
    )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SheetsApplication).container
