# F-Droid — warunki i plan udostępnienia NaviLas

> Zapis z sesji (2026-08-23). **Nie wdrożone** — wracamy do tematu później.  
> Cel: dystrybucja przez F-Droid teraz; czynności tak, by nie blokowały przyszłego Google Play.  
> Play Store **nie** jest w zakresie bieżących prac.

Przewodnik oficjalny: [Submitting to F-Droid Quick Start](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)  
Repo metadanych: [fdroiddata](https://gitlab.com/fdroid/fdroiddata)  
Anti-Features: [F-Droid Anti-Features](https://f-droid.org/docs/Anti-Features/)

---

## Stan projektu w momencie zapisu

| Element | Stan | Znaczenie dla F-Droid |
|---------|------|------------------------|
| `Woszik/NaviLas` | **prywatne** | Blokuje oficjalne F-Droid — źródła muszą być publiczne |
| `Woszik/NaviLas-releases` | publiczne | OK jako kanał APK / testerzy (osobno od F-Droid) |
| `LICENSE` w root | **brak** | Wymagana wolna licencja FOSS |
| ApplicationId | `pl.navilas.finder` | Nazwa pliku metadanych: `metadata/pl.navilas.finder.yml` |
| Auto-update z GitHub | włączony przy starcie | Ryzyko Anti-Feature (NonFreeNet / Tracking) — wyłączyć w buildzie F-Droid |
| Zależności | MapLibre, AndroidX, Material, OkHttp | Wyglądają na FOSS / akceptowalne |
| Release keystore | `~/.navilas/navilas-release.keystore` | Zachować; przy reproducible builds = ten sam podpis co GitHub |

---

## Dwie ścieżki dystrybucji

### A) Oficjalne repo F-Droid (preferowane „bycie w F-Droid”)

- Merge Request do `fdroiddata` z YAML metadanych.
- F-Droid buduje ze źródeł (albo weryfikuje reproducible + Twój APK).
- Recenzja wolontariuszy: zwykle dni–kilka tygodni.
- Aktualizacje: kolejne tagi / AutoUpdate według metadanych.

### B) Własne repo F-Droid / tylko GitHub Releases

- Szybciej, pełna kontrola.
- Użytkownik dodaje URL repo w kliencie F-Droid ręcznie.
- To **nie** to samo co obecność w oficjalnym katalogu F-Droid.

Na później zakładamy ścieżkę **A** (GitHub Releases zostaje równolegle dla testerów).

---

## Warunki wejścia do oficjalnego F-Droid (must)

1. **Kod FOSS** — publiczne repozytorium źródeł.
2. **Licencja wolna** — plik `LICENSE` (np. GPL-3.0, Apache-2.0, MIT) — do wyboru przy wdrażaniu.
3. **Budowa ze źródeł** — Gradle, bez zamkniętych binariów w APK.
4. **Metadane** — `metadata/pl.navilas.finder.yml` w forku `fdroiddata` + Merge Request.
5. **Brak rzeczy sprzecznych z Inclusion Policy** — m.in. brak zależności non-free w sensie F-Droid.

---

## Zalecenia pod F-Droid (i zgodne z przyszłym Play)

### Flavor / flaga buildu

Rozdzielić kanały aktualizacji:

| Flavor (propozycja) | Auto-update GitHub | Gdzie dystrybucja |
|---------------------|--------------------|-------------------|
| `github` / `sideload` | tak | NaviLas-releases |
| `fdroid` | **nie** (aktualizacje tylko klient F-Droid) | Oficjalne F-Droid |
| `play` (później) | nie | Google Play |

Bez wyłączenia GitHub check przy starcie F-Droid często oznacza Anti-Feature albo prosi o zmianę.

### Reproducible builds (mocno zalecane od razu)

| Wariant | Skutek |
|---------|--------|
| Bez reproducible | F-Droid podpisuje **swoim** kluczem → APK z GitHub ≠ linia aktualizacji z F-Droid (reinstall) |
| Z reproducible | Publikacja z **Twoim** keystore → jedna linia z GitHub Releases (i ewentualnie później Play przy tym samym kluczu) |

Wymaga m.in. w metadanych: `Binaries`, `AllowedAPKSigningKeys` (SHA-256 certyfikatu), dopasowania buildu (często wyłączenie dependency metadata w AGP).

**Nie przełączać się „później” lekko** — zmiana podpisu = reinstall u użytkowników.

### Anti-Features (prawdopodobne oznaczenia, zwykle nie blokują)

- **TetheredNet** — stałe endpointy (BDL, OpenFreeMap, Nominatim, Overpass) bez wyboru serwera.
- **NonFreeNet** — jeśli app łączy się z GitHub przy starcie (update) → unikać w flavorze `fdroid`.

BDL / OSM same w sobie zwykle nie blokują włączenia.

---

## Plan wdrożenia (gdy wrócimy)

1. Wybrać licencję → dodać `LICENSE`.
2. Upublicznić `Woszik/NaviLas` (lub publiczne mirror).
3. Krótki README w repo kodu (build, licencja, atrybucje BDL/OSM).
4. Flavor `fdroid` bez auto-update z GitHub; sensowny User-Agent + kontakt (Nominatim/Overpass).
5. Opcjonalnie: `fastlane/metadata/android/...` (opisy, screenshoty).
6. Zdecydować: reproducible + obecny keystore **tak/nie**.
7. Fork `fdroiddata` → `metadata/pl.navilas.finder.yml` → Merge Request.
8. Po merge: tagi wydań jak dotychczas; F-Droid łapie kolejne wersje według `UpdateCheckMode` / AutoUpdate.

---

## Czego nie robić w tym etapie (Play-only)

- Konto Google Play / Data safety / pełna polityka pod sklep.
- Wymuszanie AAB-only.
- Biurokracja Play wykraczająca poza to, co i tak pomaga F-Droid (atrybucje, czysty FOSS, brak sideload-updatera w sklepowym/F-Droid buildzie).

---

## Przydatne linki

- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Build_Metadata_Reference/
- https://f-droid.org/docs/FAQ_-_App_Developers/
- https://f-droid.org/docs/Anti-Features/
- https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md

---

## Notatki decyzyjne (do uzupełnienia przy powrocie)

- [ ] Licencja: _______________
- [ ] Publiczne źródła: tak / mirror
- [ ] Reproducible z `~/.navilas/navilas-release.keystore`: tak / nie
- [ ] Nazwy flavorów: _______________
