# NaviLas

Aplikacja Android do wyszukiwania miejsc odpoczynku w lasach — na podstawie otwartych danych przestrzennych (Bank Danych o Lasach, OpenStreetMap).

**Licencja:** [GNU GPL v3](LICENSE) — Copyright (C) 2026 Woszik.

## Dystrybucja

### Skąd bierzesz APK

| Źródło | Flavor | Aktualizacje |
|--------|--------|--------------|
| **GitHub** ([NaviLas-releases](https://github.com/Woszik/NaviLas-releases)) | `github` | In-app z GitHub |
| **F-Droid** (po akceptacji w katalogu) | `fdroid` | Tylko klient F-Droid |

**Ważne:** APK z GitHub i z F-Droid mają **różne podpisy**. Nie instalujesz ich na zmianę bez reinstalacji. Przed zmianą źródła: **Lista → Zapisane → Kopia → Eksportuj**, potem import po instalacji z drugiego źródła.

### Kanały aktualizacji (GitHub)

| Kanał | Status | Opis |
|-------|--------|------|
| **Nightly** | aktywny lokalnie | świeże buildy testowe (dziś: `installGithubDebug`) |
| **Beta** | aktywny | wydania w NaviLas-releases + in-app (`latest.json`) |
| **Final** | **jeszcze nie** | docelowo osobny track GitHub; F-Droid pozostaje niezależną dystrybucją |

Szczegóły: [`docs/RELEASE_CHANNELS.md`](docs/RELEASE_CHANNELS.md).

### GitHub — Beta (testerzy)

https://github.com/Woszik/NaviLas-releases

Aplikacja (`github`) sprawdza nowszą **Beta** przy starcie. Aktualizację zatwierdzasz samodzielnie.

> **Play Protect:** Ostrzeżenie przy instalacji APK spoza Google Play jest normalne — NaviLas nie jest w Sklepie Play. Instaluj wyłącznie z powyższego linku (repozytorium `NaviLas-releases`).
>
> Typowa ścieżka na telefonie: w oknie Play Protect wybierz **Więcej szczegółów**, a potem **Zainstaluj bez skanowania**.

## Kopia zapisanych miejsc

1. **Lista → Zapisane → Kopia → Eksportuj…** — zapis JSON (np. Pobrane)
2. Po reinstalacji: **Importuj…** → Scal lub Zastąp

Szczegóły: menu **⋮ → O aplikacji**.

## Budowanie ze źródeł

Wymagania: JDK 17, Android SDK.

```bash
# Wersja dla GitHub (auto-update włączony)
./gradlew :app:assembleGithubRelease

# Wersja dla F-Droid (bez auto-update z GitHub)
./gradlew :app:assembleFdroidRelease

# Debug lokalny (GitHub flavor)
./gradlew :app:assembleGithubDebug
```

Release keystore (opcjonalnie, dla podpisanego APK): zmienne `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Funkcje

- Wyszukiwanie miejsc odpoczynku (GPS, mapa, miejscowość, korytarz)
- Przeglądanie mapy (browse) z filtrowaniem punktów
- Filtry miejsc (wiata, ławostoły, palenisko, woda, źródło, parking, Zanocuj) — wyszukiwanie i mapa
- Mapa ze strefami „Zanocuj w lesie” i śledzeniem pozycji GPS
- Dojazd samochód / motocykl (ocena dróg OSM dla moto)
- Eksport do nawigacji zewnętrznej: Google Maps, OsmAnd, Cruiser, kopiowanie współrzędnych GPS — [`docs/NAVIGATION_EXPORT.md`](docs/NAVIGATION_EXPORT.md)
- Wyszukiwanie miejscowości z grupowaniem po województwach (Nominatim + Overpass)
- BDL offline
- Zapisane miejsca z kategoriami i komentarzami
- Eksport / import kopii zapasowej
- Bezpośrednia nawigacja **Szukaj / Mapa / Lista**
- Menu aplikacji i trwałe ustawienia: motyw **System / Czujnik światła / Dzień / Noc**, tryb startowy i niewygaszanie ekranu podczas śledzenia

## Stan roboczy

Bieżąca **Beta:** **0.5.44** ([`CHANGELOG.md`](CHANGELOG.md)).

**Nightly** i tematy **do dopracowania:** [`docs/STATUS.md`](docs/STATUS.md).

Model Nightly / Beta / Final: [`docs/RELEASE_CHANNELS.md`](docs/RELEASE_CHANNELS.md).

## Źródła danych

- [Bank Danych o Lasach (BDL)](https://www.bdl.lasy.gov.pl/)
- [OpenStreetMap](https://www.openstreetmap.org/copyright) (ODbL)
- OpenFreeMap / MapLibre

## Kontakt

woszi@pm.me

## Dokumentacja

- [`CHANGELOG.md`](CHANGELOG.md) — historia wydań **Beta** (GitHub), linki do APK, downgrade
- [`docs/RELEASE_CHANNELS.md`](docs/RELEASE_CHANNELS.md) — kanały Nightly / Beta / Final
- [`docs/STATUS.md`](docs/STATUS.md) — stan projektu, Nightly, tematy do dopracowania
- [`docs/NAVIGATION_EXPORT.md`](docs/NAVIGATION_EXPORT.md) — nawigacja zewnętrzna (Google Maps, OsmAnd, Cruiser, GPS)
- [`docs/OSM_ROADS.md`](docs/OSM_ROADS.md) — drogi OpenStreetMap (profil motocyklowy)
- [`docs/FDROID.md`](docs/FDROID.md) — F-Droid, flavory, MR do fdroiddata
- [`docs/fdroid/pl.navilas.finder.yml`](docs/fdroid/pl.navilas.finder.yml) — szablon metadanych F-Droid
- [`docs/APP_UPDATES.md`](docs/APP_UPDATES.md) — in-app update (**Beta**, flavor `github`)
- [`docs/BDL_POINT_CATEGORIES.md`](docs/BDL_POINT_CATEGORIES.md) — kategorie BDL
- [`docs/osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt`](docs/osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt) — setup profili OsmAnd/BRouter (testy urządzeniowe)
