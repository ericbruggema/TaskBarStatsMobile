<div align="center">

# TaskBarStatsMobile (experimentell)

**Arbeitsspeicher, Netzwerk, Speicher und Ping live auf deinem Android-Telefon: Statusleiste, Widgets, Benachrichtigung und Cockpit.**

<a href="https://github.com/ericbruggema/TaskBarStatsMobile/releases/latest"><img alt="Neueste APK herunterladen" src="https://img.shields.io/badge/Neueste%20APK%20herunterladen-4FC3F7?style=for-the-badge&logo=android&logoColor=white"></a>

<sub>Öffne die Seite und tippe unter <em>Assets</em> auf die <code>.apk</code>-Datei. Die App kann auch selbst nach Updates suchen (standardmäßig aus).</sub>

<a href="https://github.com/ericbruggema/TaskBarStatsMobile/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/ericbruggema/TaskBarStatsMobile?label=latest&color=4FC3F7"></a>
<a href="https://github.com/ericbruggema/TaskBarStatsMobile/actions/workflows/android.yml"><img alt="Android CI" src="https://github.com/ericbruggema/TaskBarStatsMobile/actions/workflows/android.yml/badge.svg"></a>
<img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
<img alt="MIT" src="https://img.shields.io/badge/license-MIT-lightgrey">

[English](README.md) · [Nederlands](README.nl.md) · **Deutsch**

</div>

TaskBarStatsMobile ist der Android-Begleiter des Windows-Monitors [TaskbarStats](https://github.com/ericbruggema/TaskbarStats): Arbeitsspeicher, Netzwerk, Speicher, Ping und Temperatur live auf einem Android-Smartphone oder -Tablet, als Dashboard, Vollbild-Cockpit, Widget auf dem Startbildschirm, dauerhafte Benachrichtigung (auch auf dem Sperrbildschirm), Schnelleinstellungs-Kachel und winzige Zahlen **in der Statusleiste** neben Uhr und Systemsymbolen. Kacheln lassen sich ausblenden und umsortieren, jedes Widget hat eigene Einstellungen, und die CPU-Auslastung ist optional (siehe unten).
Der Akku fehlt mit Absicht: Android zeigt ihn schon überall an.

Dies ist eine eigenständige Kotlin- / Jetpack-Compose-App, keine Portierung des WinForms-Codes. Sie übernimmt nur die Idee, die Farbthemen und das Layout der Windows-App.

| Dashboard | Cockpit | Widget und Einstellungen |
|---|---|---|
| ![Dashboard](docs/screenshots/dashboard.png) | ![Cockpit](docs/screenshots/cockpit.png) | ![Widget-Tab](docs/screenshots/widget-tab.png) |

| Widget auf dem Startbildschirm | Benachrichtigung | Sperrbildschirm |
|---|---|---|
| ![Widget](docs/screenshots/homescreen.png) | ![Benachrichtigung](docs/screenshots/notification.png) | ![Sperrbildschirm](docs/screenshots/lockscreen.png) |

**Demo (Englisch):** [57-s-Teaser](docs/videos/taskbarstatsmobile-teaser-en.mp4) · [151-s-Tour](docs/videos/taskbarstatsmobile-tour-en.mp4) · [Gif](docs/videos/taskbarstatsmobile-hero-en.gif) (Hochformat; Bildschirmaufnahmen des Emulators mit erfundenem Datenverkehr; Werkzeuge in [tools/demo](tools/demo)).

[Datenschutzerklärung](PRIVACY.de.md): Die App sammelt nichts und sendet nichts, außer einer Ping-Verbindung zu `1.1.1.1` (und, nur wenn du es einschaltest, einer täglichen Update-Prüfung bei GitHub).

Alle Screenshots stammen aus einem Android-15-Emulator mit eigenem (erfundenem) Datenverkehr; keine personenbezogenen Daten. Die Screenshots zeigen die englische Oberfläche.

## Was sie anzeigt und was Android erlaubt

| Messwert | Quelle | Hinweise |
|---|---|---|
| Arbeitsspeicher | `ActivityManager.MemoryInfo` | |
| Netzwerkgeschwindigkeit | `TrafficStats` | in der Statusleiste als `850B`, `9.4K`, `397K`, `2.0M`, `112M` |
| Speicher | `StatFs` | |
| Ping | TCP-Verbindung zu 1.1.1.1:443 | |
| Temperatur | Akkutemperatur + Thermalstatus von `PowerManager` | Android hat keine Sensoren pro Kern |
| CPU % | `/proc/stat` | **seit Android 8 für normale Apps gesperrt.** Funktioniert über [Shizuku](https://shizuku.rikka.app/) (siehe unten) oder auf gerooteten / alten Geräten |
| GPU, FPS | nicht verfügbar | |

Alle Werte werden vor dem Zeichnen auf 0–100 % begrenzt, damit ein Balken nie länger als seine Bahn sein kann.

## Mini-Werte in der Statusleiste

Android lässt Apps nicht in die Statusleiste zeichnen. Daher zeigt die App ein kleines, nicht berührbares Überlagerungsfenster in genau der Höhe der Statusleiste (Berechtigung *Über anderen Apps einblenden*; die Schaltfläche öffnet die passende Einstellungsseite).

- Zwei Zeilen winziger Text: `CPU | RAM | ↓↑ | PING`. Die CPU erscheint nur, wenn sie verfügbar ist.
- Position: **Nach der Uhr** oder **Vor den Symbolen**, dazu ◀ ▶-Tasten, um sie in 8-dp-Schritten an eine freie Stelle zu schieben.
- Die Pille richtet ihre Breite nach dem Inhalt (mit festen Mindestbreiten, damit sie nicht zittert) und schrumpft auf schmalen Bildschirmen, danach entfallen die CPU- und Ping-Zellen, damit sie nie Uhr oder Systemsymbole verdeckt.
- Sie blendet sich aus, wenn die Statusleiste verborgen ist (Video, Spiele, das Cockpit), und kommt mit ihr zurück.
- Sie wird alle 2 s vom Live-Monitor-Dienst aktualisiert (*Live-Monitor starten* oder automatisch beim App-Start, wenn die Überlagerung an ist).
- Auf dem Sperrbildschirm kann sie nicht erscheinen (Android blendet dort Überlagerungen aus); die Benachrichtigung wird dort angezeigt.

## Kacheln (Sichtbarkeit und Reihenfolge)

Im Tab **Kacheln** schaltest du jede Kachel (Arbeitsspeicher, Netzwerk, Speicher, Ping, Temperatur, CPU, Verbindung, WLAN-Daten, Mobilfunkdaten, Laufzeit) ein oder aus und verschiebst sie nach oben oder unten. Reihenfolge und Sichtbarkeit gelten für das Dashboard (Ping und Temperatur stehen nebeneinander, wenn sie aufeinanderfolgen) und für das Vollbild-Cockpit (das Netzwerk wird zu einer Download- und einer Upload-Zelle). *Kacheln zurücksetzen* stellt den Standard wieder her.

Die Kacheln für WLAN- und Mobilfunkdaten zeigen den Datenverbrauch über einen Zeitraum, den du oben im Tab wählst (heute, 7 oder 30 Tage; erfordert Nutzungszugriff).

![Der Tab Kacheln mit dem Datenzeitraum](docs/screenshots/tiles.png)

## Widgets und Schnelleinstellungs-Kachel

Im Tab Widget wird jeder Typ als lebendes Bild gezeigt; tippe auf ein Bild, um dieses Widget auf deinen Startbildschirm zu legen. Es gibt sechs Widget-Typen, die du mischen und beliebig oft platzieren kannst: **Mini** (1x1, eine Zahl), **Duo** (2x1, zwei Werte), **Small** (2x2, ein Wert, standardmäßig Netzwerk), **Strip** (4x1, ohne Grafiken), **Dashboard** (4x2, mit Grafiken) und **Large** (4x3, alle Infos in zwei Zeilen: Arbeitsspeicher, Netzwerk, CPU, Speicher, Datenverbrauch, Ping, Verbindung und Laufzeit). Jedes Widget auf dem Startbildschirm hat eigene Einstellungen (Zellen MEM / NET / CPU / DISK / DATA / PING / TEMP / LINK / UPTIME, Grafiken und Balken an oder aus, und die Deckkraft des Hintergrunds, 20-100 %), gespeichert pro Widget-ID. Öffne sie mit **Widget N Einstellungen** im Tab Widget (Launcher öffnen die Einstellungen eines angehefteten Widgets nicht von selbst) oder mit *Neu konfigurieren* am Widget. Das Widget ist in der Größe veränderbar und zeichnet sich im Seitenverhältnis, das du ihm gibst. Die **Schnelleinstellungs-Kachel** (über den Kachel-Editor hinzufügen) zeigt `RAM 46%` und die Netzwerkgeschwindigkeit und schaltet den Live-Monitor mit einem Tipp ein oder aus.

Die Galerie im Tab Widget (jedes Bild ist eine Schaltfläche):

![Widget-Galerie](docs/screenshots/widget-gallery.png)

Widgets auf dem Startbildschirm (Large, Strip, Duo und Mini):

![Widget-Typen auf dem Startbildschirm](docs/screenshots/widgets-kinds.png)

Widget-Einstellungen (mit dem Grafik-Schalter) und die Schnelleinstellungs-Kachel:

![Widget-Einstellungen und Schnelleinstellungs-Kachel](docs/screenshots/widget-settings-qs.png)

## Weitere Tabs: Apps, Verlauf, Warnungen, Berechtigungen

| Tab | Was er tut | Benötigt |
|---|---|---|
| **Apps → Datenverbrauch** | WLAN- und Mobilfunkdaten pro Tag, letzte 7 oder 30 Tage, mit Summe | Nutzungszugriff |
| **Apps → Datenverkehr pro App** | Download / Upload pro App (heute, 7 oder 30 Tage) | Nutzungszugriff |
| **Apps → Speicher pro App** | App, Daten und **Cache** pro App, der gesamte Cache, Sortierung nach Größe oder Cache. Tippe auf eine App für die Zahlen und die Wege, den Cache zu leeren: *Alle Caches leeren* (Shizuku leert den Cache aller Apps auf einmal; Apps in Benutzung oder vom System verwaltete behalten manchmal etwas), die Infoseite der App (Speicher → Cache leeren; Android erlaubt einer App sonst nur das Leeren des eigenen Caches) oder die Speichereinstellungen | Nutzungszugriff (alle leeren: Shizuku) |
| **Apps → Meistgenutzte Apps** | Bildschirmzeit pro App | Nutzungszugriff |
| **Apps → Autostart** | Apps, die sich für `BOOT_COMPLETED` registriert haben (Android hat keine echte Autostart-Liste) | nichts (`QUERY_ALL_PACKAGES` wird bei der Installation gewährt) |
| **Apps → Prozesse** | laufende Prozesse mit CPU und RAM, alle 3 s. Tippe auf einen für Details (Befehlszeile, wer ihn gestartet hat, Benutzer, Alter, was ihn am Laufen hält, seine Komponenten) und Aktionen: App öffnen (holt sie nach vorn), App-Info, Beenden erzwingen, den Prozess beenden, die Befehlszeile kopieren | Shizuku |
| **Verlauf** | Minutenmittel von Arbeitsspeicher, Netzwerk, Ping und CPU bis zu 24 h (im Speicher: er baut sich auf, solange die App offen ist oder der Live-Monitor läuft) | nichts |
| **Warnungen** | Benachrichtigung, wenn Arbeitsspeicher, Speicher, Ping oder Temperatur einen Grenzwert überschreiten (standardmäßig aus, höchstens eine pro 10 min und Wert, benötigt den Live-Monitor) | Benachrichtigungen |
| **Berechtigungen** | eine Karte pro optionaler Berechtigung mit Status und einer Schaltfläche, die genau die richtige Android-Seite öffnet (Benachrichtigungen, über anderen Apps einblenden, Nutzungszugriff, Akku-Optimierung, Shizuku) | - |

Zwei weitere Kacheln: **Verbindung** (WLAN / Mobilfunk, Signal in dBm, Verbindungsgeschwindigkeit, Band) und **Laufzeit**. Alles, was eine Sonderberechtigung braucht, wird dort erklärt, wo es genutzt wird, und verlinkt direkt auf die richtige Einstellungsseite; der Status wird beim Zurückkehren neu gelesen. Nichts verlässt das Gerät.

![Berechtigungen](docs/screenshots/permissions.png)

| Datenverkehr pro App | Prozesse (Shizuku) | Verlauf | Daten-Kacheln |
|---|---|---|---|
| ![Datenverkehr](docs/screenshots/apps-traffic.png) | ![Prozesse](docs/screenshots/apps-processes.png) | ![Verlauf](docs/screenshots/history.png) | ![Kacheln](docs/screenshots/dashboard-data.png) |

## Erster Start und Statusleisten-Einträge

Beim ersten Start (und über *Einrichtung erneut ausführen* im Tab Widget) fragt ein Einrichtungsbildschirm, **was** angezeigt werden soll (Download, Upload, Arbeitsspeicher, CPU, Speicher, Ping, Temperatur - beliebig kombinierbar) und **wo**:

- **Symbole neben der Uhr**: ein Statusleistensymbol pro Wert, mit dem Wert hineingezeichnet (ein kleines Label über dem Wert, z. B. `RAM 46%`, `↓ 123B`). Braucht keine zusätzliche Berechtigung. Android bestimmt die genaue Reihenfolge und Stelle (immer links, neben der Uhr). Der erste Wert sitzt auf dem Symbol der Hauptbenachrichtigung, die anderen bekommen je eine eigene Benachrichtigung (getrennte Gruppen, sonst faltet Android sie zu einem allgemeinen Symbol zusammen; normale Wichtigkeit, aber ohne Ton, weil manche Telefone die Symbole stummer Benachrichtigungen ausblenden).
- **Textleiste**: eine Leiste mit allen gewählten Werten nach der Uhr oder vor den Systemsymbolen (braucht *Über anderen Apps einblenden*; die Einrichtung öffnet die richtige Seite). Sind die Symbole ebenfalls an, beginnt die Leiste danach. Download und Upload teilen sich eine zweizeilige Zelle.

Beides kann gleichzeitig an sein. *Start* startet den Live-Monitor sofort. Im Tab Widget blendet *Benachrichtigung* → *Nur bei Verbindung* die Symbole aus, solange keine Verbindung besteht (die Hauptbenachrichtigung muss bleiben: Android verlangt eine für einen Vordergrunddienst).

![Einrichtung](docs/screenshots/setup.png)

![Statusleistensymbole und Leiste](docs/screenshots/statusbar-icons.png)

| Prozessdetails | Cache pro App | Nur Symbole |
|---|---|---|
| ![Prozessdetails](docs/screenshots/process-details.png) | ![Cache](docs/screenshots/cache.png) | ![Nur Symbole](docs/screenshots/icons-only.png) |

## Kamera-Aussparung

Eine Frontkamera in der Mitte des oberen Rands verdeckt keinen Teil der App mehr. Mit **Kamera freihalten** (Tab Widget, standardmäßig an) werden Dashboard, Vollbild-Cockpit und Statusleistenstreifen um die Aussparung herum gezeichnet statt dahinter; der Streifen bleibt auf seiner Seite der Kamera und schrumpft oder lässt Zellen weg, wenn zu wenig Platz ist. Wähle als Position **Mitte, um die Kamera** und der Streifen wird in zwei Hälften geteilt, eine links und eine rechts der Kamera (ohne Kamera ist es ein zentrierter Streifen). Jeder gewählte Wert bleibt im Streifen (CPU, Ping, Temperatur, Speicher usw.); bei Platzmangel wird nur der Text kleiner. Schalte **Werte bei Platzmangel ausblenden** ein, damit CPU, Ping, Temperatur und Speicher stattdessen wegfallen. Die Hälften werden nach dem Platz auf beiden Seiten verteilt. Oben: mit eingeschaltetem Schalter; unten: Kameraoption aus.

![Alle Positionen des Statusleistenstreifens, mit den Einstellungen](docs/screenshots/statusbar-positions.png)

Wähle die Position in einer **Vorschau der Statusleiste**: Tippe links (neben der Uhr), in die Mitte (um die Kamera) oder rechts (vor den Symbolen). Die Vorschau zeigt Kamera, freien Platz und Streifen maßstabsgetreu. Landet trotzdem ein Teil hinter der Kamera, schalte *Kamera automatisch erkennen* aus und stelle **Kamerabreite**, **zusätzlichen freien Platz** auf beiden Seiten und **Position** selbst ein; alle Maße sind in Bildschirmpixeln (z. B. Kamera 50 px, 5 px frei auf jeder Seite). Vorschau und echte Statusleiste folgen sofort, und beim Bewegen eines Reglers zeigt eine rote Markierung auf der echten Statusleiste die Kamera (dunkel) und den freien Platz (hell). Meldet dein Telefon keine Kamera-Aussparung, ist der Breitenregler auch bei eingeschalteter automatischer Erkennung verfügbar.

![Die Vorschau und die Kamera-Einstellungen](docs/screenshots/strip-preview.png)

**Das App-Logo neben der Uhr:** Solange der Streifen an ist, zeigt Android selbst ein kleines "über anderen Apps einblenden"-Symbol mit dem App-Logo. Eine App kann es nicht entfernen, aber die Schaltfläche *Android-Symbol "über anderen Apps" ausblenden* öffnet die Systemeinstellung, in der du diese Benachrichtigung ausschalten kannst (auf vielen Telefonen; manche sperren es).

**Ein Symbol, das wechselt:** Viele Telefone zeigen nur eines der Statusleistensymbole. Schalte *In einem Symbol wechseln* ein, und das eine Symbol wechselt alle paar Sekunden zwischen den gewählten Werten (standardmäßig aus).

![Ein wechselndes Symbol](docs/screenshots/rotate-icon.png) Das Hauptsymbol der Benachrichtigung zeigt immer einen Live-Wert (den ersten gewählten Wert, standardmäßig die Download-Geschwindigkeit), nie das feste App-Symbol.

![Cockpit mit ausgeschaltetem und eingeschaltetem Schalter](docs/screenshots/camera-cutout.png)

## Sperrbildschirm

Die dauerhafte Benachrichtigung (`MEM 50% ↓ ↑ / Disk 13% Ping 17 ms`, mit einer **Stopp**-Schaltfläche, die den Live-Monitor beendet) ist auf dem Sperrbildschirm öffentlich. Der Kanal hat normale Wichtigkeit (Android blendet Benachrichtigungen mit niedriger Wichtigkeit auf dem Sperrbildschirm aus), aber keinen Ton und keine Vibration.

## Themen

Dieselben 14 Farbthemen wie die Windows-App (Default, Dark, Light, Love, CGA, Matrix, Amber, Game Boy, Dracula, Ocean, Sunset, Forest, Neon, …) plus die der App selbst. Sie färben App, Widget und Benachrichtigung; bei hellen Themen werden die festen Serienfarben für den Kontrast automatisch abgedunkelt. **Windows-Thema importieren (.json)** liest eine Themendatei der Windows-App (`TextColor`, `BackgroundColor`, `AccentColor`, `WarnColor`).

## Updates

Optional und standardmäßig aus: *Nach Updates suchen* (Tab Widget) fragt einmal täglich bei GitHub nach der neuesten Version und meldet eine neue Version einmal. *Herunterladen und installieren* öffnet den APK-Link im Browser; Android fragt vor der Installation nach Bestätigung. Ausgeblendet, wenn die App aus dem Play Store stammt. Siehe die [Datenschutzerklärung](PRIVACY.de.md).

![Die Update-Prüfung in vier Zuständen](docs/screenshots/updates.png)

## Sprachen

Englisch, Niederländisch und Deutsch (`res/values*/strings.xml`), je nach Systemsprache (App-Sprache ab Android 13).

## CPU % mit Shizuku

1. Installiere die Shizuku-App und starte sie (Android 11+: Drahtloses Debugging, kein PC nötig; oder `adb`).
2. Öffne TaskBarStatsMobile; die CPU-Kachel sagt, was fehlt. Tippe auf **Shizuku-Zugriff erlauben** und bestätige die Frage von Shizuku.
3. TaskBarStatsMobile startet über Shizuku einen winzigen `UserService` (Shell-Rechte), der `/proc/stat` für ihn liest.

Shizuku muss nach einem Neustart erneut gestartet werden (außer ab Android 13 mit seinem Autostart im vertrauten WLAN).
`dumpsys cpuinfo` mit der `DUMP`-Berechtigung wurde versucht und funktioniert **nicht**: Der Dienst ist für normale Apps unsichtbar (SELinux).

## Bauen

Benötigt JDK 17 und das Android SDK (Plattform 35, Build-Tools 35). Gradle kommt vom Wrapper.

```
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`start-emulator.bat` startet den Emulator (AVD `tsphone`) und wartet, bis er hochgefahren ist; `start-emulator.bat install` baut und installiert außerdem die Debug-App.

local.properties (mit `sdk.dir`) steht in der .gitignore. Nur auf einem Android-15-Emulator (x86_64, Google APIs) getestet; der Shizuku-Ablauf wurde auf dem Emulator mit einem über `adb` gestarteten Shizuku-Server getestet, nicht auf echter Hardware.

## Release-Build

`build-release.bat` (oder `./gradlew assembleRelease bundleRelease`) erzeugt eine signierte, verkleinerte (R8) APK von etwa 1 MB und ein AAB. Dafür werden `keystore.properties` und `taskbarstats-release.jks` neben `gradlew` benötigt; beide stehen in der **.gitignore** und werden einmalig mit `keytool` erzeugt (RSA 2048, 10000 Tage gültig). **Sichere sie**: Ein mit einem anderen Schlüssel signiertes Release kann ein installiertes nicht aktualisieren. R8 behält den Shizuku-`UserService` und die AIDL-Stubs (`app/proguard-rules.pro`). Der Release-Build wurde auf dem Emulator getestet: UI, Widget, Schnelleinstellungs-Kachel und die Shizuku-CPU-Quelle funktionieren nach dem Verkleinern. Ein Entwurf der Store-Beschreibung steht in [PLAY-STORE.md](PLAY-STORE.md). Der CI-Job (`.github/workflows/android.yml`) baut die Debug-APK und führt Lint bei jeder Änderung am Code aus.

## Code-Überblick

| Datei | Rolle |
|---|---|
| `Sampler.kt` | ein Sampler-Thread (1 s) + ein Ping-Thread; `Snapshot` = unveränderlicher Moment (wie `MetricsSnapshot` unter Windows); Verlaufsringe |
| `StatsRenderer.kt` | zeichnet das Widget in eine einfache `Bitmap` (derselbe Renderer für Widget, Benachrichtigung und Vorschau in der App) |
| `StatusBarOverlay.kt` | die Statusleisten-Pille: Platzierung, Anpassung, Vollbild-Erkennung (Window Insets) |
| `MonitorService.kt`, `StatsWidget.kt` | Vordergrunddienst (Widget + Benachrichtigung alle 2 s), `AppWidgetProvider` |
| `MainActivity.kt` | Compose-UI: Dashboard, Cockpit (immersiv, hält den Bildschirm an), Tab Widget/Einstellungen |
| `Tiles.kt` | welche Kacheln in welcher Reihenfolge gezeigt werden (Dashboard und Cockpit) |
| `WidgetOptions.kt`, `WidgetConfigActivity.kt` | Zellen und Deckkraft pro Widget und der Einstellungsbildschirm |
| `StatsTileService.kt` | die Schnelleinstellungs-Kachel |
| `Themes.kt`, `Fmt.kt` | Paletten und Import von Windows-Themen; Zahlenformatierung |
| `ShizukuCpu.kt`, `aidl/IStatsService.aidl` | optionale CPU-Quelle und Prozessliste (`top`) über Shizukus `UserService` |
| `AppData.kt` | Nutzungszugriff, Datenverbrauch, Datenverkehr / Speicher / Bildschirmzeit pro App, Autostart-Liste, Prozess-Parsing |
| `Alerts.kt` | Schwellenwert-Benachrichtigungen |
| `StatusItems.kt`, `Setup.kt` | die gewählten Statusleisten-Einträge (Symbole zeichnen, Werte) und der Einrichtungsbildschirm beim ersten Start |
| `Tabs2.kt` | die Tabs Apps, Verlauf, Warnungen und Berechtigungen |

## Noch nicht erledigt

Dauerhafter Verlauf (er liegt nur im Speicher), die Play-Store-Grafiken und eine Prüfung auf echter Hardware (alles wurde auf einem Android-15-Emulator getestet).
