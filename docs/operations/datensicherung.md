# Datensicherung, Aufbewahrung und Löschung

Stand: 23.10.2026. Dieses Dokument beschreibt, wie Dukkan Daten sichert, wie lange es sie aufbewahrt
und wie es sie löscht. Es ist die technische Grundlage für das Verzeichnis von Verarbeitungstätigkeiten
(Art. 30 DSGVO), die technischen und organisatorischen Maßnahmen (TOM, Art. 32 DSGVO) und den Abschnitt
„Speicherdauer“ der Datenschutzerklärung.

> **Keine Rechtsberatung.** Die Fristen in Abschnitt 3 sind Vorschläge oder technische Werte. Welche
> gesetzlichen Aufbewahrungspflichten für dich gelten, hängt vom Geschäftsmodell ab (wer Vertragspartner
> der Kundschaft ist, ob Dukkan Rechnungen stellt). Kläre das mit Steuerberatung und Datenschutzberatung
> und trag die Ergebnisse in die markierten Stellen ein.

## 1. Was gesichert wird

| Daten | Wo | Gesichert durch |
|---|---|---|
| Datenbank: Konten, Adressen, Märkte, Sortimente, Bestellungen, Benachrichtigungen | PostgreSQL 18 (Volume `postgres-data`) | nächtlicher `pg_dump` und verschlüsselte Offsite-Kopie |
| Hochgeladene Fotos (Logos, Produktbilder) | Volume `media` | verschlüsselte Offsite-Kopie |
| Lösch-Journal (IDs gelöschter Konten) | Volume `erasures` | verschlüsselte Offsite-Kopie, **wird beim Restore nicht überschrieben** |
| Konfiguration und Geheimnisse (`.env`) | nur auf dem Server | **du**: Passwortmanager, zusätzlich Papier im Safe für `RESTIC_PASSWORD` |
| Quellcode, Images | GitHub, GitHub Container Registry | GitHub |

Nicht gesichert werden Fotos und Videos von Einkaufslisten (werden gelesen und nicht gespeichert),
Sitzungs-Caches und Logs.

## 2. Wie gesichert wird (3-2-1)

- **3 Kopien:** die laufende Datenbank, die Dumps der letzten 7 Tage auf dem Server und die
  Offsite-Snapshots.
- **2 Medien/Orte:** Server-Volume und ein S3-Speicher bei einem anderen Anbieter oder an einem
  anderen Standort in der EU.
- **1 Kopie außer Haus und verschlüsselt:** restic verschlüsselt mit AES-256, bevor Daten den Server
  verlassen. Der Speicheranbieter sieht nur verschlüsselte Blöcke.
- **Tests:**
  - Jeden Sonntag wird der neue Dump automatisch in eine Testdatenbank eingespielt und geprüft.
  - Jeden Sonntag liest restic außerdem 10 % der Offsite-Daten zurück.
  - Eine vollständige Wiederherstellung auf einem neuen Server übst du vor dem Start und dann alle
    6 Monate (Protokoll unten).
- **Überwachung:** Mit `OFFSITE_HEALTHCHECK_URL` kommt eine Mail, wenn eine Nacht fehlschlägt oder
  ausfällt.
- **Ziele:**
  - Höchstens 24 Stunden Datenverlust (RPO).
  - Wiederherstellung innerhalb weniger Stunden (RTO); den tatsächlichen Wert trägst du nach der
    Übung ein.

Bedienung, Einrichtung und Restore: `infrastructure/production/BACKUP.md`.

## 3. Speicherdauer und Löschung

| Daten | Speicherdauer | Wie gelöscht | Rechtsgrundlage / Hinweis |
|---|---|---|---|
| Kundenkonto (Name, E-Mail, Sprache, Passwort-Hash) | bis zur Löschung durch die Person | „Konto löschen“ in App/Web: sofort anonymisiert | Art. 6 Abs. 1 lit. b; Art. 17 DSGVO |
| Lieferadressen im Adressbuch | bis die Person sie löscht oder das Konto löscht | sofort | Art. 6 Abs. 1 lit. b |
| Warenkörbe, Favoriten, Benachrichtigungen, Geräte-Token | bis zur Kontolöschung; gelesene Benachrichtigungen nach 180 Tagen | sofort bzw. nächtlicher Job | `dukkan.notifications.retention` |
| Bestellungen (Artikel, Beträge, Markt, Zeitfenster) | **festlegen** (Vorschlag: so lange, wie die Märkte sie für ihre Buchhaltung brauchen) | bei Kontolöschung ohne Name und Adresse; Beträge, PLZ und Ort bleiben | § 147 AO / § 257 HGB, falls Belege: je nach Art 6, 8 oder 10 Jahre. **Mit Steuerberatung klären**, ob Dukkan oder der Markt aufbewahrungspflichtig ist |
| Lieferadresse in abgeschlossenen Bestellungen | **festlegen**, z. B. 3 Jahre (Verjährung, § 195 BGB) | nächtlicher Job, wenn `DUKKAN_ORDER_ADDRESS_RETENTION=P3Y` gesetzt ist | Art. 5 Abs. 1 lit. c, e DSGVO (Datensparsamkeit) |
| Anmeldesitzungen (Refresh-Token) | 30 Tage nach letzter Nutzung | nächtlicher Job | technisch notwendig |
| Links für Passwort-Reset / E-Mail-Bestätigung | 30 Minuten / 3 Tage | nächtlicher Job | technisch notwendig |
| Zähler gegen Missbrauch (Login-Versuche je IP) | höchstens 1 Stunde | stündlicher Job | Art. 6 Abs. 1 lit. f (Sicherheit) |
| Technische Ereignisse (Outbox) | 14 Tage | nächtlicher Job | technisch notwendig |
| Server-Logs der Container | rotierend, höchstens 5 × 20 MB pro Dienst (je nach Last einige Tage bis Wochen) | automatisch überschrieben | Art. 6 Abs. 1 lit. f; Logs enthalten Konto-IDs, keine Namen oder Adressen |
| Lokale Datenbank-Dumps | 7 Tage | automatisch | Art. 32 DSGVO |
| Offsite-Backups | bis ca. 3 Monate (7 tägliche, 4 wöchentliche, 3 monatliche Stände) | automatisch (`restic forget --prune`) | Art. 32 DSGVO |
| Lösch-Journal (nur zufällige Konto-IDs und Zeitpunkt) | dauerhaft | – | nötig, damit Löschungen nach einem Restore erneut greifen |

### Löschung und Backups

Backups löscht man nicht einzeln: Sie sind verschlüsselt, nur für die Wiederherstellung da und
nach spätestens etwa 3 Monaten automatisch weg. Damit eine Wiederherstellung gelöschte Daten nicht
zurückbringt, schreibt Dukkan jede Kontolöschung in ein **Lösch-Journal** außerhalb der Datenbank.
Nach jedem Start, also auch nach einem Restore, löscht das Backend alle dort genannten Konten erneut
(`ErasureReplay`). Das Journal enthält nur die zufällige Konto-ID und den Zeitpunkt.

Diese Speicherdauer der Backups gehört in die Datenschutzerklärung, zum Beispiel:
„Sicherungskopien werden verschlüsselt in der EU gespeichert und nach spätestens drei Monaten
überschrieben. Gelöschte Konten werden bei einer Wiederherstellung erneut gelöscht.“

## 4. Technische und organisatorische Maßnahmen (Auszug für die TOM)

| Bereich | Maßnahme | Stand |
|---|---|---|
| Verschlüsselung in der Übertragung | HTTPS mit automatischen Zertifikaten (Caddy), HSTS; SMTP mit STARTTLS | umgesetzt |
| Verschlüsselung der Backups | restic AES-256, Schlüssel nur beim Betreiber | umgesetzt |
| Verschlüsselung der Server-Festplatte | beim Hosting-Anbieter aktivieren, sofern angeboten | **du** |
| Zugriffskontrolle | Datenbank nur im internen Docker-Netz, Actuator nicht öffentlich, Admin-Rolle nur für benannte Konten, Passwörter mit bcrypt, kurze Token-Laufzeit (15 min), Rate-Limits | umgesetzt |
| Server-Zugang | SSH nur mit Schlüssel, Firewall nur 22/80/443, automatische Sicherheitsupdates, wenige Personen | **du** |
| Verfügbarkeit, Belastbarkeit | Health-Checks, automatischer Neustart, Backups 3-2-1, Uptime-Überwachung | umgesetzt, Überwachung **du** |
| Wiederherstellbarkeit (Art. 32 Abs. 1 lit. c) | `restore.sh`, wöchentlicher automatischer Restore-Test | umgesetzt |
| Regelmäßige Überprüfung (lit. d) | Restore-Übung alle 6 Monate, Protokoll unten; Dependabot-Updates | **du** |
| Datensparsamkeit, Privacy by Default (Art. 25) | keine Tracker, kein Analytics; Einkaufslisten-Fotos werden nicht gespeichert; Fotos ohne Standort-Metadaten; Adressen in alten Bestellungen werden entfernt (konfigurierbar) | umgesetzt |
| Betroffenenrechte | Datenexport (Art. 15/20) und Kontolöschung (Art. 17) in App und Web | umgesetzt |
| Auftragsverarbeiter (Art. 28) | AV-Verträge mit Hosting, Backup-Speicher, E-Mail-Versand, ggf. Anthropic, Karten | **du** |
| Speicherort | Server und Backups in der EU | **du** (Anbieterwahl) |

## 5. Deine Aufgaben

- [ ] Server und Backup-Speicher in der EU bei **verschiedenen Anbietern oder Standorten** buchen, AV-Verträge abschließen
- [ ] `RESTIC_PASSWORD` erzeugen, im Passwortmanager und auf Papier im Safe aufbewahren
- [ ] Healthcheck einrichten (`OFFSITE_HEALTHCHECK_URL`), Alarm an mindestens zwei Personen
- [ ] Festplattenverschlüsselung beim Hoster aktivieren, falls angeboten
- [ ] Mit Steuerberatung klären: Aufbewahrungsfrist für Bestellungen; ggf. `DUKKAN_ORDER_ADDRESS_RETENTION` setzen
- [ ] Speicherdauer und Backups in die Datenschutzerklärung aufnehmen (Abschnitt 3)
- [ ] Verzeichnis von Verarbeitungstätigkeiten und TOM-Dokument aus diesem Text erstellen
- [ ] Restore-Übung vor dem Start, dann alle 6 Monate:

| Datum | Wer | Was (Snapshot, Server) | Dauer | Ergebnis |
|---|---|---|---|---|
| | | | | |
