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
