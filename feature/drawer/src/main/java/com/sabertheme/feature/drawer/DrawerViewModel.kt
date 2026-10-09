package com.sabertheme.feature.drawer

import android.view.View
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabertheme.core.data.AppRepository
import com.sabertheme.core.data.LaunchStats
import com.sabertheme.core.model.AppKey
import com.sabertheme.core.ui.AppActions
import com.sabertheme.core.ui.AppIconResolver
import com.sabertheme.core.ui.AppLauncher
import com.sabertheme.core.ui.LauncherApp
import com.sabertheme.core.ui.MenuItem
import com.sabertheme.core.ui.MenuOrigin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.text.Collator
import javax.inject.Inject

@Immutable
data class SearchResults(
    val query: String,
    val apps: List<LauncherApp>,
    val contacts: List<ContactResult>,
    val contactsNeedPermission: Boolean,
    val settings: List<SettingShortcut>,
)

@HiltViewModel
class DrawerViewModel @Inject constructor(
    appRepository: AppRepository,
    iconResolver: AppIconResolver,
    stats: LaunchStats,
    private val launcher: AppLauncher,
    private val actions: AppActions,
    private val contactSearch: ContactSearch,
) : ViewModel() {

    /** Every launchable app, A–Z by the locale's collation. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val apps: StateFlow<List<LauncherApp>> = appRepository.apps
        .mapLatest { list ->
            val collator = Collator.getInstance()
            list.sortedWith { a, b -> collator.compare(a.label, b.label) }.map { iconResolver.app(it) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Top four by launch count, among apps still installed. */
    val suggested: StateFlow<List<LauncherApp>> = combine(apps, stats.counts) { apps, counts ->
        val byKey = apps.associateBy { it.key }
        LaunchStats.top(counts.filterKeys(byKey::containsKey), SUGGESTED).mapNotNull(byKey::get)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val onHome: StateFlow<Set<AppKey>> = actions.onHome.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val contactsGranted = MutableStateFlow(contactSearch.granted())

    /** Null while the query is blank (the A–Z grid shows instead). */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<SearchResults?> = combine(
        _query.debounce { if (it.isBlank()) 0L else DEBOUNCE_MS },
        apps,
        contactsGranted,
    ) { q, apps, granted -> Triple(q, apps, granted) }
        .mapLatest { (q, apps, granted) ->
            if (q.isBlank()) return@mapLatest null
            SearchResults(
                query = q.trim(),
                apps = AppSearch.rank(q, apps, { it.entry.label }, { it.entry.packageName }).take(MAX_APPS),
                contacts = if (granted) contactSearch.search(q) else emptyList(),
                contactsNeedPermission = !granted,
                settings = SettingsSearch.search(q),
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun recheckContacts() {
        contactsGranted.value = contactSearch.granted()
    }

    fun launch(app: LauncherApp, view: View, bounds: Rect) = launcher.launch(app.key, view, bounds)

    fun menu(app: LauncherApp, bounds: Rect): List<MenuItem> =
        actions.menu(app.key, bounds, MenuOrigin.Drawer, onHome = app.key in onHome.value)

    private companion object {
        const val SUGGESTED = 4
        const val MAX_APPS = 8
        const val DEBOUNCE_MS = 120L
    }
}
