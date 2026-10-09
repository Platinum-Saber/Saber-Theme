---
paths:
  - "**/AndroidManifest.xml"
---
# Launcher manifest rules

- The home activity needs an intent filter with `MAIN` + `HOME` + `DEFAULT`
  categories, `launchMode="singleTask"`, and should survive config changes
  without recreating (handle orientation/density in Compose state).
- Prefer `<queries>` with a `MAIN`/`LAUNCHER` intent over
  `QUERY_ALL_PACKAGES`; the latter needs Play policy justification.
- Use `LauncherApps` (not raw `PackageManager`) for app lists, so work
  profile and Samsung Secure Folder apps show up correctly.
- One UI may kill or reset third-party launchers; don't rely on the
  launcher process staying alive for state — persist with DataStore.
- `windowSoftInputMode="adjustResize"` with edge-to-edge: Compose gets IME
  insets and the drawer search uses `imePadding()`; `adjustPan` would pan
  the whole window over the keyboard.
- Permissions are declared in the module that uses them (manifests merge),
  each with a comment saying which feature needs it:
  - `:app`: `VIBRATE`, `REQUEST_DELETE_PACKAGES` (Uninstall in the app menu).
  - `:core:widgetdata`: `INTERNET` + `ACCESS_COARSE_LOCATION` (weather,
    coarse only), `READ_CALENDAR`.
  - `:feature:drawer`: `READ_CONTACTS` (search; asked from an inline row).
  - `:feature:widgets` + `:widgets-glance`: `SET_ALARM` (normal; One UI's
    `SHOW_ALARMS` handler demands it, or Clock/Alarm taps throw
    `SecurityException`).
  Runtime permissions are only requested from a visible "Allow" control,
  never at startup.
- `MediaListenerService` (`:core:widgetdata`) is a `NotificationListenerService`
  that reads nothing; being enabled is what lets `MediaSessionManager`
  return sessions. Keep it `exported="true"` with
  `BIND_NOTIFICATION_LISTENER_SERVICE`, and never add notification reading
  without a product decision.
- Exported widgets (`:widgets-glance`): one `GlanceAppWidgetReceiver` per
  widget (`exported="true"`, `APPWIDGET_UPDATE`, provider XML with
  `initialLayout="@layout/glance_default_loading_layout"`), plus
  `WidgetUpdateReceiver` (`exported="false"`) for exempt implicit
  broadcasts only (TIME_SET, TIMEZONE_CHANGED, LOCALE_CHANGED,
  NEXT_ALARM_CLOCK_CHANGED); it checks the action before acting.
  `GlancePermissionActivity` is translucent and `exported="false"`.
  `WidgetRefreshWorker` is WorkManager (no manifest entry). Tap targets
  without data (`SHOW_ALARMS`, `POWER_USAGE_SUMMARY`) need `<queries>`
  entries because `openAction()` resolves them to explicit intents.
- Icon pack (`:iconpack`, separate APK): `InfoActivity` is `exported="true"`
  with MAIN/LAUNCHER plus one `DEFAULT` filter listing the theme actions
  launchers query (`org.adw.launcher.THEMES`, `com.novalauncher.THEME`,
  `com.teslacoilsw.launcher.THEME`, `com.anddoes.launcher.THEME`,
  `com.gau.go.launcherex.theme`, `com.fede.launcher.THEME_ICONPACK`). No
  permissions.
