# Security

Saber is a home-screen launcher. It runs all the time, sees what's on Home,
and holds some sensitive permissions: notification listener, accessibility
(lock only), contacts, calendar, call log, coarse location and media. This file covers
what it protects, how, and how to re-check it. Audit: 2026-10-10.

## What could go wrong
- **Data leaving the phone** through backups, phone-to-phone transfer, the
  network, logs, or the screen (recordings and screen shares).
- **Other apps** driving Saber through exported components, or feeding it
  hostile input. That input is media sessions, artwork providers,
  notifications and broadcasts.
- **Memory and cache misuse**: unbounded reads or caches, leaked streams,
  receivers or activities. Home restarts into the same state, so a crash can
  repeat in a loop.

## What's in place
| Area | Protection |
|---|---|
| Backups | `data_extraction_rules.xml` lets backups and phone-to-phone transfer carry only `datastore/settings.preferences_pb`: the layout and settings. Wallpaper photos, the weather cache (holds the coarse location), launch counts (`usage` store, `@UsageStore`) and widget state stay on the device. |
| Screen | `HomeActivity` registers a screen-recording callback (API 35+, `DETECT_SCREEN_RECORDING`). While Home is recorded or shared, the mascot gets no messages, so the WhatsApp cloud, the briefing rows and their previews hide, and she never speaks unasked. |
| Briefing data | Off by default. Gmail sender, subject and preview wait in `noBackupFilesDir/briefing_state.json` until labelled; then only sender, subject and label stay, for at most 48 h. The Moodle token is in `noBackupFilesDir/briefing_secrets.json`; the password is sent once over HTTPS POST and never stored or kept across configuration changes. Rendered speech is in `noBackupFilesDir/briefing_audio`. Turning the briefing off deletes all of it. Mail is labelled on the phone; nothing but the Moodle requests leaves it. |
| Speaking aloud | She reads the briefing when the cloud is opened. Unasked, only new urgent items, once each, and only on a visible Home with the ringer on, no Do Not Disturb, no music, no screen recording, between 07:00 and 22:00 (`SpeakGuard`). |
| Notifications | `MediaListenerService` reads only WhatsApp / WhatsApp Business and Gmail notifications (Gmail for the briefing, owner's decision 2026-10-16). The package comes from the system, so other apps can't pose as WhatsApp. Text lives in memory only. The cloud drops its cached preview text when the list changes; a chat leaves when its notification goes. |
| Network | HTTPS only, system CAs only (`network_security_config.xml`). Location goes to Open-Meteo rounded to 2 decimals (~1 km). Moodle gets the user's token over HTTPS POST (`http://` sites are refused). Responses are capped at 512 KB. |
| Exported components | HOME activity (it acts only on `CATEGORY_HOME`). Accessibility and notification listener services, guarded by system bind permissions. Glance widget receivers. `CalendarChangeReceiver`, which only redraws the calendar widget and collapses bursts into one redraw a second later. Everything else is `exported="false"`. |
| Accessibility | `LockScreenService` requests no event types and can't read window content; it only performs `GLOBAL_ACTION_LOCK_SCREEN`. |
| Untrusted input | Artwork only from `content://` URIs (never `file://`), at most 12 MB, decoded downscaled. Wallpaper files are opened only by names Saber made (`UUID.webp`), so a tampered setting like `../x` reads as missing. Imports are re-encoded, which strips EXIF/GPS. App icons for the colour tint are loaded from each app's own resources (as LauncherApps does), inside `runCatching`, drawn at 32 px off the main thread; only the resulting colour is kept. |
| Caches | Icon and icon-colour caches pruned to installed apps. Artwork cache 8 entries, failed-artwork set 64, glyph cache LRU by bytes, launch counts capped at 64 apps. |
| Logging | Warnings only, with package names at most; never message text, locations or contacts. The briefing logs counts and timings (`SaberBrain`), plus engine error messages and stack traces, which name files and code, never mail. |
| Build | Release builds are minified and not debuggable. The benchmark build is `profileable` for shell only. Security lint checks fail the build (`SECURITY_LINT_ERRORS` in `build-logic/.../ProjectExtensions.kt`). Debug builds run StrictMode (see below). |

## Re-running the checks
Each command is run from the repo root, piped through the trim hook as in
`CLAUDE.md`.
- **Unit tests** (`./gradlew testDebugUnitTest`):
  - `LimitsTest`: capped reads and the coalescer.
  - `WallpaperStoreNameTest`: path traversal.
  - `LaunchCountsMigrationTest`: launch counts leave the backed-up store.
- **Lint**: `./gradlew lintDebug` must pass. As a sanity check, a hard-coded
  `android:debuggable` must fail it with `HardcodedDebugMode`.
- **Exported surface** (adb, S23). Both probes must be denied with
  `SecurityException`:
  - `am start -n com.sabertheme.launcher/com.sabertheme.widgets.glance.GlancePermissionActivity` (not exported).
  - `am startservice -n com.sabertheme.launcher/com.sabertheme.core.widgetdata.MediaListenerService` (requires `BIND_NOTIFICATION_LISTENER_SERVICE`).
- **Broadcast spam**: send 200x `PROVIDER_CHANGED` for
  `content://com.android.calendar` and 200x `APPWIDGET_UPDATE` to a Glance
  receiver. The process must stay up (same pid), with PSS not growing.
- **Leaks**: in a debug build, exercise Home, drawer, search, settings,
  widgets and the cloud.
  1. Force a GC with `adb shell run-as com.sabertheme.launcher kill -10 <pid>`.
  2. `adb logcat -d --pid=<pid> | grep StrictMode` must be empty.
  3. `dumpsys meminfo` must show 1 activity and 1 view root, and heap flat
     across repeated cycles.
- **Screen recording**: start the Quick Settings screen recorder on Home;
  the message cloud must disappear and return afterwards. Note that
  `adb shell screenrecord` doesn't count as a recording to Android.
  `dumpsys window` (ScreenRecordingCallbackController) shows the callback.
- **Backups**: a real cloud backup uploads to the owner's account, so it's
  done by hand. The rules are validated by lint (`DataExtractionRules`).

## Known and accepted
- Exported Glance receivers accept `APPWIDGET_UPDATE` from any app. The
  worst case is a redraw, as with any widget.
- Message previews show on Home to whoever is looking at the phone, as the
  owner chose. Turn them off with Settings → Mascot → Message cloud.
- The weather API learns a ~1 km location. It's needed for the forecast.
- Moodle sees when the phone asks for deadlines (hourly while the briefing is on), as the official Moodle app would.
- The briefing speaks names and subjects aloud when the cloud is tapped, to whoever is nearby; unasked speech is limited by `SpeakGuard`.
- `SelectedPhotoAccess` lint warning: with limited photo access, VLC artwork
  can't be found (accepted earlier; it only affects thumbnails).
