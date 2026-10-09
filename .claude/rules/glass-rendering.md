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
- Sensors run only while `EffectsPolicy` is Active (touched within 3 s,
  resumed, no Power Saving, no thermal throttling, animations on). Check
  with `adb shell dumpsys sensorservice` (Previous Registrations).
- Every state change animates with a `GlassMotion` spring; pass
  `env.reducedMotion` so "Remove animations" snaps.
