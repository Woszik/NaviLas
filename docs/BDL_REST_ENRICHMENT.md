# BDL — miejsca odpoczynku i wzbogacanie (Checkpoint 3+)

## Podstawowy wynik

**Warstwa 15** — `Miejsca wypoczynku - ob. punktowe`  
Jeden rekord = jeden wynik wyszukiwania (`RestSite`).

**Dodatkowo (standalone), warstwy 17 / 19** — gdy obiekt ma infrastrukturę odpoczynku:

- `wiata=T` **lub** `palenisko=T` **lub** `lawostoly=T`

i **nie** leży w ≤ `restLinkRadiusMeters` (domyślnie **100 m**) od już wybranego wyniku z warstwy 15  
(wtedy pozostaje tylko jako `relatedObjects` / satelita).

Przykład: *Miejsce postoju pojazdów Uroczysko Potempowe* (warstwa 19, wiata+palenisko+ławostoły)  
→ wynik wyszukiwania, mimo braku rekordu na warstwie 15.

**Warstwa 27** (pomniki, kapliczki, …) — **nie** tworzy samodzielnych wyników.

## Cechy (wyłącznie pola BDL)

Potwierdzone pola flagowe `T`/`N`:

| Feature | Pole BDL | Etykieta UI |
|---|---|---|
| WIATA | `wiata` | Wiata |
| PALENISKO | `palenisko` | Palenisko |
| PARKING | `parking` | Parking |
| WODA_PITNA | `woda_pitna` | Woda pitna |
| LAWOSTOLY | `lawostoly` | Ławostoły |
| KUCHENKA | `kuchenka` | Kuchenka |
| TOALETY | `toalety_tm` / `toalety_st` / `os_toalety` / `n_toalety` | Toalety |
| LAD_ROWER | `lad_rower` | Ładowanie rowerów |
| SERW_ROWER | `serw_rower` | Serwis rowerowy |
| KAPIELISKO | `kapielisko` | Kąpielisko |
| MARINA | `marina` | Marina |

**Uwaga:** w tym MapServerze **nie ma** osobnych warstw „Źródło” ani „Pomnik przyrody”.  
Nie tworzymy sztucznych kategorii o takich nazwach. Obiekty z warstwy 27 (np. drzewo, krzyż) mogą pojawić się wyłącznie jako **powiązane obiekty** z nazwą z `nzw_ob`.

## Warstwy powiązań przestrzennych

| ID | Nazwa | Rola |
|---|---|---|
| 17 | Parkingi leśne - ob. punktowe | satelita; albo **wynik**, jeśli amenities + brak 15 w 100 m |
| 19 | Miejsca postoju pojazdów - ob. punktowe | satelita; albo **wynik**, jeśli amenities + brak 15 w 100 m |
| 25 | Punkty widokowe - ob. punktowe | obiekt powiązany |
| 27 | Inne punktowe nienoclegowe… | obiekt powiązany (nazwa BDL) |
| 0 | Obszar programu Zanocuj w Lesie | status strefy (poligony) |

Pole `msc_odp` na warstwach 25/27 to flaga T/N („czy to miejsce odpoczynku”), **nie** klucz obcy do warstwy 15.  
`foreign_key` nie łączy parkingu z miejscem wypoczynku w próbkach — stąd **relacja przestrzenna**.

## Łączenie

1. Pobierz miejsca (15) + pojazdy (17/19) + satelity (25/27) + poligony Zanocuj (0) w envelope ~ promienia wyszukiwania.
2. Zbuduj wyniki z warstwy 15.
3. Dla 17/19 spełniających amenities: jeśli brak wyniku 15 w ≤ 100 m → dodaj jako wynik; w przeciwnym razie tylko satelita.
4. Dla każdego wyniku: satelita w odległości **≤ `restLinkRadiusMeters`** → `relatedObjects`.
5. Deduplikacja po `id` (`BdlIdentity`).
6. Jeśli w related jest parking (warstwa 17) lub flaga / warstwa źródłowa parking → cecha `PARKING`.
7. Cel nawigacji samochodu: parking powiązany, albo sam punkt (15/19), albo współrzędne parkingu gdy wynik = warstwa 17.

Parametr: `SearchConfig.restLinkRadiusMeters`.

## Zanocuj

- **IN_ZONE** — punkt wewnątrz poligonu (punkt na granicy = IN_ZONE)
- **NEAR_ZONE** — poza poligonem, odległość do granicy ≤ `zanocujNearZoneMeters` (domyślnie **500 m**)
- **OUTSIDE_ZONE** — pozostałe
- Wiele poligonów: najlepszy status `IN_ZONE > NEAR_ZONE > OUTSIDE_ZONE`
- **Nigdy** centroid strefy do decyzji przynależności
- Geometria warstwy 0: `maxAllowableOffset=0` (przy `outSR=4326` wartości typu 250 niszczą pierścienie)

Filtr UI: `ALL` | `ONLY_IN_ZONE` (tylko IN_ZONE; NEAR niewystarczający).

## OSM

Wyłącznie drogi / cel nawigacji motocykla (Checkpoint 2). Bez nazw, cech ani atrakcji z OSM.
