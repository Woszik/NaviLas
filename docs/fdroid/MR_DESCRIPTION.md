# Opis Merge Request do fdroiddata (wklej w GitLab)

**Tytuł MR:** `New App: NaviLas (pl.navilas.finder)`

**Opis:**

## Summary

NaviLas helps find forest rest sites in Poland using Bank Danych o Lasach (BDL) and OpenStreetMap.

## License

GPL-3.0-or-later — https://github.com/Woszik/NaviLas/blob/main/LICENSE

## Source

Public: https://github.com/Woszik/NaviLas

## Build

- Flavor `fdroid` (`gradle: fdroid` → `assembleFdroidRelease`) — no in-app GitHub updater (`APP_UPDATE_ENABLED=false`)
- Parallel `github` flavor for sideload/APK releases (not used by F-Droid)
- Current proposed build: `0.5.68`, ABI-split versionCodes 811–814, commit `54f93be`

## Anti-Features

- **TetheredNet** — fixed endpoints: BDL, OpenStreetMap/Nominatim/Overpass, OpenFreeMap tiles

## Notes for reviewers

- Proposed build is now **0.5.68** ABI-split (versionCodes 811–814), commit `54f93be9277e78d3a8eae09788ddd14f9caf0830`
- No reproducible builds / `Binaries` — GitHub APK is the `github` flavor and cannot match `assembleFdroidRelease`. F-Droid may sign with its own key (accepted by author)
- GitHub Releases remain a separate tester channel from F-Droid
