# Historia wersji NaviLas (kanał Beta)

Wydania **Beta** opublikowane w [NaviLas-releases](https://github.com/Woszik/NaviLas-releases).

- **Nightly** (lokalne / przyszłe CI) — nie są tu uwzględniane; stan: [`docs/STATUS.md`](docs/STATUS.md).
- **Final** — jeszcze nie istnieje; model kanałów: [`docs/RELEASE_CHANNELS.md`](docs/RELEASE_CHANNELS.md).

Format: `versionName` (versionCode) — data — krótki opis. Link do APK na GitHub Releases.

---

## 0.5.42 (49) — 2026-09-01

**APK:** [navilas-0.5.42.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.42/navilas-0.5.42.apk)

Zakazy wstępu BDL na mapie (paczka offline, zoom jak Zanocuj, filtr poza strefą). Obiekty BDL w Search i Browse, belki Filtry miejsc / Obiekty BDL na mapie, wielozaznaczenie z porównaniem oraz etykiety dojazdu moto.

**Poprzednia wersja:** [0.5.36](#0536-42--2026-08-30)

---

## 0.5.36 (42) — 2026-08-30

**APK:** [navilas-0.5.36.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.36/navilas-0.5.36.apk)

W przeglądaniu mapy punkty BDL są znowu rysowane na widocznym obszarze (bez klastrów). Domyślny kanał aktualizacji to Beta i nowsze.

**Poprzednia wersja:** [0.5.35](#0535-39--2026-08-30)

---

## 0.5.35 (39) — 2026-08-30

**APK:** [navilas-0.5.35.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.35/navilas-0.5.35.apk)

Przypomnienie o aktualizacji danych BDL offline, gdy baza ma co najmniej 30 dni. Dialog pokazuje datę pobrania i ostatni zakres; **Aktualizuj** pobiera zapisaną konfigurację, **Później** odkłada pytanie o 7 dni.

**Poprzednia wersja:** [0.5.34](#0534-38--2026-08-30)

---

## 0.5.34 (38) — 2026-08-30

**APK:** [navilas-0.5.34.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.34/navilas-0.5.34.apk)

Przewidywalny i szybszy start: domyślne Wyszukiwanie, bez wyścigu z trybem Przeglądania, obliczenia filtrów poza wątkiem interfejsu, etapowa analiza dróg OSM oraz grupowanie punktów BDL na mapie. Rozszerzone obiekty BDL, pełniejsze wyszukiwanie miejscowości, poprawiony eksport do nawigacji i czytelne komunikaty postępu.

Nowe menu i ustawienia obejmują tryb startowy, niewygaszanie ekranu oraz motyw System / Czujnik światła / Dzień / Noc. Motyw nocny ma własną paletę i ciemny styl mapy; w trybie dziennym tekst overflow menu jest czarny. Układ zweryfikowano na KINGKONG 8 w pionie i poziomie.

**Poprzednia wersja:** [0.5.33](#0533-37--2026-08-28)

---

## 0.5.33 (37) — 2026-08-28

**APK:** [navilas-0.5.33.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.33/navilas-0.5.33.apk)

Wspólne filtry miejsc w wyszukiwaniu i na mapie (bottom sheet „Filtry” z podsumowaniem na żywo przed zatwierdzeniem), śledzenie pozycji GPS na mapie (FAB ▶/❚❚), klasyfikacja źródeł naturalnych w danych BDL (filtr „Źródło”). Po aktualizacji zalecane ponowne pobranie pakietu BDL offline.

**Poprzednia wersja:** [0.5.24](#0524-28--2026-08-26)

---

## 0.5.24 (28) — 2026-08-26

**APK:** [navilas-0.5.24.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.24/navilas-0.5.24.apk)

Przeglądanie mapy (browse), wyszukiwanie wzdłuż korytarza (corridor search), spójność UI między ekranami.

**Poprzednia wersja:** [0.5.7-fdroid-prep](#057-fdroid-prep-9--2026-08-23)

---

## 0.5.7-fdroid-prep (9) — 2026-08-23

**APK:** [navilas-0.5.7-fdroid-prep.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.7-fdroid-prep/navilas-0.5.7-fdroid-prep.apk)

Przygotowanie kanału F-Droid: licencja GPL-3.0, publiczne źródła, flavory `github` / `fdroid`.

**Poprzednia wersja:** [0.5.6-update-cache](#056-update-cache-8--2026-08-23)

---

## 0.5.6-update-cache (8) — 2026-08-23

**APK:** [navilas-0.5.6-update-cache.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.6-update-cache/navilas-0.5.6-update-cache.apk)

Omijanie cache CDN przy sprawdzaniu aktualizacji.

**Poprzednia wersja:** [0.5.5-saved-backup](#055-saved-backup-7--2026-08-23)

---

## 0.5.5-saved-backup (7) — 2026-08-23

**APK:** [navilas-0.5.5-saved-backup.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.5-saved-backup/navilas-0.5.5-saved-backup.apk)

Eksport i import zapisanych punktów (kopia zapasowa JSON przed reinstalacją).

**Poprzednia wersja:** [0.5.4-install-session](#054-install-session-6--2026-08-23)

---

## 0.5.4-install-session (6) — 2026-08-23

**APK:** [navilas-0.5.4-install-session.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.4-install-session/navilas-0.5.4-install-session.apk)

Płynniejsza instalacja aktualizacji (PackageInstaller), jeden ciągły dialog pobieranie → instalacja.

**Poprzednia wersja:** [0.5.3-update-ui](#053-update-ui-5--2026-08-23)

---

## 0.5.3-update-ui (5) — 2026-08-23

**APK:** [navilas-0.5.3-update-ui.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.3-update-ui/navilas-0.5.3-update-ui.apk)

Stabilniejsza aktualizacja: bez migania okienek, uprawnienie do instalacji przed pobraniem APK.

**Poprzednia wersja:** [0.5.2-update-check](#052-update-check-4--2026-08-23)

---

## 0.5.2-update-check (4) — 2026-08-23

**APK:** [navilas-0.5.2-update-check.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.2-update-check/navilas-0.5.2-update-check.apk)

Sprawdzanie aktualizacji przy każdym starcie; dialog tylko gdy jest nowsza wersja (Aktualizuj / Później).

**Poprzednia wersja:** [0.5.1-page-nav](#051-page-nav-3--2026-08-23)

---

## 0.5.1-page-nav (3) — 2026-08-23

**APK:** [navilas-0.5.1-page-nav.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.1-page-nav/navilas-0.5.1-page-nav.apk)

Przewijanie między ekranami (swipe) i strzałki w stopce nawigacji.

**Poprzednia wersja:** [0.5.0-app-update](#050-app-update-2--2026-08-22)

---

## 0.5.0-app-update (2) — 2026-08-22

**APK:** [navilas-0.5.0-app-update.apk](https://github.com/Woszik/NaviLas-releases/releases/download/v0.5.0-app-update/navilas-0.5.0-app-update.apk)

Pierwsza wersja z auto-update z GitHub (manifest `latest.json`, pobieranie i weryfikacja SHA-256).

---

## Powrót do starszej wersji

Aplikacja **nie obsługuje downgrade in-app**. Aby wrócić do wcześniejszej wersji:

1. Eksportuj zapisane miejsca: **Lista → Zapisane → Kopia → Eksportuj**.
2. Pobierz starszy APK z [Releases](https://github.com/Woszik/NaviLas-releases/releases) (ten sam kanał GitHub).
3. Zainstaluj ręcznie — wymaga **niższego** `versionCode` niż obecny; Android nie pozwoli na downgrade przez auto-update.

Szczegóły: [`docs/APP_UPDATES.md`](docs/APP_UPDATES.md).
