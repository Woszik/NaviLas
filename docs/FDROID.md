# F-Droid — NaviLas

Stan: **MR otwarty, po pierwszej rundzie recenzji — czekamy na kolejne spojrzenie maintainera** (2026-08-30).

Przewodnik: [Submitting to F-Droid](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)

**Relacja do kanałów GitHub:** F-Droid to osobna dystrybucja (inny podpis, flavor `fdroid`). Do otwartego MR proponowana jest obecna stabilna **Beta 0.5.35**; przyjęcie i publikacja nadal zależą od maintainerów F-Droid. Model kanałów GitHub: [`RELEASE_CHANNELS.md`](RELEASE_CHANNELS.md).

---

## Bieżący etap (zapis na pauzę)

| Element | Wartość |
|---------|---------|
| **Merge Request** | https://gitlab.com/fdroid/fdroiddata/-/merge_requests/46612 |
| **Tytuł MR** | New App: NaviLas (pl.navilas.finder) |
| **Fork fdroiddata** | https://gitlab.com/Woszik/fdroiddata |
| **Branch MR** | `pl.navilas.finder` |
| **Plik metadanych** | `metadata/pl.navilas.finder.yml` |
| **Kandydat w szablonie repo NaviLas** | tag `v0.5.35`, versionCode 39 (hash commita po tagu) |
| **Kandydat na forku GitLab (live MR)** | nadal `0.5.7-fdroid-prep` do czasu `git push` z `/tmp/fdroiddata` (brak tokenu GitLab w tym środowisku) |
| **Status MR** | Open — labele `New App` + `waiting-on-response` (patrz niżej) |
| **Pipeline na forku** | czerwony — **normalne** na forku kontrybutora, nie blokuje review |
| **Ostatnia aktywność autora** | 2026-08-30 — szablon 0.5.34 i usunięcie `Binaries` w repo NaviLas; commit na klonie forka: `de6444239` (push do GitLab ręcznie) |
| **Ostatnia aktywność recenzenta** | ~2026-08-24 — duckniii / linsui (prośby); od wtedy cisza |

### Zrobione

- [x] GPL-3.0, publiczne źródła ([NaviLas](https://github.com/Woszik/NaviLas))
- [x] Flavory `github` / `fdroid` (updater tylko w `github`)
- [x] Konto GitLab, fork `fdroiddata`
- [x] Plik `metadata/pl.navilas.finder.yml` na branchu `pl.navilas.finder`
- [x] Merge Request !46612

### Co znaczy `waiting-on-response`

To **nie** jest kolejka „czekamy aż Woj coś dopisze”. Label wstawia recenzent/bot przy prośbie o poprawki i **często zostaje**, nawet po Twojej odpowiedzi. Nikt go automatycznie nie zdejmuje.

**New App** = typ MR (nowa aplikacja), nie „nieruszony ticket”.

Kolejka nowych aplikacji jest długa, recenzenci to wolontariusze. **6 dni ciszy po odpowiedzi jest normalne** — typowy czas to dni–tygodnie, bywa kilka miesięcy. **Nie pingować** po mniej niż ~2–3 tygodniach od ostatniej odpowiedzi (krótko, po angielsku, bez „please merge”).

### Po powrocie (gdy maintainer odpowie)

1. Przeczytać komentarze w MR → odpowiedzieć w wątku (krótko, po angielsku).
2. Jeśli prośba o poprawkę: commit na branch `pl.navilas.finder` w fork — MR się zaktualizuje.
3. Po **merge** MR: aplikacja trafi do F-Droid po kolejnym cyklu publikacji (nie od razu).
4. Kolejne wersje: tag na GitHub; F-Droid łapie tagi (`UpdateCheckMode: Tags`).

**Prawdopodobna następna uwaga:** w YAML jest `Binaries` + `AllowedAPKSigningKeys` (reproducible). APK z NaviLas-releases to flavor **`github`**, recipe buduje **`fdroidRelease`** — sumy się nie zepną. Albo usunąć `Binaries` i zostawić podpis F-Droid, albo publikować osobny APK flavoru `fdroid` pod ten sam tag (koszt: reproducible). Decyzja projektu: **nie** robimy reproducible (patrz tabela poniżej).

Szablon odpowiedzi / opis MR: [`docs/fdroid/MR_DESCRIPTION.md`](fdroid/MR_DESCRIPTION.md)

---

## Decyzje

| Temat | Decyzja |
|-------|---------|
| Licencja | **GPL-3.0-or-later** — plik [`LICENSE`](../LICENSE) |
| Źródła | **Publiczne** — `https://github.com/Woszik/NaviLas` |
| Reproducible / jeden podpis | **Nie** — za wysoki koszt utrzymania |
| GitHub + F-Droid równolegle | **Tak** — różne podpisy; wersja 0.5.35 jest Betą GitHub i kandydatem do otwartego MR F-Droid |
| Zmiana źródła APK | Reinstalacja + eksport/import punktów |
| Flavory Gradle | `github` (updater Beta), `fdroid` (bez GitHub update) |

---

## Flavory

| Flavor | `APP_UPDATE_ENABLED` | Dystrybucja | Build release |
|--------|----------------------|-------------|---------------|
| `github` | tak | NaviLas-releases (**Beta**), CI tag | `assembleGithubRelease` |
| `fdroid` | nie | F-Droid (po merge MR); obecny kandydat 0.5.35 | `assembleFdroidRelease` |

CI GitHub Actions buduje wyłącznie **`githubRelease`** (dziś = Beta).

---

## Stan repozytoriów

| Repo | Rola |
|------|------|
| [Woszik/NaviLas](https://github.com/Woszik/NaviLas) | Kod źródłowy (publiczny) |
| [Woszik/NaviLas-releases](https://github.com/Woszik/NaviLas-releases) | APK **Beta**, `latest.json` |
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
