# Rechtstexte – Entwürfe

> **ENTWURF, keine Rechtsberatung.** Diese Texte sind eine Arbeitsgrundlage für eine Anwältin oder einen
> Anwalt (IT- und Wettbewerbsrecht, Datenschutz). Sie beschreiben, was die Software tatsächlich tut
> (Stand: Code vom 28.10.2026), damit die Prüfung schneller geht. Veröffentlichen erst nach Freigabe.

| Datei | Wofür | Wo veröffentlichen |
|---|---|---|
| [impressum.md](impressum.md) | Anbieterkennzeichnung § 5 DDG | `DUKKAN_IMPRINT_URL`, z. B. `https://dukkan.de/impressum` |
| [datenschutz.md](datenschutz.md) | Datenschutzerklärung Art. 13/14 DSGVO (App, Web, Portal) | `DUKKAN_PRIVACY_URL` |
| [agb-kunden.md](agb-kunden.md) | Nutzungsbedingungen für Kundinnen und Kunden | `DUKKAN_TERMS_URL`, Version in `DUKKAN_TERMS_VERSION` |
| [agb-haendler.md](agb-haendler.md) | Händlerbedingungen (P2B-VO, DSA) | im Portal verlinken; mit jedem Pilotmarkt vereinbaren |

## Grundannahmen der Entwürfe (bitte prüfen lassen)

1. **Dukkan ist Vermittler, nicht Verkäufer.** Der Kaufvertrag kommt zwischen Kundin/Kunde und dem
   Markt zustande. Dukkan betreibt die Plattform. Das entspricht dem Code: Der Markt nimmt Bestellungen
   an oder lehnt sie ab, packt, liefert oder übergibt und kassiert.
2. **Bezahlung nur beim Markt** (bar oder Karte bei Abholung/Lieferung). Dukkan nimmt kein Geld an.
3. **Für Händler kostenlos** in der Startphase.
4. **Kein Alkohol, kein Tabak.**
5. **E-Mail-Bestätigung** ist Pflicht vor Bestellung und Markteröffnung.
6. **Keine KI, kein Tracking, keine Werbung, keine Cookies.**
7. **Lieferung:** noch offen. Die Entwürfe gehen davon aus, dass der Markt selbst liefert oder nur
   Abholung anbietet.

Platzhalter stehen in eckigen Klammern: `[Firma]`, `[Anschrift]` … Stellen, die eine Entscheidung
oder Prüfung brauchen, sind mit **⚖️ Prüfen:** markiert.

## Nach der Freigabe

1. Texte als Seiten auf der Domain veröffentlichen (z. B. `/impressum`, `/datenschutz`, `/agb`,
   `/haendler-agb`).
2. In der `.env` des Servers eintragen: `DUKKAN_IMPRINT_URL`, `DUKKAN_PRIVACY_URL`, `DUKKAN_TERMS_URL`,
   `DUKKAN_TERMS_VERSION` (z. B. `2026-11`) und `DUKKAN_SUPPORT_EMAIL`. Die Apps verlinken sie dann
   überall (`GET /platform`). Neue Konten müssen die AGB-Version bestätigen.
3. Bei jeder Änderung der AGB `DUKKAN_TERMS_VERSION` erhöhen.
