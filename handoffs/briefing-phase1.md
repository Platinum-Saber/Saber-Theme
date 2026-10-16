# Handoff: Saber's briefing (Phase 1)

**Goal:** Saber reads out what needs attention (Moodle deadlines, important Gmail, deliveries, today's events, missed calls) in a calm sage-like voice, all on the phone.

## Decisions
- **No model where data is structured.** Calendar, Moodle, the call log and Gmail capture are plain code.
- **The language model only labels mail.** Gemma 4 E2B on the GPU (LiteRT-LM 0.17.1) labels one mail per fresh conversation at temperature 0.25. One big "do everything" prompt failed in testing (memory `on-device-llm-benchmark`).
- **Wording comes from code.** `BriefingComposer` writes the sage style ("Notice. … End of report.", "Warning."). It uses module names, never codes, and sender names, never addresses. Deadlines more than 72 h away are left out.
- **Separate process.** All model work happens in `:brain` (`RemoteCoroutineWorker`). Home never loads either model.
- **Voice.** Kokoro `bf_emma` at 1.0× through sherpa-onnx v1.13.8 (JitPack, limited to its group). The owner picked it from an audition of 11 stock voices. No cloning; the Great Sage repo's F5-TTS clone of the voice actress was ruled out. Clips are made right after composing, and Samsung TTS speaks when the model is missing.
- **Mail source.** Gmail notifications only (owner's product decision); no Gmail sign-in.
- **Moodle sign-in.** Username and password go to `login/token.php`; only the token is kept. The site is the owner's university Moodle (online.uom.lk).
- **Speaking.** Saber speaks when the cloud is opened, and unasked only for new urgent items within `SpeakGuard`.
- **App is arm64-only** (`abiFilters`).

## State
**Verified on the S23 (2026-10-16):**
- **Background runs:** runs happen in `:brain` and the launcher keeps its own process.
- **Moodle:** sign-in works; 3 real deadlines and a 53-module index came through.
- **Missed calls:** read from the call log.
- **Mail labelling:** checked with 3 synthetic mails. The model loads in 13 s, takes 1–2 s per mail, and labelled all 3 correctly (Reply / Delivery with item and ETA / Urgent).
- **Voice:** Emma loads in 1.6 s and renders at about 1.2× real time; the owner approved how she sounds.
- **Cloud:** tapping it speaks with her mouth moving.
- **In-app model download:** both models were downloaded from Settings → Briefing over Wi-Fi with the adb copies moved aside. Kokoro (132 MB) was cancelled at 1 MB, resumed, verified and unpacked to 189 MB. Gemma (2.6 GB) took about 16 min and passed SHA-256. After that the brain loaded both from `no_backup/briefing-models` ("Voice loaded in 1883 ms"; "Model loaded in 13642 ms", 1 test mail labelled by the model). Delete, with its "Delete?" confirm, removed the voice, and a re-download restored it.

**On the device now:**
- Briefing on; Moodle connected; call log allowed.
- Models were downloaded in the app into `no_backup/briefing-models/` (`gemma-4-E2B-it.litertlm`, `kokoro-int8-multi-lang-v1_0/`). The old adb-pushed copies are still in `Android/data/com.sabertheme.launcher/files/{models,voices}.bak` (about 2.8 GB, unused; delete them once you're happy).
- Settings backup is in the session scratchpad, not the repo.

**Models:** download them from Settings → Briefing ("Voice" and "On-device model"). `ModelFiles` still falls back to an adb-pushed copy. If you push one anyway, keep in mind that adb leaves pushed files owned by `shell` (mode 660, folders 770) and the app gets `Permission denied`. After any push, run:
`adb shell "find /sdcard/Android/data/com.sabertheme.launcher/files -type d -exec chmod 777 {} + ; find /sdcard/Android/data/com.sabertheme.launcher/files -type f -exec chmod 666 {} +"`
(The top folder's own chmod fails; that's harmless.)

**Untested:**
- A real Gmail arriving end to end (only synthetic queued mail was tested).
- The unasked urgent announcement on the device.
- Quiet hours / DND / silent mode blocking speech.
- The hourly run firing on its own.
- Turning the briefing off, which should wipe the store and the clips.
- The perf check (Home should still draw 0 frames while she idles).

## Open items
1. **Download notification.** There is no `POST_NOTIFICATIONS`, so the dataSync progress notification shows only in the task manager. Ask for it if the owner wants it in the shade.
2. **Make the voice faster.** Rendering is about 1.2× real time on CPU. Try `numThreads = 6`, or sherpa-onnx's NNAPI/XNNPACK provider.
3. **Mail leaves the briefing only after 24 h.** It is not removed when it's read in Gmail. Remove it on `GmailNotifications.remove`.
4. **Upgrade LiteRT-LM** 0.17.1 → 0.18.x after an on-device check.
5. **Remaining Phase 2 ideas:** asking her questions (cloud model or a voice query), a seasonal outfit.

## Next step
Send yourself a real Gmail with "please reply by Friday". Within about 2 minutes, logcat `SaberBrain` should show "Labelled 1 mail … (model)", and the cloud should list it. Then run the perf check from `handoffs/icons-tint-recents.md`.
