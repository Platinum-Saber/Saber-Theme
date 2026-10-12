# Handoff: unique icons, colour tint, Recents theming

**Goal:** owner-reported fixes after 1.0.0: one glyph per brand, a 0-50% colour tint on glass icon tiles, and Recents matching the theme.

## Decisions
- One glyph per brand; a `packages.json` key lists several packages only for variants of one app. `IconMapperTest.VARIANTS` enforces it. `compass` was renamed to `chrome`.
- Tint: `DominantColor` (core/model, pure) runs on a 32 px render of the real icon and is cached in `AppIconResolver`. It feeds the `accent` uniform in `GLASS_AGSL`, which is always written because shader programs are pooled. Glass tile only; the row is hidden for Bare.
- Recents is One UI's, so Saber can't draw it. Themed through Good Lock → Theme Park → Icon → Iconpack → Saber Icons; the docs-only route was chosen. The suggestion row is One UI's "Show recommended apps" switch, left on.

## State
- Verified on the S23:
  - Drawer glyphs are distinct, and WhatsApp shows the full handset.
  - Tint at 50% reads well. The user's settings were restored to 0% afterwards.
  - The Theme Park "SaberIcons" theme is applied; the Settings card in Recents shows the Saber glyph.
- Not done:
  - Lawnchair check of the new pack.
  - `installBenchmark` perf run for the accent uniform.
  - A matching tile background in the icon pack, so Theme Park's icons look like the glass tiles.
- After reinstalling, Settings showed "Needs Saber's accessibility service" for double-tap lock. The owner must turn it back on (the handoff before this says it was on).

## Next step
Run the benchmark (12 swipes, tint 0 and 50%), then pick from `handoffs/m4-mascot-and-polish.md` (dock gap while dragging).
