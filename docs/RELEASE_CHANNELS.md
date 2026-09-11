# Kanały aktualizacji NaviLas

Model dystrybucji **GitHub** (flavor `github`): **Nightly**, **Beta**, **Final**.

Ostatnia aktualizacja: **2026-09-07**.

Publiczny opis dla instalacji: [NaviLas-releases README](https://github.com/Woszik/NaviLas-releases#wybierz-kanał-świadomie).

```
Nightly  →  (stabilizacja)  →  Beta  →  (dopracowanie)  →  Final
   ↑ nightly.json                  ↑ latest.json            ↑ final.json (gdy powstanie)

F-Droid: niezależna dystrybucja flavoru `fdroid`; tylko na wyraźne polecenie, nie przy każdym wydaniu GitHub.
```

| Kanał | Status | Destynacja | Obietnica |
|-------|--------|------------|-----------|
| **Nightly** | aktywny | tag `nightly` (Pre-release) + `nightly.json` | Testowanie pomysłów; może być niestabilne. Zachęta do zabawy i **opinii / sugestii**. |
| **Beta** | aktywny | tagi `vX.Y.Z` + `latest.json` | Publikacja i użytkowanie testowe **na zasadach bety**. |
| **Final** | **jeszcze nie** | przyszły `final.json` | Docelowo kanał produkcyjny; błędy po zgłoszeniu — **naprawy priorytetowe**. |

**F-Droid** to osobna dystrybucja (flavor `fdroid`, inny podpis). MR !46612 proponuje **Beta 0.5.68** (tag `v0.5.68`).

**Ważne:** APK GitHub i F-Droid mają **różne podpisy** — zmiana źródła dystrybucji wymaga reinstalacji (najpierw eksport zapisanych miejsc). Nightly / Beta / Final na GitHubie mają **ten sam podpis**.

---

## Wybór w aplikacji (flavor `github`)

Ustawienia → **Aktualizacje (GitHub)**:

| Opcja | Co sprawdza | Sens |
|-------|-------------|------|
| **Nightly i nowsze** | `nightly.json` + `latest.json` + `final.json` — najwyższy `versionCode` | Świeże pomysły + wszystko stabilniejsze |
| **Beta i nowsze** (domyślne) | `latest.json` + `final.json` | Użytkowanie testowe na zasadach bety |
| **Tylko Final** | `final.json` | Cisza, dopóki Final nie istnieje |

Brak pliku (404) jest pomijany. Flavor `fdroid` nie łączy się z GitHub.

---

## Nightly

**Cel:** świeże zmiany z `main` — **praktycznie do testowania pomysłów**. Nie jest obietnicą stabilności; chętnych do „pobawienia się” zachęcamy do instalacji i feedbacku ([Issues](https://github.com/Woszik/NaviLas/issues)).

| | Wartość |
|---|---|
| Źródło | każdy push na `main` → workflow `nightly.yml` |
| In-app | gdy użytkownik wybierze Nightly i nowsze |
| Changelog | nie w [`CHANGELOG.md`](../CHANGELOG.md) |
| versionName | sufiks, np. `0.5.53-nightly` |
| Podpis | ten sam release keystore co Beta |
| Release | rolling tag `nightly` (prerelease) |

---

## Beta

**Cel:** wersja do **publikowania i szerszego użytkowania testowego**, na ogólnych zasadach bety (mogą być błędy; zakres funkcji może się zmieniać).

| | Wartość |
|---|---|
| Źródło | tag `vX.Y.Z` **bez myślnika** (np. `v0.5.46`, nie `v0.5.53-nightly`) |
| In-app | tak (`latest.json`) |
| Changelog | pełny wpis w [`CHANGELOG.md`](../CHANGELOG.md) |
| versionName | czysta, np. `0.5.46` |
| Podpis | release keystore |

Przykład bieżącej Beta: **0.5.68** (versionCode 81), tag `v0.5.68`.

Procedura: [`APP_UPDATES.md`](APP_UPDATES.md).

---

## Final *(planowane — nie istnieje)*

Docelowy kanał dla zwykłych użytkowników. Jak każda aplikacja może zawierać błędy — po wykryciu lub zgłoszeniu usuwane **w trybie priorytetowym**.

`final.json` pojawi się przy pierwszym Final. Do tego czasu opcja „Tylko Final” nie oferuje aktualizacji.

F-Droid pozostaje niezależny i aktualizowany wyłącznie na polecenie.

---

## Mapowanie obecnego stanu (2026-09-07)

| Co masz | Kanał |
|---------|-------|
| 0.5.68 / `latest.json` | **Beta** (versionCode 81) |
| 0.5.71-nightly / `nightly.json` | **Nightly** (versionCode 84) |
| F-Droid MR !46612 | kandydat **0.5.68** (ten sam co Beta GitHub) |
| Final | **jeszcze nie** |
