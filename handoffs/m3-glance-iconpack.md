# Handoff: Milestone 3 — Glance widgets + icon-pack APK

**Goal:** Build M3 per `docs/plans/m3-glance-iconpack.md` (approved): Saber's widgets exported as Glance AppWidgets for other launchers (`:widgets-glance`) and a standalone ADW/Nova icon-pack APK (`:iconpack`). Steps 1–3 are done; continue at step 4 (icon pack), then step 5 (docs + wrap-up).

## Decisions
- User choices: Glance = Clock, Weather, Calendar, Battery, Next alarm (no Media); icon-pack components dumped from the S23 into `design/icons/components.json`; adaptive icons (opaque Glance-style background + glyph foreground + monochrome layer); icon pack verified in **Lawnchair 15 Beta 3** (Nova dropped, likely unmaintained).
- Glance cannot blur: translucent fill + 1 dp border (`.claude/rules/glass-rendering.md`).
- The widget data layer lives in `:core:widgetdata`, shared by `:feature:widgets` (in-launcher) and `:widgets-glance`.
- Icons: `node tools/build-icons.mjs` stays the single glyph generator; the icon pack gets its own generator (`tools/build-iconpack.mjs`) reusing `tools/glyph-source.mjs`. Never edit generated files.
- Process: commit and push after every step with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; test on the real S23 over adb; Saber stays the default home (other launchers are started explicitly with `am start`). Perf work stays closed.

## Files
- `docs/plans/m3-glance-iconpack.md`: approved plan (steps, verification).
- `docs/architecture.md`: module graph and systems (Glance pipeline and icon pack get documented in step 5).
- `core/widgetdata/.../widgetdata/`: sources, `WidgetState` + `snapshot()`, `WidgetPermissions`, `WidgetSources`, `WeatherSource.refresh()`, `OpenMeteo`, `WidgetFormat`, `MediaListenerService` (+ manifest permissions).
- `widgets-glance/.../glance/`: `GlanceSupport.kt` (entry point, `SaberGlanceReceiver`, `GlanceTokens`, `GlanceFrame`, `GlanceStateFrame`, `openAction()`, `ringImage()`), one file per widget, `WidgetRefreshWorker.kt` (+ `SaberGlanceWidgets`: `updateAll`, `anyPlaced`, `onAppStart`, `publishPreviews`), `GlancePermissionActivity`, `WidgetUpdateReceiver`; res: card drawables, TextClock layouts, provider XMLs.
- `app/.../SaberApp.kt`: calls `SaberGlanceWidgets.onAppStart` and publishes picker previews once per install.
- `design/icons/glyphs.js`, `design/icons/packages.json` (glyph → package names), `tools/glyph-source.mjs` (`loadGlyphs`, `loadPackages`, `toPathData`, `resName`, `ROOT`), `tools/build-icons.mjs`.
- `.claude/rules/launcher-manifest.md`: permissions, receivers, listener service (add the icon-pack filters in step 4/5).

## State
- `main` = `origin/main`, working tree clean. Debug build on the S23 (Saber holds HOME). Lawnchair 15 Beta 3 (`app.lawnchair`) installed over adb (checksum matched GitHub; Play Protect blocks browser sideloads of it).
- Step 1: `:core:widgetdata` extracted, no behaviour change.
- Step 2: `:widgets-glance` foundation + Saber Clock (TextClock via `AndroidRemoteViews`, so the host keeps time without wakeups).
- Step 3: Weather, Calendar, Battery, Next alarm widgets; "Tap to allow" → `GlancePermissionActivity`; `WidgetRefreshWorker` (15 min, unique, self-cancels when no widget is placed); generated previews. Verified in One UI Home: all five at default sizes with live data, previews, taps, revoke → allow flow.
- Glance gotchas found: `ColorProvider(resId)` is RestrictedApi in 1.2 (use day/night `androidx.glance.color.ColorProvider`); `AndroidRemoteViews` fills the height without `wrapContentHeight()`; Glance's `glance-action:` URI breaks data-less tap intents (SHOW_ALARMS, POWER_USAGE_SUMMARY), so `openAction()` makes them explicit (needs the `<queries>` entries); `setWidgetPreviews` needs API 35 (guarded).
- Limits: battery level/charging only update on the worker, app start or another widget redraw; after a permission change widgets keep their last drawing until Saber's process restarts.
- Not verified: Glance non-default sizes (Weather 4x1/4x2, Calendar 4x2, Battery/Alarm 2x2); the worker firing naturally; 2 lint warnings in `:widgets-glance` (unread: `build/` is blocked by tool permissions).
- No Saber widgets are placed anywhere (the One UI test page was deleted: One UI has no per-widget Remove while it isn't the default home, so test on a fresh page and delete the page afterwards).
- Carried from M2, still unverified on device: edge-flip while dragging, contacts search, Uninstall on a third-party app, Add/Remove home from menus, Suggested row, Media widget, `ROLE_HOME` dialog, photo import from settings, locked-profile behaviour, fresh-install layout. Rough edges: dock doesn't open a gap while dragging; locked-profile slots look empty; "Done" text contrast; `AppRepository.installed` collected twice. Perf after widgets: jank 1.09%, p90 8 ms.

## Session gotchas
- Bash: set `MSYS_NO_PATHCONV=1` before adb commands with device paths (`/data/...`, `/sdcard/...`), or Git Bash rewrites them.
- Long or multiple heredocs in one Bash call fail with "unexpected EOF"; write files with the Write tool instead.
- `adb` can drop when the cable reconnects; `adb kill-server`/`start-server`, then ask the user to re-allow USB debugging.
- Back up and restore Saber's `settings.preferences_pb` before on-device edits of its layout or settings (memory `device-layout-backup`).

## Next step
M3 step 4: `tools/dump-components.mjs` (adb `cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER` → components for packages in `packages.json` → merge into `design/icons/components.json`), `tools/build-iconpack.mjs` (adaptive icons with foreground glyph in the 108-unit canvas + `<monochrome>`, `res/xml/appfilter.xml` + `assets/appfilter.xml`, `res/xml/drawable.xml`), `:iconpack` app module (`com.sabertheme.iconpack`, icon-pack intent filters, small info activity) + an appfilter unit test. Verify: install `:iconpack`, start Lawnchair with `am start -n app.lawnchair/.LawnchairLauncher`, the user applies "Saber Icons" in Lawnchair settings, screenshot. Fallback if Lawnchair ignores vector/adaptive drawables: rasterise to PNG at build time.
