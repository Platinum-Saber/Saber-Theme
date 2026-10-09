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
- `feature/widgets/src/main/java/com/sabertheme/feature/widgets/` — sources, `WidgetState`, `WidgetViews.kt` (per-size layouts to mirror in Glance), `WidgetCatalog`.
- `design/icons/glyphs.js`, `design/icons/packages.json`, `tools/build-icons.mjs` — icon source and generator.
- `design/figma-plugin/src/code.js` — `WIDGETS` table and `Render=Glance` styling (`GLANCE_ALPHA`).
- `.claude/rules/launcher-manifest.md`, `.claude/rules/glass-rendering.md` — manifest/permission and glass rules.

## State
- `main` = `origin/main`, all M2 steps committed. Debug build on the S23 (holds HOME). The user's layout and settings were restored after on-device tests (memory `device-layout-backup` has the procedure).
- Perf after widgets: jank 1.09%, p90 8 ms, p99 15 ms (benchmark build, 12 swipes). Reported only.
- Not yet verified on device: edge-flip while dragging, contacts search (no `READ_CONTACTS`), Uninstall on a third-party app, Add/Remove home from menus, Suggested row, Media widget, `ROLE_HOME` request dialog, photo import from the settings page, locked-profile behaviour, fresh-install default layout.
- Known rough edges: dock items don't shift to open a gap while dragging (outline only); locked-profile slots look empty and refuse drops; white "Done" text on the light accent pill is low contrast; `AppRepository.installed` is a cold flow collected separately by home and drawer (two LauncherApps callbacks).

## Next step
M3 step 1: extract `:core:widgetdata` from `:feature:widgets` (sources, state, permissions, OpenMeteo, WidgetFormat, WidgetSources, MediaListenerService + manifest entries, tests), add `WidgetDataSource.snapshot()`. No behaviour change; build, test, lint, check home widgets on the S23, commit, push.
