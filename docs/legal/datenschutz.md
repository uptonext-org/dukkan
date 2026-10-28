# Datenschutzerklärung

> ENTWURF – vor Veröffentlichung von einer Datenschutzberatung prüfen lassen. Platzhalter in
> [eckigen Klammern]. Er beschreibt die Datenverarbeitung, wie sie im Code umgesetzt ist (Stand
> 28.10.2026, KI aus, kein Tracking). Ändert sich die Konfiguration (Push, KI, Zahlungsdienst,
> Analyse), muss dieser Text angepasst werden.

Stand: [Datum]

## 1. Verantwortlicher

[Firma]
[Anschrift]
E-Mail: [datenschutz@…] · Telefon: [+49 …]

Datenschutzbeauftragte/r: [Name und Kontakt, falls bestellt].
⚖️ **Prüfen:** Ist eine Benennung nötig (§ 38 BDSG)? In der Regel erst ab 20 Personen, die ständig mit
Daten arbeiten.

## 2. Worum es geht

Dukkan ist ein Marktplatz für lokale Lebensmittelmärkte in Hamburg. Sie können über die App
(Android, iOS), die Web-App und das Händlerportal Märkte finden, Produkte ansehen und bei einem Markt
zur Abholung oder Lieferung bestellen. Den Kaufvertrag schließen Sie mit dem Markt. Bezahlt wird
direkt beim Markt.

Wir verarbeiten nur die Daten, die für diese Dienste nötig sind. **Wir setzen keine Cookies, kein
Tracking, keine Analyse- oder Werbedienste und keine künstliche Intelligenz ein.** Wir verkaufen
keine Daten.

## 3. Welche Daten wir wofür verarbeiten

### 3.1 Aufruf von App, Web-App und Portal

Beim Aufruf verarbeitet unser Server technisch notwendige Daten:
- IP-Adresse
- Zeitpunkt und aufgerufene Adresse
- Browser bzw. App-Version

Zweck ist die Auslieferung der Inhalte und die Sicherheit des Dienstes.

- Rechtsgrundlage: Art. 6 Abs. 1 lit. f DSGVO. Unser berechtigtes Interesse ist ein sicherer und
  stabiler Betrieb.
- Speicherdauer: Server-Protokolle werden fortlaufend überschrieben (Rotation), spätestens nach
  [14] Tagen.
- ⚖️ **Prüfen:** Speicherdauer und Rotation passend zur tatsächlichen Konfiguration festlegen.

### 3.2 Kundenkonto

Für ein Konto verarbeiten wir:
- Vor- und Nachname, E-Mail-Adresse und Passwort (nur als Hash gespeichert)
- bevorzugte Sprache
- Zeitpunkt der Bestätigung Ihrer E-Mail-Adresse
- Version und Zeitpunkt Ihrer Zustimmung zu den Nutzungsbedingungen

Freiwillig und nur wenn Sie sie anlegen:
- Lieferadressen
- Favoriten (Märkte und Produkte)
- zuletzt besuchte Märkte
- Einstellungen für Mitteilungen

Rechtsgrundlage: Art. 6 Abs. 1 lit. b DSGVO (Nutzungsvertrag).

Wir senden Ihnen eine E-Mail, um Ihre Adresse zu bestätigen. Erst danach können Sie bestellen.

**Anmeldung mit Apple oder Google:** Wenn Sie diese Anmeldung wählen, erhalten wir von Apple bzw.
Google eine Kennung, Ihren Namen und Ihre E-Mail-Adresse. Es gelten zusätzlich die
Datenschutzhinweise dieser Anbieter. Apple bzw. Google erfahren dabei, dass Sie sich bei Dukkan
anmelden. Rechtsgrundlage: Art. 6 Abs. 1 lit. b DSGVO.

### 3.3 Bestellungen

Für eine Bestellung verarbeiten wir:
- Warenkorb
- Abholung oder Lieferung, Zeitfenster
- bei Lieferung die Lieferadresse
- Bestellstatus
- Änderungen durch den Markt, etwa fehlende Artikel

**Weitergabe an den Markt:** Der Markt, bei dem Sie bestellen, erhält Ihren Namen, die Bestellung und
bei Lieferung Ihre Adresse. Er braucht diese Angaben, um die Bestellung zu packen, zu übergeben oder zu
liefern und abzurechnen. Für seine eigene Verarbeitung (z. B. Kasse, Buchhaltung) ist der Markt
selbst verantwortlich. Wer der Markt ist, steht in der App auf der Seite des Markts und in der
Bestellbestätigung.

⚖️ **Prüfen:** Rollenverteilung zwischen Dukkan und Markt. Gemeinsame Verantwortung nach Art. 26 oder
Übermittlung an einen eigenständig Verantwortlichen? Die Händler-AGB regeln das passend dazu.

Wir schicken Ihnen E-Mails zur Bestellung: Eingang, Änderung, Absage, Stornierung. Auf Wunsch kommen
Mitteilungen in der App dazu.

- Rechtsgrundlage: Art. 6 Abs. 1 lit. b DSGVO.
- Speicherdauer: Bestellungen bewahren wir für die gesetzlichen Aufbewahrungspflichten des Markts und
  für eigene Nachweise auf (Art. 6 Abs. 1 lit. c DSGVO; bis zu [10] Jahre für buchungsrelevante
  Angaben, § 147 AO, § 257 HGB).
- Lieferadressen in abgeschlossenen Bestellungen löschen wir nach [Frist, z. B. 3 Jahren].
- ⚖️ **Prüfen:** Fristen mit der Steuerberatung festlegen. Technisch: `DUKKAN_ORDER_ADDRESS_RETENTION`.

### 3.4 Feedback an einen Markt

Wenn ein Markt es einschaltet, können Sie ihm eine Nachricht und eine Bewertung schicken. Der Markt
sieht Ihren Vornamen, die Nachricht und die Bewertung. Rechtsgrundlage: Art. 6 Abs. 1 lit. b DSGVO.

### 3.5 Standort

Die App fragt nach Ihrem Standort, um Märkte in der Nähe zu zeigen und Adressen vorzuschlagen. Das
geschieht nur, wenn Sie es auf Ihrem Gerät erlauben. Wir übertragen den Standort für die Suche und
speichern ihn nicht. Adressen suchen wir in einem eigenen Straßenverzeichnis; dafür nutzen wir keinen
externen Kartendienst.

Rechtsgrundlage: Art. 6 Abs. 1 lit. a DSGVO (Ihre Einwilligung über die Berechtigung des Geräts). Sie
können sie jederzeit in den Einstellungen des Geräts widerrufen.

### 3.6 Spracheingabe im Browser

In der Web-App können Sie Suchbegriffe oder Ihre Einkaufsliste diktieren, sofern diese Funktion
eingeschaltet ist. Die Spracherkennung übernimmt Ihr Browser. Je nach Browser schickt er die Aufnahme
an den Hersteller zur Erkennung, z. B. Google (Chrome) oder Apple (Safari). Dukkan erhält nur den
erkannten Text. Für die Verarbeitung durch den Browserhersteller gelten dessen Datenschutzhinweise.

### 3.7 Händlerinnen und Händler

Für Händlerkonten verarbeiten wir zusätzlich die Angaben zum Markt:
- Name, Anschrift, Telefon
- rechtlicher Name, Registerangaben
- Öffnungszeiten, Sortiment, Preise
- Fotos, die Sie hochladen

Name, Anschrift und rechtliche Angaben des Markts werden Kundinnen und Kunden angezeigt (Impressum des
Markts). Das verlangen § 5 DDG und Art. 30 DSA.

Rechtsgrundlage: Art. 6 Abs. 1 lit. b und c DSGVO.

### 3.8 Sicherheit

Zum Schutz vor Missbrauch protokollieren wir sicherheitsrelevante Ereignisse:
- fehlgeschlagene Anmeldungen
- erreichte Anfragegrenzen
- verweigerte Zugriffe
- Änderungen durch Administratoren

Gespeichert werden Konto-Kennung, IP-Adresse, Zeitpunkt und Pfad. E-Mail-Adressen und Passwörter
landen nicht im Protokoll. Außerdem zählen wir Anmeldeversuche je IP-Adresse und je Konto, um
Passwort-Raten zu verhindern.

Rechtsgrundlage: Art. 6 Abs. 1 lit. f DSGVO (Schutz der Konten und des Dienstes). Speicherdauer wie
unter 3.1.

### 3.9 Speicherung auf Ihrem Gerät

Die App und die Web-App speichern auf Ihrem Gerät nur, was für den Dienst nötig ist:
- Anmeldung (Token): in der App im sicheren Speicher des Geräts, im Browser im lokalen Speicher
- gewählte Sprache und Einstellungen

Das ist ohne Einwilligung erlaubt (§ 25 Abs. 2 Nr. 2 TDDDG). Cookies setzen wir nicht.

### 3.10 Mitteilungen (Push)

⚖️ **Nur aufnehmen, wenn Push eingeschaltet wird** (`DUKKAN_FCM_PROJECT_ID`).

Wenn Sie Mitteilungen erlauben, speichern wir eine Gerätekennung und senden die Mitteilungen über
Firebase Cloud Messaging (Google Ireland Ltd.; Übermittlung in die USA möglich, Grundlage:
EU-US Data Privacy Framework bzw. Standardvertragsklauseln). Sie können Mitteilungen in der App oder
in den Geräteeinstellungen abschalten.

Rechtsgrundlage: Art. 6 Abs. 1 lit. a DSGVO.

### 3.11 Karten

⚖️ **Nur aufnehmen, wenn ein Kartendienst eingetragen ist** (`DUKKAN_MAP_TILE_URL`).

Die Web-App lädt Kartenausschnitte von [Anbieter, Sitz]. Dabei erhält der Anbieter Ihre IP-Adresse.
Auf Android zeigt die App Karten über Google Maps (Google Ireland Ltd.).

Rechtsgrundlage: Art. 6 Abs. 1 lit. f DSGVO (Darstellung der Standorte der Märkte).

## 4. Empfänger und Auftragsverarbeiter

Neben dem jeweiligen Markt (siehe 3.3) setzen wir Dienstleister ein, die Daten nur nach unserer
Weisung verarbeiten (Art. 28 DSGVO):

| Dienst | Anbieter | Zweck | Ort |
|---|---|---|---|
| Hosting (Server, Datenbank) | [Anbieter] | Betrieb von App, Web-App und Portal | [Deutschland/EU] |
| E-Mail-Versand | [Anbieter] | Bestätigungs-, Passwort- und Bestell-E-Mails | [EU] |
| Datensicherung | [Anbieter] | verschlüsselte Kopie außerhalb des Servers | [EU] |
| Karten (optional) | [Anbieter] | Kartenausschnitte | [EU] |
| Push (optional) | Google (Firebase) | Mitteilungen | EU/USA |

Die Datensicherung ist vor der Übertragung verschlüsselt; der Anbieter kann sie nicht lesen.

## 5. Datensicherung

- Wir sichern die Datenbank jede Nacht.
- Sicherungen bleiben 7 Tage auf dem Server.
- Eine verschlüsselte Kopie liegt bei einem zweiten Anbieter in der EU. Davon behalten wir 7 tägliche,
  4 wöchentliche und 3 monatliche Stände.
- Löschen Sie Ihr Konto, löschen wir es nach einer Wiederherstellung aus einer älteren Sicherung
  automatisch erneut.

## 6. Ihre Rechte

Sie haben das Recht auf:
- Auskunft (Art. 15)
- Berichtigung (Art. 16)
- Löschung (Art. 17)
- Einschränkung der Verarbeitung (Art. 18)
- Datenübertragbarkeit (Art. 20)
- Widerspruch gegen Verarbeitungen auf Grundlage berechtigter Interessen (Art. 21)
- Widerruf erteilter Einwilligungen mit Wirkung für die Zukunft (Art. 7 Abs. 3)

**Direkt in der App:**
- **Daten herunterladen:** Unter „Konto“ erhalten Sie alle über Sie gespeicherten Daten als Datei.
- **Konto löschen:** Unter „Konto“ löschen Sie Ihr Konto sofort. Name, E-Mail-Adresse, Adressen,
  Favoriten, Geräte und Feedback werden entfernt. Bestellungen bleiben ohne Ihre persönlichen Angaben
  erhalten, soweit Aufbewahrungspflichten bestehen.

Sie können sich bei einer Datenschutz-Aufsichtsbehörde beschweren. Für uns zuständig ist: Der
Hamburgische Beauftragte für Datenschutz und Informationsfreiheit, Ludwig-Erhard-Straße 22, 20459
Hamburg.

## 7. Pflicht zur Angabe, automatisierte Entscheidungen

Ohne Konto und die Angaben zur Bestellung können wir keine Bestellung vermitteln. Darüber hinaus sind
Angaben freiwillig. Wir treffen keine automatisierten Entscheidungen und erstellen keine Profile im
Sinne von Art. 22 DSGVO.

## 8. Änderungen

Wir passen diese Erklärung an, wenn sich der Dienst ändert. Es gilt die jeweils hier veröffentlichte
Fassung.
