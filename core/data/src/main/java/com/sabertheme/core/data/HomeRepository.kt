package com.sabertheme.core.data

import com.sabertheme.core.model.AppEntry
import com.sabertheme.core.model.AppKey
import com.sabertheme.core.model.HomeLayout
import com.sabertheme.core.model.HomeLayoutPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installed apps + the saved layout, reconciled; any change is written back.
 * A saved M1 (v1) layout is migrated by the codec and saved as v2 here.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val apps: AppRepository,
    private val layouts: LayoutRepository,
) {
    data class Home(val layout: HomeLayout, val apps: Map<AppKey, AppEntry>)

    /** [glyphOf] maps a package to its glyph key; used only for the first-run layout. */
    fun home(glyphOf: (String) -> String?): Flow<Home> =
        combine(apps.installed, layouts.layout) { installed, saved ->
            val layout = saved?.let { HomeLayoutPolicy.reconcile(it, installed.apps, installed.lockedProfiles) }
                ?: HomeLayoutPolicy.default(installed.apps, glyphOf)
            // Reconcile is stable, so the re-emitted saved layout won't save again.
            if (layout != saved) layouts.save(layout)
            Home(layout, installed.apps.associateBy { it.key })
        }
}
