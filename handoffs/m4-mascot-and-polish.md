# Handoff: M4 — mascot, polish and remaining work

**Goal:** M3 and M4 (Saber mascot) are done; continue with the open list below (start: calendar refresh for Glance + dock gap while dragging).

## Decisions
- Mascot: an original vector rig drawn in code (`:feature:mascot`). It always animates on Home (user choice). It draws on a fixed full-window, z-on-top `SurfaceView` behind the Compose view, never in Compose: a Compose redraw re-runs every glass shader (8 ms GPU), and a moving SurfaceView flickers. 101 dp tall. Outfits: Armour / Winter / Casual.
- Media widget: Spotify / VLC switcher. Resume order: MediaBrowser → MEDIA_BUTTON → open app. Starting one app pauses the others explicitly, because AudioHardening ignores focus requests from background-started apps. VLC artwork comes from the MediaStore thumbnail by title (its ArtworkProvider only serves system callers); this needs READ_MEDIA_VIDEO "Allow all".
- Tilt keeps running at SENSOR_DELAY_UI when idle, with a deadband, so a still phone renders 0 frames.
- Photo wallpapers: framing sidecar (`PhotoFraming`, zoom relative to fill; below fill = blurred copy behind). No sidecar = centred fill.
- Double-tap lock: accessibility service with only GLOBAL_ACTION_LOCK_SCREEN (keeps biometrics), not device admin.
- Process: commit + push per step (`Co-Authored-By: Claude Opus 5.5`); test on the S23; back up `settings.preferences_pb` before on-device setting/layout tests (memory `device-layout-backup`); plan mode for >3 files.

## Files
- `docs/plans/m4-mascot.md`: the M4 plan. `docs/architecture.md`: Mascot, Exported widgets, wallpaper framing and double-tap sections.
- `feature/mascot/.../`: `SaberRig.kt` (drawing), `Pose.kt` (20 expressions), `MascotBrain.kt`/`MascotPhysics.kt` (pure + tests), `MascotLayer.kt` (frame loop, gestures), `MascotSurface.kt`.
- `core/widgetdata/.../`: `MediaSource.kt`, `MediaPicker.kt`, `MediaResumer.kt`, `MediaArt.kt` (+ LetterboxTrim).
- `feature/settings/.../WallpaperEditor.kt`, `core/model/.../PhotoFraming.kt`, `core/data/.../WallpaperStore.kt`.
- `app/.../LockScreenService.kt`, `LockPrompt.kt`, `core/data/.../ScreenLock.kt`.
- `tools/build-app-icon.mjs` + `design/logo/*.svg` (app icon); `iconpack/` + `tools/build-iconpack.mjs`, `tools/dump-components.mjs`.
- `.claude/rules/glass-rendering.md` (mascot surface exception), `.claude/rules/launcher-manifest.md` (permissions, services).

## State
- Works and is verified on the S23: page reorder, Clock/Alarm tap (SET_ALARM fix), Media switcher + resume + VLC thumbnails, wallpaper editor (fit/fill/pan, cancel cleanup), mascot (poke/long-press/drag/throw, moments, sleep, dance, tilt, outfits, settings), double-tap lock. Perf: 12 swipes 1.14% jank, p90 6 ms. Home window draws 0 frames while the mascot idles.
- On device: debug build; Saber is the default home; user layout (page 2 has Media widget + reordered pages); wallpaper = photo d2b28961 with no framing; mascot Winter; lock service on; Saber images are in `Pictures/Wallpapers/Saber`.
- Untested: double-tap on an icon does NOT lock (the phone was locked during the check); Glance non-default sizes and the worker firing naturally; contacts search with the grant; locked-profile slots; Media 4x1 title-cycle on a live widget.

## Open items (priority order)
1. ~~Calendar `PROVIDER_CHANGED` → Glance calendar refresh~~: done (`CalendarChangeReceiver`); confirm with a real event edit.
2. Dock doesn't open a gap while dragging; locked-profile slots look empty; "Done" text contrast; `AppRepository.installed` collected twice.
3. Glance: battery only refreshes via the worker; widgets stale after a permission change until the process restarts; no Glance Media widget.
4. Mascot: sinks into the bar when sitting/sleeping; ideas — seasonal outfit, time-of-day/charging reactions.
5. Icon pack: dark tile background blends into Lawnchair's drawer; Chrome → compass mapping; the pack's own icon isn't the logo yet.
6. Lint: SelectedPhotoAccess (limited photo access won't find VLC videos) — accepted for now.

## Next step
Add `CalendarContract.ACTION_PROVIDER_CHANGED` (data scheme `content`, host `com.android.calendar`) to `WidgetUpdateReceiver` and refresh the calendar Glance widget; verify on the S23 by adding an event.
