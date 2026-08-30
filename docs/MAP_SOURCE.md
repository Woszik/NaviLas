# Źródło mapy (Checkpoint 1A)

## Rozdzielenie warstw

| Warstwa | Wybór | Uwagi |
|---|---|---|
| **Silnik mapy** | MapLibre Native Android (`org.maplibre.gl:android-sdk:11.8.6`) | Renderuje styl + kafelki; nie jest źródłem danych OSM. |
| **Styl mapy** | OpenFreeMap **Liberty / Dark** | Styl jest wybierany automatycznie zgodnie z motywem aplikacji. Oba pliki JSON są zgodne ze specyfikacją MapLibre. |
| **Źródło kafelków / danych** | OpenFreeMap / OpenMapTiles (wektor) + Natural Earth (raster cieniowania) | Dane mapowe pochodzą z OpenStreetMap (przetworzone do schematu OpenMapTiles). |

Kod: `pl.navilas.finder.map.MapConfig`.

## URL używany w aplikacji

- **Style URL (ładowany przez MapLibre):**  
  `https://tiles.openfreemap.org/styles/liberty`
- **Źródło wektorowe (zdefiniowane w stylu, TileJSON):**  
  `https://tiles.openfreemap.org/planet`
- **Raster Natural Earth (ze stylu):**  
  `https://tiles.openfreemap.org/natural_earth/ne2sr/{z}/{x}/{y}.png`

**Nie** używamy `tile.openstreetmap.org` ani `demotiles.maplibre.org` (demo MapLibre).

## Skąd pochodzi mapa

1. **Dane geograficzne:** OpenStreetMap (ODbL) — https://www.openstreetmap.org/copyright  
2. **Hosting kafelków + styl:** OpenFreeMap (projekt open-source, publiczna instancja) — https://openfreemap.org/  
3. **Schemat kafelków:** OpenMapTiles (bez modyfikacji schematu po stronie OpenFreeMap)  
4. **Silnik renderujący:** MapLibre Native

## Zasady użycia (stan na Checkpoint 1A)

### OpenFreeMap (publiczna instancja)
- Źródło zasad: https://openfreemap.org/ oraz https://openfreemap.org/tos.html (ToS, 26.02.2025)
- Publiczna instancja: **bezpłatna**, bez limitu view/requestów deklarowanego na stronie, **bez rejestracji / API key**
- **Użycie komercyjne: dozwolone** (FAQ OpenFreeMap)
- Usługa **as-is**, **bez SLA** / gwarancji dostępności; usługa może zostać wyłączona
- **Atrybucja wymagana**; przy MapLibre atrybucja jest dodawana automatycznie
  - Treść referencyjna: `OpenFreeMap © OpenMapTiles Data from OpenStreetMap`
- Zakaz nadużyć / nielegalnego scrapingu (ToS)

### OpenStreetMap (dane)
- Dane OSM: licencja **ODbL** — wymagana atrybucja „© OpenStreetMap contributors”
- **Uwaga:** serwery kafelków `tile.openstreetmap.org` mają osobną, restrykcyjną politykę i **nie** są używane w tej aplikacji

### MapLibre
- Silnik open-source (BSD); nie dostarcza kafelków produkcyjnych OSM

## Czy nadaje się do prototypu?

**Tak.** OpenFreeMap Liberty/Dark to rzeczywista mapa OSM (nie demotiles), bez klucza API, z jasną atrybucją i pozwoleniem na użycie w tym etapie.

## Czy nadaje się do produkcji?

**Warunkowo / częściowo.**
- **OK krótkoterminowo / soft-launch**, o ile zachowana atrybucja i rozsądny ruch.
- **Dla stabilnej produkcji zalecane:**
  - self-host OpenFreeMap / własny stack kafelków OSM, **albo**
  - komercyjny dostawca z SLA (MapTiler, Stadia, etc.)
- Publiczna instancja OpenFreeMap **nie gwarantuje SLA** — nie należy na niej opierać krytycznej produkcji bez planu awaryjnego.

## Dlaczego nie inne opcje

| Opcja | Powód odrzucenia na 1A |
|---|---|
| `demotiles.maplibre.org` | Tylko demo MapLibre, nie pełna mapa OSM |
| `tile.openstreetmap.org` | Polityka użycia OSMF — nie przeznaczone do aplikacji produkcyjnych / ciężkiego ruchu |
| Losowy publiczny tile server | Brak weryfikacji ToS / atrybucji / stabilności |
