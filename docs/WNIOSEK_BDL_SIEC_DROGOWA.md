# Wniosek do BDL / DGLP — sieć drogowa i status udostępnienia

**Stan:** treść gotowa do wysłania, **jeszcze nie wysłana**.  
**Do:** [bdl@bdl.lasy.gov.pl](mailto:bdl@bdl.lasy.gov.pl)  
**Temat:** Wniosek o ponowne wykorzystanie danych sieci drogowej LP (status udostępnienia do ruchu) — aplikacja NaviLas

Poniżej treść do skopiowania do wiadomości e-mail.

---

Do: bdl@bdl.lasy.gov.pl  
Temat: Wniosek o ponowne wykorzystanie danych sieci drogowej LP (status udostępnienia do ruchu) — aplikacja NaviLas

Szanowni Państwo,

zwracam się z wnioskiem o ponowne wykorzystanie danych Państwowego Gospodarstwa Leśnego Lasy Państwowe / Banku Danych o Lasach na potrzeby niekomercyjnej, otwartoźródłowej aplikacji **NaviLas**.

## 1. Czym jest NaviLas

NaviLas to aplikacja na telefony z systemem Android, która pomaga znaleźć **oficjalne miejsca odpoczynku i postoju w lasach**: wiaty i inne obiekty warstwy BDL „miejsca wypoczynku”, **parkingi leśne**, **miejsca postoju pojazdów**, obiekty powiązane (m.in. punkty widokowe) oraz strefy programu **„Zanocuj w lesie”**. **Domyślnie** pokazuje na mapie **zakazy wstępu do lasu** z danych BDL i nie proponuje miejsc leżących w tych strefach (użytkownik może to wyłączyć w filtrach). Dane BDL aplikacja **pobiera na telefon** (kopia offline): miejsca odpoczynku i strefy „Zanocuj w lesie” — przypomnienie o aktualizacji po **30 dniach**; zakazy wstępu — osobna, częstsza baza, przypomnienie już po **7 dniach**. Jeśli użytkownik pominie aktualizację zakazów, aplikacja **uporczywie przypomina o niej codziennie**, aż dane zostaną odświeżone. Chodzi o to, żeby nie pokazywać nieaktualnych stref, w których wstęp jest zabroniony.

Aplikacja **nie zastępuje** zarządzeń nadleśniczego ani znaków w terenie. W informacji o aplikacji jest wyraźnie napisane, że NaviLas **nie jest oficjalną aplikacją Lasów Państwowych ani BDL**, a dane mapowe BDL mają charakter poglądowy.

Kod jest na licencji GNU GPL v3. Kontakt: woszi@pm.me.

## 2. Jak działa aplikacja

Użytkownik szuka miejsc wokół GPS, punktu na mapie, miejscowości albo wzdłuż korytarza trasy. Wyniki pochodzą z **Banku Danych o Lasach** (usługa MapServer „Czas w Las” oraz opcjonalna kopia offline na urządzeniu). Mapa tła i nazwy miejscowości pochodzą z OpenStreetMap.

W profilu **motocykl** aplikacja dodatkowo ocenia dojazd: szuka najbliższej drogi w OpenStreetMap (ok. 400 m od punktu) i pokazuje odległość oraz rodzaj nawierzchni. **Nie usuwa** miejsc z listy tylko dlatego, że w OSM brakuje tagu prawnego — większość leśnych `track` w OSM nie ma informacji, czy wjazd jest dozwolony.

Obowiązujące prawo (art. 29 ustawy o lasach, zarządzenie Dyrektora Generalnego LP nr 36/2021) jest jasne: w lesie pojazd silnikowy może poruszać się drogą publiczną albo drogą leśną **udostępnioną i oznakowaną**. Domyślnie droga leśna jest zamknięta. **Brak szlabanu nie oznacza zgody.**

Tego statusu NaviLas dziś **nie ma w danych**. Publiczny BDL udostępnia m.in. miejsca postoju i parkingi oraz dojazdy pożarowe (geometria PPOZ — nie jest to zgoda na ruch turystyczny). Wykazy dróg otwartych nadleśnictwa publikują zwykle jako PDF, bez jednolitej warstwy GIS dla całej Polski.

Dlatego aplikacja może jedynie ostrożnie informować („dostęp niepewny”) albo — przy oficjalnym parkingu/postoju BDL i drodze z operatorem LP w OSM — napisać, że to dojazd do takiego obiektu. To **heurystyka**, nie orzeczenie prawne.

## 3. Po co potrzebuję danych — troska o las

Proszę o możliwość ponownego wykorzystania **geometrii sieci drogowej LP** oraz **statusu udostępnienia drogi do ruchu** (drogi ujęte w docelowej sieci / otwarte na podstawie zarządzeń), w miarę możliwości dla **całej Polski**, z informacją o licencji, formacie i cyklu aktualizacji.

**Cel jest ochronny, nie „otwierający teren”.**

Zależy mi na tym, żeby aplikacja **nie sugerowała użytkownikom jeżdżenia po drogach leśnych niedopuszczonych do ruchu**. Bez oficjalnej flagi udostępnienia NaviLas nie potrafi wiarygodnie odróżnić drogi, którą wolno dojechać do parkingu LP, od drogi gospodarczej albo pożarowej, po której turysta wjeżdżać nie powinien. Chcę to naprawić danymi źródłowymi, a nie zgadywaniem z OpenStreetMap.

Dane wykorzystałbym wyłącznie do:

- pokazywania dojazdu tylko tam, gdzie LP dopuszcza ruch,
- oznaczania pozostałych leśnych odcinków jako niedostępnych / niepewnych,
- ograniczenia nawigacji motocyklowej do dróg zgodnych z Państwa statusem.

Nie zamierzam publikować pełnej kopii sieci jako osobnego produktu ani zachęcać do wjazdu poza udostępnionymi drogami i wyznaczonymi parkingami / postojami.

Jeśli warstwa nie może być publiczna w całości, proszę o informację, jaki zakres (np. wyłącznie drogi udostępnione do ruchu, bez pozostałej sieci gospodarczej) i na jakich warunkach byłby możliwy.

## 4. Instalacja (stan obecny)

NaviLas nie jest w Google Play. Wersję testową (**Beta**) można pobrać z publicznego repozytorium wydań:

**https://github.com/Woszik/NaviLas-releases**

Na stronie jest plik APK aktualnego wydania Beta. Po instalacji aplikacja sama sprawdza nowszą Betę; aktualizację zatwierdza użytkownik.

Przy pierwszym otwarciu APK Android / Play Protect może ostrzec, że aplikacja pochodzi spoza Sklepu Play — to oczekiwane. Proszę instalować **wyłącznie** z powyższego repozytorium. W oknie Play Protect: „Więcej szczegółów”, potem instalacja bez skanowania.

Aplikacja jest bezpłatna. Zapisane miejsca można wyeksportować (Lista → Zapisane → Kopia).

## 5. Prośba o opinię i ewentualne zaakceptowanie

Jeżeli temat Państwa zainteresuje, bardzo proszę o **uruchomienie aplikacji i opinię**: czy sposób pokazywania miejsc BDL, stref „Zanocuj w lesie”, zakazów wstępu i dojazdu jest zgodny z tym, jak Lasy Państwowe chcą, żeby korzystano z lasu. Chętnie wprowadzę poprawki wynikające z Państwa uwag.

Gdyby po zapoznaniu się z NaviLas uznali Państwo, że aplikacja działa w interesie lasu i odwiedzających, uprzejmie proszę o rozważenie **możliwości przedstawiania jej jako zaakceptowanej przez Lasy Państwowe** (w brzmieniu, które Państwo zatwierdzą — np. opinia, rekomendacja albo adnotacja, że LP zapoznały się z aplikacją i nie zgłaszają zastrzeżeń). Nie będę używał znaku LP ani sformułowania o akceptacji bez wyraźnej zgody.

Będę wdzięczny za odpowiedź, czy wniosek o dane sieci drogowej i statusu udostępnienia mogą Państwo rozpatrzyć, a jeśli nie — kto jest właściwym adresatem w DGLP / SILP.

Z poważaniem  
Woszik  
autor aplikacji NaviLas  
woszi@pm.me  
https://github.com/Woszik/NaviLas-releases
