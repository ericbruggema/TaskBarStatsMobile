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
- Not yet made: feature graphic (1024x500), 512x512 icon PNG (the app icon is a vector), Screenshots: `docs/screenshots/`.
- Upload `app-release.aab` and enrol in Play App Signing; the local keystore then becomes the upload key.

**Privacy policy URL:** https://github.com/ericbruggema/TaskBarStatsMobile/blob/main/PRIVACY.md

## Dutch listing (Nederlands)

**Korte beschrijving (max 80):**
Live geheugen, netwerk, opslag en ping in je statusbalk, widget en vergrendelscherm.

**Volledige beschrijving:**
TaskBarStatsMobile laat in één oogopslag zien wat je telefoon doet: geheugen, netwerksnelheid, opslag, ping en temperatuur.

- Kleine cijfers in de statusbalk, naast de klok en de systeemiconen
- Widgets op het beginscherm in zes formaten, elk met eigen cellen, grafieken en dekking
- Snelle-instellingen-tegel en een doorlopende melding die ook op het vergrendelscherm staat (met Stop-knop)
- Dashboard en een immersieve fullscreen cockpit
- Tegels verbergen en herschikken; 14 kleurthema's, plus import van Windows-TaskbarStats-thema's
- Dataverbruik (wifi en mobiel), verkeer en opslag per app, meest gebruikte apps, autostartlijst, geschiedenis en drempelmeldingen
- Installatie bij de eerste start: kies wat je toont (download, upload, geheugen, CPU, opslag, ping, temperatuur) en waar; één statusbalkicoon per onderdeel met de waarde erin, en/of een tekststrip
- Cache per app met wissen in één tik, plus details en acties voor elk draaiend proces (openen, geforceerd stoppen, beëindigen, opdrachtregel)
- Optioneel CPU % en proceslijst via Shizuku
- Engels, Nederlands en Duits; geen reclame, geen tracking, geen account

De batterij is bewust weggelaten: Android toont die al.

## German listing (Deutsch)

**Kurzbeschreibung (max. 80):**
Arbeitsspeicher, Netzwerk, Speicher und Ping live in Statusleiste, Widget und Sperrbildschirm.

**Vollständige Beschreibung:**
TaskBarStatsMobile zeigt auf einen Blick, was dein Telefon gerade tut: Arbeitsspeicher, Netzwerkgeschwindigkeit, Speicher, Ping und Temperatur.

- Winzige Zahlen in der Statusleiste, neben Uhr und Systemsymbolen
- Widgets für den Startbildschirm in sechs Größen, jedes mit eigenen Zellen, Grafiken und Deckkraft
- Schnelleinstellungs-Kachel und eine dauerhafte Benachrichtigung, die auch auf dem Sperrbildschirm erscheint (mit Stopp-Schaltfläche)
- Dashboard und ein immersives Vollbild-Cockpit
- Kacheln ausblenden und umsortieren; 14 Farbthemen, dazu Import von Windows-TaskbarStats-Themen
- Datenverbrauch (WLAN und Mobilfunk), Datenverkehr und Speicher pro App, meistgenutzte Apps, Autostart-Liste, Verlauf und Schwellenwert-Warnungen
- Einrichtung beim ersten Start: Wähle, was angezeigt wird (Download, Upload, Arbeitsspeicher, CPU, Speicher, Ping, Temperatur) und wo; ein Statusleistensymbol pro Wert mit dem Wert darin und/oder eine Textleiste
- Cache pro App mit Alles-leeren in einem Tipp, dazu Details und Aktionen für jeden laufenden Prozess (öffnen, Beenden erzwingen, beenden, Befehlszeile)
- Optional CPU-Auslastung und Prozessliste über Shizuku
- Englisch, Niederländisch und Deutsch; keine Werbung, kein Tracking, kein Konto

Der Akku fehlt mit Absicht: Android zeigt ihn bereits an.
