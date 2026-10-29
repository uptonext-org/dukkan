# Katalog aus Open Food Facts

`katalog-openfoodfacts.csv`: 105 echte Produkte türkischer, arabischer und persischer Marken, die in
Deutschland verkauft werden, für den gemeinsamen Katalog (`POST /admin/catalog/import`, gleiches
Format wie `start-katalog.csv`). Händler wählen sie unter „Produkt hinzufügen“ aus und setzen nur
noch Preis und Verfügbarkeit.

## Quelle und Lizenz

Daten von [Open Food Facts](https://world.openfoodfacts.org), abgerufen am 2026-10-29, lizenziert
unter der [Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/1-0/).
Die App nennt die Quelle unter Profil → Rechtliches → „Produktdaten“. Diese Datei ist eine
abgeleitete Datenbank und steht ebenfalls unter der ODbL.

## Auswahl

- Nur Produkte, die Open Food Facts als vollständig führt: Name, Marke, Menge, Kategorie und
  **Zutaten** bestätigt; gültiger EAN/GTIN-Barcode; in Deutschland verkauft.
- Marken: Yayla, Tat, Pınar, Ülker, Eti, Torku, Duru, Gazi, Tukaş, Koska, Doğuş, Egetürk, Efepaşa,
  Sera, Öncü, Çaykur, Marmarabirlik, Tadım, Durra, Cortas, Chtoura, Baladna, Al Yaman, Kasih,
  Sadaf, Ahmad Tea, Baraka. Von 457 Treffern erfüllten 106 alle Bedingungen; eines wurde
  verworfen, weil Name und Menge sich widersprachen (Durra Tahina 800 g/500 g).
- Allergene: aus Open Food Facts; wo das Feld leer war, aus den Original-Zutaten ergänzt (8 Produkte,
  z. B. Milch bei Ayran, Gluten bei Bulgur, Sesam bei Helva).

## Was nicht aus der Quelle stammt

- **Namen in fehlenden Sprachen** (meist Arabisch, oft Englisch) hat Claude übersetzt; die Marke
  steht im eigenen Feld, nicht im Namen. Bitte vor dem Import gegenlesen.
- **Kategorie** und **Küche** sind aus den Open-Food-Facts-Kategorien und der Marke zugeordnet.
- Keine Fotos und Beschreibungen (der CSV-Import nimmt keine Bilder auf).

Angaben auf der Verpackung haben Vorrang: Händler sind für die Allergenkennzeichnung der
angebotenen Ware verantwortlich (LMIV).
