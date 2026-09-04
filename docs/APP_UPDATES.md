# Aktualizacje NaviLas z GitHub

Dotyczy wyłącznie buildu **`github`** (`BuildConfig.APP_UPDATE_ENABLED = true`).  
Flavor **`fdroid`** nie łączy się z GitHub — aktualizacje tylko przez klienta F-Droid.

**Kanały:** Nightly / Beta / Final — model i obietnice: [`RELEASE_CHANNELS.md`](RELEASE_CHANNELS.md).  
Tekst dla instalujących: [NaviLas-releases — Wybierz kanał](https://github.com/Woszik/NaviLas-releases#wybierz-kanał-świadomie).

**Dziś ten dokument opisuje updater GitHub z wyborem kanału.**

Nightly i Beta są na GitHub (`nightly.json` / `latest.json`). Final jeszcze nie istnieje (`final.json`). F-Droid jest niezależną dystrybucją flavoru `fdroid` i **nie** jest aktualizowany przy publikacji GitHub — tylko na wyraźne polecenie.

NaviLas (Beta) pobiera informacje o nowej wersji z publicznego repozytorium **NaviLas-releases** (`latest.json`).

## Architektura (Beta — obecny stan)

| Element | Lokalizacja |
|---------|-------------|
| Kod aplikacji | `Woszik/NaviLas` (publiczne) |
| APK + `latest.json` (Beta) | `Woszik/NaviLas-releases` (publiczne) |
| URL manifestów | `latest.json` (Beta), `nightly.json` (Nightly), `final.json` (Final, opcjonalny) |
| Build CI | `./gradlew :app:assembleGithubRelease` |
| Wybór kanału | Ustawienia: Nightly i nowsze / Beta i nowsze / Tylko Final |

## Pierwsze uruchomienie (jednorazowo)

### 1. Utwórz publiczne repo releases

```bash
gh repo create Woszik/NaviLas-releases --public \
  --description "NaviLas — opis aplikacji i pliki APK do instalacji"
```

Dodaj README z opisem aplikacji i instrukcją instalacji (zezwolenie na „nieznane źródła”).

### 2. Wygeneruj keystore release

```bash
keytool -genkeypair -v \
  -keystore navilas-release.keystore \
  -alias navilas \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12
```

**Ważne:** ten sam keystore musi podpisywać wszystkie wersje. Utrata keystore = użytkownicy muszą odinstalować aplikację i zainstalować od nowa.

### 3. Sekrety w repo NaviLas (Settings → Secrets)

| Secret | Wartość |
|--------|---------|
| `RELEASES_REPO_TOKEN` | Fine-grained PAT z `Contents: Read and write` tylko dla `NaviLas-releases` |
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 navilas-release.keystore` |
| `RELEASE_KEYSTORE_PASSWORD` | hasło keystore |
| `RELEASE_KEY_ALIAS` | np. `navilas` |
| `RELEASE_KEY_PASSWORD` | hasło klucza |

### 4. Pierwsza Beta ręczna (smoke test)

1. Podnieś `versionCode` i `versionName` w `app/build.gradle.kts`.
2. Zbuduj i opublikuj APK:

```bash
./gradlew :app:assembleGithubRelease
sha256sum app/build/outputs/apk/github/release/app-github-release.apk
```

3. Utwórz release w `NaviLas-releases` z assetem `navilas-X.Y.Z.apk`.
4. Wrzuć `latest.json` (wzór: `releases/latest.json.example`) na gałąź `main`.

## Publikacja Nightly (CI)

Każdy push na `main` (oraz `workflow_dispatch`) uruchamia `.github/workflows/nightly.yml`:

1. Buduje `assembleGithubRelease`.
2. Nadpisuje prerelease `nightly` w NaviLas-releases (`navilas-<versionName>.apk`).
3. Aktualizuje `nightly.json` (`channel: nightly`).

`versionCode` musi rosnąć względem poprzedniego Nightly i zainstalowanej Bety.

F-Droid **nie** jest przy tym ruszany.

## Publikacja kolejnej Beta (CI)

Nightly nie nadpisuje `latest.json`. Beta tylko z czystego tagu `vX.Y.Z`.

1. Zaktualizuj w `app/build.gradle.kts`:
   - `versionCode` — zawsze +1 względem poprzedniej Beta (i wyżej niż zainstalowany Nightly, jeśli ma ten sam podpis i ma się dać nadpisać)
   - `versionName` — np. `0.5.34` (krótka nazwa, bez sufiksu roboczego Nightly)
2. Uzupełnij [`CHANGELOG.md`](../CHANGELOG.md) — wpis Beta + link do APK.
3. Commit — **pierwszy akapit** commita trafia do `releaseNotes` w `latest.json` (dialog aktualizacji w aplikacji). Bez pustej linii w środku — CI bierze tylko do pierwszej pustej linii.
4. Tag + push:

```bash
git tag v0.5.34
git push origin main --tags
```

5. Workflow `.github/workflows/release.yml`:
   - buduje release APK,
   - liczy SHA-256,
   - publikuje release w `NaviLas-releases`,
   - aktualizuje `latest.json` na `main`.
6. Ręcznie: README w repo `NaviLas-releases` — tabela historii wersji (patrz [`CHANGELOG.md`](../CHANGELOG.md)).

### Release notes — dwa miejsca

| Gdzie | Źródło | Długość |
|-------|--------|---------|
| Dialog aktualizacji w aplikacji | Pierwszy akapit commita release → `latest.json` | 1–3 zdania |
| Pełny opis po wdrożeniu | [`CHANGELOG.md`](../CHANGELOG.md) | Kilka punktów + link APK |

## Powrót do starszej wersji (downgrade)

Auto-update **nie** instaluje starszej wersji. Downgrade in-app **nie jest wspierany** (ograniczenie Androida — niższy `versionCode` nie nadpisze nowszego APK).

**Ręczny powrót** (ten sam podpis GitHub, zwykle w obrębie Beta):

1. **Lista → Zapisane → Kopia → Eksportuj** (zabezpieczenie danych).
2. [NaviLas-releases → Releases](https://github.com/Woszik/NaviLas-releases/releases) — pobierz starszy `navilas-*.apk`.
3. Zainstaluj ręcznie (jak przy pierwszej instalacji).

Historia opublikowanych **Beta** i linki do APK: [`CHANGELOG.md`](../CHANGELOG.md).

## Zachowanie aplikacji

- **Start:** sprawdzenie manifestów wybranego kanału po ~2 s. Brak nowszej wersji → cisza. Jest nowsza → dialog z etykietą Nightly / Beta / Final.
- **Ręcznie:** menu **⋮ → Sprawdź aktualizacje** albo przycisk na ekranie Wyszukiwanie (przy braku update: komunikat „masz najnowszą”).
- **Nowa wersja:** dialog z release notes → „Aktualizuj” → pobieranie → weryfikacja SHA-256 → instalator systemowy.
- **Później:** wersja zapisana jako odrzucona do czasu pojawienia się wyższego `versionCode`.
- **Wymuszenie:** `minVersionCode` w manifest — dialog bez „Później”.

## Troubleshooting

| Problem | Rozwiązanie |
|---------|-------------|
| „Masz najnowszą wersję” mimo nowego APK | `versionCode` w APK ≤ obecny na telefonie |
| Błąd sumy kontrolnej | `sha256` w `latest.json` nie pasuje do APK |
| Instalacja odrzucona | Inny klucz podpisu niż zainstalowana wersja |
| Brak dialogu przy starcie | Brak sieci (auto milczy) / wersja odrzucona („Później”) / już masz tę wersję |
| 404 manifestu | Repo `NaviLas-releases` nie istnieje lub brak `latest.json` na `main` |

## Bezpieczeństwo

- Token GitHub **tylko w CI**, nigdy w APK.
- Pobieranie wyłącznie przez HTTPS.
- Instalacja tylko po pozytywnej weryfikacji SHA-256.
