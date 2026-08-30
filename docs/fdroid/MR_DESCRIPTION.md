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
- Current proposed build: `v0.5.34` (versionCode 38)

## Anti-Features

- **TetheredNet** — fixed endpoints: BDL, OpenStreetMap/Nominatim/Overpass, OpenFreeMap tiles

## Notes for reviewers

- Proposed build is now **0.5.34** (versionCode 38), commit `64bcec381cca119307118c9fd58f6754f7c8bddf`
- No reproducible builds / `Binaries` — GitHub APK is the `github` flavor and cannot match `fdroidRelease`. F-Droid may sign with its own key (accepted by author)
- GitHub Releases remain a separate tester channel from F-Droid
