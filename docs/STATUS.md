# Stan projektu NaviLas

Ostatnia aktualizacja dokumentacji: **2026-09-03**.

## Kanały aktualizacji

| Kanał | Status | Bieżąca wersja | versionCode |
|-------|--------|----------------|-------------|
| **Nightly** | GitHub (prerelease `nightly`) | 0.5.52-nightly | 64 |
| **Beta** | GitHub Releases | 0.5.46 | 58 |
| **Final** | **nie istnieje** | — | — |

Model i mapowanie: [`RELEASE_CHANNELS.md`](RELEASE_CHANNELS.md).  
Historia **Beta**: [`CHANGELOG.md`](../CHANGELOG.md).  
Publikacja / in-app updater (dziś = Beta): [`APP_UPDATES.md`](APP_UPDATES.md).

Instalacja lokalnego buildu deweloperskiego:

```bash
./gradlew :app:installGithubDebug
```

Oficjalna **Beta 0.5.46** (versionCode 58): APK z [NaviLas-releases v0.5.46](https://github.com/Woszik/NaviLas-releases/releases/tag/v0.5.46). Zakazy wstępu włączone od startu; dojazd moto do oficjalnego parkingu/postoju LP. Propozycja F-Droid bez zmian: [`FDROID.md`](FDROID.md) / MR !46612.

## Nightly 0.5.52

Opisy OsmAnd moto ujednolicone w aplikacji i docs: dialog Krótka / Kręta / Standardowa z mapowaniem na BRouter / Motocykl; import `.osf` z jasnymi nazwami profili. Checklist NAWIGUJ zamknięty.

## Nightly 0.5.51

Szukanie **miejsca BDL po nazwie** z paczki offline (warstwy 15/17/19). Pole na ekranie Szukaj i w arkusz Filtrów (także w Browse). Od 3 znaków lista podczas wpisywania: polskie znaki, prefiks, 1 literówka (2 przy dłuższym słowie). Wybór skacze na mapę i otwiera kartę — bez ZNAJDŹ.

## Nightly 0.5.50

Karta „Szczegóły” podczas analizy dróg: widać postęp w karcie (pasek) oraz używamy alternatywnych endpointów Overpass przy braku łączności z `overpass-api.de`.

## Nightly 0.5.49

Szczegóły → **Zarządca** → **Dociągnij z BDL**: nadleśnictwo (główny kontakt) i leśnictwo z poligonów `WMS_BDL`, na żądanie, z sieci. Nie wchodzi do paczki miejsc offline.

## Nightly 0.5.48

Klik w punkt w przeglądaniu mapy zawsze otwiera kartę (bez toggle i bez zoomu kamery). Karta leży na mapie, nie ściska MapView. Analiza OSM moto na karcie, nie na pasku nad mapą. Nightly 0.5.47 (NAWIGUJ) bez zmian w kolejności aplikacji.

## Nightly 0.5.47

NAWIGUJ: **OsmAnd** (zalecane) → **Cruiser** → **Współrzędne GPS** → **Wybierz nawigację** (systemowy chooser) → **Google Maps**. Brak OsmAnd/Cruisera → dialog, nie cichy fallback na `geo:`. Po instalacji OsmAnd (albo z Ustawień) import profili moto `.osf`. Szczegóły: [`NAVIGATION_EXPORT.md`](NAVIGATION_EXPORT.md).

## Zakres Beta 0.5.46

Funkcje ustabilizowane i opublikowane w kanale Beta:

### Nawigacja zewnętrzna (Checkpoint 3)

Wynik → **NAWIGUJ** → wybór aplikacji:

| Opcja | Zachowanie (Beta 0.5.46; Nightly 0.5.47 ma inną kolejność — wyżej) |
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
- **Obiekty BDL** (Search i Browse, domyślnie OFF): punkty spoza wyników odpoczynku — Widok (25) i Inne (27) z CORE; woda / zabawa / nocleg gdy pełna baza. Kategorie zaznacza się niezależnie. Kolory + karta + NAWIGUJ. Szczegóły: [`BDL_POINT_CATEGORIES.md`](BDL_POINT_CATEGORIES.md) § overlay.
- **Filtry na mapie:** arkusz **Filtry** ma belki **Tryb**, **Nazwa miejsca BDL** (offline, Search i Browse), **Szukaj** (w wyszukiwaniu: źródło, promień, miejscowość, korytarz, **ZNAJDŹ**), **Profil**, **Filtry miejsc** i **Obiekty BDL**. Zmiana trybu z mapy zostawia Cię na mapie. W przeglądaniu belka Szukaj (promień) jest ukryta; **Odśwież mapę** jest w panelu Tryb.
- **Wielozaznaczenie** punktów (max 8) z listą i tabelą **Porównaj**.
- **Profil moto:** etykiety `surface` / `tracktype` (gruntowa, przejezdność); w przeglądaniu analiza OSM tylko dla zaznaczonych. Nietagowany `track`/`service` zostaje w wynikach, ale karta / Szczegóły / lista piszą **dostęp niepewny** (OSM bez zakazu i bez zezwolenia) — bez zaostrzania filtra. BDL 17/19 przy drodze z operatorem LP: **dojazd do oficjalnego parkingu / postoju LP**; ten sam tekst dla innych punktów przy tym korytarzu.
- **Szczegóły:** okno jak karta miejsca (nie surowy dialog). Cechy BDL na pinie; **W pobliżu** (200 m) z paczki overlay offline, grupowane nazwy, bez rysowania na mapie — piny tylko po włączeniu Obiektów BDL.
- **Śledzenie GPS:** pauza → play wznawia zapisaną skalę, kierunek i ogniskowy (bez skoku do zoom 13).
- **Zakazy wstępu BDL:** overlay (osobny MapServer, live, **domyślnie włączony**) od zoom 7.5 jak Zanocuj; paczka offline (cała Polska); po **7 dniach** monit, **Później** odkłada o **24 godziny**. Filtr **Poza strefą zakazu wstępu** jest **domyślnie włączony** i ukrywa wyniki w poligonie. Pobieranie wymaga Referera portalu BDL.
- **Przewidywalny start:** domyślnie pierwszy start otwiera Search, a kolejne przywracają ostatni tryb. Ustawienia mogą to nadpisać na stałe Wyszukiwanie albo Przeglądanie mapy. Browse nie uruchamia się w tle dla Search.
- **Browse bez blokowania UI:** mapa pojawia się od razu, punkty są przygotowywane w tle i rysowane na widocznym obszarze (bez klastrów).
- **Bez wyścigu trybów:** przejście Browse → Search anuluje stare zadanie; zakończone ładowanie nie może przejąć ekranu.
- **Kosztowne filtry poza UI:** filtrowanie udogodnień i sortowanie wyników działa w tle; duży promień parkingu pokazuje ostrzeżenie.
- **Etapowe Search:** miejsca BDL są prezentowane przed analizą dróg motocyklowych, której postęp jest widoczny jako `Analizuję drogi OSM: n/total` (limit 50 wyników).

### Interfejs i ustawienia

- Dolny pasek ma bezpośrednie zakładki **Szukaj / Mapa / Lista**; gesty i strzałki pozostają jako alternatywa.
- Menu aplikacji porządkuje dostęp do **Ustawień**, danych BDL offline, aktualizacji (tylko flavor GitHub) i informacji o aplikacji.
- Ustawienia zapisują motyw **System / Czujnik światła / Dzień / Noc**, zachowanie startowe **ostatni tryb / Wyszukiwanie / Przeglądanie mapy**, niewygaszanie ekranu podczas śledzenia oraz (flavor GitHub) kanał aktualizacji **Nightly i nowsze / Beta i nowsze / Tylko Final**. Domyślnie Beta i nowsze. Tryb czujnika stosuje histerezę 30/150 lx i wymaga stabilnego odczytu przez 2 sekundy.
- Motyw jest nakładany przed utworzeniem głównego ekranu; kolory tekstu, powierzchni, kart i kontrolek korzystają z palety DayNight zamiast stałych jasnych kolorów. Overflow menu w trybie dziennym ma czarny tekst.
- Mapa wybiera styl OpenFreeMap **Liberty** albo **Dark** zgodnie z aktywnym motywem.
- Jeśli baza BDL offline jest gotowa i ma **co najmniej 30 dni**, po starcie pojawia się przypomnienie „Aktualizacja danych BDL” (data pobrania + ostatni zakres). **Aktualizuj** pobiera ponownie zapisaną konfigurację; **Później** odkłada pytanie o 7 dni. Brak bazy albo trwające pobieranie nie pokazuje dialogu. W kanale GitHub dialog nie nachodzi na ofertę aktualizacji APK.
- Pola z wartością domyślną: klik czyści i otwiera klawiaturę, przy polu jest zakres.

### Weryfikacja KINGKONG 8

Test ręczny 2026-08-30 na fizycznym urządzeniu:

- pion: tryb dzienny i nocny, ekran Wyszukiwanie, menu oraz Ustawienia,
- poziom: tryb dzienny i nocny, ekran Wyszukiwanie i mapa,
- zmiana motywu odświeża aktywność i zachowuje wybór,
- tryb czujnika światła poprawnie przełącza Dzień ↔ Noc na fizycznym czujniku KINGKONG,
- bezpośrednie zakładki mieszczą się i pozostają dostępne w obu orientacjach,
- kontrast zielonych przycisków i tekstu statusu sprawdzony w trybie nocnym,
- w trybie dziennym tekst overflow menu jest czarny.

## Do dopracowania

Tematy świadomie odłożone:

### 1. (Opcjonalnie później) Integracja Calimoto

Zamiast eksportu GPX — **kopiowanie współrzędnych** do schowka (obecne rozwiązanie). Pełna integracja Calimoto nie jest priorytetem.

### 2. Final na GitHub

Nightly i Beta są w aplikacji i CI. Final (`final.json`) jeszcze nie istnieje.

## Dokumentacja powiązana

| Plik | Opis |
|------|------|
| [`TODO.md`](TODO.md) | Lista do zrobienia (poza bieżącym kodem) |
| [`WNIOSEK_BDL_SIEC_DROGOWA.md`](WNIOSEK_BDL_SIEC_DROGOWA.md) | Wniosek do BDL/DGLP o sieć drogową (do wysłania) |
| [`RELEASE_CHANNELS.md`](RELEASE_CHANNELS.md) | Nightly / Beta / Final |
| [`BDL_POINT_CATEGORIES.md`](BDL_POINT_CATEGORIES.md) | Warstwy BDL + overlay browse |
| [`NAVIGATION_EXPORT.md`](NAVIGATION_EXPORT.md) | Intenty, URL-e, checklist testów nawigacji |
| [`OSM_ROADS.md`](OSM_ROADS.md) | Drogi OSM, Overpass, profil moto |
| [`osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt`](osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt) | Import profili OsmAnd (.osf) |
| [`osmand_brouter_KINGKONG_2026-08-30.txt`](osmand_brouter_KINGKONG_2026-08-30.txt) | BRouter + segmenty na urządzeniu testowym |
| [`osmand/build_moto_profiles_osf.py`](osmand/build_moto_profiles_osf.py) | Generator pakietu `.osf` |
