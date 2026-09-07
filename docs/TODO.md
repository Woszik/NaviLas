# Do zrobienia

Otwarte sprawy poza bieżącym kodem Nightly / Bety. Stan projektu: [`STATUS.md`](STATUS.md).

## Otwarte

Brak spraw poza pomysłami na przyszłość (Compact poniżej).

## Zrobione

### Wniosek o sieć drogową LP — odpowiedź BDL (2026-09-07)

**BDLPOMOC-3343.** BDL: LP **nie posiada** geometrii sieci drogowej ani statusu udostępnienia dróg do ruchu kołowego. NaviLas zostaje przy heurystykach OSM („dostęp niepewny”, dojazd do parkingu / postoju LP). BDL nie wskazał innego adresata. Ewentualny wniosek do DGLP / SILP — tylko jeśli zdecydujesz. Treść: [`WNIOSEK_BDL_SIEC_DROGOWA.md`](WNIOSEK_BDL_SIEC_DROGOWA.md). Oryginał korespondencji lokalnie `~/Dokumenty/NaviLas/korespondencja-bdl/`.

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

### Głuchy klik punktu w przeglądaniu mapy (Nightly 0.5.48 → 0.5.60)

Od **0.5.60**: drugi klik w zaznaczony pin odznacza. Zamknięcie X albo puste tło (debounce). Browse bez zoomu kamery. Job OSM z tokenem pokolenia. Karta overlay na mapie; postęp analizy na karcie.

### Porównaj overlay (Beta 0.5.68)

Lista → Porównaj: karta na treść + scrim (tap / wstecz). Bez belki tytułu/X. Nazwy przyklejone. Zostaje otwarta po obrocie i po kliku nazwy (mapa → powrót na listę). Swipe pagera wyłączony, dopóki karta jest otwarta na liście.

## Pomysły na przyszłość

Niższy priorytet niż **Otwarte** powyżej. Nie startować, dopóki nie polecisz.

### Gęstość Compact (małe ekrany)

Jeden APK, nie osobna aplikacja. Standard vs Compact; Ustawienia Auto / zawsze standard / zawsze zwarty. Auto: przekątna fizyczna **&lt; 6,0″** (BV6900 i ~5″ → Compact; KINGKONG 8 → Standard). Compact: ciaśniejszy chrome, w poziomie karta POI jako dolny sheet zamiast panelu bocznego; lista C (Porównaj w sheet) opcjonalnie w tym samym pakiecie.

Szacunek: **3–4,5 dnia** z testami na BV6900 + KINGKONG 8. Bez D (split). Szczegóły i kolejność A–D: [`LANDSCAPE_ERGONOMICS.md`](LANDSCAPE_ERGONOMICS.md).
