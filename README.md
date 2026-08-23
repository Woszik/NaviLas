# NaviLas

Aplikacja Android do wyszukiwania miejsc odpoczynku w lasach — na podstawie otwartych danych przestrzennych (Bank Danych o Lasach, OpenStreetMap).

## Instalacja (testy)

Oficjalne wydania APK i aktualizacje:

**https://github.com/Woszik/NaviLas-releases**

Aplikacja sprawdza dostępność nowszej wersji przy starcie. Aktualizację zatwierdzasz samodzielnie.

> **Play Protect:** Przy instalacji APK spoza Google Play system może pokazać ostrzeżenie — to normalne przy dystrybucji spoza sklepu. Instalujesz na własną odpowiedzialność z zaufanego źródła (link powyżej).

## Kopia zapisanych miejsc (eksport / import)

Zapisane miejsca i kategorie możesz **zabezpieczyć przed utratą** (np. przed odinstalowaniem aplikacji lub zmianą telefonu):

1. Otwórz ekran **Lista** → **Zapisane**
2. Kliknij **Kopia** → **Eksportuj zapisane…**
3. Zapisz plik JSON w wybranym miejscu (np. **Pobrane** lub chmura)

Po ponownej instalacji NaviLas:

1. **Lista** → **Zapisane** → **Kopia** → **Importuj zapisane…**
2. Wybierz wcześniej zapisany plik
3. Wybierz **Scal** (dodaje brakujące) lub **Zastąp wszystko** (przywraca kopię na czysto)

Plik kopii jest zwykłym JSON — aplikacja **nie wysyła go automatycznie** nigdzie poza Twoim urządzeniem.

Więcej informacji: ikona **ⓘ** na górnym pasku → **O aplikacji**.

## Funkcje

- Wyszukiwanie miejsc odpoczynku (GPS, punkt na mapie, miejscowość)
- Mapa ze strefami „Zanocuj w lesie”
- Ocena dojazdu (samochód / motocykl)
- Pobieranie danych BDL do użycia offline
- Zapisywanie miejsc z kategoriami i komentarzami
- Eksport i import zapisanych miejsc
- Automatyczne sprawdzanie aktualizacji z GitHub

## Źródła danych

- [Bank Danych o Lasach (BDL)](https://www.bdl.lasy.gov.pl/)
- [OpenStreetMap](https://www.openstreetmap.org/copyright)
- OpenFreeMap / MapLibre

## Kontakt

woszi@pm.me

## Dokumentacja developerska

Szczegóły techniczne w katalogu [`docs/`](docs/):

- [`APP_UPDATES.md`](docs/APP_UPDATES.md) — aktualizacje z GitHub
- [`FDROID.md`](docs/FDROID.md) — plan publikacji w F-Droid
- [`BDL_POINT_CATEGORIES.md`](docs/BDL_POINT_CATEGORIES.md) — kategorie punktów BDL
