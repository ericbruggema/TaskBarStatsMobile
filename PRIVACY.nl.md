# Privacyverklaring: TaskBarStatsMobile

[English](PRIVACY.md) · **Nederlands** · [Deutsch](PRIVACY.de.md)

_Laatst bijgewerkt: 7 oktober 2026_

TaskBarStatsMobile toont live statistieken van je eigen telefoon. De app is open source (MIT):
https://github.com/ericbruggema/TaskBarStatsMobile

## In het kort

- **Wij verzamelen niets.** De app heeft geen account, geen analyse, geen reclame, geen crashrapportage en geen SDK's van derden.
- **Alles blijft op je toestel.** De statistieken, je instellingen en de geschiedenis die in de app te zien is, staan alleen op je telefoon en worden nergens heen gestuurd.
- **Er wordt niets gedeeld of verkocht.**

## Wat de app leest en waarom

Dit wordt alleen op je eigen scherm getoond en nergens anders:

| Wat | Waarom | Toestemming |
|---|---|---|
| Geheugen, opslag, batterijtemperatuur, uptime, netwerksnelheid, wifi- / mobiele verbinding (signaal, snelheid) | de live cijfers | geen, of netwerk- / wifistatus |
| Dataverbruik per dag en per app, opslag en cache per app, schermtijd, lijst met geïnstalleerde apps | het tabblad Apps en de datategels | Gebruikstoegang (zet je zelf aan), geïnstalleerde apps |
| CPU-gebruik en de proceslijst | optioneel, alleen met de aparte Shizuku-app | Shizuku (geef je zelf toestemming voor) |
| Meldingen | de live melding, statusbalkiconen en je waarschuwingen | Meldingen |
| Tekenen over andere apps | de optionele tekststrip in de statusbalk | Weergeven over andere apps |

Elke speciale toestemming is optioneel en kun je intrekken in de instellingen van Android; de app verbergt dan gewoon de functies die er afhankelijk van zijn.

## De netwerkverbindingen

Om de ping te meten opent de app een korte TCP-verbinding naar `1.1.1.1` (de openbare DNS-dienst van Cloudflare) op poort 443 en meet hoe lang dat duurt. Er wordt geen persoonlijke informatie en geen identificatie verstuurd. Cloudflare kan zien dat jouw IP-adres met zijn dienst verbond, zoals bij elke verbinding ermee. Wil je dat niet, laat het pingonderdeel dan uit.

**Updatecontrole (standaard uit).** Zet je *Op updates controleren* aan, dan vraagt de app één keer per dag bij `api.github.com` naar de nieuwste release van deze repository en meldt een nieuwe versie één keer. Alleen het verzoek zelf wordt verstuurd (zoals bij elk webverzoek kan GitHub je IP-adres zien); niets over jou of je telefoon. De app installeert nooit zelf iets: *Downloaden en installeren* opent de APK-link in je browser en Android vraagt om je bevestiging. De controle is niet beschikbaar als de app uit de Google Play Store is geïnstalleerd.

## Opslag en verwijdering

Je instellingen staan in de privéopslag van de app op je telefoon. De geschiedenisgrafieken staan alleen in het geheugen en verdwijnen als de app stopt. De app verwijderen of zijn gegevens wissen verwijdert alles.

## Kinderen

De app is niet op kinderen gericht en verzamelt van niemand gegevens.

## Wijzigingen en contact

Als deze verklaring verandert, wordt de nieuwe versie met een nieuwe datum in deze repository gepubliceerd. Vragen: open een issue op https://github.com/ericbruggema/TaskBarStatsMobile/issues
