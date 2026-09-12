# Odpowiedź maintainera (linsui) — MR !46612

## 2026-09-10 — kolejka testów (mostly ready)

linsui: *This MR is mostly ready. We'll test it later. If everything works well we'll merge it. Meantime if you release a new version please update this MR. Currently we have lots of MRs waiting for test so it may take a long time. If you'd like to help you can test those MRs and posting the result.*

Metadane i ABI split są OK. Następny krok po ich stronie: test APK na urządzeniu, potem merge. Nightly **0.5.70** (ikona) **nie** aktualizuje tego MR — F-Droid zostaje na Beta **0.5.68**. Nowa Beta / tag → wtedy YAML i fork.

Opcjonalna pomoc w kolejce (nie wymagana): MRy z labelem **review requested**, APK z artifactu CI, komentarz czy działa ([How to Help](https://f-droid.org/docs/How_to_Help/#review-apps-waiting-for-test)).

### Odpowiedź dla linsui (wklej w MR)

```
Thanks. We'll keep this MR on 0.5.68 and update it if we tag a newer Beta before you test.
```

---

## 2026-09-10 — ABI split

linsui: *Please setup abi split, see https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/#setup-abi-split*

Gradle: `-PABI=` filtruje `.so` MapLibre i ustawia `versionCode = 81*10 + {1..4}`. GitHub bez `-PABI` zostaje uniwersalny.

Commit NaviLas: `54f93be` na `fdroid/0.5.68`. YAML: cztery bloki 811–814 + `VercodeOperation`.

### Odpowiedź dla linsui (wklej w MR)

```
Done. Gradle takes -PABI=armeabi-v7a|arm64-v8a|x86|x86_64 (ndk.abiFilters, versionCode = 81*10+1..4). GitHub builds stay universal. Metadata now has four build blocks (811–814) and VercodeOperation as in the guide. Commit 54f93be9277e78d3a8eae09788ddd14f9caf0830.
```

---

## 2026-09-09 — drugi pipeline (commit `11f459fe`)

linsui ponownie: *Triggered a pipeline. Please check.*

Pipeline MR [#2833029751](https://gitlab.com/fdroid/fdroiddata/-/pipelines/2833029751):

| Job | Wynik | Przyczyna |
|-----|--------|-----------|
| schema / lint / rewritemeta / checkupdates / check source | **OK** | poprawka `gradle: fdroid` + format YAML |
| **fdroid build** | fail | Gradle **BUILD SUCCESSFUL** (`assembleFdroidRelease`, 3m 24s). Potem F-Droid: `FileNotFoundError: build/pl.navilas.finder/build/outputs/apk` — APK jest w `app/build/outputs/apk/...`. Brakowało `subdir: app`. |

Poprawka na forku: `subdir: app` (jak szablon F-Droid i np. NeoStumbler).

### Odpowiedź dla linsui (wklej w MR)

```
Thanks. Gradle built successfully (assembleFdroidRelease). F-Droid then looked for the APK under the repo-root build/outputs/apk instead of app/build/outputs/apk. I added `subdir: app`.
```

---

## 2026-09-09 — pierwszy pipeline (commit `76cfbfe4`)

linsui: *Triggered a pipeline. Please check.*

Pipeline MR [#2832432063](https://gitlab.com/fdroid/fdroiddata/-/pipelines/2832432063) (commit `76cfbfe4`):

| Job | Wynik | Przyczyna |
|-----|--------|-----------|
| **fdroid build** | fail | `gradle: fdroidRelease` → task `assembleFdroidReleaseRelease` (F-Droid dokleja `Release`). Trzeba `gradle: fdroid` → `assembleFdroidRelease`. |
| **fdroid rewritemeta** | fail | format YAML (łamanie linii, kolejność WebSite, `versionName` bez cudzysłowu) |
| **checkupdates** | fail | to samo + brak `AutoName: NaviLas` |
| schema / lint / check source | OK | |

Poprawka na forku: `gradle: fdroid`, `AutoName`, format jak po `rewritemeta`.

### Odpowiedź dla linsui (wklej w MR)

```
Thanks. The pipeline failed because `gradle: fdroidRelease` made F-Droid run `assembleFdroidReleaseRelease`. I changed it to `gradle: fdroid` (assembleFdroidRelease) and applied rewritemeta formatting (AutoName, field order).
```

---

## 2026-08-23 — pierwsze prośby

1. **Szablon MR:** Edit → wybierz **App Inclusion** template, przeczytaj, zaznacz checkboxy.
2. **Opisy:** Usuń Summary/Description z fdroiddata — dodaj **fastlane** w repo NaviLas (zrobione: `fastlane/metadata/android/`).
3. **Commit w Builds:** Użyj **pełnego hash** commita, nie tagu.

Szablon YAML: [`pl.navilas.finder.yml`](pl.navilas.finder.yml).
