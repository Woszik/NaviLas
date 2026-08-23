# NaviLas

Aplikacja Android do wyszukiwania miejsc odpoczynku w lasach — na podstawie otwartych danych przestrzennych (Bank Danych o Lasach, OpenStreetMap).

**Licencja:** [GNU GPL v3](LICENSE) — Copyright (C) 2026 Woszik.

## Dystrybucja (dwa kanały)

| Kanał | Instalacja | Aktualizacje |
|-------|------------|--------------|
| **GitHub** (testerzy) | [NaviLas-releases](https://github.com/Woszik/NaviLas-releases) | In-app z GitHub (`github` flavor) |
| **F-Droid** | Klient F-Droid (po akceptacji w katalogu) | Tylko F-Droid (`fdroid` flavor) |

**Ważne:** APK z GitHub i z F-Droid mają **różne podpisy**. Nie instalujesz ich na zmianę bez reinstalacji. Przed zmianą kanału: **Lista → Zapisane → Kopia → Eksportuj**, potem import po instalacji z drugiego źródła.

### GitHub (testy)

https://github.com/Woszik/NaviLas-releases

Aplikacja (`github`) sprawdza nowszą wersję przy starcie. Aktualizację zatwierdzasz samodzielnie.

> **Play Protect:** Ostrzeżenie przy APK spoza Google Play jest normalne — instaluj tylko z powyższego linku.

## Kopia zapisanych miejsc

1. **Lista → Zapisane → Kopia → Eksportuj…** — zapis JSON (np. Pobrane)
2. Po reinstalacji: **Importuj…** → Scal lub Zastąp

Szczegóły: ikona **ⓘ** → O aplikacji.

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

- Wyszukiwanie miejsc odpoczynku (GPS, mapa, miejscowość)
- Mapa ze strefami „Zanocuj w lesie”
- Dojazd samochód / motocykl
- BDL offline
- Zapisane miejsca z kategoriami i komentarzami
- Eksport / import kopii zapasowej

## Źródła danych

- [Bank Danych o Lasach (BDL)](https://www.bdl.lasy.gov.pl/)
- [OpenStreetMap](https://www.openstreetmap.org/copyright) (ODbL)
- OpenFreeMap / MapLibre

## Kontakt

woszi@pm.me

## Dokumentacja

- [`docs/FDROID.md`](docs/FDROID.md) — F-Droid, flavory, MR do fdroiddata
- [`docs/fdroid/pl.navilas.finder.yml`](docs/fdroid/pl.navilas.finder.yml) — szablon metadanych F-Droid
- [`docs/APP_UPDATES.md`](docs/APP_UPDATES.md) — auto-update (flavor `github`)
- [`docs/BDL_POINT_CATEGORIES.md`](docs/BDL_POINT_CATEGORIES.md) — kategorie BDL
