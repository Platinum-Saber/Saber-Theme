# Handoff: Milestone 2 — in-launcher experience

**Goal:** Build M2 per `docs/plans/m2-in-launcher.md`; steps 1–5 done, continue at step 6 (edit mode + widget picker), then settings (7), docs (8).

## Decisions
- M2 = drawer/search, native widgets, edit mode, settings. Glance widgets + icon-pack APK are M3.
- Perf work is closed (user: no more frame-cost work). Step 1 left 1.0–1.3% jank on the old 7-page layout (first frame of a swipe records a new page); on the 2-page curated home it is 0.2–0.3%, p90 6 ms. After step 4 (real widgets, benchmark, 12 swipes): 1.09% jank, p90 8 ms, p99 15 ms — reported, not acted on.
- Default widget page: Clock 4x2, Weather 2x2, Calendar 2x2, Battery 2x1, Alarm 2x1. Media is picker-only (needs notification-listener access, and all six don't fit 4x5).
- Reconcile never appends apps and never adds/removes pages; apps of locked/paused profiles keep their slots.
- `HomeItem.Folder` field is `folderId` (`HomeItem.id` is the stable cross-type id: `app:`, `folder:`, `widget:`).
- Widgets reach home through `HomeScreen(widgetContent = { widget, size, modifier -> … })`; `HomeActivity` injects `WidgetSources` and calls `WidgetHost`, and calls `permissions.recheck()` in `onResume`. Features never depend on each other.
- Commit and push after every step. Trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Files
- `docs/plans/m2-in-launcher.md` — approved plan (steps 1–8, verification).
- `docs/architecture.md` — architecture, layout v2, perf history.
- `core/model/.../HomeLayout.kt` — `HomeLayout`, `HomePage`, `Placed`, `HomeItem`, `WidgetType`, `WidgetSize`.
- `core/model/.../HomeLayoutPolicy.kt` — default layout, reconcile, `firstFreeSpot`/`add`/`move`/`remove`/`makeFolder`/`find` (use these in edit mode and "Add to home").
- `core/model/.../HomeLayoutCodec.kt` — v2 format + v1 migration.
- `core/data/.../AppRepository.kt` — `installed: Flow<Installed(apps, lockedProfiles)>`, `apps`, launch, app info.
- `feature/home/.../HomeScreen.kt` — pager, positioned `HomePage` Layout + `HomeGrid`, `WidgetPlaceholder`, dock, menus.
- `core/ui/.../AppIcons.kt` (AppTile, HomeAppIcon, FolderIcon(name, apps), BoundsRef, cell metrics), `GlassMenu.kt`, `LauncherApp` (was `HomeApp`).
- `core/ui/.../AppIconResolver.kt` (singleton icon cache, `app(entry)`), `AppLauncher.kt` (clip-reveal launch, app info) — use both in the drawer.
- `feature/home/.../HomeViewModel.kt` — UI state mapping; delegates icons/launch to `:core:ui`.
- `feature/widgets/` — `WidgetState`/`WidgetDataSource`, one `@Singleton` source per type (shared via `shareIn(WhileShown)`), `WidgetPermissions.gated`, `WidgetViews.kt` (glass content per size), `WidgetHost`, `WidgetCatalog` + `WidgetPreview` (sample data, for the step-6 picker). Weather caches Open-Meteo JSON in DataStore `weather`.
- `feature/drawer/` — `AppDrawer` + `DrawerState` (hoisted in `HomeActivity`; `fraction` drives home blur via `HomeScreen(backgroundBlur)`), `DrawerViewModel`, `AppSearch` (ranking + rail sections), `SettingsSearch`, `ContactSearch`. Home opens it via `onOpenDrawer(withKeyboard)` (swipe up / search pill); Home button closes it (`onNewIntent`).
- `core/ui/.../AppActions.kt` — shared app long-press menu (`MenuOrigin.Home`/`Drawer`), add/remove home via `LayoutRepository.update` + `HomeLayoutPolicy.add`/`removeApp`. `MenuItem.badge` replaces the hard-coded "Soon".
- `core/data/.../LaunchStats.kt` — launch counts (recorded in `AppLauncher.launch`), `LaunchStats.top` for Suggested.
- `core/designsystem/.../glass/GlassProgram.kt` — pooled AGSL shaders; `core/icons/.../GlyphImages.kt` — cached glyph bitmaps.

## State
- `main` = `origin/main`. Debug build installed on the S23 (holds HOME); M1 layout migrated on device without issues.
- Benchmark build is profileable + unobfuscated (`simpleperf record --app com.sabertheme.launcher` works). adb is at `C:/Users/User/AppData/Local/Android/Sdk/platform-tools/adb.exe` (not on bash PATH).
- Calendar + Location now granted by the user on device; calendar shows real events. Weather untested with real data: device location is off (`location_mode=0`), widget now says "Location is off".
- Untested: contacts search (READ_CONTACTS not granted), Uninstall on a third-party app, Add/Remove home round trip on device, Suggested row (fills as launches are counted); Media widget (picker-only, needs notification access); fresh-install default layout on device (unit-tested only; don't `pm clear` the user's phone); locked-profile behaviour on device; photo-wallpaper import.

## Next step
M2 step 6 (`docs/plans/m2-in-launcher.md`): edit mode in `:feature:home` — menu "Edit home screen" / long-press-drag, 0.8 scaled page + thumbnails + toolbar, drag & drop with occupancy preview, folders, dock, Remove target, add/delete pages, folder rename; widget picker sheet from `WidgetCatalog`/`WidgetPreview` (wired from `:app`, features stay independent). Layout writes via `LayoutRepository.update` + `HomeLayoutPolicy`.
