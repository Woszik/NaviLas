# Do zrobienia

Otwarte sprawy poza bieżącym kodem Nightly / Bety. Stan projektu: [`STATUS.md`](STATUS.md).

## Otwarte

### Wniosek do DGLP / BDL — oczekiwanie na odpowiedź merytoryczną

- **Wysłany:** 2026-09-03 na [bdl@bdl.lasy.gov.pl](mailto:bdl@bdl.lasy.gov.pl).
- **Numer zgłoszenia:** **BDLPOMOC-3343** (BDL-Pomoc; autoresponder 2026-09-03).
- **Treść wniosku:** [`WNIOSEK_BDL_SIEC_DROGOWA.md`](WNIOSEK_BDL_SIEC_DROGOWA.md).
- **Dalej:** odpowiedź BDL/DGLP (zakres, licencja, format, cykl) albo wskazanie właściwego adresata SILP.
- Oryginał PDF autorespondera: lokalnie `~/Dokumenty/NaviLas/korespondencja-bdl/` (poza GitHub).

## Zrobione

### Wniosek o sieć drogową LP — wysłany (2026-09-03)

E-mail z wnioskiem o geometrię sieci i status udostępnienia do ruchu; treść zapisana w docs.

### Opisy OsmAnd moto + Nightly 0.5.52

Ujednolicone teksty w aplikacji (dialog stylu trasy, import `.osf`) i w docs: mapowanie Krótka/Kręta/Standardowa → `profile=` / BRouter. Checklist NAWIGUJ zamknięty.

### Testy ręczne NAWIGUJ (Nightly 0.5.47+)

Checklist z [`NAVIGATION_EXPORT.md`](NAVIGATION_EXPORT.md) potwierdzony na urządzeniu.

### Wyszukiwanie miejsca po nazwie (Nightly 0.5.51 / UI 0.5.55)

Offline BDL 15/17/19, propozycje od 3 znaków, fold PL + literówki, skok na mapę. W **Filtrach miejsc** na dole: belka **Szukaj miejsca po nazwie** (domyślnie zwinięta).

### Zarządca BDL w Szczegółach (Nightly 0.5.49)

Szczegóły → Dociągnij z BDL: nadleśnictwo + leśnictwo z `WMS_BDL`. Wymaga sieci.

### Głuchy klik punktu w przeglądaniu mapy (Nightly 0.5.48)

Klik w pin zawsze otwiera kartę; zamknięcie X albo puste tło (debounce). Browse bez zoomu kamery. Job OSM z tokenem pokolenia. Karta overlay na mapie; postęp analizy na karcie.
