# Privacy policy: TaskBarStatsMobile

**English** · [Nederlands](PRIVACY.nl.md) · [Deutsch](PRIVACY.de.md)

_Last updated: 7 October 2026_

TaskBarStatsMobile shows live statistics of your own phone. It is open source (MIT):
https://github.com/ericbruggema/TaskBarStatsMobile

## In short

- **We collect nothing.** The app has no account, no analytics, no advertising, no crash reporting and no third-party SDKs.
- **Everything stays on your device.** The statistics, your settings and the history shown in the app are kept only on your phone and are never sent anywhere.
- **No data is shared or sold.**

## What the app reads, and why

All of this is shown on your own screen and nowhere else:

| What | Why | Permission |
|---|---|---|
| Memory, storage, battery temperature, uptime, network speed, Wi-Fi / mobile connection (signal, speed) | the live figures | none, or Network / Wi-Fi state |
| Data use per day and per app, storage and cache per app, screen time, list of installed apps | the Apps tab and the data tiles | Usage access (you switch it on yourself), installed apps |
| CPU use and the process list | optional, only with the separate Shizuku app | Shizuku (you allow it yourself) |
| Notifications | the live notification, status bar icons and your alerts | Notifications |
| Drawing over other apps | the optional text strip in the status bar | Display over other apps |

Every special permission is optional and can be withdrawn in Android's settings; the app then simply hides the features that need it.

## The network connections

To measure the ping the app opens a short TCP connection to `1.1.1.1` (Cloudflare's public DNS service) on port 443 and measures how long it takes. No personal data and no identifier is sent. Cloudflare can see that your IP address connected to its service, like any connection to it. If you do not want this, leave the ping item off.

**Update check (off by default).** If you switch on *Check for updates*, the app asks `api.github.com` once a day for the latest release of this repository and tells you once per new version. Only the request itself is sent (like any web request, GitHub can see your IP address); nothing about you or your phone. The app never installs anything by itself: *Download and install* opens the APK link in your browser and Android asks for your confirmation. The check is not available when the app was installed from the Google Play Store.

## Storage and deletion

Your settings live in the app's private storage on your phone. The history graphs are kept in memory only and disappear when the app stops. Uninstalling the app or clearing its data removes everything.

## Children

The app is not directed at children and collects no data from anyone.

## Changes and contact

If this policy changes, the new version is published in this repository with a new date. Questions: open an issue at https://github.com/ericbruggema/TaskBarStatsMobile/issues
