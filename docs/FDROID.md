# F-Droid — NaviLas

Stan: **MR złożony, czekamy na review maintainerów** (2026-08-23).

Przewodnik: [Submitting to F-Droid](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)

---

## Bieżący etap (zapis na pauzę)

| Element | Wartość |
|---------|---------|
| **Merge Request** | https://gitlab.com/fdroid/fdroiddata/-/merge_requests/46612 |
| **Tytuł MR** | New App: NaviLas (pl.navilas.finder) |
| **Fork fdroiddata** | https://gitlab.com/Woszik/fdroiddata |
| **Branch MR** | `pl.navilas.finder` |
| **Plik metadanych** | `metadata/pl.navilas.finder.yml` |
| **Pierwszy build w YAML** | tag `v0.5.7-fdroid-prep`, versionCode 9 |
| **Status MR** | Open — oczekiwanie na komentarz maintainera |
| **Pipeline na forku** | może być czerwony — **normalne**, nie blokuje review |

### Zrobione

- [x] GPL-3.0, publiczne źródła ([NaviLas](https://github.com/Woszik/NaviLas))
- [x] Flavory `github` / `fdroid` (updater tylko w `github`)
- [x] Konto GitLab, fork `fdroiddata`
- [x] Plik `metadata/pl.navilas.finder.yml` na branchu `pl.navilas.finder`
- [x] Merge Request !46612

### Po powrocie (gdy maintainer odpowie)

1. Przeczytać komentarze w MR → odpowiedzieć w wątku (krótko, po angielsku).
2. Jeśli prośba o poprawkę: commit na branch `pl.navilas.finder` w fork — MR się zaktualizuje.
3. Po **merge** MR: aplikacja trafi do F-Droid po kolejnym cyklu publikacji (nie od razu).
4. Kolejne wersje: tag na GitHub; F-Droid łapie tagi (`UpdateCheckMode: Tags`).

Szablon odpowiedzi / opis MR: [`docs/fdroid/MR_DESCRIPTION.md`](fdroid/MR_DESCRIPTION.md)

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
| `fdroid` | nie | F-Droid (po merge MR) | `assembleFdroidRelease` |

CI GitHub Actions buduje wyłącznie **`githubRelease`**.

---

## Stan repozytoriów

| Repo | Rola |
|------|------|
| [Woszik/NaviLas](https://github.com/Woszik/NaviLas) | Kod źródłowy (publiczny) |
| [Woszik/NaviLas-releases](https://github.com/Woszik/NaviLas-releases) | APK dla testerów, `latest.json` |
| [Woszik/fdroiddata](https://gitlab.com/Woszik/fdroiddata) | Fork pod MR (branch `pl.navilas.finder`) |

---

## Anti-Features (deklaracja w YAML)

- **TetheredNet** — BDL, OSM, OpenFreeMap bez wyboru serwera
- W flavorze `fdroid` **brak** połączenia z GitHub przy starcie → uniknięcie **NonFreeNet** od updatera

---

## Play Store

Poza zakresem. Przygotowanie (GPL, publiczne źródła, atrybucje) nie blokuje przyszłego Play.

---

## Linki

- [MR !46612](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/46612)
- [Szablon YAML](fdroid/pl.navilas.finder.yml)
- [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/)
- [Anti-Features](https://f-droid.org/docs/Anti-Features/)
- [CONTRIBUTING fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md)
