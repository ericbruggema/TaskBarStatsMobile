# Datenschutzerklärung: TaskBarStatsMobile

[English](PRIVACY.md) · [Nederlands](PRIVACY.nl.md) · **Deutsch**

_Zuletzt aktualisiert: 4. Oktober 2026_

TaskBarStatsMobile zeigt Live-Statistiken deines eigenen Telefons. Die App ist Open Source (MIT):
https://github.com/ericbruggema/TaskBarStatsMobile

## Kurz gesagt

- **Wir sammeln nichts.** Die App hat kein Konto, keine Analyse, keine Werbung, keine Absturzberichte und keine SDKs von Drittanbietern.
- **Alles bleibt auf deinem Gerät.** Die Statistiken, deine Einstellungen und der in der App gezeigte Verlauf liegen nur auf deinem Telefon und werden nirgendwohin gesendet.
- **Es wird nichts weitergegeben oder verkauft.**

## Was die App liest und warum

All dies wird nur auf deinem eigenen Bildschirm angezeigt und nirgendwo sonst:

| Was | Warum | Berechtigung |
|---|---|---|
| Arbeitsspeicher, Speicher, Akkutemperatur, Laufzeit, Netzwerkgeschwindigkeit, WLAN- / Mobilfunkverbindung (Signal, Geschwindigkeit) | die Live-Werte | keine, oder Netzwerk- / WLAN-Status |
| Datenverbrauch pro Tag und pro App, Speicher und Cache pro App, Bildschirmzeit, Liste der installierten Apps | der Tab Apps und die Daten-Kacheln | Nutzungszugriff (schaltest du selbst ein), installierte Apps |
| CPU-Auslastung und die Prozessliste | optional, nur mit der separaten Shizuku-App | Shizuku (erlaubst du selbst) |
| Benachrichtigungen | die Live-Benachrichtigung, Statusleistensymbole und deine Warnungen | Benachrichtigungen |
| Über anderen Apps zeichnen | die optionale Textleiste in der Statusleiste | Über anderen Apps einblenden |

Jede Sonderberechtigung ist optional und lässt sich in den Android-Einstellungen entziehen; die App blendet dann einfach die Funktionen aus, die sie benötigen.

## Die eine Netzwerkverbindung

Zur Ping-Messung öffnet die App eine kurze TCP-Verbindung zu `1.1.1.1` (dem öffentlichen DNS-Dienst von Cloudflare) auf Port 443 und misst, wie lange sie dauert. Es werden keine personenbezogenen Daten und keine Kennung gesendet. Cloudflare kann sehen, dass sich deine IP-Adresse mit seinem Dienst verbunden hat, wie bei jeder Verbindung dorthin. Wenn du das nicht möchtest, lass den Ping-Wert ausgeschaltet.

## Speicherung und Löschung

Deine Einstellungen liegen im privaten Speicher der App auf deinem Telefon. Die Verlaufsgrafiken liegen nur im Arbeitsspeicher und verschwinden, wenn die App stoppt. Das Deinstallieren der App oder das Löschen ihrer Daten entfernt alles.

## Kinder

Die App richtet sich nicht an Kinder und sammelt von niemandem Daten.

## Änderungen und Kontakt

Ändert sich diese Erklärung, wird die neue Fassung mit neuem Datum in diesem Repository veröffentlicht. Fragen: Eröffne ein Issue unter https://github.com/ericbruggema/TaskBarStatsMobile/issues
