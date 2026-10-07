<div align="center">

# TaskBarStatsMobile (experimenteel)

**Live geheugen, netwerk, opslag en ping op je Android-telefoon: statusbalk, widgets, melding en cockpit.**

<a href="https://github.com/ericbruggema/TaskBarStatsMobile/releases/latest"><img alt="Download nieuwste APK" src="https://img.shields.io/badge/Download%20nieuwste%20APK-4FC3F7?style=for-the-badge&logo=android&logoColor=white"></a>

<sub>Open de pagina en tik op het <code>.apk</code>-bestand onder <em>Assets</em>. De app kan ook zelf op updates controleren (standaard uit).</sub>

<a href="https://github.com/ericbruggema/TaskBarStatsMobile/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/ericbruggema/TaskBarStatsMobile?label=latest&color=4FC3F7"></a>
<a href="https://github.com/ericbruggema/TaskBarStatsMobile/actions/workflows/android.yml"><img alt="Android CI" src="https://github.com/ericbruggema/TaskBarStatsMobile/actions/workflows/android.yml/badge.svg"></a>
<img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
<img alt="MIT" src="https://img.shields.io/badge/license-MIT-lightgrey">

[English](README.md) · **Nederlands** · [Deutsch](README.de.md)

</div>

TaskBarStatsMobile is de Android-companion van de Windows-monitor [TaskbarStats](https://github.com/ericbruggema/TaskbarStats): live geheugen, netwerk, opslag, ping en temperatuur op een Android-telefoon of -tablet, als dashboard, fullscreen cockpit, widget op het beginscherm, doorlopende melding (ook op het vergrendelscherm), Snelle-instellingen-tegel en kleine cijfers **in de statusbalk** naast de klok en de systeemiconen. Tegels kun je verbergen en herschikken, elke widget heeft eigen instellingen en CPU % is optioneel (zie hieronder).
De batterij is bewust weggelaten: Android toont die al overal.

Dit is een aparte Kotlin / Jetpack Compose-app, geen port van de WinForms-code. Alleen het idee, de kleurthema's en de indeling komen van de Windows-app.

| Dashboard | Cockpit | Widget en instellingen |
|---|---|---|
| ![Dashboard](docs/screenshots/dashboard.png) | ![Cockpit](docs/screenshots/cockpit.png) | ![Widget-tab](docs/screenshots/widget-tab.png) |

| Widget op het beginscherm | Melding | Vergrendelscherm |
|---|---|---|
| ![Widget](docs/screenshots/homescreen.png) | ![Melding](docs/screenshots/notification.png) | ![Vergrendelscherm](docs/screenshots/lockscreen.png) |

**Demo (Engels):** [teaser van 57 s](docs/videos/taskbarstatsmobile-teaser-en.mp4) · [volledige tour van 151 s](docs/videos/taskbarstatsmobile-tour-en.mp4) · [gif](docs/videos/taskbarstatsmobile-hero-en.gif) (staand; schermopnames van de emulator met verzonnen verkeer; gereedschap in [tools/demo](tools/demo)).

[Privacyverklaring](PRIVACY.nl.md): de app verzamelt niets en verstuurt niets behalve één ping-verbinding naar `1.1.1.1` (en, alleen als je dat aanzet, een dagelijkse updatecontrole bij GitHub).

Alle screenshots komen uit een Android 15-emulator met eigen (verzonnen) verkeer; geen persoonlijke gegevens. De screenshots tonen de Engelse interface.

## Wat de app toont en wat Android toestaat

| Meting | Bron | Opmerkingen |
|---|---|---|
| Geheugen | `ActivityManager.MemoryInfo` | |
| Netwerksnelheid | `TrafficStats` | in de statusbalk als `850B`, `9.4K`, `397K`, `2.0M`, `112M` |
| Opslag | `StatFs` | |
| Ping | TCP-verbinding naar 1.1.1.1:443 | |
| Temperatuur | batterijtemperatuur + thermische status van `PowerManager` | Android heeft geen sensoren per kern |
| CPU % | `/proc/stat` | **sinds Android 8 geblokkeerd voor gewone apps.** Werkt via [Shizuku](https://shizuku.rikka.app/) (zie onder), of op gerootte / oude toestellen |
| GPU, FPS | niet beschikbaar | |

Alle waarden worden vóór het tekenen begrensd tot 0–100 %, zodat een balk nooit langer kan zijn dan zijn baan.

## Minicijfers in de statusbalk

Android laat apps niet in de statusbalk tekenen. Daarom toont de app een klein, niet-aanraakbaar overlayvenster met precies de hoogte van de statusbalk (toestemming *Weergeven over andere apps*; de knop opent de juiste instellingenpagina).

- Twee regels piepkleine tekst: `CPU | RAM | ↓↑ | PING`. CPU verschijnt alleen als die beschikbaar is.
- Positie: **Na de klok** of **Vóór de iconen**, plus ◀ ▶-knoppen om hem in stappen van 8 dp naar een vrije plek te schuiven.
- De pil krijgt zijn breedte van de inhoud (met vaste minimumbreedtes zodat hij niet trilt) en krimpt op smalle schermen, waarna de CPU- en pingcellen verdwijnen, zodat hij nooit de klok of systeemiconen bedekt.
- Hij verbergt zichzelf als de statusbalk verborgen is (video, games, de cockpit) en komt er mee terug.
- Hij wordt elke 2 s ververst door de live-monitorservice (*Start live monitor*, of automatisch bij het starten van de app als de overlay aan staat).
- Op het vergrendelscherm kan hij niet verschijnen (Android verbergt overlays daar); de melding wordt daar wel getoond.

## Tegels (zichtbaarheid en volgorde)

Op het tabblad **Tegels** zet je elke tegel (geheugen, netwerk, opslag, ping, temperatuur, CPU, verbinding, wifi-data, mobiele data, uptime) aan of uit en verplaats je hem omhoog of omlaag. Volgorde en zichtbaarheid gelden voor het dashboard (ping en temperatuur staan naast elkaar als ze op elkaar volgen) en voor de fullscreen cockpit (netwerk wordt een download- en een uploadcel). *Tegels herstellen* zet de standaard terug.

De tegels voor wifi- en mobiele data tonen het dataverbruik over een periode die je bovenaan het tabblad kiest (vandaag, 7 of 30 dagen; vereist Gebruikstoegang).

![Het tabblad Tegels met de dataperiode](docs/screenshots/tiles.png)

## Widgets en Snelle-instellingen-tegel

Op het tabblad Widget wordt elk type als levend plaatje getoond; tik op een plaatje om die widget op je beginscherm te zetten. Er zijn zes widgettypen die je kunt mengen en zo vaak plaatsen als je wilt: **Mini** (1x1, één getal), **Duo** (2x1, twee onderdelen), **Small** (2x2, één onderdeel, standaard netwerk), **Strip** (4x1, zonder grafieken), **Dashboard** (4x2, met grafieken) en **Large** (4x3, alle info in twee rijen: geheugen, netwerk, CPU, opslag, dataverbruik, ping, verbinding en uptime). Elke widget op het beginscherm heeft eigen instellingen (cellen MEM / NET / CPU / DISK / DATA / PING / TEMP / LINK / UPTIME, grafieken en balken aan of uit, en de dekking van de achtergrond, 20-100 %), opgeslagen per widget-id. Open ze met **Widget N instellingen** op het tabblad Widget (launchers openen de instellingen van een vastgezette widget niet uit zichzelf) of met *Opnieuw instellen* op de widget. De widget is vergrootbaar en tekent zichzelf in de beeldverhouding die jij hem geeft. De **Snelle-instellingen-tegel** (toevoegen via de tegelbewerker) toont `RAM 46%` en de netwerksnelheid en zet de live monitor met één tik aan of uit.

De galerij op het tabblad Widget (elk plaatje is een knop):

![Widgetgalerij](docs/screenshots/widget-gallery.png)

Widgets op het beginscherm (Large, Strip, Duo en Mini):

![Widgettypen op het beginscherm](docs/screenshots/widgets-kinds.png)

Widgetinstellingen (met de grafiekenschakelaar) en de Snelle-instellingen-tegel:

![Widgetinstellingen en Snelle-instellingen-tegel](docs/screenshots/widget-settings-qs.png)

## Meer tabbladen: Apps, Geschiedenis, Meldingen, Rechten

| Tabblad | Wat het doet | Vereist |
|---|---|---|
| **Apps → Dataverbruik** | wifi- en mobiele data per dag, laatste 7 of 30 dagen, met een totaal | Gebruikstoegang |
| **Apps → Verkeer per app** | download / upload per app (vandaag, 7 of 30 dagen) | Gebruikstoegang |
| **Apps → Opslag per app** | app, data en **cache** per app, de totale cache, sorteren op grootte of cache. Tik op een app voor de cijfers en de manieren om cache te wissen: *Alle caches wissen* (Shizuku wist de cache van alle apps tegelijk; apps die in gebruik zijn of door het systeem worden beheerd houden soms iets), de infopagina van de app (Opslag → Cache wissen; Android laat een app anders alleen zijn eigen cache wissen) of de opslaginstellingen | Gebruikstoegang (alles wissen: Shizuku) |
| **Apps → Meest gebruikte apps** | schermtijd per app | Gebruikstoegang |
| **Apps → Autostart** | apps die zich voor `BOOT_COMPLETED` hebben aangemeld (Android heeft geen echte autostartlijst) | niets (`QUERY_ALL_PACKAGES` wordt bij installatie verleend) |
| **Apps → Processen** | draaiende processen met CPU en RAM, elke 3 s. Tik op een proces voor details (opdrachtregel, wie het startte, gebruiker, leeftijd, wat het draaiend houdt, de componenten) en acties: de app openen (haalt hem naar voren), appinfo, geforceerd stoppen, het proces beëindigen, de opdrachtregel kopiëren | Shizuku |
| **Geschiedenis** | gemiddelden per minuut van geheugen, netwerk, ping en CPU tot 24 uur (in het geheugen: het bouwt zich op zolang de app open is of de live monitor draait) | niets |
| **Meldingen** | melding als geheugen, opslag, ping of temperatuur boven een grens komt (standaard uit, maximaal één per 10 min per onderdeel, vereist de live monitor) | meldingen |
| **Rechten** | één kaart per optionele toestemming met de status en een knop die precies de juiste Android-pagina opent (meldingen, weergeven over andere apps, gebruikstoegang, batterij-optimalisatie, Shizuku) | - |

Nog twee tegels: **Verbinding** (wifi / mobiel, signaal in dBm, linksnelheid, band) en **Uptime**. Alles wat een speciale toestemming nodig heeft wordt uitgelegd waar het gebruikt wordt en linkt rechtstreeks naar de juiste instellingenpagina; de status wordt opnieuw gelezen als je terugkomt. Er verlaat niets het toestel.

![Rechten](docs/screenshots/permissions.png)

| Verkeer per app | Processen (Shizuku) | Geschiedenis | Datategels |
|---|---|---|---|
| ![Verkeer](docs/screenshots/apps-traffic.png) | ![Processen](docs/screenshots/apps-processes.png) | ![Geschiedenis](docs/screenshots/history.png) | ![Tegels](docs/screenshots/dashboard-data.png) |

## Eerste start en statusbalk-items

Bij de eerste start (en via *Installatie opnieuw uitvoeren* op het tabblad Widget) vraagt een installatiescherm **wat** je wilt tonen (download, upload, geheugen, CPU, opslag, ping, temperatuur - elke combinatie) en **waar**:

- **Iconen naast de klok**: één statusbalkicoon per onderdeel met de waarde erin getekend (een klein label boven de waarde, bv. `RAM 46%`, `↓ 123B`). Vereist geen extra toestemming. Android bepaalt de exacte volgorde en plaats (altijd links, naast de klok). Het eerste onderdeel zit op het icoon van de hoofdmelding, de andere krijgen een eigen melding (aparte groepen, anders vouwt Android ze samen tot één algemeen icoon; normaal belang maar zonder geluid, omdat sommige telefoons de iconen van stille meldingen verbergen).
- **Tekststrip**: één strip met alle gekozen onderdelen na de klok of vóór de systeemiconen (vereist *Weergeven over andere apps*; het installatiescherm opent de juiste pagina). Staan de iconen ook aan, dan begint de strip erna. Download en upload delen één cel van twee regels.

Beide kunnen tegelijk aan staan. *Start* start de live monitor meteen. Op het tabblad Widget verbergt *Melding* → *Alleen bij verbinding* de iconen zolang er geen verbinding is (de hoofdmelding moet blijven: Android eist er één voor een voorgrondservice).

![Installatie](docs/screenshots/setup.png)

![Statusbalkiconen en strip](docs/screenshots/statusbar-icons.png)

| Procesdetails | Cache per app | Alleen iconen |
|---|---|---|
| ![Procesdetails](docs/screenshots/process-details.png) | ![Cache](docs/screenshots/cache.png) | ![Alleen iconen](docs/screenshots/icons-only.png) |

## Cameragat

Een selfiecamera in het midden van de bovenrand verbergt niet langer een stuk van de app. Met **Camera vrijhouden** (tabblad Widget, standaard aan) worden het dashboard, de fullscreen cockpit en de statusbalkstrook om het cameragat heen getekend in plaats van erachter; de strook blijft aan zijn kant van de camera en krimpt of laat cellen weg als er te weinig ruimte is. Kies als positie **Midden, om de camera** en de strook wordt in twee helften gesplitst, één links en één rechts van de camera (zonder camera is het één gecentreerde strook). Elk gekozen onderdeel blijft in de strook staan (CPU, ping, temperatuur, opslag enzovoort); de tekst wordt alleen kleiner als de ruimte krap is. Zet **Onderdelen weglaten bij te weinig ruimte** aan om CPU, ping, temperatuur en opslag juist te laten wegvallen. De helften worden verdeeld naar de ruimte aan elke kant. Boven: met de schakelaar aan; onder: camera-optie uit.

![Alle posities van de statusbalkstrook, met de instellingen](docs/screenshots/statusbar-positions.png)

Kies de positie in een **voorbeeld van de statusbalk**: tik links (naast de klok), in het midden (om de camera) of rechts (voor de iconen). Het voorbeeld toont de camera, de vrije ruimte en de strook op schaal. Komt er toch nog een stuk achter de camera, zet dan *Camera automatisch herkennen* uit en stel zelf de **breedte van de camera**, de **extra vrije ruimte** aan beide kanten en de **plek** in; alle maten zijn in schermpixels (bijvoorbeeld camera 50 px, 5 px vrij aan elke kant). Het voorbeeld en de echte statusbalk volgen direct, en terwijl je aan een schuifregelaar zit toont een rode markering op de echte statusbalk de camera (donker) en de vrije ruimte (licht). Meldt je telefoon geen cameragat, dan is de breedte-regelaar ook beschikbaar als automatisch herkennen aan staat.

![Het voorbeeld en de camera-instellingen](docs/screenshots/strip-preview.png)

**Eén icoon dat wisselt:** veel telefoons tonen maar één van de statusbalkiconen. Zet *Wisselen in één icoon* aan en het ene icoon wisselt om de paar seconden tussen de gekozen onderdelen (standaard uit).

![Eén wisselend icoon](docs/screenshots/rotate-icon.png) Het hoofdicoon van de melding toont altijd een live cijfer (het eerste gekozen onderdeel, standaard de downloadsnelheid), nooit het vaste app-icoon.

![Cockpit met de schakelaar uit en aan](docs/screenshots/camera-cutout.png)

## Vergrendelscherm

De doorlopende melding (`MEM 50% ↓ ↑ / Disk 13% Ping 17 ms`, met een **Stop**-knop die de live monitor beëindigt) is openbaar op het vergrendelscherm. Het kanaal heeft normaal belang (Android verbergt meldingen met laag belang op het vergrendelscherm) maar geen geluid of trilling.

## Thema's

Dezelfde 14 kleurthema's als de Windows-app (Default, Dark, Light, Love, CGA, Matrix, Amber, Game Boy, Dracula, Ocean, Sunset, Forest, Neon, …) plus die van de app zelf. Ze kleuren de app, de widget en de melding; bij lichte thema's worden de vaste reeksenkleuren automatisch donkerder gemaakt voor het contrast. **Windows-thema importeren (.json)** leest een themabestand van de Windows-app (`TextColor`, `BackgroundColor`, `AccentColor`, `WarnColor`).

## Updates

Optioneel en standaard uit: *Op updates controleren* (tabblad Widget) vraagt één keer per dag bij GitHub naar de nieuwste release en meldt een nieuwe versie één keer. *Downloaden en installeren* opent de APK-link in je browser; Android vraagt om bevestiging voor het installeren. Verborgen als de app uit de Play Store komt. Zie de [privacyverklaring](PRIVACY.nl.md).

![De updatecontrole in vier toestanden](docs/screenshots/updates.png)

## Talen

Engels, Nederlands en Duits (`res/values*/strings.xml`), volgens de systeemtaal (taal per app vanaf Android 13).

## CPU % met Shizuku

1. Installeer de Shizuku-app en start hem (Android 11+: draadloos foutopsporen, geen pc nodig; of `adb`).
2. Open TaskBarStatsMobile; de CPU-tegel zegt wat er ontbreekt. Tik op **Shizuku-toegang toestaan** en accepteer de vraag van Shizuku.
3. TaskBarStatsMobile start via Shizuku een kleine `UserService` (shell-rechten) die `/proc/stat` voor hem leest.

Shizuku moet na een herstart opnieuw worden gestart (behalve op Android 13+ met zijn automatisch starten op vertrouwd wifi).
`dumpsys cpuinfo` met de `DUMP`-toestemming is geprobeerd en werkt **niet**: de service is onzichtbaar voor gewone apps (SELinux).

## Bouwen

Vereist JDK 17 en de Android SDK (platform 35, build-tools 35). Gradle komt van de wrapper.

```
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`start-emulator.bat` start de emulator (AVD `tsphone`) en wacht tot hij is opgestart; `start-emulator.bat install` bouwt en installeert ook de debug-app.

local.properties (met `sdk.dir`) staat in .gitignore. Alleen getest op een Android 15-emulator (x86_64, Google APIs); de Shizuku-flow is getest op de emulator met de Shizuku-server gestart via `adb`, niet op echte hardware.

## Releasebuild

`build-release.bat` (of `./gradlew assembleRelease bundleRelease`) maakt een ondertekende, verkleinde (R8) APK van ongeveer 1 MB en een AAB. Daarvoor zijn `keystore.properties` en `taskbarstats-release.jks` naast `gradlew` nodig; beide staan in **.gitignore** en worden eenmalig met `keytool` gemaakt (RSA 2048, 10000 dagen geldig). **Maak er een back-up van**: een release met een andere sleutel kan een geïnstalleerde niet bijwerken. R8 behoudt de Shizuku-`UserService` en de AIDL-stubs (`app/proguard-rules.pro`). De releasebuild is getest op de emulator: UI, widget, Snelle-instellingen-tegel en de Shizuku-CPU-bron werken allemaal na het verkleinen. Een concept van de winkelvermelding staat in [PLAY-STORE.md](PLAY-STORE.md). De CI-taak (`.github/workflows/android.yml`) bouwt de debug-APK en draait lint bij elke wijziging in de code.

## Code-overzicht

| Bestand | Rol |
|---|---|
| `Sampler.kt` | één sampler-thread (1 s) + een ping-thread; `Snapshot` = onveranderlijk moment (zoals `MetricsSnapshot` op Windows); geschiedenisringen |
| `StatsRenderer.kt` | tekent de widget naar een gewone `Bitmap` (dezelfde renderer voor widget, melding en voorbeeld in de app) |
| `StatusBarOverlay.kt` | de statusbalkpil: plaatsing, passen, fullscreen-herkenning (window insets) |
| `MonitorService.kt`, `StatsWidget.kt` | voorgrondservice (widget + melding elke 2 s), `AppWidgetProvider` |
| `MainActivity.kt` | Compose-UI: Dashboard, Cockpit (immersief, houdt het scherm aan), tabblad Widget/instellingen |
| `Tiles.kt` | welke tegels getoond worden en in welke volgorde (dashboard en cockpit) |
| `WidgetOptions.kt`, `WidgetConfigActivity.kt` | cellen en dekking per widget, en het instellingenscherm |
| `StatsTileService.kt` | de Snelle-instellingen-tegel |
| `Themes.kt`, `Fmt.kt` | paletten en import van Windows-thema's; getalopmaak |
| `ShizukuCpu.kt`, `aidl/IStatsService.aidl` | optionele CPU-bron en proceslijst (`top`) via Shizuku's `UserService` |
| `AppData.kt` | gebruikstoegang, dataverbruik, verkeer / opslag / schermtijd per app, autostartlijst, procesparsing |
| `Alerts.kt` | drempelmeldingen |
| `StatusItems.kt`, `Setup.kt` | de gekozen statusbalk-items (iconen tekenen, waarden) en het installatiescherm bij de eerste start |
| `Tabs2.kt` | de tabbladen Apps, Geschiedenis, Meldingen en Rechten |

## Nog niet gedaan

Blijvende geschiedenis (die staat nu alleen in het geheugen), de Play Store-afbeeldingen en een controle op echte hardware (alles is getest op een Android 15-emulator).
