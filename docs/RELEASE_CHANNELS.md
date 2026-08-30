# Kanały aktualizacji NaviLas

Model dystrybucji **GitHub** (flavor `github`): **Nightly**, **Beta**, **Final**.

Ostatnia aktualizacja: **2026-08-30**.

```
Nightly  →  (stabilizacja)  →  Beta  →  (dopracowanie)  →  Final
   ↑ lokal / CI (docelowo)         ↑ GitHub dziś              ↑ osobny track GitHub (później)

F-Droid: niezależna dystrybucja flavoru `fdroid`; kandydat 0.5.34 jest w otwartym MR.
```

| Kanał | Status | Destynacja | Dla kogo |
|-------|--------|------------|----------|
| **Nightly** | aktywny (lokalnie) | lokalny debug / CI (docelowo) | szybkie testy na urządzeniu |
| **Beta** | aktywny | [NaviLas-releases](https://github.com/Woszik/NaviLas-releases) | szersze testy |
| **Final** | **jeszcze nie istnieje** | osobny track GitHub + F-Droid | zwykli użytkownicy (docelowo) |

**F-Droid** to osobna dystrybucja (flavor `fdroid`, inny podpis). Beta 0.5.34 jest aktualnym kandydatem w otwartym MR; nazwy kanałów GitHub nie są osobnymi kanałami F-Droid.

**Ważne:** APK GitHub i F-Droid mają **różne podpisy** — zmiana kanału dystrybucji wymaga reinstalacji (najpierw eksport zapisanych miejsc).

---

## Nightly

**Cel:** świeże zmiany bez statusu „wydanie do testów szerszych”.

| | Dziś | Docelowo |
|---|------|----------|
| Źródło | lokalny `./gradlew :app:installGithubDebug` | CI + osobny manifest (np. `nightly.json`) |
| In-app update | nie | opcjonalnie, po wyborze kanału Nightly |
| Changelog | nie w [`CHANGELOG.md`](../CHANGELOG.md) | krótki / pomijany |
| versionName | sufiks roboczy, np. `0.5.34-calimoto-gpx` | np. `0.5.34-nightly.N` lub data/build |
| Oczekiwania | może być niestabilne | to samo |

Nightly `0.5.34-calimoto-gpx` został 2026-08-30 ustabilizowany i promowany do Beta `0.5.34`. Następny Nightly musi otrzymać nowy `versionCode`.

---

## Beta

**Cel:** względnie stabilna wersja do testów — to, co historycznie nazywaliśmy „oficjalnym GitHub”.

| | Wartość |
|---|---|
| Źródło | tagi `vX.Y.Z`, APK w NaviLas-releases, `latest.json` |
| In-app update | tak (`BuildConfig.APP_UPDATE_ENABLED`, flavor `github`) |
| Changelog | pełny wpis w [`CHANGELOG.md`](../CHANGELOG.md) |
| versionName | czysta, np. `0.5.33` (opcjonalnie jawny `-beta` w UI później) |
| Podpis | release keystore |

Przykład bieżącej Beta: **0.5.34** (versionCode 38), tag `v0.5.34`.

Procedura publikacji Beta: [`APP_UPDATES.md`](APP_UPDATES.md).

---

## Final *(planowane — nie istnieje)*

**Cel:** wydanie produkcyjne, zalecane dla zwykłych użytkowników.

| | Plan |
|---|---|
| GitHub | osobny track (osobny manifest / release, nie ten sam co Beta) |
| F-Droid | niezależna publikacja flavoru `fdroid`; polityka stabilnych wydań zostanie ustalona po przyjęciu aplikacji |
| Częstotliwość | rzadziej niż Beta |
| Changelog | pełny |
| Jakość | najwyższa w modelu trzech kanałów |

Do czasu powstania Final:

- GitHub in-app = **Beta** (`latest.json`)
- Nightly = lokal / później CI
- F-Droid = niezależny pipeline; 0.5.34 jest kandydatem do otwartego MR

---

## Mapowanie obecnego stanu (2026-08-30)

| Co masz | Kanał |
|---------|-------|
| 0.5.34 na NaviLas-releases | **Beta** |
| kolejny lokalny build z numerem wyższym niż 38 | **Nightly** |
| F-Droid MR / przyszłe buildy F-Droid | dystrybucja osobna; kandydat 0.5.34 |
| Final na GitHub | **jeszcze nie** |

---

## Implementacja (TODO — nie zrobione)

Dokumentacja modelu jest ustalona; kod i CI jeszcze nie rozróżniają kanałów poza „jeden manifest Beta”:

1. Osobne manifesty: `latest.json` (Beta) vs np. `nightly.json` (Nightly) vs przyszły `final.json`.
2. Naming APK / tagów Nightly vs Beta.
3. Wybór kanału w aplikacji (lub osobne buildy).
4. Track Final na GitHub + powiązanie F-Droid z Final.

Szczegóły techniczne obecnego updatera Beta: [`APP_UPDATES.md`](APP_UPDATES.md).
