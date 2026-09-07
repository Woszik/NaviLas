# Ergonomia landscape i gęstość ekranu

Ostatnia aktualizacja: **2026-09-07**.

Testy ręczne: **Cubot KINGKONG 8** (wzorzec „wystarczająco duży”) i **Blackview BV6900** (na razie **za mały**). Docelowy próg małego ekranu: ok. **5 cali** przekątnej.

## Rekomendowana kolejność

| Krok | Zakres | Stan |
|------|--------|------|
| **A** | Landscape: niższy toolbar, stopka bez ◀▶ (zostają zakładki + gest) | **zrobione** Nightly **0.5.62** |
| **A′ / 1** | Landscape: **jedna belka** (⋮ + Szukaj/Mapa/Lista), bez tytułu, bez dolnego paska | **zrobione** Nightly **0.5.63** |
| **B** | Landscape mapa: pełna wysokość, hint overlay, karta POI z boku | **zrobione** Nightly **0.5.62** |
| **C** | Lista: Porównaj overlay — treść, sticky nazwy, bez belki, zostaje po obrocie | **zrobione** Nightly **0.5.67** |
| **D** | Landscape dwukolumnowy | **odłożone** |

Portret: dwa paski jak dotychczas.

## Kolejka Nightly (nie Compact)

1. ~~Jedna belka chrome w poziomie~~ **0.5.63**
2. ~~**C** — Porównaj overlay (treść, sticky nazwy, bez belki, obrót)~~ **0.5.67**
3. Compact — nadal **pomysł na przyszłość** (poniżej Otwarte w TODO).

## Gęstość Compact — pomysł na przyszłość

Priorytet **poniżej** [`TODO.md`](TODO.md) (sekcja Otwarte). Jeden APK.

| | |
|--|--|
| Tryby | Standard (KINGKONG 8) / Compact (BV6900 teraz; docelowo ~5″) |
| Auto | przekątna `DisplayMetrics` **&lt; 6,0″** → Compact |
| Ustawienia | Automatycznie (domyślnie) / Zawsze standardowy / Zawsze zwarty |
| Compact UI | ciaśniejszy chrome; poziom: karta POI **dolny sheet ~38% wysokości**; lista C opcjonalnie |
| Szacunek | 3–4,5 dnia (ustawienia + mapa sheet + chrome; lista C w tym pakiecie). Bez D. |

## Dlaczego BV6900 nadal ciasny po A+B

Oba telefony są ~720 px w krótszym boku. W landscape **wysokość w dp** jest zbliżona (~400 vs ~416). Różnica to głównie **fizyczne cale** (BV6900 ~5,8″ vs KINGKONG 8 ~6,4″): te same kontrolki są większe względem palca i zajmują więcej „poczucia” ekranu. Panel POI 300 dp zjada też większy ułamek **szerokości** landscape na BV6900.

Dlatego sam `layout-land` nie wystarczy na 5″ / BV6900 — potrzebna osobna **gęstość compact** (poniżej), a nie tylko kolejny landscape.

Compact nie jest w kolejce Nightly — zapis w [`TODO.md`](TODO.md) → Pomysły na przyszłość.
