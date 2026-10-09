# Milestone 2 — In-launcher experience

**Status:** done (2026-10-10). Follow-up: `handoffs/m3-glance-iconpack.md`.

## Context
M1 shipped (pushed to `origin/main`): glass engine, living-glass reactive layer, custom wallpapers, home with every app on pages. M1 left paging jank at 2.3% (target < 1%). The user chose for M2:
- **Scope:** app drawer + universal search, native glass widgets, edit mode, settings. Glance exported widgets and the icon-pack APK move to **M3**.
- **Home model:** curated home + drawer (Figma: page 1 widgets, page 2 chosen apps; every app lives in the drawer; new installs go to the drawer only).
- **Search:** apps, contacts, settings shortcuts, web.
- **Perf first:** get paging jank < 1% before widgets add more glass nodes.

## Module changes
```
:app ─┬─ :feature:home      (pager, grid, dock, folders, edit mode)
      ├─ :feature:drawer    NEW (drawer sheet, search)
      ├─ :feature:widgets   NEW (native glass widgets + data sources, picker catalog)
      └─ :feature:settings  NEW (settings screen, wallpaper picker moved here)
features → :core:ui NEW (AppTile, HomeAppIcon, FolderIcon, GlassMenu, BoundsRef, AppIconResolver)
:core:ui → :core:designsystem, :core:icons, :core:data, :core:model
```
- Features never depend on each other. `:app` wires them with slots: `HomeScreen(widgetContent = { spec -> WidgetHost(spec) }, onOpenDrawer, onOpenSettings, onPickWidget)`.
- New convention: none needed; `:feature:widgets` adds the Kotlin serialization plugin (catalog entry) for Open-Meteo parsing.
- Hosting stays in `HomeActivity`: Home + overlays (Drawer, Settings, Widget picker) as state, no nav library.

## Steps (commit + push after each)

### 1. Paging perf (< 1% jank)
- Profile first: Perfetto system trace of the benchmark build during swipes (`adb shell perfetto -o … -t 10s gfx view sched`), find per-frame UI/RenderThread cost.
- Expected fix: give the glass background its own layer so a moving tile re-records only `drawRect` + shadow, not its glyph/content. In `GlassSurface` (`core/designsystem/.../glass/GlassSurface.kt`) split into `[press layer] → dropShadow → glassBackground (own graphicsLayer) → content (own graphicsLayer)`.
- Cheaper tiles: reuse one `GlassPressState` per tile only when pressed; avoid `onGloballyPositioned` + `positionInWindow` per node per frame by computing origin in draw from `LayoutCoordinates` cached in `onPlaced` (one call per draw).
- Target on S23 benchmark build: p90 ≤ 8 ms, jank < 1%; record in docs/architecture.md.

### 2. Layout model v2 (curated home, positioned grid, widgets)
- `core/model`: `HomeLayout(dock, pages: List<HomePage>)`, `HomePage(items: List<Placed>)`, `Placed(item, col, row, spanX, spanY)`, `HomeItem.App | Folder | Widget(id, type, config)`, `WidgetType` enum (Clock, Weather, Calendar, Media, Battery, Alarm) with allowed sizes (2×1, 2×2, 4×1, 4×2) on the 4×5 grid.
- `HomeLayoutPolicy`: new default = page 1 widgets as in Figma (Clock 4×2, Weather 2×2, Calendar 2×2, Media 4×1, Battery/Alarm 2×1 – fitted to 4×5), page 2 = dock apps excluded, Social folder + up to 15 most useful mapped-glyph apps; reconcile only removes uninstalled apps (no appending); `firstFreeSpot(page, spanX, spanY)`, `move`, `remove`, `makeFolder` helpers for edit mode.
- `HomeLayoutCodec` v2 (`W` lines, positions); v1 decoder kept for migration: keep dock + the first page trimmed to 20 placed items, prepend the default widget page.
- Fix the M1 gap: reconcile drops apps only if their profile is present **and unlocked** (`UserManager.isQuietModeEnabled` / `isUserUnlocked`), so locked work/Secure Folder apps keep their slots.
- Tests: policy (default, reconcile, locked profile, free spot, folder ops), codec v2 round-trip, v1→v2 migration.

### 3. `:core:ui` extraction (no behaviour change)
- Move from `feature/home`: `HomeIcons.kt` (AppTile, HomeAppIcon, FolderIcon, BoundsRef), `GlassMenu.kt`; move icon resolution from `HomeViewModel` (`resolveSuspending`) into `AppIconResolver` (singleton cache, uses `AppRepository.monochromeIcon`, `IconMapper`).
- Add `AppLauncher` helper (clip-reveal options + `AppRepository.launch`) shared by home and drawer.

### 4. Native widgets (`:feature:widgets`)
- `WidgetDataSource<T>`: `Flow<WidgetState<T>>` = Loading | Ready(T) | NeedsPermission(kind) | Error.
- Sources: Clock (minute ticker via `ACTION_TIME_TICK`), Calendar (`CalendarContract.Instances`, next 3 events, `READ_CALENDAR`), Weather (Open-Meteo, last known coarse location via `LocationManager`, cached 30 min in DataStore, `INTERNET` + `ACCESS_COARSE_LOCATION`), Battery (sticky `ACTION_BATTERY_CHANGED`), Media (`MediaSessionManager.getActiveSessions` with a `NotificationListenerService` component; play/pause/skip via `TransportControls`), Next alarm (`AlarmManager.nextAlarmClock`, `ACTION_NEXT_ALARM_CLOCK_CHANGED`).
- Glass composables per Figma widget sets (sizes above), Regular material, `Saber.type` styles; NeedsPermission renders an "Allow" glass button (runtime permission or notification-listener settings).
- `WidgetHost(spec)` + `WidgetCatalog` (types, sizes, previews) exported for the home slot and the picker.
- Manifest: permissions above, notification listener `<service>`.
- Tests: Open-Meteo JSON parsing, calendar row → model mapping, clock formatting.

### 5. Drawer + universal search (`:feature:drawer`)
- Open: swipe up on home (vertical drag on pager area) or tap the search pill (opens with keyboard). Full-height Thick glass sheet from below (`GlassSheet` motion), handle, search field, Suggested row (top 4 by launch count — `LaunchStats` in DataStore, recorded by `AppLauncher`), "All apps" A–Z 4-col grid with alphabet rail fast-scroll (Figma "App drawer").
- Search (Figma "Search"): debounced 120 ms, sections in order Apps / Contacts / Settings / Web.
  - Apps: score = exact > prefix > word-start > substring > fuzzy subsequence, on label and package.
  - Contacts: `ContactsContract.Contacts` filter URI; `READ_CONTACTS` asked from an inline "Search contacts" row the first time; tap opens the contact.
  - Settings: built-in list of `Settings.ACTION_*` intents with keywords (Wi-Fi, Bluetooth, Display, Battery, Sound, Notifications, Apps, Location, Security, About phone, Default home app).
  - Web: last row → `ACTION_WEB_SEARCH`.
- App long-press menu (drawer and home): Add to home (first free spot on the apps page, new page if full), App info, Uninstall (`ACTION_DELETE`, `REQUEST_DELETE_PACKAGES`), Remove from home (home only).
- Tests: app ranking, settings keyword matching.

### 6. Edit mode (`:feature:home`)
- Enter: home menu "Edit home screen" or long-press-and-drag an icon/widget. Figma "Edit mode": page shrinks to 0.8 inside a dashed outline, page thumbnails with "+" page, toolbar (Wallpaper, Widgets, Settings), Done.
- Drag & drop on the 4×5 grid with occupancy preview; drop on app → folder; drag out of open folder; drag to/from dock (max 4); drop on "Remove" target; add/delete empty pages; folder rename in the open folder while editing. Widgets move as spans (no resize; pick size in picker).
- Widget picker sheet (Figma "Widget picker"): chips by category, live previews from `WidgetCatalog`, tap to add at first free spot.
- All moves are springs from origin (`GlassMotion.morph`); haptics on pick-up and drop; layout writes go through `LayoutRepository`.

### 7. Settings (`:feature:settings`)
- Full-screen Thick glass page from home menu "Launcher settings" / edit toolbar. Sections: Wallpaper (moved from `HomeOptionsSheet` incl. photo import), Glass (intensity, tilt effects on/off), Icons (Tile / Bare style per Figma, labels on/off), Home (set as default home via `RoleManager.createRequestRoleIntent(ROLE_HOME)`), About (version, Glass Lab in debug/benchmark).
- `GlassSettings` gains `iconStyle`, `showLabels`, `tiltEnabled`; `HomeOptionsSheet` is removed (menu opens Settings).

### 8. Docs and wrap-up
- docs/architecture.md: module graph, layout v2, widgets, drawer/search, edit mode, perf numbers; `.claude/rules/launcher-manifest.md`: new permissions and listener service; handoff note for M3 (Glance + icon pack).

## Verification
- `./gradlew testDebugUnitTest lintDebug assembleDebug` green after every step (JAVA_HOME per CLAUDE.md, trim hook).
- On the S23 (`installDebug`, screenshots via `adb exec-out screencap`):
  - Perf: `installBenchmark`, 12 page swipes, `dumpsys gfxinfo` p90 ≤ 8 ms, jank < 1% (step 1, re-check after step 4 with widgets on page 1).
  - Migration: existing M1 install upgrades to a widget page + one apps page, dock and Social folder kept, nothing crashes.
  - Widgets: each shows real data or an Allow button; granting permission updates live; media controls work with Spotify/YouTube Music playing.
  - Drawer: swipe up opens; A–Z rail jumps; search "cal" shows Calculator/Calendar apps, a contact (after permission), Settings matches, web row; launching records suggestions.
  - Edit: move app, make folder, rename, move widget, add page, add widget from picker, remove; kill the launcher (`adb shell am force-stop`) and confirm layout persists.
  - Settings: Bare icon style and labels toggle apply live; default-home request dialog appears.
  - Locked Secure Folder keeps its apps' home slots.
