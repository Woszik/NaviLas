# Metadane F-Droid

Szablon: [`pl.navilas.finder.yml`](pl.navilas.finder.yml)

## Merge Request

1. Fork https://gitlab.com/fdroid/fdroiddata
2. Skopiuj YAML do `metadata/pl.navilas.finder.yml`
3. Utwórz MR — pierwsze wydanie NaviLas (GPL-3.0, flavor `fdroid`)
4. Po każdym release zaktualizuj `versionName`, `versionCode`, `commit`, `CurrentVersion*`

Bez sekcji `Binaries` — F-Droid podpisuje własnym kluczem (różny od GitHub Releases).

Szczegóły: [`../FDROID.md`](../FDROID.md)
