# Kanały aktualizacji NaviLas

Model dystrybucji **GitHub** (flavor `github`): **Nightly**, **Beta**, **Final**.

Ostatnia aktualizacja: **2026-09-01**.

```
Nightly  →  (stabilizacja)  →  Beta  →  (dopracowanie)  →  Final
   ↑ GitHub nightly.json           ↑ latest.json            ↑ final.json (gdy powstanie)

F-Droid: niezależna dystrybucja flavoru `fdroid`; tylko na wyraźne polecenie, nie przy każdym wydaniu GitHub.
```

| Kanał | Status | Destynacja | Dla kogo |
|-------|--------|------------|----------|
| **Nightly** | aktywny | [NaviLas-releases](https://github.com/Woszik/NaviLas-releases) tag `nightly` + `nightly.json` | testy na bieżąco |
| **Beta** | aktywny | tagi `vX.Y.Z` + `latest.json` | szersze testy |
| **Final** | **jeszcze nie istnieje** | przyszły `final.json` | zwykli użytkownicy |

**F-Droid** to osobna dystrybucja (flavor `fdroid`, inny podpis). MR !46612 zostaje przy ostatniej uzgodnionej propozycji, dopóki nie polecisz aktualizacji. Model kanałów GitHub nie jest kanałem F-Droid.

**Ważne:** APK GitHub i F-Droid mają **różne podpisy** — zmiana kanału dystrybucji wymaga reinstalacji (najpierw eksport zapisanych miejsc).

---

## Wybór w aplikacji (flavor `github`)

Ustawienia → **Aktualizacje (GitHub)**:

| Opcja | Co sprawdza |
|-------|-------------|
| **Nightly i nowsze** | `nightly.json` + `latest.json` + `final.json` — najwyższy `versionCode` |
| **Beta i nowsze** (domyślne) | `latest.json` + `final.json` |
| **Tylko Final** | `final.json` (cisza, dopóki Final nie istnieje) |

Brak pliku (404) jest pomijany. Flavor `fdroid` nie łączy się z GitHub.

---

## Nightly

**Cel:** świeże zmiany z `main`, bez statusu Beta.

| | Wartość |
|---|---|
| Źródło | każdy push na `main` → workflow `nightly.yml` |
| In-app | tak, gdy użytkownik wybierze Nightly i nowsze |
| Changelog | nie w [`CHANGELOG.md`](../CHANGELOG.md) |
| versionName | sufiks, np. `0.5.36-nightly` |
| Podpis | ten sam release keystore co Beta |
| Release | rolling tag `nightly` (prerelease) |

---

## Beta

**Cel:** względnie stabilna wersja do testów.

| | Wartość |
|---|---|
| Źródło | tag `vX.Y.Z` **bez myślnika** (np. `v0.5.35`, nie `v0.5.36-nightly`) |
| In-app | tak (`latest.json`) |
| Changelog | pełny wpis w [`CHANGELOG.md`](../CHANGELOG.md) |
| versionName | czysta, np. `0.5.35` |
| Podpis | release keystore |

Przykład bieżącej Beta: **0.5.42** (versionCode 49), tag `v0.5.42`.

Procedura: [`APP_UPDATES.md`](APP_UPDATES.md).

---

## Final *(planowane — nie istnieje)*

`final.json` pojawi się przy pierwszym Final. Do tego czasu opcja „Tylko Final” nie oferuje aktualizacji.

F-Droid pozostaje niezależny i aktualizowany wyłącznie na polecenie.

---

## Mapowanie obecnego stanu (2026-09-01)

| Co masz | Kanał |
|---------|-------|
| 0.5.42 / `latest.json` | **Beta** |
| 0.5.42 / `nightly.json` | **Nightly** (ten sam kod po promocji) |
| F-Droid MR | osobna dystrybucja; nie ruszana przy Nightly |
| Final | **jeszcze nie** |
