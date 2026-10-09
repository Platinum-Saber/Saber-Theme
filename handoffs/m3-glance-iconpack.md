# Handoff: Milestone 3 — Glance widgets + icon-pack APK

**Goal:** Build M3 per `docs/plans/m3-glance-iconpack.md`; **complete** (steps 1–5). No M4 plan exists yet; start from "Open items for M4" below.

## Decisions
- User choices: Glance = Clock, Weather, Calendar, Battery, Next alarm (no Media); icon-pack components dumped from the S23 into `design/icons/components.json`; adaptive icons (opaque `#1A1C22` background + glyph foreground + monochrome layer); icon pack verified in **Lawnchair 15 Beta 3** (Nova dropped, likely unmaintained).
- Glance cannot blur: translucent fill + 1 dp border (`.claude/rules/glass-rendering.md`).
- The widget data layer lives in `:core:widgetdata`, shared by `:feature:widgets` (in-launcher) and `:widgets-glance`.
- Icons: `design/icons/glyphs.js` is the single glyph source. `tools/build-icons.mjs` (launcher) and `tools/build-iconpack.mjs` (pack) both reuse `tools/glyph-source.mjs`. `tools/dump-components.mjs` only adds to `components.json`. Never edit generated files.
- Process: commit and push after every step with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; test on the real S23 over adb; Saber stays the default home (other launchers are started explicitly with `am start`). Perf work stays closed.

## Files
- `docs/architecture.md`: modules, "Exported widgets (Glance)", "Icon system" (icon pack).
- `.claude/rules/launcher-manifest.md`: Glance receivers/activity/queries, icon-pack intent filters.
- `widgets-glance/.../glance/`: `GlanceSupport.kt`, one file per widget, `WidgetRefreshWorker.kt` (+ `SaberGlanceWidgets`), `GlancePermissionActivity`, `WidgetUpdateReceiver`.
- `iconpack/`: `build.gradle.kts`, manifest, `InfoActivity.kt`, hand-written `values/`, `mipmap-anydpi/ic_launcher.xml`, `raw/keep.xml`; everything else is generated. `AppFilterTest` checks appfilter/drawable.xml.
- `design/icons/components.json` (57 components, 39 glyphs from the S23).

## State
- `main` = `origin/main` after the step 5 commit. Debug launcher + `:iconpack` on the S23 (Saber holds HOME). Lawnchair 15 Beta 3 (`app.lawnchair`) installed with "Saber Icons" applied.
- Steps 1–3: see git history (`:core:widgetdata`, Glance foundation + Clock, the other four widgets). Verified in One UI Home at default sizes.
- Step 4: icon pack verified in Lawnchair (home, folder, dock, drawer): mapped apps themed with Lawnchair's shape mask, unmapped apps keep their own icons; vector adaptive icons work (no PNG fallback). `aapt2 dump` shows appfilter + 126 `drawable/ic_*` entries.
- Full `assembleDebug testDebugUnitTest lintDebug` green. Lint warnings are pre-existing kinds only (`DataExtractionRules` in `:app` and `:iconpack`, `UseKtx`, `CheckResult` on `setWidgetPreviews`, typos in the Google Fonts certs array, one newer serialization version).

## Open items for M4
- Glance: verify non-default sizes (Weather 4x1/4x2, Calendar 4x2, Battery/Alarm 2x2) and the worker firing naturally; battery only updates on the worker/app start/redraw; after a permission change widgets keep their last drawing until Saber's process restarts; calendar `PROVIDER_CHANGED` isn't wired into `WidgetUpdateReceiver` (plan listed it); Media Glance widget (needs session pushes).
- Icon pack: icon background matches Lawnchair's dark drawer (low separation; one colour in `iconpack/src/main/res/values/colors.xml`); `packages.json` maps Chrome → compass and Brave → browser (check intent); `ideas`/`podcasts` glyphs have no components on the S23; no calendar date-dynamic icon; re-run `dump-components.mjs` after installing new apps.
- Carried from M2, still unverified on device: edge-flip while dragging, contacts search, Uninstall on a third-party app, Add/Remove home from menus, Suggested row, Media widget, `ROLE_HOME` dialog, photo import from settings, locked-profile behaviour, fresh-install layout. Rough edges: dock doesn't open a gap while dragging; locked-profile slots look empty; "Done" text contrast; `AppRepository.installed` collected twice. Perf after widgets: jank 1.09%, p90 8 ms.

## Session gotchas
- `adb` isn't on the Bash PATH: use `/c/Users/User/AppData/Local/Android/Sdk/platform-tools/adb.exe` (`dump-components.mjs` finds it via `LOCALAPPDATA`). Set `MSYS_NO_PATHCONV=1` before adb commands with device paths.
- Long or multiple heredocs in one Bash call fail with "unexpected EOF"; write files with the Write tool instead.
- `adb` can drop when the cable reconnects; `adb kill-server`/`start-server`, then ask the user to re-allow USB debugging.
- Back up and restore Saber's `settings.preferences_pb` before on-device edits of its layout or settings (memory `device-layout-backup`).
- One UI has no per-widget Remove while it isn't the default home: test Glance widgets on a fresh page and delete the page afterwards.
