# Handoff: Milestone 1 — living-glass engine + home

**Goal:** Build M1 of the Saber-Theme launcher: Gradle scaffold, reactive liquid-glass `GlassSurface`, own wallpaper, home pager with app grid + dock, installable as default home on the S23.

## Decisions
- Launcher draws its own wallpaper (bundled Aurora Dawn/Night), so glass can blur and refract it.
- Glass reacts to touch, device tilt, motion physics and its backdrop (adaptive tint for ≥4.5:1 contrast).
- Battery: effects idle out after 3 s and turn off on Power Saving, thermal throttling or "Remove animations"; user intensity slider.
- Sensor and animation values are read only in draw or graphicsLayer lambdas, never in composition.
- minSdk 33 (AGSL), compile/target 36, applicationId `com.sabertheme.launcher`, Hilt, DataStore, version catalog, `build-logic` convention plugins.
- Engine spike (own AGSL shader vs Kyant `backdrop` lib) decides the glass implementation; the `GlassSurface` API stays fixed either way.
- Widgets: native Compose glass widgets plus Glance exports (no blur, more opaque fallback). Icons: built in plus a standalone ADW/Nova icon-pack APK. Widgets/drawer/settings are M2+.
- Glass token values = `GLASS_COLORS` / `GLASS_FLOATS` / `GLANCE_ALPHA` in the plugin. User approved the current look.
- Figma is on the Starter plan: MCP calls are capped; change designs via the local plugin, which the user runs in Figma desktop.

## Files
- `C:\Users\User\.claude\plans\read-the-claude-md-and-toasty-blossom.md`: approved M1 plan (modules, reactive architecture, steps, verification).
- `docs/architecture.md`: module graph, glass pipeline, widget/icon systems. Needs a "Living glass" section and the spike result in step 7.
- `.claude/rules/glass-rendering.md`: glass constraints (path-scoped). Extend in step 7.
- `.claude/rules/launcher-manifest.md`: HOME intent, `singleTask`, LauncherApps, DataStore rules.
- `design/figma-plugin/code.js`: Figma builder; also the current source of glyph SVG data (`APPS`, `UI`) and the glass tokens.
- `design/figma-plugin/manifest.json`, `README.md`: how the user runs the plugin.
- Figma file: https://www.figma.com/design/SN5PAzoJmPaZTz9UdzJ0uu (final per user).

## State
- M1 steps 1–7 done, committed and pushed to `origin/main`. The S23 holds the HOME role (`cmd role add-role-holder android.app.role.HOME com.sabertheme.launcher 0`).
- Engine: own AGSL shader (Kyant not benchmarked; see docs/architecture.md). Custom photo wallpapers added on request.
- Perf on the real home: p90 9 ms, jank 2.3% (target < 1%, open). GPU 3 ms; cost is per-node CPU re-record while paging plus page composition.
- Not yet tried on device: photo import through the system picker (user to verify).
- Known gap: locked work profile / Secure Folder apps drop out of the layout and return at the end.

## Next step
Either reduce paging jank below 1% (ideas: derive glass origin from page offset instead of per-node `onGloballyPositioned`; lighter tiles; prewarm page composition) or start M2 (drawer + search, widgets, edit mode, settings, Glance, icon-pack APK). Ask the user which.
