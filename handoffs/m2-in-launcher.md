# Handoff: Milestone 2 — in-launcher experience

**Goal:** Build M2 per `docs/plans/m2-in-launcher.md`: paging perf fix, layout v2 (curated home + widgets), `:core:ui`, native glass widgets, drawer + universal search, edit mode, settings.

## Decisions
- M2 = drawer/search, native widgets, edit mode, settings. Glance widgets + icon-pack APK are M3.
- Curated home: page 1 widgets, page 2 chosen apps; all apps live in the drawer; new installs go to the drawer only. M1 layouts migrate (codec v1 → v2).
- Search covers apps, contacts (READ_CONTACTS on first use), settings shortcuts, web.
- Perf first: paging jank must be < 1% (now 2.3%, p90 9 ms, GPU 3 ms) before adding widgets.
- Features never depend on each other; `:app` wires them with composable slots. Shared UI goes to a new `:core:ui`.
- Engine is our own AGSL shader (Kyant not used). Glass rules: `.claude/rules/glass-rendering.md`.
- Commit and push after every step (user wants pushes). Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Files
- `docs/plans/m2-in-launcher.md` — approved M2 plan (steps 1–8, verification).
- `docs/architecture.md` — architecture, living glass, perf numbers.
- `core/designsystem/.../glass/GlassModifier.kt` — glass `Modifier.Node` (position tracking, uniforms, adaptive tint); step 1 target.
- `core/designsystem/.../glass/GlassSurface.kt` — public glass API (press, shadow, background); split layers here in step 1.
- `feature/home/.../HomeScreen.kt` — pager, grid, dock, indicator, overlays, page velocity/parallax.
- `feature/home/.../HomeIcons.kt`, `GlassMenu.kt`, `FolderOverlay.kt` — move icons/menu to `:core:ui` in step 3.
- `feature/home/.../HomeViewModel.kt` — home state, icon resolution, wallpapers, intensity.
- `core/model/.../HomeLayout*.kt` — layout model, policy, codec v1 (to become v2).
- `core/data/` — `AppRepository` (LauncherApps), `LayoutRepository`, `HomeRepository`, `SettingsRepository`, `WallpaperStore`.
- `app/.../HomeActivity.kt` — root: environment, effects controller, theme, home, options sheet, Glass Lab.

## State
- M1 complete and pushed (`main` = `origin/main`, last commit 9f83c1b). Debug build installed on the S23; it holds the HOME role.
- Benchmark build type (`installBenchmark`) exists; Glass Lab shows in debug and benchmark builds.
- Untested by the user: photo-wallpaper import via the system picker.
- Known gap (fixed in M2 step 2): locked work profile / Secure Folder apps drop out of the layout.

## Next step
M2 step 1: install the benchmark build, capture a Perfetto trace during 12 page swipes (`adb shell perfetto … gfx view sched`), find the per-frame cost. Then split `GlassSurface` so the glass background and content get separate layers, compute the origin in draw from cached coordinates, and re-measure with `dumpsys gfxinfo` (target p90 ≤ 8 ms, jank < 1%). Commands and JAVA_HOME are in `CLAUDE.md`.
