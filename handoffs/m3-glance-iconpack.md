# Handoff: Milestone 3 — Glance widgets + icon-pack APK

**Goal:** Build M3 per `docs/plans/m3-glance-iconpack.md` (approved): the native widgets exported as Glance AppWidgets for other launchers (`:widgets-glance`), and a standalone ADW/Nova icon-pack APK (`:iconpack`) built from the same glyphs. M2 (in-launcher experience) is complete.

## Decisions carried over
- Glance cannot blur: map the glass tokens to a translucent tint + 1 px border (`.claude/rules/glass-rendering.md`; Figma "Widgets" board already has `Render=Glance` variants of every widget).
- `:widgets-glance` reuses the widget data layer. `docs/architecture.md` says: if that coupling grows, move the sources to `:core:widgetdata`. Today the sources (`ClockSource`, `CalendarSource`, `WeatherSource`, …, `WidgetPermissions`, `OpenMeteo`, `WidgetFormat`) live in `:feature:widgets` next to the in-launcher UI; the plan extracts them to `:core:widgetdata` in step 1.
- Glance refresh: WorkManager plus broadcast triggers (time, battery, alarm changed); the in-launcher sources are `shareIn(WhileShown)` flows, which do not fit a background widget host as-is.
- Icon pack: separate `applicationId`, `appfilter.xml` + `drawable.xml` generated from `design/icons/packages.json` and the `glyph_*` drawables (`node tools/build-icons.mjs` is the single generator; never edit generated files).
- Process: plan mode first (more than ~3 files); commit and push after every step with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; test on the real S23.
- Perf work stays closed unless the user reopens it.

## Files
- `docs/plans/m3-glance-iconpack.md` — approved M3 plan (steps 1–5, verification). User choices: Glance = Clock/Weather/Calendar/Battery/Alarm (no Media); components dumped from the S23; adaptive icons; icon pack checked in Lawnchair (user installs it; Nova dropped, likely unmaintained).
- `docs/architecture.md` — current module graph (M3 modules shown dashed), widget system, icon system.
- `core/widgetdata/src/main/java/com/sabertheme/core/widgetdata/` — sources, `WidgetState` + `snapshot()`, `WidgetPermissions`, `WidgetSources`, `OpenMeteo`, `WidgetFormat`, `MediaListenerService` (+ manifest permissions).
- `feature/widgets/src/main/java/com/sabertheme/feature/widgets/` — in-launcher UI: `WidgetViews.kt` (per-size layouts to mirror in Glance), `WidgetHost`, `WidgetCatalog`, `WidgetPicker`.
- `design/icons/glyphs.js`, `design/icons/packages.json`, `tools/build-icons.mjs` — icon source and generator.
- `design/figma-plugin/src/code.js` — `WIDGETS` table and `Render=Glance` styling (`GLANCE_ALPHA`).
- `.claude/rules/launcher-manifest.md`, `.claude/rules/glass-rendering.md` — manifest/permission and glass rules.

## State
- M3 step 3 done: Weather/Calendar/Battery/Alarm Glance widgets (`WeatherGlanceWidget.kt`, `CalendarGlanceWidget.kt`, `BatteryAlarmGlanceWidgets.kt`), `GlanceStateFrame` (loading/error/"Tap to allow"), `GlancePermissionActivity`, `WidgetRefreshWorker` + `SaberGlanceWidgets` (`updateAll`, `anyPlaced`, `onAppStart`, `publishPreviews`), `WeatherSource.refresh()`, `RefreshWeatherAction` (weather tap), `openAction()` for data-less tap intents, generated previews published from `SaberApp`. Verified in One UI Home: all five widgets at default sizes with live data, picker previews correct, taps open Clock alarms / battery usage / calendar chooser, revoke → "Tap to allow" → system dialog → events back.
  - Limits: battery level/charging only update on the 15-min worker, app start or any widget redraw (no manifest broadcast exists); widgets keep their last drawing until Saber's process restarts after a permission change. Not verified: non-default sizes of Weather (4x1/4x2), Calendar (4x2), Battery/Alarm (2x2); the 15-min worker firing naturally; lint reports 2 warnings in `:widgets-glance` that weren't reviewed (lint report sits in `build/`, which tool permissions block).
  - Test widgets are still on One UI Home's first page (Clock 2x2, Weather, Calendar, Battery, Alarm); the user removes them when done.
- M3 step 2 done: `:widgets-glance` (Glance 1.2.0, WorkManager 2.12.0 in the catalog) with `GlanceSupport.kt` (`WidgetDataEntryPoint`, `GlanceTokens` from `LightSaberColors`/`DarkSaberColors`, `GlanceFrame` card drawables `glance_frame_24/28`, responsive `currentLayout()`), `ClockGlanceWidget` (TextClock layouts via `AndroidRemoteViews` + alarm line), `WidgetUpdateReceiver` + `SaberGlanceWidgets.updateAll`. Verified in One UI Home at 4x2, 4x1 and 2x2 (clock ticks without wakeups). Glance gotchas: `ColorProvider(resId)` is RestrictedApi in 1.2 (use the day/night `androidx.glance.color.ColorProvider`); `AndroidRemoteViews` fills the height unless given `wrapContentHeight()`. A test Saber Clock is still on One UI Home's first page (user removes it after step 3).
- M3 step 1 done: `:core:widgetdata` extracted; home widgets verified unchanged on the S23. `MediaListenerService` moved package, so any notification access granted to the old component would need re-granting (none was granted).
- Lawnchair 15 Beta 3 installed on the S23 over adb (checksum matched GitHub's release asset; Play Protect blocks browser sideloads of it). Saber stays the HOME role holder.
- `main` = `origin/main`, all M2 steps committed. Debug build on the S23 (holds HOME). The user's layout and settings were restored after on-device tests (memory `device-layout-backup` has the procedure).
- Perf after widgets: jank 1.09%, p90 8 ms, p99 15 ms (benchmark build, 12 swipes). Reported only.
- Not yet verified on device: edge-flip while dragging, contacts search (no `READ_CONTACTS`), Uninstall on a third-party app, Add/Remove home from menus, Suggested row, Media widget, `ROLE_HOME` request dialog, photo import from the settings page, locked-profile behaviour, fresh-install default layout.
- Known rough edges: dock items don't shift to open a gap while dragging (outline only); locked-profile slots look empty and refuse drops; white "Done" text on the light accent pill is low contrast; `AppRepository.installed` is a cold flow collected separately by home and drawer (two LauncherApps callbacks).

## Next step
M3 step 4 (`docs/plans/m3-glance-iconpack.md`): `tools/dump-components.mjs` (adb launcher activities → `design/icons/components.json`), `tools/build-iconpack.mjs` (adaptive icons + monochrome, `appfilter.xml`, `drawable.xml`), `:iconpack` app module with icon-pack intent filters + appfilter unit test; verify in Lawnchair 15 Beta 3 (already installed; start it with `am start -n app.lawnchair/.LawnchairLauncher`).
