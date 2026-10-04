# Play Store listing (draft)

**App name:** TaskBarStatsMobile (max 30 chars). Package name: `com.ericbruggema.taskbarstatsmobile` (permanent once uploaded).

**Short description (max 80):**
Live memory, network, storage and ping in your status bar, widget and lock screen.

**Full description:**
TaskBarStatsMobile shows what your phone is doing, at a glance: memory, network speed, storage, ping and temperature.

- Tiny figures in the status bar, next to the clock and system icons
- Home-screen widget with its own cells and opacity per widget
- Quick Settings tile and an ongoing notification that also shows on the lock screen (with a Stop button)
- Dashboard and an immersive fullscreen cockpit
- Hide and reorder tiles; 14 colour themes, plus import of Windows TaskbarStats themes
- Data use, traffic and storage per app, most used apps, autostart list, history and threshold alerts
- First-start setup: choose what to show (download, upload, memory, CPU, storage, ping, temperature) and where; one status bar icon per item with its value drawn in it, and/or a text strip
- Cache per app with one-tap clear-all, plus tap-through details and actions for every running process (open, force stop, end, command line)
- Optional CPU % and process list through Shizuku
- English, Dutch and German; no ads, no tracking, no account

The battery is left out on purpose: Android already shows it.

**Category:** Tools. **Contact / source:** https://github.com/ericbruggema/TaskBarStatsMobile (MIT)

## Data safety / policy notes
- No data is collected or shared. The only network use is a TCP connect to 1.1.1.1:443 for the ping figure.
- Permissions: *Display over other apps* (status-bar figures), foreground service of type `specialUse` (live monitor), notifications. Declare the `specialUse` subtype in Play Console: "Live system statistics in a notification and status-bar overlay".
- `PACKAGE_USAGE_STATS` (usage access) and `QUERY_ALL_PACKAGES` need a declaration in Play Console and may be refused; if so, remove the Apps tab sections that need them (the manifest and `AppData.kt` are the only places).
- Not yet made: feature graphic (1024x500), 512x512 icon PNG (the app icon is a vector), privacy-policy URL (required even without data collection). Screenshots: `docs/screenshots/android/`.
- Upload `app-release.aab` and enrol in Play App Signing; the local keystore then becomes the upload key.

**Privacy policy URL:** https://github.com/ericbruggema/TaskBarStatsMobile/blob/main/PRIVACY.md (available once `PRIVACY.md` is pushed to `main`)
