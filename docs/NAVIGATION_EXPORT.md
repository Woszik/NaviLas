# Eksport nawigacji (Checkpoint 3)

> **Stan (2026-09-03):** Menu **NAWIGUJ** (5 opcji) od **Nightly 0.5.47**. Opisy stylu trasy OsmAnd ujednolicone w **Nightly 0.5.52**. Beta 0.5.46 miała inną kolejność (Google Maps pierwsze) i fallback Cruisera na systemowy chooser.
> Kanały: [`RELEASE_CHANNELS.md`](RELEASE_CHANNELS.md). Stan projektu: [`STATUS.md`](STATUS.md).

## Menu NAWIGUJ

Wynik → **NAWIGUJ** → wybór:

1. **OsmAnd (zalecane)** — nie wymagane; brak instalacji → dialog ze sklepem
2. **Cruiser** — brak instalacji → dialog ze sklepem (bez cichego choosera)
3. **Współrzędne GPS** — schowek
4. **Wybierz nawigację** — systemowy wybór aplikacji (`Intent.createChooser` na `geo:`)
5. **Google Maps** — URL https, bez locku na pakiet

Etykiety OsmAnd/Cruiser zmieniają się, gdy aplikacja nie jest zainstalowana. Wykrywanie: OsmAnd+ `net.osmand.plus`, darmowy OsmAnd `net.osmand`, Cruiser `gr.talent.cruiser` (`<queries>` w manifeście).

Profil **MOTOCYKL** bez zakwalifikowanej drogi OSM: przycisk NAWIGUJ ukryty.

## Google Maps

`https://www.google.com/maps/dir/?api=1&destination=LAT,LON`

- SAMOCHÓD: współrzędne miejsca (wynik)
- MOTOCYKL: punkt najbliższej zakwalifikowanej drogi OSM

## OsmAnd

1. **Główna ścieżka:** `osmand.api://navigate?dest_lat=…&dest_lon=…&dest_name=…&profile=…` na zainstalowany pakiet OsmAnd+ albo darmowy OsmAnd — [OsmAnd API](https://github.com/osmandapp/osmand-api-demo)
   - SAMOCHÓD: `profile=car` (bez dodatkowego dialogu)
   - MOTOCYKL: dialog stylu trasy, potem `profile=` jak w tabeli
2. **Fallback:** intent `geo:LAT,LON?q=LAT,LON(Nazwa)` — OsmAnd+ przez `GeoIntentActivity`, darmowy OsmAnd przez `setPackage`
3. **Nie używać** `https://osmand.net/map/…` bez pakietu — otwiera przeglądarkę zamiast aplikacji

Brak OsmAnd: dialog (zalecenie offline w lesie, nie wymóg) → Play Store OsmAnd+. Po powrocie, jeśli OsmAnd jest już zainstalowany, NaviLas proponuje import profili `.osf`. Ten sam import jest w Ustawieniach.

Mapowanie (po imporcie `NaviLas_osmand_moto_profiles.osf` z assetów aplikacji):

| NaviLas (dialog) | Parametr `profile=` | Profil OsmAnd | BRouter |
|------------------|---------------------|---------------|---------|
| Krótka — BRouter trekking | `brouter_trekking` | `Brouter[trekking]` | `trekking.brf` (zalecany, nie wymagany) |
| Kręta — BRouter moped | `brouter_moped` | `Brouter[moped]` | `moped.brf` (zalecany, nie wymagany) |
| Standardowa — Motocykl OsmAnd | `motorcycle` | Motocykl | wbudowany OsmAnd |

Setup: Ustawienia → Wgraj profile NaviLas do OsmAnd, albo [`osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt`](osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt). Przed testem Krótka/Kręta uruchom aplikację BRouter.

## Cruiser (Emux)

- Intent `geo:LAT,LON?q=LAT,LON(Nazwa)` **tylko** na pakiet `gr.talent.cruiser`
- Brak aplikacji: dialog + Play Store. **Bez** fallbacku na goły `geo:` (to otwierało systemowy chooser)
- W aplikacji użytkownik planuje trasę i startuje nawigację w Cruiser

## Wybierz nawigację

Jedyny moment, gdy Android pokazuje listę programów: `Intent.createChooser` na ten sam `geo:` co wyżej.

## Współrzędne GPS

- Format schowka: `LAT, LON` (6 miejsc po przecinku, np. `52.200000, 21.100000`)
- Do ręcznego wklejenia w Calimoto i innych nawigacjach bez integracji intent

## Testy ręczne na urządzeniu (checklist)

1. NAWIGUJ → lista w kolejności: OsmAnd, Cruiser, Współrzędne GPS, Wybierz nawigację, Google Maps.
2. OsmAnd zainstalowany → otwiera **aplikację** (plan nawigacji), nie przeglądarkę. OsmAnd+ i darmowy OsmAnd.
3. OsmAnd brak → dialog, nie snackbar; Play Store; po instalacji propozycja profili `.osf`.
4. Cruiser zainstalowany → pin w Cruiser. Brak → dialog, **nie** systemowy chooser.
5. Wybierz nawigację → systemowa lista aplikacji.
6. Google Maps → trasa https do właściwego celu.
7. Współrzędne GPS → snackbar + wklejenie w Calimoto ręcznie.
8. Profil MOTOCYKL bez odpowiedniej drogi: przycisk NAWIGUJ ukryty.
9. Ustawienia → Wgraj profile NaviLas do OsmAnd (gdy OsmAnd jest).
10. MOTOCYKL → OsmAnd → dialog Krótka / Kręta / Standardowa → OsmAnd przełącza profil i liczy trasę (BRouter dla Krótka/Kręta).

**Stan checklisty:** potwierdzone (Nightly 0.5.47+ / opisy 0.5.52).
