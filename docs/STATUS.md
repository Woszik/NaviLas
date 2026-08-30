# Stan projektu NaviLas

Ostatnia aktualizacja dokumentacji: **2026-08-30**.

## Kanały aktualizacji

| Kanał | Status | Bieżąca wersja | versionCode |
|-------|--------|----------------|-------------|
| **Nightly** | brak aktywnego po promocji | — | > 38 dla następnego buildu |
| **Beta** | GitHub Releases | 0.5.34 | 38 |
| **Final** | **nie istnieje** | — | — |

Model i mapowanie: [`RELEASE_CHANNELS.md`](RELEASE_CHANNELS.md).  
Historia **Beta**: [`CHANGELOG.md`](../CHANGELOG.md).  
Publikacja / in-app updater (dziś = Beta): [`APP_UPDATES.md`](APP_UPDATES.md).

Instalacja lokalnego buildu deweloperskiego:

```bash
./gradlew :app:installGithubDebug
```

Oficjalna **Beta 0.5.34** na urządzeniu testowym: APK z [NaviLas-releases v0.5.34](https://github.com/Woszik/NaviLas-releases/releases/tag/v0.5.34). Propozycja F-Droid: [`FDROID.md`](FDROID.md) / MR !46612.

## Zakres Beta 0.5.34

Funkcje ustabilizowane i opublikowane w kanale Beta:

### Nawigacja zewnętrzna (Checkpoint 3)

Wynik → **NAWIGUJ** → wybór aplikacji:

| Opcja | Zachowanie |
|-------|------------|
| Google Maps | URL Directions API do właściwego celu (auto: miejsce, moto: droga OSM) |
| OsmAnd | `osmand.api://navigate` na `net.osmand.plus`; fallback `geo:` → GeoIntentActivity |
| Cruiser | `geo:` na `gr.talent.cruiser` |
| Kopiuj współrzędne GPS | Schowek `lat, lon` (6 miejsc) + snackbar |

Szczegóły techniczne: [`NAVIGATION_EXPORT.md`](NAVIGATION_EXPORT.md).

### Wyszukiwanie miejscowości

- Nominatim + **Overpass** (pełniejsza lista osiedli o tej samej nazwie, bez limitu ~50 wyników Nominatim).
- Grupowanie wyników **po województwie** (`VoivodeshipResolver`).
- Cache geokodowania: `PersistentLocalityGeocodeStore`.

### Mapa

- **Ekran włączony** podczas aktywnego śledzenia pozycji GPS (`FLAG_KEEP_SCREEN_ON`).
- **Obiekty BDL** (browse, domyślnie OFF): punkty spoza wyników odpoczynku — Widok (25) i Inne (27) z CORE; woda / zabawa / nocleg gdy pełna baza. Kolory + karta + NAWIGUJ. Szczegóły: [`BDL_POINT_CATEGORIES.md`](BDL_POINT_CATEGORIES.md) § overlay.
- **Przewidywalny start:** domyślnie pierwszy start otwiera Search, a kolejne przywracają ostatni tryb. Ustawienia mogą to nadpisać na stałe Wyszukiwanie albo Przeglądanie mapy. Browse nie uruchamia się w tle dla Search.
- **Browse bez blokowania UI:** mapa pojawia się od razu, punkty są przygotowywane w tle, ograniczane do obszaru mapy i grupowane przy szerokim widoku.
- **Bez wyścigu trybów:** przejście Browse → Search anuluje stare zadanie; zakończone ładowanie nie może przejąć ekranu.
- **Kosztowne filtry poza UI:** filtrowanie udogodnień i sortowanie wyników działa w tle; duży promień parkingu pokazuje ostrzeżenie.
- **Etapowe Search:** miejsca BDL są prezentowane przed analizą dróg motocyklowych, której postęp jest widoczny jako `Analizuję drogi OSM: n/total` (limit 50 wyników).

### Interfejs i ustawienia

- Dolny pasek ma bezpośrednie zakładki **Szukaj / Mapa / Lista**; gesty i strzałki pozostają jako alternatywa.
- Menu aplikacji porządkuje dostęp do **Ustawień**, danych BDL offline, aktualizacji (tylko flavor GitHub) i informacji o aplikacji.
- Ustawienia zapisują motyw **System / Czujnik światła / Dzień / Noc**, zachowanie startowe **ostatni tryb / Wyszukiwanie / Przeglądanie mapy** oraz opcję niewygaszania ekranu podczas śledzenia. Tryb czujnika stosuje histerezę 30/150 lx i wymaga stabilnego odczytu przez 2 sekundy.
- Motyw jest nakładany przed utworzeniem głównego ekranu; kolory tekstu, powierzchni, kart i kontrolek korzystają z palety DayNight zamiast stałych jasnych kolorów.
- Mapa wybiera styl OpenFreeMap **Liberty** albo **Dark** zgodnie z aktywnym motywem.

### Weryfikacja KINGKONG 8

Test ręczny 2026-08-30 na fizycznym urządzeniu:

- pion: tryb dzienny i nocny, ekran Wyszukiwanie, menu oraz Ustawienia,
- poziom: tryb dzienny i nocny, ekran Wyszukiwanie i mapa,
- zmiana motywu odświeża aktywność i zachowuje wybór,
- tryb czujnika światła poprawnie przełącza Dzień ↔ Noc na fizycznym czujniku KINGKONG,
- bezpośrednie zakładki mieszczą się i pozostają dostępne w obu orientacjach,
- kontrast zielonych przycisków i tekstu statusu sprawdzony w trybie nocnym.

## Do dopracowania

Tematy świadomie odłożone — kod lub setup może być częściowo gotowy, ale **nie uznajemy za domknięte**:

### 1. OsmAnd — styl trasy motocyklowej

**Cel:** Po wyborze OsmAnd w profilu MOTOCYKL dialog: **Krótka** / **Kręta** / **Standardowa**, mapowane na profile OsmAnd/BRouter.

| NaviLas | Profil OsmAnd | BRouter |
|---------|---------------|---------|
| Krótka | `Brouter[trekking]` | `trekking.brf` |
| Kręta | `Brouter[moped]` | `moped.brf` |
| Standardowa | Motocykl (`motorcycle`) | wbudowany OsmAnd |

**Stan:** Dialog i parametry `profile=` są w Beta 0.5.34. Wymaga weryfikacji na urządzeniu, czy OsmAnd **faktycznie przełącza** profil po intencie API (możliwa korekta kluczy `brouter_moped` / `brouter_trekking` vs nazwy wyświetlane). Setup telefonu: [`osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt`](osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt).

### 2. (Opcjonalnie później) Integracja Calimoto

Zamiast eksportu GPX — **kopiowanie współrzędnych** do schowka (obecne rozwiązanie). Pełna integracja Calimoto nie jest priorytetem.

### 3. Rozdzielenie Nightly / Beta / Final w CI i aplikacji

Model kanałów ustalony w docs; implementacja (osobne manifesty, naming, wybór kanału) — [`RELEASE_CHANNELS.md`](RELEASE_CHANNELS.md) → sekcja TODO.

## Dokumentacja powiązana

| Plik | Opis |
|------|------|
| [`RELEASE_CHANNELS.md`](RELEASE_CHANNELS.md) | Nightly / Beta / Final |
| [`BDL_POINT_CATEGORIES.md`](BDL_POINT_CATEGORIES.md) | Warstwy BDL + overlay browse |
| [`NAVIGATION_EXPORT.md`](NAVIGATION_EXPORT.md) | Intenty, URL-e, checklist testów nawigacji |
| [`OSM_ROADS.md`](OSM_ROADS.md) | Drogi OSM, Overpass, profil moto |
| [`osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt`](osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt) | Import profili OsmAnd (.osf) |
| [`osmand_brouter_KINGKONG_2026-08-30.txt`](osmand_brouter_KINGKONG_2026-08-30.txt) | BRouter + segmenty na urządzeniu testowym |
| [`osmand/build_moto_profiles_osf.py`](osmand/build_moto_profiles_osf.py) | Generator pakietu `.osf` |
