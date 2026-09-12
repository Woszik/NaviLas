# Planowanie trasy — instrukcja (Nightly 0.5.77+)

Instrukcja dla testerów i użytkowników. Kopia żyje też w [Issue #1](https://github.com/Woszik/NaviLas/issues/1). Przy zmianie zachowania funkcji **aktualizuj ten plik i Issue**.

**Zasada:** OsmAnd wyznacza trasę. NaviLas szuka miejsc **przy** trasie i pomaga dodać punkty korekty. Linia trasy w NaviLas jest **tylko do odczytu**.

Potrzebujesz: **OsmAnd** + **NaviLas 0.5.77** (lub nowszy).

---

## 1. Wyznacz trasę w OsmAnd

1. W OsmAnd ustaw cel (i ewentualnie punkty pośrednie).
2. Poczekaj, aż OsmAnd **policzy trasę**.
3. Wyeksportuj / udostępnij trasę jako **GPX**  
   (menu trasy → Udostępnij / Eksport → GPX).
4. Wybierz **NaviLas**.

Jeśli w NaviLas jest już inna trasa, zobaczysz pytanie: **„Zastąpić obecną trasę?”**.

Po udanym imporcie NaviLas sam przełącza się na tryb **„Wzdłuż trasy z OsmAnd”** i pokazuje linię na mapie.

---

## 2. Szukaj miejsc przy trasie

1. Zakładka **Szukaj** → źródło: **Wzdłuż trasy z OsmAnd**.
2. Ustaw **Pas przy trasie (km)** — domyślnie 5 km po obu stronach.
3. Wybierz **tryb** i **profil** jak zwykle (samochód / moto).
4. Naciśnij **Znajdź wzdłuż linii**.

Na liście odległość to **km trasy** (wzdłuż linii), nie prosta do GPS.

---

## 3. Punkty z ręki (cel / punkt trasy)

| Gest | Co robi |
|------|---------|
| **Tap** w pustą mapę | Arkusz: *Ustaw cel trasy* / *Dodaj punkt trasy* / *Ustaw punkt wyszukiwania* |
| **Długie przytrzymanie** | Od razu **ustawia cel** |
| **Tap w niebieski punkt** | Menu: *Przesuń* / *Nawiguj tutaj* / *Usuń* |

- **Cel** — koniec trasy (w planie jest tylko jeden).
- **Punkt trasy** — punkt pośredni do korekty w OsmAnd.
- Linii trasy **nie edytujesz** w NaviLas — tylko punkty.

*Przesuń:* wybierz opcję, potem tapnij nowe miejsce na mapie.

---

## 4. Korekta trasy (powrót do OsmAnd)

1. W panelu trasy: **Udostępnij trasę (GPX)**.
2. Wybierz **OsmAnd**.
3. W OsmAnd przelicz trasę przez nowe punkty.
4. Ponownie **udostępnij GPX → NaviLas** (podmieni plan).

Bez tego kroku NaviLas nadal pokazuje **starą** geometrię — to zamierzone.

---

## 5. Pamięć i czyszczenie

- Trasa **wraca po restarcie** apki (wraz z punktami).
- **Wyczyść trasę** — usuwa plan; tryb wraca do GPS.
- Ręczny tryb **„Wzdłuż linii”** (korytarz rysowany palcem) to **osobna** funkcja — nie zapisuje się po restarcie.

---

## Szybka checklista dla testerów

1. OsmAnd → GPX → NaviLas → widać linię i tryb trasy.
2. Restart NaviLas → trasa wraca.
3. ZNAJDŹ → wyniki z „km trasy”.
4. Tap mapy → arkusz; długie przytrzymanie → cel.
5. Punkt → Przesuń / Usuń.
6. **Udostępnij trasę (GPX)** → chooser (bez crasha) → OsmAnd.
7. Nowy GPX z OsmAnd → dialog zastąpienia → nowa linia.
8. Wyczyść trasę → mapa bez linii.

---

## Czego nie robić / typowe pułapki

- Nie oczekuj, że NaviLas **sam** przeliczy prowadzenie — to robi OsmAnd.
- Udostępnianie **samego punktu** (bez trasy) z OsmAnd ≠ import trasy.
- Tryb **Przeglądaj mapę** nie planuje trasy.
- Bardzo długie trasy: pierwsze wyszukanie może chwilę potrwać (pas jest dzielony na odcinki).
- Wersja **0.5.76** miała crash przy eksporcie — testuj **≥ 0.5.77**.
