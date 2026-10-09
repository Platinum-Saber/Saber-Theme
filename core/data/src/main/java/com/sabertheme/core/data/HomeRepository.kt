package com.sabertheme.core.data

import com.sabertheme.core.model.AppEntry
import com.sabertheme.core.model.AppKey
import com.sabertheme.core.model.HomeLayout
import com.sabertheme.core.model.HomeLayoutPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Installed apps + the saved layout, reconciled; any change is written back. */
@Singleton
class HomeRepository @Inject constructor(
    private val apps: AppRepository,
    private val layouts: LayoutRepository,
) {
    data class Home(val layout: HomeLayout, val apps: Map<AppKey, AppEntry>)

    /** [glyphOf] maps a package to its glyph key; used only for the first-run layout. */
    fun home(glyphOf: (String) -> String?): Flow<Home> =
        combine(apps.apps, layouts.layout) { installed, saved ->
            val layout = saved?.let { HomeLayoutPolicy.reconcile(it, installed) }
                ?: HomeLayoutPolicy.default(installed, glyphOf)
            // Reconcile is stable, so the re-emitted saved layout won't save again.
            if (layout != saved) layouts.save(layout)
            Home(layout, installed.associateBy { it.key })
        }
}
