# Dane OSM — drogi (Checkpoint 2)

## Źródło

| Element | Wartość |
|---|---|
| Dane | OpenStreetMap (ODbL) — https://www.openstreetmap.org/copyright |
| API zapytań | **Overpass API** (publiczna instancja FOSSGIS) |
| Endpoint | `https://overpass-api.de/api/interpreter` |
| Alternatywa (nieużywana domyślnie) | `https://overpass.kumi.systems/api/interpreter` / private.coffee |

**Nie** pobieramy całej Polski ani pełnego extractu.  
**Nie** używamy `tile.openstreetmap.org` do analizy dróg.

## Sposób zapytania

Dla zbioru punktowych POI BDL budujemy lokalne zapytanie Overpass QL:

```
[out:json][timeout:60];
(
  way(around:400,LAT1,LON1)[highway];
  way(around:400,LAT2,LON2)[highway];
  ...
);
out tags geom;
```

- Promień wokół POI: **400 m** (pokrywa próg REJECTED > 300 m)
- Batche: do **20** punktów na request (limit długości zapytania / obciążenia)
- `out tags geom` — tagi + geometria linii (bez full nodes osobno)
- `User-Agent`: `NaviLas/... (Android; prototype)`

Obszary „Zanocuj w Lesie” (`PoiGeometryKind.AREA`) **nie** są punktami wejściowymi do `around` — bez rankingu drogowego.

## Ograniczenia usługi

Źródło: https://wiki.openstreetmap.org/wiki/Overpass_API

- Wytyczna: **&lt; 10 000 zapytań/dzień** i **&lt; 1 GB/dzień** na publiczną instancję
- **2 równoległe sloty** na IP; HTTP **429** → odczekać ≥ 30 s
- Brak SLA — usługa społecznościowa, as-is
- Wymagana identyfikacja klienta (`User-Agent`)
- Prototyp: OK przy umiarkowanym użyciu; produkcja: własny Overpass / komercyjny provider / cache offline (poza zakresem CP2)

## Klasyfikacja (skrót)

Zob. `RoadClassifier` w kodzie:

| Klasa | Reguła (uproszczenie CP2) |
|---|---|
| `NOT_ROAD` | m.in. `footway`, `path`, `cycleway`, `steps`, `pedestrian`, `bridleway` |
| `MOTO_RESTRICTED` | `motorcycle=no`, `motor_vehicle=no` (bez `motorcycle=yes`), `access=no`/`private`, `vehicle=no` (bez wyjątku moto) |
| `MOTO_ALLOWED` | typowe drogi jezdne (`residential`, `tertiary`, …) bez restrykcji |
| `MOTO_UNKNOWN` | pozostałe / niejednoznaczne tagi |

## Suitability (progi konfigurowalne)

`<=50 m` EXCELLENT · `<=150 m` GOOD · `<=300 m` WEAK · `>300 m` / brak drogi / NOT_ROAD / RESTRICTED → REJECTED

## Przykład rzeczywisty (Checkpoint 2)

- **BDL POI:** `wiata turystyczna` (warstwa 15), `foreign_key=4f0521b1-d850-4783-a339-3fbe5f1e660c`
- **Współrzędne:** 52.202265, 21.181408 (Warszawa / Wawer)
- **Zapytanie:** `way(around:400,52.202265,21.181408)[highway]; out tags geom;`
- **Wynik Overpass:** 97 ways w 400 m (residential, service, track, path, footway, …)
- **Najbliższa geometria `highway`:** `way/383300409` `path` ≈ **3,5 m** → `NOT_ROAD` (ignorowana przy rankingu, jeśli istnieje droga jezdna)
- **Najbliższa droga jezdna (używana do oceny):** `way/281014857` `service` ≈ **11 m** → `MOTO_ALLOWED`, suitability **EXCELLENT** (≤50 m)
- **Przykład drogi lokalnej w otoczeniu:** `way/27548154` `residential` „Mieczysława Pożaryskiego”
