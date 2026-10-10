# Saber-Theme

Custom Android home-screen launcher, Kotlin + Jetpack Compose. Primary
target: the owner's Galaxy S23 (SM-S911B, Android 16 / API 36, One UI 8.5). Budget: keep this file
under ~500 tokens; conditional guidance goes in `.claude/rules/`.

## Output
- No preamble, no recap. Lead with the answer or the diff.
- Plan mode for anything touching more than ~3 files; `/handoff` then
  `/clear` at the boundary. Broad sweeps go to the `explorer` agent.

## Architecture
See `docs/architecture.md` (modules, glass pipeline, widgets, icons).

## Commands
Run from the repo root. Always pipe through the trim hook.
- JDK: `JAVA_HOME=D:\Installations\Android\App\AndroidStudio\jbr` (Studio's bundled JDK 21; not set globally).
- Build: `./gradlew assembleDebug 2>&1 | python C:/Users/User/.claude/hooks/trim_output.py`
- Install on phone: `./gradlew installDebug 2>&1 | python C:/Users/User/.claude/hooks/trim_output.py`
- Unit tests: `./gradlew testDebugUnitTest 2>&1 | python C:/Users/User/.claude/hooks/trim_output.py`
- Lint: `./gradlew lintDebug 2>&1 | python C:/Users/User/.claude/hooks/trim_output.py`
- Perf: `./gradlew installBenchmark` (R8, debug-signed), then `adb shell dumpsys gfxinfo com.sabertheme.launcher`.
- Glyphs/plugin: `node tools/build-icons.mjs`, `node tools/build-plugin.mjs` (edit `design/icons/`, never generated files).
- Icon pack: `node tools/dump-components.mjs` (adb), `node tools/build-iconpack.mjs`, `./gradlew :iconpack:installDebug`.
- App icon: `node tools/build-app-icon.mjs` (edit `design/logo/*.svg`).
- Device: `adb devices -l`; logs: `adb logcat -d --pid=$(adb shell pidof -s <appId>) | tail -200` (never stream unbounded logcat).

## House rules
- Test on the real S23 over ADB; don't assume emulator behaviour matches One UI.
- Never commit or read signing material (`*.jks`, `*.keystore`, `keystore.properties`) or `local.properties`.
- Use Gradle version catalogs (`gradle/libs.versions.toml`) for every dependency.
