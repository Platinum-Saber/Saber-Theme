# Saber-Theme Builder (Figma plugin)

Builds the Saber-Theme design file: icon pack, components, widgets and
screens. Running it again is safe: it deletes only the nodes it created and
rebuilds them. It reuses the variables, text/effect styles and wallpapers
already in the file, and creates any that are missing.

## Run
1. Open the Figma **desktop** app (development plugins need it).
2. Open the "Saber-Theme" file.
3. Menu → Plugins → Development → **Import plugin from manifest…** →
   pick `design/figma-plugin/manifest.json`.
4. Plugins → Development → **Saber-Theme Builder**.
5. A toast reports `done ✓` or lists errors. Plugins → Development →
   Open console shows the full log.

## What it creates
| Page | Content |
|---|---|
| Foundations & Components | Components board: IconTile, AppIcon, Folder, Dock, SearchPill, PageIndicator, StatusBar, GestureBar, ContextMenu, WidgetFrame |
| Widgets & Icons | Icon pack board (keyline, 42 app glyphs, 21 UI glyphs), icon previews on light and dark wallpapers, Widgets board (9 widget sets × Theme × Render=Glass/Glance) |
| Screens | 8 screens × light/dark: Home·Widgets, Home·Apps, App drawer, Search, Widget picker, Edit mode, Folder open, Settings |

Starter-plan limits respected: 3 pages, 1 variable mode (light and dark are
variable groups, not modes).
