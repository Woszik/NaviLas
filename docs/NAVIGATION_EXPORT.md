# Eksport nawigacji (Checkpoint 3)

## Google Maps
`https://www.google.com/maps/dir/?api=1&destination=LAT,LON`

- SAMOCHÓD: parking BDL (jeśli powiązany ≤100 m) albo współrzędne miejsca
- MOTOCYKL: punkt najbliższej zakwalifikowanej drogi OSM

## OsmAnd
1. Intent `geo:LAT,LON?q=LAT,LON(Nazwa)` — [dokumentacja OsmAnd](https://osmand.net/docs/technical/algorithms/osmand-intents/)
2. Fallback: `https://osmand.net/map/?finish=LAT,LON&profile=car|motorcycle&pin=LAT,LON`

Bez SDK OsmAnd.

## calimoto / GPX
- Plik GPX 1.1 z jednym `<wpt>`
- `ACTION_SEND` + MIME `application/gpx+xml` przez Android Sharesheet / FileProvider
- Brak założonego deep-linku calimoto

## Testy ręczne na urządzeniu (checklist)
1. Wynik → NAWIGUJ → Google Maps otwiera trasę do właściwego celu (parking / miejsce / droga).
2. NAWIGUJ → OsmAnd (lub chooser) otwiera punkt.
3. NAWIGUJ → GPX pojawia się w Sharesheet; plik zawiera nazwę miejsca i współrzędne celu.
4. Profil MOTOCYKL bez odpowiedniej drogi: przycisk NAWIGUJ ukryty.
