# Eksport nawigacji (Checkpoint 3)

> **Stan (2026-08-30):** Menu **NAWIGUJ** (4 opcje) jest dostępne w **Beta 0.5.34**.
> Kanały: [`RELEASE_CHANNELS.md`](RELEASE_CHANNELS.md). Stan projektu: [`STATUS.md`](STATUS.md).

## Menu NAWIGUJ

Wynik → **NAWIGUJ** → wybór:

1. **Google Maps**
2. **OsmAnd**
3. **Cruiser**
4. **Kopiuj współrzędne GPS**

Profil **MOTOCYKL** bez zakwalifikowanej drogi OSM: przycisk NAWIGUJ ukryty.

## Google Maps

`https://www.google.com/maps/dir/?api=1&destination=LAT,LON`

- SAMOCHÓD: współrzędne miejsca (wynik)
- MOTOCYKL: punkt najbliższej zakwalifikowanej drogi OSM

## OsmAnd

### Działające (zweryfikowane w Beta 0.5.34)

1. **Główna ścieżka:** `osmand.api://navigate?dest_lat=…&dest_lon=…&dest_name=…&profile=…` na pakiet `net.osmand.plus` — [OsmAnd API](https://github.com/osmandapp/osmand-api-demo)
   - SAMOCHÓD: `profile=car` (bez dodatkowego dialogu)
   - MOTOCYKL (bez wyboru stylu): `profile=motorcycle`
2. **Fallback:** intent `geo:LAT,LON?q=LAT,LON(Nazwa)` na `net.osmand.plus.activities.search.GeoIntentActivity`
3. **Nie używać** `https://osmand.net/map/…` bez pakietu — otwiera przeglądarkę zamiast aplikacji

### Do dopracowania — styl trasy motocyklowej

Dialog **Styl trasy OsmAnd** (Krótka / Kręta / Standardowa) jest w **Beta 0.5.34**, ale **nie uznajemy integracji za domkniętą** — wymaga testów na urządzeniu i ewentualnej korekty parametrów API.

Planowane mapowanie (po imporcie `NaviLas_osmand_moto_profiles.osf`):

| NaviLas | Parametr `profile=` | Profil OsmAnd | BRouter |
|---------|----------------------|---------------|---------|
| Krótka | `brouter_trekking` | `Brouter[trekking]` | `trekking.brf` |
| Kręta | `brouter_moped` | `Brouter[moped]` | `moped.brf` |
| Standardowa | `motorcycle` | Motocykl | wbudowany OsmAnd |

Setup profili i weryfikacja ręczna: [`osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt`](osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt).

## Cruiser (Emux)

- Intent `geo:LAT,LON?q=LAT,LON(Nazwa)` na pakiet `gr.talent.cruiser`
- Fallback: ten sam `geo:` bez pakietu (chooser)
- W aplikacji użytkownik planuje trasę i startuje nawigację w Cruiser

## Kopiuj współrzędne GPS

- Format schowka: `LAT, LON` (6 miejsc po przecinku, np. `52.200000, 21.100000`)
- Do ręcznego wklejenia w Calimoto i innych nawigacjach bez integracji intent

## Testy ręczne na urządzeniu (checklist)

1. Wynik → NAWIGUJ → Google Maps otwiera trasę do właściwego celu.
2. NAWIGUJ → OsmAnd otwiera **aplikację OsmAnd** (plan nawigacji), nie przeglądarkę.
3. NAWIGUJ → Cruiser otwiera Cruiser z pinem docelowym.
4. NAWIGUJ → Kopiuj współrzędne GPS → snackbar + wklejenie w Calimoto ręcznie.
5. Profil MOTOCYKL bez odpowiedniej drogi: przycisk NAWIGUJ ukryty.

**Do dopracowania (OsmAnd moto):**

6. MOTOCYKL → OsmAnd → dialog Krótka / Kręta / Standardowa → OsmAnd przełącza profil i liczy trasę offline (BRouter dla Krótka/Kręta).
