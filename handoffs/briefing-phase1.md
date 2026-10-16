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

**On the device now:**
- Briefing on; Moodle connected; call log allowed.
- Models are in `Android/data/com.sabertheme.launcher/files/{models/gemma-4-E2B-it.litertlm, voices/kokoro-int8-multi-lang-v1_0/}`.
- Settings backup is in the session scratchpad, not the repo.

**Gotcha:** adb leaves pushed files owned by `shell` (mode 660, folders 770), and the app gets `Permission denied`. After any push, run:
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
1. **In-app download of both models.** This would remove the adb push and chmod step. Gemma is 2.6 GB from HF `litert-community/gemma-4-E2B-it-litert-lm`; Kokoro is 132 MB from the sherpa-onnx `tts-models` release.
2. **Make the voice faster.** Rendering is about 1.2× real time on CPU. Try `numThreads = 6`, or sherpa-onnx's NNAPI/XNNPACK provider.
3. **Mail leaves the briefing only after 24 h.** It is not removed when it's read in Gmail. Remove it on `GmailNotifications.remove`.
4. **Upgrade LiteRT-LM** 0.17.1 → 0.18.x after an on-device check.
5. **Remaining Phase 2 ideas:** asking her questions (cloud model or a voice query), a seasonal outfit.

## Next step
Send yourself a real Gmail with "please reply by Friday". Within about 2 minutes, logcat `SaberBrain` should show "Labelled 1 mail … (model)", and the cloud should list it. Then run the perf check from `handoffs/icons-tint-recents.md`.
