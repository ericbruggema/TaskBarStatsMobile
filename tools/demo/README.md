# Demo video toolkit (Android)

How `docs/tour/promo/android-*-en.mp4` and the gif were made. Not part of the app; paths assume Windows and `%TEMP%\demo`.

1. Emulator `tsphone` running, debug app installed, Shizuku granted, usage access / overlay allowed (`adb shell appops set ...`).
2. `traffic.py start` generates lively fake traffic on the emulator (`nc` against a speed-test host) so the numbers move; `traffic.py stop` ends it.
3. `scenes.py <scene> ...` records one scene each (needs a debuggable build: `write_prefs` uses `run-as`; scenes `gallery` and `datatiles` cover the widget gallery and the data tiles; `history_alerts` waits 4 minutes so the history graphs fill) with `adb shell screenrecord` while driving the UI with `adb input` (touch dots on). `setup` must be recorded last (it resets the preferences).
4. `compose.py short|full` builds the 1080x1920 videos with ffmpeg: phone frame, captions (PIL), a magnifier on the status bar, intro/outro cards. Output in `%TEMP%\demo\out`.

Captions are English only (project rule). The emulator has no personal data. Tips: keep the traffic generator off while recording the process list (it shows up in it), `locksettings set-disabled false` makes the lock screen show, `wm dismiss-keyguard` unlocks again, and a sleeping display makes `screenrecord` fail with INVALID_LAYER_STACK.
