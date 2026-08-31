# BDL — kategorie i typy punktów (referencja NaviLas)

> **Źródło:** usługa ArcGIS MapServer „Czas w Las”  
> `https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/Czas_w_las/WFS_BDL_czas_w_las/MapServer`  
> **Ostatnia weryfikacja warstw:** 2026-08-22 (REST `?f=json`)  
> **Kontekst projektu:** ten plik opisuje **klasyfikację BDL**, nie kategorie użytkownika w zapisanych punktach (`SavedPointCategory`).

---

## 1. Warstwy MapServer — pełny katalog (ID → nazwa)

| ID | Nazwa warstwy BDL |
|---:|---|
| 0 | Obszar programu Zanocuj w Lesie - ob. powierzchniowy |
| 1 | Ośrodki szkoleniowo-wypoczynkowe - ob. punktowe |
| 2 | Hotele - ob. punktowe |
| 3 | Kwatery myśliwskie - ob. punktowe |
| 4 | Pokoje gościnne - ob. punktowe |
| 5 | Schroniska leśne - ob. punktowe |
| 6 | Miejsca biwakowania - ob. punktowe |
| 7 | Miejsca biwakowania - ob. powierzchniowe |
| 8 | Pola biwakowe - ob. punktowe |
| 9 | Pola biwakowe - ob. powierzchniowe |
| 10 | Kempingi - ob. punktowe |
| 11 | Kempingi - ob. powierzchniowe |
| 12 | Obozowiska harcerskie - ob. punktowe |
| 13 | Obozowiska harcerskie - ob. powierzchniowe |
| 14 | Inne powierzchniowe obiekty noclegowe - ob. powierzchniowe |
| **15** | **Miejsca wypoczynku - ob. punktowe** |
| 16 | Miejsca wypoczynku - ob. powierzchniowe |
| **17** | **Parkingi leśne - ob. punktowe** |
| 18 | Parkingi leśne - ob. powierzchniowe |
| **19** | **Miejsca postoju pojazdów - ob. punktowe** |
| 20 | Miejsca postoju pojazdów - ob. powierzchniowe |
| 21 | Miejsca/place zabaw dla dzieci - ob. punktowe |
| 22 | Miejsca/place zabaw dla dzieci - ob. powierzchniowe |
| 23 | Inne powierzchniowe nienoclegowe obiekty rekreacyjno-wypoczynkowe - ob. punktowe |
| 24 | Inne powierzchniowe nienoclegowe obiekty rekreacyjno-wypoczynkowe - ob. powierzchniowe |
| **25** | **Punkty widokowe - ob. punktowe** |
| 26 | Punkty wodowania i cumowania sprzętu wodnego - ob. punktowe |
| **27** | **Inne punktowe nienoclegowe obiekty rekreacyjno-wypoczynkowe i edukacyjne - ob. punktowe** |
| 28 | Ośrodki edukacji ekologicznej - ob. punktowe |
| 29 | Izby edukacji leśnej - ob. punktowe |
| 30 | Zielona klasa - ob. punktowe |
| 31 | Inne kubaturowe obiekty nienoclegowe - ob. punktowe |
| 32 | Strefy intensywnego oddziaływania społecznego - ob. powierzchniowe |
| 33 | Strefy zrównoważonego oddziaływania społecznego - ob. powierzchniowe |
| 34 | Ścieżki dydaktyczne - ob. liniowe |
| 35 | Szlaki turystyczne - ob. liniowe |
| 36–47 | Zabytki (punktowe / liniowe / powierzchniowe, ruchome / nieruchome / archeologiczne / inne) |

**Geometria:** `punktowe` | `powierzchniowe` | `liniowe` — decyduje o sposobie renderowania i wyszukiwania w NaviLas.

---

## 2. Warstwy używane przez NaviLas (wyszukiwanie miejsc odpoczynku)

Pakiet **NaviLas core** (offline + online) pobiera **6 warstw**:

| ID | Stała w kodzie | Rola w aplikacji |
|---:|---|---|
| 0 | `LAYER_ZANOCUJ` | Poligony programu „Zanocuj w Lesie” — status strefy, nie wynik punktowy |
| 15 | `LAYER_REST` | **Główny wynik wyszukiwania** — miejsce wypoczynku |
| 17 | `LAYER_PARKING` | Parking leśny — satelita lub wynik samodzielny (patrz §4) |
| 19 | `LAYER_STOP` | Miejsce postoju pojazdów — satelita lub wynik samodzielny |
| 25 | `LAYER_VIEWPOINT` | Punkt widokowy — tylko obiekt powiązany (`relatedObjects`) |
| 27 | `LAYER_OTHER` | Inne obiekty (kapliczki, pomniki przyrody, …) — tylko powiązane |

Implementacja: `RestSiteRepository` (`pl.navilas.finder.data.bdl`).

Pakiet **pełna baza BDL** offline dodaje m.in. warstwy noclegowe (1–14), edukacyjne (28–31), strefy (32–33), szlaki (34–35) — lista ID w `BdlOfflineStore.FULL_BDL_LAYER_IDS`. Zabytki 36–47 **nie** są pobierane.

### Overlay „Obiekty BDL” (przeglądanie mapy)

Osobna warstwa na mapie, **domyślnie wyłączona** (od Beta **0.5.34**). Nie dubluje wyników odpoczynku (15 / amenity 17/19). Wejście: rozwijany przycisk **Obiekty BDL** na ekranie 1 oraz belka **Obiekty BDL** w arkuszu **Filtry** na mapie — w Search i Browse (Nightly 0.5.38). Każda grupa jest niezależna; puste zaznaczenie nic nie rysuje (Nightly 0.5.37).

| Grupa UI | Warstwy | Pakiet | Kolor |
|----------|---------|--------|-------|
| Widok | 25 | CORE | niebieski |
| Inne / edukacja | 27; 28–31 | 27 = CORE; 28–31 = FULL | brąz |
| Woda | 26 | FULL | turkus |
| Zabawa / rekreacja | 21, 23 | FULL | pomarańcz |
| Nocleg leśny | 1–6, 8, 10, 12 (punktowe) | FULL | burgund |

Klik → karta + szczegóły + NAWIGUJ (współrzędne punktu). Lista wyników zostaje przy zaznaczeniu. Overlay rysowany tylko w bbox widoku (min. zoom ~8.5, cap 400). Poligony/szlaki — poza zakresem.

### Overlay „Zakazy wstępu” (Nightly 0.5.40)

**Nie** jest to Czas w Las. Osobny MapServer `Mapa_zakazow_wstepu_do_lasu`. Domyślnie wyłączone; checkbox przy Obiektach BDL / Filtrach. **Osobna baza offline** (nie paczka BDL miejsc): przycisk „Pobierz zakazy offline” zapisuje uproszczone poligony całej Polski w `filesDir/entry_bans/bans.json`. Gdy baza jest na urządzeniu, mapa filtruje lokalny indeks (działa bez sieci). Bez bazy — jak wcześniej, zapytanie **bbox widoku** (HTTPS).

Aktualność jest **krótsza niż BDL miejsc**: po **7 dniach** monit „Aktualizacja zakazów wstępu”; **Później** / zamknięcie odkłada pytanie o **24 godziny**. Monit tylko gdy baza już istnieje i jest przeterminowana; nie nachodzi na dialog BDL ani aktualizacji APK.

| UI | Źródło | Rysunek |
|----|--------|---------|
| Zakazy wstępu BDL | warstwy 6 / 7 / 2 (inne przyczyny, zabiegi SOR, pożar — LOD szczegółowy) | czerwona plama + obrys |

Klik obszaru → dialog (przyczyna, nadleśnictwo, leśnictwo, oddział, data od/do). Miejsce w poligonie: znacznik na karcie i liście. Warstwa 0 (wilgotność ściółki) **nie** jest zakazem. Informacja pomocnicza — obowiązują oficjalne źródła LP / BDL.

---

## 3. Cechy punktowe BDL (`SiteFeature`) — flagi T/N na rekordzie

To **atrybuty infrastruktury** na warstwach punktowych (głównie 15, czasem 17/19), **nie** osobne warstwy MapServer.

| Enum (`SiteFeature`) | Pole(a) BDL | Etykieta UI (PL) |
|---|---|---|
| `WIATA` | `wiata` | Wiata |
| `PALENISKO` | `palenisko` | Palenisko |
| `PARKING` | `parking` (+ warstwa 17 / parking w pobliżu) | Parking |
| `WODA_PITNA` | `woda_pitna` | Woda pitna |
| `LAWOSTOLY` | `lawostoly` | Ławostoły |
| `KUCHENKA` | `kuchenka` | Kuchenka |
| `TOALETY` | `toalety_tm`, `toalety_st`, `os_toalety`, `n_toalety` | Toalety |
| `LAD_ROWER` | `lad_rower` | Ładowanie rowerów |
| `SERW_ROWER` | `serw_rower` | Serwis rowerowy |
| `KAPIELISKO` | `kapielisko` | Kąpielisko |
| `MARINA` | `marina` | Marina |

Wartość **`T`** = tak, **`N`** / brak = nie. Ekstrakcja: `BdlFeatureExtractor`.

**Świadomie nie mapujemy** na osobne kategorie wyników BDL: „Pomnik przyrody” itp. — takich warstw/pól **nie ma** w tym MapServerze.

**Filtr „Źródło naturalne” (od 0.5.33):** osobna logika poza kategoriami wyniku — warstwa **27** z `zrodlo=T` w promieniu 200 m + heurystyka `inne_atr` na miejscu odpoczynku. Klasyfikacja: pewne / niepewne / odrzut (`NaturalSpringClassifier`). W trybie offline wymaga ponownego pobrania BDL (pola `inne_atr`, `zrodlo` w eksporcie).

---

## 4. Reguły wyniku vs obiekt powiązany (NaviLas)

| Warstwa | Kiedy trafia na listę wyników | Kiedy tylko `relatedObjects` |
|---|---|---|
| **15** | Zawsze (w promieniu) | — |
| **17, 19** | `wiata=T` **lub** `palenisko=T` **lub** `lawostoly=T` **oraz** brak miejsca z warstwy 15 w ≤ 100 m | W pozostałych przypadkach — satelita w ≤ 100 m od wyniku |
| **25, 27** | **Nigdy** samodzielnie | Satelita w ≤ 100 m (nazwa z `nzw_ob`) |
| **0** | — (poligon) | Status Zanocuj dla punktów 15/17/19 |

Parametr promienia łączenia: `SearchConfig.restLinkRadiusMeters` (domyślnie **100 m**).

---

## 5. Kod typu obiektu — pole `tur_rec_pnt_cd`

Pole tekstowe na warstwach punktowych; przechowywane w `RelatedBdlObject.typeCode`.  
Przykłady z próbek API (2026-08-22):

| Warstwa | `tur_rec_pnt_cd` | Przykład `nzw_ob` |
|---:|---|---|
| 15 | `MSC WYPOCZ` | Wiata w Milówce |
| 17 | `PARKING` | Parking Leśny |
| 19 | `MSC POST` | Pęperzyńska |
| 21 | `PL ZABAW` | Przystanek zabawa |
| 25 | `PKT WIDOK` | Punkt widokowy nad Wartą |
| 26 | `PT WODOW` | Przystań rekreacyjna z miejscem wodowania… |
| 27 | `IN PT NIEN` | Kapliczka, pomnik przyrody, drzewo Bartek, … |

**Uwaga:** słownik kodów nie jest wystawiony w metadanych warstwy (`domain: null`); pełna lista wymagałaby skanowania całej bazy. W praktyce **nazwa prezentacyjna** pochodzi z `nzw_ob`.

Inne pola pomocnicze:
- **`msc_odp`** (warstwy 25/27) — flaga T/N „czy miejsce odpoczynku”, **nie** klucz obcy do warstwy 15
- **`nzw_ob`** — nazwa obiektu wyświetlana użytkownikowi

---

## 6. Status strefy Zanocuj (`ZanocujStatus`) — klasyfikacja względem poligonów warstwy 0

| Status | Znaczenie |
|---|---|
| `IN_ZONE` | Punkt wewnątrz poligonu Zanocuj |
| `NEAR_ZONE` | Poza poligonem, ≤ 500 m od granicy (domyślnie) |
| `OUTSIDE_ZONE` | Dalej od strefy |

To **nie** kategoria typu obiektu BDL, ale klasyfikacja **przynależności do programu** „Zanocuj w Lesie”.

---

## 7. Identyfikator rekordu (odwołania w kodzie)

Format: `bdl:{layerId}:{scheme}:{value}` — priorytet: `foreign_key` → `tur_rec_pnt_id` / `tur_sleep_poly_id` → `objectid`.

Szczegóły: [POI_IDENTITY.md](./POI_IDENTITY.md).

---

## 8. Powiązane dokumenty w repozytorium

| Plik | Zawartość |
|---|---|
| [BDL_REST_ENRICHMENT.md](./BDL_REST_ENRICHMENT.md) | Pipeline wyszukiwania, łączenie warstw, Zanocuj |
| [POI_IDENTITY.md](./POI_IDENTITY.md) | Klucze `BdlIdentity` |
| `domain/RestSiteModels.kt` | `SiteFeature`, `RestSite`, `ZanocujStatus` |
| `data/bdl/RestSiteRepository.kt` | Stałe warstw i nazwy BDL |
| `data/bdl/ForestEntryBanLoader.kt` | Overlay zakazów wstępu (osobny MapServer) |
| `data/bdl/ForestEntryBanStore.kt` | Osobna baza offline zakazów (`entry_bans/bans.json`) |

---

## 9. Rozróżnienie: BDL vs kategorie użytkownika (NaviLas)

| | **Kategorie BDL** (ten plik) | **Kategorie zapisane** (aplikacja) |
|---|---|---|
| Źródło | MapServer Lasy Państwowe | Użytkownik (lokalnie) |
| Przykłady | Warstwa 15, parking 17, wiata/palenisko | „Weekend”, „Ulubione” |
| Przechowywanie | API / offline BDL | `filesDir/saved_points.json` |
| Wiele na punkt | Warstwa = 1; cechy = wiele flag T/N | Wiele kategorii użytkownika dozwolone |
