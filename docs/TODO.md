# Do zrobienia

Otwarte sprawy poza bieżącym kodem Nightly / Bety. Stan projektu: [`STATUS.md`](STATUS.md).

## Otwarte

### Wniosek do DGLP / BDL — sieć drogowa i status udostępnienia

- **Po co:** NaviLas nie ma krajowej warstwy „droga leśna otwarta / zamknięta”. OSM prawie nigdy nie ma tagu prawnego. Heurystyka moto (BDL 17/19 + `operator` LP + korytarz) to tylko przybliżenie UI.
- **Co LP ma wewnątrz:** sieć drogowa SILP oraz flaga udostępnienia (zarządzenie DG LP **36/2021**: otwarte drogi „ujęte w docelowej sieci”). Publicznie tego nie wystawiają — nadleśnictwa publikują PDF-y, nie GIS.
- **Co zapytać:** ponowne wykorzystanie geometrii sieci drogowej + statusu udostępnienia do ruchu (cała Polska), na potrzeby aplikacji NaviLas.
- **Adres:** [bdl@bdl.lasy.gov.pl](mailto:bdl@bdl.lasy.gov.pl)
- **Treść listu:** [`WNIOSEK_BDL_SIEC_DROGOWA.md`](WNIOSEK_BDL_SIEC_DROGOWA.md)
- **Nieznane do czasu odpowiedzi:** czas, zakres warstwy, licencja.
- **Stan:** treść gotowa, **nie wysłane**.

## Zrobione

### Opisy OsmAnd moto + Nightly 0.5.52

Ujednolicone teksty w aplikacji (dialog stylu trasy, import `.osf`) i w docs: mapowanie Krótka/Kręta/Standardowa → `profile=` / BRouter. Checklist NAWIGUJ zamknięty.

### Testy ręczne NAWIGUJ (Nightly 0.5.47+)

Checklist z [`NAVIGATION_EXPORT.md`](NAVIGATION_EXPORT.md) potwierdzony na urządzeniu.

### Wyszukiwanie miejsca po nazwie (Nightly 0.5.51)

Offline BDL 15/17/19, propozycje od 3 znaków, fold PL + literówki, skok na mapę.

### Zarządca BDL w Szczegółach (Nightly 0.5.49)

Szczegóły → Dociągnij z BDL: nadleśnictwo + leśnictwo z `WMS_BDL`. Wymaga sieci.

### Głuchy klik punktu w przeglądaniu mapy (Nightly 0.5.48)

Klik w pin zawsze otwiera kartę; zamknięcie X albo puste tło (debounce). Browse bez zoomu kamery. Job OSM z tokenem pokolenia. Karta overlay na mapie; postęp analizy na karcie.
