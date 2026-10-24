# Go-Live-Plan Dukkan

Stand: 23.10.2026. Dieser Plan führt von „läuft in der CI“ zu „echte Kundinnen bestellen in Hamburg“.
Er trennt, **was im Code erledigt ist** (Abschnitt 2), und **was nur du als Betreiber tun kannst**
(Abschnitt 3: Verträge, Konten, Texte, Geld). Die Reihenfolge in Abschnitt 4 ist die empfohlene.

> Hinweis: Ich bin keine Rechtsberatung. Die rechtlichen Punkte unten sind eine Checkliste,
> die Texte (Impressum, Datenschutzerklärung, AGB) brauchen eine Anwältin, einen Anwalt oder einen
> seriösen Generator.

## 1. Was live geht

| Teil | Für wen | Adresse (Beispiel) |
|---|---|---|
| Web-App (`apps/customer-web`) | Kundinnen und Kunden im Browser, auch auf dem Handy | `https://dukkan.de` |
| Android- und iOS-App (`apps/mobile`) | Kundinnen und Kunden, Händler unterwegs | Play Store, App Store |
| Händlerportal (`apps/web`) | Händler am Rechner, Admins | `https://haendler.dukkan.de` |
| Backend + Datenbank | alle | `https://dukkan.de/api/v1` |

Empfehlung für den Start: **ein Server in der EU** mit Docker (`infrastructure/production`), zuerst
die **Web-App und das Portal**. Die Store-Apps folgen, sobald die Developer-Konten freigeschaltet sind.
Die Prüfung im App Store dauert erfahrungsgemäß Tage, die Freischaltung neuer Konten oft länger.

## 2. Bereits umgesetzt (im Code)

| Punkt | Wo |
|---|---|
| Web-App für Kunden mit allen App-Screens, eigenen URLs, Karte, Fotos, Sprache und Video | `apps/customer-web` |
| Produktionsprofil `prod`: Startprüfung verweigert den Start bei Demo-Profil, Standard-Passwörtern, Platzhalter-JWT-Secret, fehlendem SMTP und Mail-Links, die nicht mit https beginnen | `application-prod.yml`, `ProductionReadinessCheck` |
| Keine erfundenen Demo-Märkte in der Produktion: Seed-Daten liegen getrennt (`db/demo`) und laufen in `prod` nicht | `db/demo/V3__seed_hamburg.sql`, Test `ProductionMigrationsIntegrationTest` |
| Echte Client-IP hinter dem Proxy: Login-Limits gelten pro Person, nicht für alle zusammen | `server.forward-headers-strategy: native` |
| Märkte gehen in `prod` erst nach Prüfung durch einen Admin online | `merchant-auto-approve: false` |
| Erster Admin ohne SQL: `DUKKAN_BOOTSTRAP_ADMIN_EMAIL` | `AdminBootstrap` |
| Swagger-UI in `prod` aus | `application-prod.yml` |
| Impressum, Datenschutz, AGB und Kontakt in App und Web verlinkt (`GET /platform`) | `features/legal` |
| AGB-Zustimmung bei der Registrierung (Version und Zeitpunkt gespeichert, 422 `TERMS_NOT_ACCEPTED` ohne Zustimmung) | V18, `TermsPolicy` |
| Bestell-E-Mails an Kundinnen und Kunden (de/en/ar): Eingangsbestätigung mit allen Bestelldaten und Verkäufer-Impressum (§ 312i, § 312f BGB), geänderte Bestellung nach Fehlartikeln, Absage durch den Markt, Stornierung | `notification/application/OrderMails.java` |
| Bestellknopf „Zahlungspflichtig bestellen“ (§ 312j BGB) und Hinweis „Du bezahlst direkt beim Markt“, sobald `DUKKAN_PAYMENT=ON_SITE` gesetzt ist | Warenkorb, Bestellbestätigung |
| Server-Setup: Caddy mit automatischem HTTPS, Backend, Web, Portal, PostgreSQL | `infrastructure/production` |
| Datensicherung: nächtlicher Dump, wöchentlicher automatischer Restore-Test, verschlüsselte Offsite-Kopie (restic), `restore.sh`; Lösch-Journal, damit ein Restore gelöschte Konten nicht zurückbringt; optionale Frist für Lieferadressen in alten Bestellungen | `BACKUP.md`, `docs/operations/datensicherung.md` |
| Docker-Images für Web und Portal (nginx, ohne Root, CSP und Security-Header) | `apps/customer-web/Dockerfile`, `apps/web/Dockerfile` |
| Release-Workflow: Tag `v1.0.0` testet, baut und veröffentlicht alle Images in der GitHub Container Registry | `.github/workflows/release.yml` |
| Signiertes Android-App-Bundle per Knopfdruck (sobald die Secrets gesetzt sind) | `.github/workflows/mobile-release.yml` |
| Release-Signierung, Version und API-Adresse für Store-Builds | `android/app/build.gradle`, `npm run release:api` |
| Android: Bestätigungslink aus der Willkommensmail öffnet die App (fehlte vorher) | `AndroidManifest.xml` |
| iOS-Datenschutz-Manifest um E-Mail, Name, Telefon, Adresse und Bestellungen ergänzt | `PrivacyInfo.xcprivacy` |
| Schon vorher vorhanden: Datenexport und Kontolöschung (Art. 15/17/20 DSGVO), Rate-Limits, Passwort-Reset, E-Mail-Bestätigung, strukturierte Logs, Prometheus-Metriken | B-51, B-53, B-39 |

## 3. Was du tun musst

### 3.1 Entscheidungen (zuerst, alles andere hängt daran)

1. **Bezahlung.** Empfehlung für den Start: **Bezahlung beim Markt** (bar oder Karte bei Abholung
   oder Lieferung), `DUKKAN_PAYMENT=ON_SITE`. Dukkan vermittelt dann nur, nimmt kein Geld an und
   braucht keinen Zahlungsdienstleister. Online-Zahlung (z. B. Stripe Connect, Mollie, Adyen) ist
   ein eigenes Projekt (Backlog B-43) mit Vertrag und regulatorischer Prüfung.
2. **Geschäftsmodell** (B-46): Provision, Monatsgebühr oder anfangs kostenlos. Das bestimmt die
   Händler-AGB.
3. **Lieferung:** Wer liefert, der Markt selbst? Dann steht im Händlervertrag, dass der Markt für
   die Lieferung verantwortlich ist.
4. **Alkohol und Tabak:** Erst einmal **keine** altersbeschränkten Produkte zulassen, oder eine
   Altersprüfung bei Übergabe vereinbaren. Sag mir, wenn du sie willst; dafür baue ich Kennzeichnung
   und Hinweis ein.
5. **Händlerprüfung:** Wer prüft neue Märkte (Gewerbeanmeldung, Impressum, Lebensmittelhygiene)
   und wie schnell? In `prod` ist die Prüfung durch einen Admin Pflicht. Das Portal zeigt dabei eine
   Prüfliste (Name/Firma, Anschrift, Telefon, Register) und gibt einen Markt erst frei, wenn die
   Pflichtangaben vollständig sind. Gewerbeanmeldung und Hygiene prüfst du weiter selbst.
6. **E-Mail-Bestätigung Pflicht?** Empfehlung: ja (`DUKKAN_REQUIRE_VERIFIED_EMAIL=true`).
7. **KI-Funktionen** (Einkaufsliste per Foto/Video, Produkterfassung, Übersetzung): Für den Start
   ohne KI möglich, die Einkaufsliste funktioniert dann für getippte und gesprochene Listen. Mit KI
   brauchst du einen Anthropic-Account, `DUKKAN_ANTHROPIC_API_KEY` und einen AV-Vertrag (DPA).

### 3.2 Recht und Verträge

- [ ] **Firma und Gewerbe:** Wer betreibt Dukkan (GmbH, UG, Einzelunternehmen)? Das steht im Impressum.
- [ ] **Impressum** (§ 5 DDG), **Datenschutzerklärung**, **AGB für Kundinnen und Kunden**,
      **Händler-AGB** (inkl. Pflichten nach P2B-Verordnung und DSA: Ranking-Kriterien, Beschwerdeweg,
      Identität der Händler). Als Seiten auf deiner Domain veröffentlichen und die Adressen in
      `DUKKAN_IMPRINT_URL`, `DUKKAN_PRIVACY_URL` und `DUKKAN_TERMS_URL` eintragen; `DUKKAN_TERMS_VERSION`
      setzen (z. B. `2026-11`).
- [ ] In die Datenschutzerklärung gehören je nach Nutzung: Hosting-Anbieter, E-Mail-Versand,
      Kartenkacheln, bei aktivierter KI Anthropic (Drittlandübermittlung USA), bei Push Google/Firebase,
      auf Android Google Maps. Außerdem die **Spracherkennung im Browser**: Chrome und Safari können
      das Audio an Google bzw. Apple senden. Nur der Text geht an Dukkan.
- [ ] **AV-Verträge (Art. 28 DSGVO)** mit Hosting, E-Mail-Dienst, ggf. Anthropic, Kartenanbieter.
- [ ] **Verzeichnis von Verarbeitungstätigkeiten** und die Aufbewahrungsfristen (Bestellungen,
      Backups 14 Tage, Löschkonzept). Datenexport und Kontolöschung sind eingebaut.
- [ ] **Widerrufsrecht:** Für schnell verderbliche Lebensmittel gibt es Ausnahmen (§ 312g BGB). Wer
      Vertragspartner ist (der Markt oder Dukkan) und wie die Belehrung aussieht, klärt die Rechtsberatung.
- [ ] **Händlerverträge** mit den ersten Märkten (Pilot), inkl. Preise inkl. MwSt., Grundpreise und
      Allergene korrekt pflegen.
- [ ] **Marke:** Name „Dukkan“ markenrechtlich prüfen, Logo, App-Icon (die Icons sind noch Platzhalter).

### 3.3 Konten und Infrastruktur

- [ ] **Domain** (z. B. `dukkan.de`) und eine Subdomain fürs Portal; DNS-Einträge auf den Server.
- [ ] **Server in der EU** (z. B. Hetzner, IONOS, OVH): Ubuntu 24.04, Docker, Firewall nur 22/80/443,
      SSH nur mit Schlüssel, automatische Sicherheitsupdates.
- [ ] **Backup-Speicher (S3) in der EU** bei einem anderen Anbieter oder Standort, `RESTIC_*` in der
      `.env`, Schlüssel sicher verwahren, Healthcheck einrichten und **eine Wiederherstellung üben**
      (`infrastructure/production/BACKUP.md`, Konzept und Fristen: `docs/operations/datensicherung.md`).
- [ ] **E-Mail-Versand** (z. B. Brevo, Mailjet, Postmark, Amazon SES EU) mit SPF, DKIM und DMARC für
      deine Domain; `SPRING_MAIL_*` und `DUKKAN_MAIL_FROM` setzen.
- [ ] **Kartenkacheln** für die Web-App: ein Anbieter mit Vertrag (z. B. MapTiler, Stadia Maps),
      `DUKKAN_MAP_TILE_URL` und die vorgeschriebene Quellenangabe. Die OpenStreetMap-Server sind
      nicht für Produktionslast gedacht.
- [ ] **Uptime-Überwachung** mit Alarm per SMS oder E-Mail (z. B. UptimeRobot, Better Stack).
- [ ] **GitHub:** Repository-Einstellung „Dependency graph“ einschalten (macht den roten Workflow grün),
      Dependabot-Alerts aktivieren; die Packages unter `ghcr.io/uptonext-org` privat lassen und auf dem
      Server mit einem Token einloggen.

### 3.4 Stores (danach)

- [ ] **Google Play Console** (Einmalgebühr) und **Apple Developer Program** (Jahresgebühr), am besten
      als Organisation (D-U-N-S-Nummer nötig, das dauert).
- [ ] **Android:** Upload-Keystore erzeugen (`keytool -genkeypair -v -keystore upload.jks -alias upload
      -keyalg RSA -keysize 4096 -validity 10000`), sicher aufbewahren. Als Repository-Secrets hinterlegen:
      `DUKKAN_UPLOAD_KEYSTORE_BASE64` (`base64 -w0 upload.jks`), `DUKKAN_UPLOAD_STORE_PASSWORD`,
      `DUKKAN_UPLOAD_KEY_ALIAS`, `DUKKAN_UPLOAD_KEY_PASSWORD`. Dazu einen **Google-Maps-Key** für Android,
      auf die App beschränkt (`DUKKAN_MAPS_API_KEY`). Dann Actions → „Mobile store build“.
- [ ] **iOS:** Team-ID in Xcode eintragen, Bundle-ID `app.dukkan.mobile` registrieren, in Xcode
      archivieren und hochladen. Vorher `npm run release:api -- https://dukkan.de/api/v1`.
- [ ] **Store-Einträge:** Beschreibung (de/en/ar), Screenshots, Datenschutz-Angaben (Apple
      „App Privacy“, Google „Data safety“: entspricht `PrivacyInfo.xcprivacy`), Support- und
      Datenschutz-URL, Altersfreigabe.
- [ ] **Push-Benachrichtigungen** (optional zum Start): Firebase-Projekt und APNs-Key. Danach baue ich
      den nativen Teil ein (B-37a). Bis dahin landen Benachrichtigungen im In-App-Postfach.
- [ ] **App Links / Universal Links** (optional): Damit Links aus Mails direkt die App öffnen statt
      der Web-App, brauche ich die endgültige Domain und deine Apple-Team-ID.

## 4. Reihenfolge

| Phase | Was | Wer |
|---|---|---|
| 0. Entscheiden | 3.1 Punkte 1–7 | du |
| 1. Recht | Firma, Texte, AV-Verträge, Pilot-Händlerverträge | du (mit Rechtsberatung) |
| 2. Infrastruktur | Domain, Server, E-Mail, Karten, `.env`, `docker compose up -d` | du, ich helfe bei Fehlern |
| 3. Release 1.0.0 | PRs mergen, `git tag v1.0.0 && git push origin v1.0.0` | ich, auf dein OK |
| 4. Einrichten | Admin-Konto, `DUKKAN_BOOTSTRAP_ADMIN_EMAIL`, Pilotmärkte registrieren, prüfen, Sortiment anlegen (CSV-Import oder KI-Erfassung im Portal) | du + Händler |
| 5. Probebetrieb | 2–4 Wochen mit 2–3 Märkten und eingeladenen Testkunden; echte Bestellungen, Bezahlung beim Markt | alle |
| 6. Launch | Web öffentlich, Store-Apps einreichen, Marketing | du |
| 7. Betrieb | Uptime-Alarme, Backups prüfen, Dependabot-PRs, Händler-Support | du, ich für Code |

Wichtig für Phase 4: In der Produktion gibt es **keinen gemeinsamen Produktkatalog**, die
erfundenen Demo-Produkte laufen dort nicht. Händler legen ihr Sortiment per CSV-Import oder
KI-Erfassung an. Admins können gute Produkte in den gemeinsamen Katalog übernehmen
(Katalogprüfung im Portal).

## 5. Checkliste für den Starttag

- [ ] `docker compose logs backend`: Start ohne „Production profile refused to start“
- [ ] `https://<domain>` lädt, Zertifikat gültig, `https://<domain>/actuator/health` antwortet 404
- [ ] Registrieren mit AGB-Haken, Bestätigungsmail kommt an (auch nicht im Spam-Ordner), Link öffnet die Web-App
- [ ] Testbestellung: Eingangsbestätigung per E-Mail kommt an, mit Artikeln, Betrag, Zeitfenster und Impressum des Markts
- [ ] „Passwort vergessen“: die Mail kommt an, der Link funktioniert
- [ ] Testbestellung im Pilotmarkt: Knopf „Zahlungspflichtig bestellen“, Händler sieht sie im Portal, Statuswechsel kommen beim Kunden an
- [ ] Impressum, Datenschutz und AGB sind im Profil verlinkt und erreichbar
- [ ] Datenexport und Kontolöschung einmal mit einem Testkonto ausprobiert
- [ ] Nächtliches Backup und Offsite-Snapshot vorhanden (`docker compose logs backup offsite`, `docker compose exec offsite restic snapshots`), Healthcheck meldet „up“
- [ ] Uptime-Alarm getestet (Backend kurz stoppen)

## 6. Bekannte Grenzen und nach dem Launch

- Online-Zahlung (B-43), Lieferlogistik (B-44), Bewertungen (B-45)
- Native Push (B-37a) und „Mit Apple/Google anmelden“ in den Apps (B-39a): brauchen deine Konten
- Bilder über ein CDN und WebP (B-32b); bei mehr als einem Server S3-Speicher statt Volume
- Geräte-Tests und Detox-Tests vor dem Store-Release (B-28a)
- In der Web-App zeigen Bestätigungsdialoge die Browser-Dialoge, keine eigenen
