# F-Droid — NaviLas

Stan: **wdrożono przygotowanie** (2026-08-23). Kolejny krok: Merge Request do [fdroiddata](https://gitlab.com/fdroid/fdroiddata).

Przewodnik: [Submitting to F-Droid](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)

---

## Decyzje

| Temat | Decyzja |
|-------|---------|
| Licencja | **GPL-3.0-or-later** — plik [`LICENSE`](../LICENSE) |
| Źródła | **Publiczne** — `https://github.com/Woszik/NaviLas` |
| Reproducible / jeden podpis | **Nie** — za wysoki koszt utrzymania |
| GitHub + F-Droid równolegle | **Tak** — dwa kanały, różne podpisy |
| Zmiana kanału | Reinstalacja + eksport/import punktów |
| Flavory Gradle | `github` (auto-update), `fdroid` (bez GitHub update) |

---

## Flavory

| Flavor | `APP_UPDATE_ENABLED` | Dystrybucja | Build release |
|--------|----------------------|-------------|---------------|
| `github` | tak | NaviLas-releases, CI tag | `assembleGithubRelease` |
| `fdroid` | nie | F-Droid (po MR) | `assembleFdroidRelease` |

CI GitHub Actions buduje wyłącznie **`githubRelease`**.

---

## Stan repozytoriów

| Repo | Rola |
|------|------|
| [Woszik/NaviLas](https://github.com/Woszik/NaviLas) | Kod źródłowy (publiczny) |
| [Woszik/NaviLas-releases](https://github.com/Woszik/NaviLas-releases) | APK dla testerów, `latest.json` |

---

## Następne kroki (ręcznie)

1. Fork `fdroiddata`
2. Użyj szablonu [`docs/fdroid/pl.navilas.finder.yml`](fdroid/pl.navilas.finder.yml)
3. Merge Request — poczekaj na review
4. Po merge: każdy tag z `fdroidRelease` → F-Droid łapie wg `UpdateCheckMode: Tags`
5. Opcjonalnie później: `fastlane/metadata/android/` (screenshoty, opisy sklepowe)

---

## Anti-Features (prawdopodobne)

- **TetheredNet** — BDL, OSM, OpenFreeMap bez wyboru serwera
- W flavorze `fdroid` **brak** połączenia z GitHub przy starcie → uniknięcie **NonFreeNet** od updatera

---

## Play Store

Poza zakresem. Przygotowanie (GPL, publiczne źródła, atrybucje) nie blokuje przyszłego Play.

---

## Linki

- [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/)
- [Anti-Features](https://f-droid.org/docs/Anti-Features/)
- [CONTRIBUTING fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md)
