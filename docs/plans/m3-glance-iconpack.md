# Milestone 3 — Glance widgets + icon-pack APK

**Status:** approved 2026-10-10; not started.

## Context
M2 finished the in-launcher experience (drawer, search, native glass widgets, edit mode, settings). The original product decision (docs/architecture.md) also promised two things for *other* launchers: Saber's widgets exported as AppWidgets, and a standalone ADW/Nova icon pack built from the same glyphs. M3 delivers both. Choices made by the user:
- **Glance set:** Clock, Weather, Calendar, Battery, Next alarm (no Media — it needs background session pushes; later).
- **Icon components:** dumped from the S23 over adb into a checked-in `design/icons/components.json`.
- **Icon look:** adaptive icons (opaque Glance-style background + glyph foreground + monochrome layer), so any launcher's shape mask applies.
- **Icon-pack check:** Nova Launcher on the S23 (the user installs it; I never download apps).

Constraints carried over: features never depend on each other; Gradle version catalog for every dependency; Glance cannot blur, so glass tokens map to a translucent tint + 1 px border (`GLANCE_ALPHA` light 0.6 `#FFFFFF`, dark 0.7 `#1A1C22`, `glass/border`); commit + push after each step; test on the real S23; perf work stays closed.

## Module changes
```
:app ─┬─ :feature:widgets ───┐   (in-launcher UI: WidgetViews, WidgetHost, WidgetCatalog, WidgetPicker)
      ├─ :widgets-glance NEW ├─→ :core:widgetdata NEW (sources, WidgetState, permissions, OpenMeteo, WidgetFormat)
      └─ (others unchanged)  ┘
:iconpack NEW  (separate application, applicationId com.sabertheme.iconpack; generated resources only)
```
- `:widgets-glance` is a library merged into the launcher APK: exported widgets must live in Saber's package to share the data layer, permissions and the weather cache.
- `:iconpack` depends on nothing at build time; its resources come from `node tools/build-iconpack.mjs`.
- Catalog additions: `androidx.glance:glance-appwidget` (latest stable, ≥ 1.1.1) and `androidx.work:work-runtime-ktx` (latest stable 2.10.x).

## Steps (commit + push after each)

### 1. Extract `:core:widgetdata` (no behaviour change)
- Move from `feature/widgets/.../widgets/`: `WidgetState.kt`, `WidgetPermissions.kt` (+ `WidgetScope`), `Broadcasts.kt`, `ClockSource.kt` (+ `AlarmSource`), `BatterySource.kt`, `CalendarSource.kt` (+ `CalendarMapper`), `WeatherSource.kt`, `OpenMeteo.kt`, `MediaSource.kt` (+ `MediaListenerService`), `WidgetFormat.kt`, `WidgetSources` (out of `WidgetHost.kt`), and their tests (`OpenMeteoTest`, `CalendarMapperTest`, `WidgetFormatTest`). Package `com.sabertheme.core.widgetdata`.
- Move the permissions + listener `<service>` from `feature/widgets/src/main/AndroidManifest.xml` and the serialization plugin/dependency with them.
- Add one helper for one-shot readers: `suspend fun <T> WidgetDataSource<T>.snapshot(timeoutMs = 20_000): WidgetState<T>` = first non-Loading state (subscribes to the shared flow, then lets `WhileShown` stop it).
- `:feature:widgets` keeps the UI and depends on `:core:widgetdata`. Update `.claude/rules/launcher-manifest.md` and the architecture graph.

### 2. Glance foundation + Clock widget (`:widgets-glance`)
- `GlanceTokens`: day/night `ColorProvider`s from the Figma Glance styling (fill, border, text primary/secondary, accent from `core/designsystem/.../theme/Color.kt`), radius 24 (2-wide) / 28 (4-wide), padding 16 × 14.
- `GlanceFrame` composable: rounded translucent background + 1 dp border (`cornerRadius` is API 31+, fine at minSdk 33).
- Hilt access via an `@EntryPoint` (`WidgetDataEntryPoint`: `WidgetSources`, `WidgetPermissions`) read with `EntryPointAccessors.fromApplication`.
- `ClockGlanceWidget` (sizes 4×2, 2×2, 4×1 via `SizeMode.Responsive`): time and date are `TextClock`s embedded with `AndroidRemoteViews` (system-updated every minute, no wakeups); next alarm line from `AlarmSource.snapshot()`. Tap → `AlarmClock.ACTION_SHOW_ALARMS`.
- Receiver + `res/xml/clock_widget_info.xml` (`targetCellWidth/Height`, `minWidth/minHeight` from the 72 dp / 96 dp cells, `resizeMode`, `widgetCategory=home_screen`, description, preview).
- `WidgetUpdateReceiver` (manifest, exempt implicit broadcasts): `TIME_SET`, `TIMEZONE_CHANGED`, `LOCALE_CHANGED`, `NEXT_ALARM_CLOCK_CHANGED`, calendar `PROVIDER_CHANGED` → `updateAll` for the affected widgets.
- Verify on the S23 before the rest (see Verification).

### 3. Weather, Calendar, Battery, Next alarm Glance widgets
- Each reads `snapshot()` in `provideGlance` and renders a Glance version of the matching `WidgetViews.kt` layout per size (sizes = `WidgetType.sizes`; glyphs via `ImageProvider(UiGlyph.X.drawable)` with tint).
- `WidgetRefreshWorker` (`CoroutineWorker`, unique periodic 30 min, network constraint for weather): refreshes weather (fetch when the cache is stale — add `WeatherSource.refresh()` reusing its fetch/cache code), then `updateAll` for every Saber Glance widget. Battery rides the same worker plus an update on tap (no manifest broadcast exists for level changes). Enqueued in receivers' `onEnabled`, cancelled when no Saber widget remains.
- NeedsPermission → "Tap to allow" → `GlancePermissionActivity` (translucent, `exported=false`): requests the runtime permission (app info after a permanent denial), then `updateAll` and finishes. Tap actions: Calendar → calendar time URI, Battery → `ACTION_POWER_USAGE_SUMMARY`, Alarm → show alarms.
- Previews: Glance generated previews (`providePreview`) if the chosen Glance version has them, otherwise `previewLayout` XML per widget.

### 4. Icon-pack generator + `:iconpack`
- `tools/dump-components.mjs`: runs `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER`, keeps components whose package is in `design/icons/packages.json`, and merges them into `design/icons/components.json` (`{ "chat": ["com.whatsapp/com.whatsapp.Main"], … }`; never drops existing entries). Reuse `loadPackages()` / `ROOT` from `tools/glyph-source.mjs`.
- `tools/build-iconpack.mjs` (reuses `loadGlyphs()`, `toPathData()`, `resName()` from `tools/glyph-source.mjs`; never hand-edit outputs):
  - `iconpack/src/main/res/drawable/ic_<key>_fg.xml`: glyph scaled into the 108-unit adaptive canvas (66-unit safe zone), white stroke.
  - `iconpack/src/main/res/drawable-anydpi/ic_<key>.xml`: `<adaptive-icon>` with background `@color/icon_bg` (opaque dark Glance fill), foreground, and `<monochrome>` (same glyph) for themed icons.
  - `res/xml/appfilter.xml` + `assets/appfilter.xml` (`<item component="ComponentInfo{pkg/activity}" drawable="ic_<key>"/>`), `res/xml/drawable.xml` (all icons, for pickers).
- `:iconpack` module: application convention plugin, no dependencies; manifest declares the ADW/Nova/Lawnchair icon-pack intent filters (`org.adw.launcher.THEMES`, `com.novalauncher.THEME`, `com.teslacoilsw.launcher.THEME`, `com.anddoes.launcher.THEME`, `com.gau.go.launcherex.theme`, `com.fede.launcher.THEME_ICONPACK`) on a tiny info activity ("Apply Saber Icons in your launcher's settings"). Debug-signed like the launcher.
- JVM unit test in `:iconpack`: parses `appfilter.xml`, checks every `drawable` exists and every component is well formed.
- Risk: if Nova ignores vector/adaptive drawables from packs, rasterise foregrounds to PNG at build time (fallback noted, only if needed).

### 5. Docs and wrap-up
- `docs/architecture.md`: `:core:widgetdata`, Glance pipeline (snapshot reads, TextClock, worker + exempt broadcasts, permission activity), icon-pack generation; `.claude/rules/launcher-manifest.md`: widget receivers, worker, permission activity, iconpack filters; CLAUDE.md command list gains `node tools/dump-components.mjs` / `build-iconpack.mjs` and `./gradlew :iconpack:installDebug`. M3 handoff → done; open-items list for M4.

## Critical files
- New: `core/widgetdata/`, `widgets-glance/`, `iconpack/`, `tools/dump-components.mjs`, `tools/build-iconpack.mjs`, `design/icons/components.json`.
- Changed: `settings.gradle.kts`, `gradle/libs.versions.toml`, `feature/widgets/build.gradle.kts` + `WidgetHost.kt` (imports), `app/build.gradle.kts`, `app/.../HomeActivity.kt` (imports only), `docs/architecture.md`, `.claude/rules/launcher-manifest.md`, `CLAUDE.md` (commands).
- Reused as-is: sources in `feature/widgets/.../widgets/` (moved), `WidgetType.sizes` (`core/model/.../HomeLayout.kt`), `UiGlyph` drawables (`core/icons`), `tools/glyph-source.mjs`.

## Verification
- Every step: `./gradlew assembleDebug testDebugUnitTest lintDebug` green (JAVA_HOME per CLAUDE.md, trim hook); moved tests still pass; step 4 adds the appfilter test.
- Step 1 on the S23: home widgets unchanged (screenshot vs before), no crashes in `logcat -b crash`.
- Steps 2–3 on the S23 without changing the default home: start One UI Home explicitly (`adb shell am start -n com.sec.android.app.launcher/.activities.LauncherActivity`), the user adds the Saber widgets from its widget picker, I screenshot each size; check clock ticks, alarm line, weather data, calendar Allow → grant flow, battery after the 15–30 min worker (or `adb shell cmd jobscheduler run` on the worker job), tap actions. Saber stays the default home throughout; the user removes the test widgets afterwards.
- Step 4: user installs Nova; I run `node tools/dump-components.mjs` and `build-iconpack.mjs`, install `:iconpack`, start Nova explicitly (`am start` on its home activity), the user applies "Saber Icons" in Nova settings; screenshots show themed dock/drawer icons with Nova's shape mask; unmapped apps keep their own icons. `aapt2 dump xmltree` sanity check on the APK's appfilter.
- Before any on-device change to Saber's own layout/settings: back up and restore per memory `device-layout-backup`.
