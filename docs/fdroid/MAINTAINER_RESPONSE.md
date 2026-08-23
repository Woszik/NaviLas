# Odpowiedź maintainera (linsui) — MR !46612

Data: 2026-08-23

## Prośby

1. **Szablon MR:** Edit → wybierz **App Inclusion** template, przeczytaj, zaznacz checkboxy.
2. **Opisy:** Usuń Summary/Description z fdroiddata — dodaj **fastlane** w repo NaviLas (zrobione: `fastlane/metadata/android/`).
3. **Commit w Builds:** Użyj **pełnego hash** commita, nie tagu `v0.5.7-fdroid-prep`.

## Co zaktualizować w fork fdroiddata

Plik: `metadata/pl.navilas.finder.yml` — wersja minimalna w [`pl.navilas.finder.yml`](pl.navilas.finder.yml).

`commit:` = hash commita na GitHub **NaviLas**, który zawiera fastlane (patrz `docs/FDROID.md` lub tag po pushu).

## Fastlane (w NaviLas)

```
fastlane/metadata/android/
  en-US/   — title, short_description, full_description, changelogs/9.txt
  pl-PL/   — title, short_description, full_description
```

Screenshoty / icon.png — opcjonalnie później (maintainer może poprosić).

## Szablon MR (App Inclusion)

W GitLab MR → **Edit** → Description → **Choose a template** → **App Inclusion**.

Typowe checkboxy (zaznacz po przeczytaniu szablonu):

- [ ] App is FOSS, license in repo
- [ ] Source code public
- [ ] I am the author or have permission
- [ ] App builds with recipe in metadata
- [ ] No NonFree dependencies (or documented)
- [ ] Descriptions in fastlane in source repo
- [ ] … (reszta wg szablonu w GitLab)

Usuń ręcznie wpisany Summary/Description z opisu MR — zostaw checklistę z szablonu.

## Odpowiedź dla linsui (skopiuj po aktualizacji MR)

```
Thanks for the review. I updated the MR:

- Switched to the App Inclusion template and checked the boxes.
- Removed Summary/Description from fdroiddata; added fastlane metadata in https://github.com/Woszik/NaviLas/tree/main/fastlane/metadata/android
- Replaced tag with full commit hash in Builds.commit: `2afd6253d1ebd04314eb64333cd2dcba5e4f4d12`

Please let me know if anything else is needed.
```
