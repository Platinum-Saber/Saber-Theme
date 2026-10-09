# Handoff: Milestone 2 — in-launcher experience

**Goal:** Build M2 per `docs/plans/m2-in-launcher.md`; steps 1–3 done, continue at step 4 (native widgets), then, drawer/search, edit mode, settings.

## Decisions
- M2 = drawer/search, native widgets, edit mode, settings. Glance widgets + icon-pack APK are M3.
- Perf work is closed (user: no more frame-cost work). Step 1 left 1.0–1.3% jank on the old 7-page layout (first frame of a swipe records a new page); on the 2-page curated home it is 0.2–0.3%, p90 6 ms. Re-check once after step 4 with real widgets, report only.
- Default widget page: Clock 4x2, Weather 2x2, Calendar 2x2, Battery 2x1, Alarm 2x1. Media is picker-only (needs notification-listener access, and all six don't fit 4x5).
- Reconcile never appends apps and never adds/removes pages; apps of locked/paused profiles keep their slots.
- `HomeItem.Folder` field is `folderId` (`HomeItem.id` is the stable cross-type id: `app:`, `folder:`, `widget:`).
- Widgets reach home through `HomeScreen(widgetContent = …)` slot; `:app` will wire `WidgetHost` in step 4. Features never depend on each other.
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
- `core/designsystem/.../glass/GlassProgram.kt` — pooled AGSL shaders; `core/icons/.../GlyphImages.kt` — cached glyph bitmaps.

## State
- `main` = `origin/main`. Debug build installed on the S23 (holds HOME); M1 layout migrated on device without issues.
- Benchmark build is profileable + unobfuscated (`simpleperf record --app com.sabertheme.launcher` works). adb is at `C:/Users/User/AppData/Local/Android/Sdk/platform-tools/adb.exe` (not on bash PATH).
- Untested: fresh-install default layout on device (unit-tested only; don't `pm clear` the user's phone); locked-profile behaviour on device; photo-wallpaper import.

## Next step
M2 step 4 (`docs/plans/m2-in-launcher.md`): `:feature:widgets` — native AppWidgetHost, wire into `HomeScreen(widgetContent = …)` from `:app`. Then re-check jank once (report only).
