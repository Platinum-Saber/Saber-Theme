---
paths:
  - "core/designsystem/**"
  - "widgets-glance/**"
---
# Glass rendering rules

- Glass samples a **cached pre-blurred wallpaper**; never blur the full
  screen live per frame.
- AGSL `RuntimeShader` is fine (minSdk ≥ 33 on target); keep shader work
  bounded to each `GlassSurface`'s own bounds.
- All glass parameters come from material tokens (`blurRadius`, `tint`,
  `tintAlpha`, `refraction`, `highlight`, `borderAlpha`); no magic numbers
  in feature code.
- Measure on the real S23: 120 Hz target, frame under 8 ms. Check with
  `adb shell dumpsys gfxinfo <appId>` before and after shader changes.
- Text on glass must keep ≥ 4.5:1 contrast on both light and dark
  wallpapers; raise `tintAlpha` rather than adding shadows.
- Glance (RemoteViews) cannot blur: map tokens to a translucent tint +
  1 px border instead.
- **Draw-phase only:** tilt, parallax, press and intensity are snapshot
  state in `GlassEnvironment` / `GlassPressState`; read them only in draw,
  `graphicsLayer` or `Modifier.Node` draw code, never in composition.
- Tilt runs at `SENSOR_DELAY_GAME` whenever Home is visible (Active and
  Idle; no slower idle rate, switching rates on a touch made Home stutter);
  off when paused, Power Saving, thermal throttling or animations off. A still phone must render
  0 frames (deadband in `TiltFilter`; check `dumpsys gfxinfo` over 10 s).
  Registrations: `adb shell dumpsys sensorservice` (Previous Registrations).
- Exception: the mascot animates continuously, but only on her own
  full-window `SurfaceView` (`MascotSurface`), which never moves (moving a
  SurfaceView desyncs position and content). Never draw continuous animation in the
  Compose tree on Home: any invalidation re-runs every glass shader (~8 ms
  GPU per frame). Check the Home window's own frames with
  `dumpsys gfxinfo <appId> framestats` (PROFILEDATA rows), not the total.
- Every state change animates with a `GlassMotion` spring; pass
  `env.reducedMotion` so "Remove animations" snaps.
