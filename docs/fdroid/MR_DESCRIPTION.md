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

- Flavor `fdroidRelease` — no in-app GitHub updater (`APP_UPDATE_ENABLED=false`)
- Parallel `github` flavor for sideload/APK releases (not used by F-Droid)
- Current proposed build: `0.5.68` (versionCode 81), commit `3d9f6b3` (Beta tag `v0.5.68` plus current Fastlane screenshots)

## Anti-Features

- **TetheredNet** — fixed endpoints: BDL, OpenStreetMap/Nominatim/Overpass, OpenFreeMap tiles

## Notes for reviewers

- Proposed build is now **0.5.68** (versionCode 81), commit `3d9f6b3a4c669cedd88d6004ba096743ca20cc55` (same app as GitHub Beta tag `v0.5.68`, plus current Fastlane screenshots and changelog 81)
- No reproducible builds / `Binaries` — GitHub APK is the `github` flavor and cannot match `fdroidRelease`. F-Droid may sign with its own key (accepted by author)
- GitHub Releases remain a separate tester channel from F-Droid
