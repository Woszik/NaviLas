# Do zrobienia

Otwarte sprawy poza bieżącym kodem Nightly / Bety. Stan projektu: [`STATUS.md`](STATUS.md).

## Otwarte

### Testy ręczne Nightly 0.5.47+ — NAWIGUJ

Checklist z [`NAVIGATION_EXPORT.md`](NAVIGATION_EXPORT.md):

1. Kolejność listy: OsmAnd, Cruiser, Współrzędne GPS, Wybierz nawigację, Google Maps.
2. OsmAnd zainstalowany → aplikacja, nie przeglądarka (OsmAnd+ i darmowy OsmAnd).
3. Brak OsmAnd → dialog + Play Store; po instalacji propozycja profili `.osf`.
4. Cruiser zainstalowany → pin. Brak → dialog, **nie** systemowy chooser.
5. Wybierz nawigację → systemowa lista aplikacji.
6. Google Maps → trasa https.
7. Współrzędne GPS → schowek.
8. MOTOCYKL bez drogi OSM: NAWIGUJ ukryty.
9. Ustawienia → Wgraj profile NaviLas do OsmAnd.
10. MOTOCYKL → OsmAnd → Krótka / Kręta / Standardowa: czy OsmAnd **przełącza profil** i liczy trasę (BRouter dla Krótka/Kręta).

### OsmAnd — weryfikacja `profile=` na urządzeniu

Dialog i import `.osf` są w Nightly 0.5.47; nie uznajemy za domknięte, dopóki nie potwierdzisz przełączania profilu. Setup: Ustawienia → Wgraj profile, albo [`osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt`](osmand/KINGKONG_OSMAND_MOTO_PROFILES.txt).

### Wniosek do DGLP / BDL — sieć drogowa i status udostępnienia

- **Po co:** NaviLas nie ma krajowej warstwy „droga leśna otwarta / zamknięta”. OSM prawie nigdy nie ma tagu prawnego. Heurystyka moto (BDL 17/19 + `operator` LP + korytarz) to tylko przybliżenie UI.
- **Co LP ma wewnątrz:** sieć drogowa SILP oraz flaga udostępnienia (zarządzenie DG LP **36/2021**: otwarte drogi „ujęte w docelowej sieci”). Publicznie tego nie wystawiają — nadleśnictwa publikują PDF-y, nie GIS.
- **Co zapytać:** ponowne wykorzystanie geometrii sieci drogowej + statusu udostępnienia do ruchu (cała Polska), na potrzeby aplikacji NaviLas.
- **Adres:** [bdl@bdl.lasy.gov.pl](mailto:bdl@bdl.lasy.gov.pl)
- **Treść listu:** [`WNIOSEK_BDL_SIEC_DROGOWA.md`](WNIOSEK_BDL_SIEC_DROGOWA.md)
- **Nieznane do czasu odpowiedzi:** czas, zakres warstwy, licencja.
- **Stan:** treść gotowa, **nie wysłane**.

## Zrobione

### Wyszukiwanie miejsca po nazwie (Nightly 0.5.51)

Offline BDL 15/17/19, propozycje od 3 znaków, fold PL + literówki, skok na mapę.

### Zarządca BDL w Szczegółach (Nightly 0.5.49)

Szczegóły → Dociągnij z BDL: nadleśnictwo + leśnictwo z `WMS_BDL`. Wymaga sieci.

### Głuchy klik punktu w przeglądaniu mapy (Nightly 0.5.48)

Klik w pin zawsze otwiera kartę; zamknięcie X albo puste tło (debounce). Browse bez zoomu kamery. Job OSM z tokenem pokolenia. Karta overlay na mapie; postęp analizy na karcie.
