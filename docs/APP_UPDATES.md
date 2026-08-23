# Aktualizacje NaviLas z GitHub

Dotyczy wyłącznie buildu **`github`** (`BuildConfig.APP_UPDATE_ENABLED = true`).  
Flavor **`fdroid`** nie łączy się z GitHub — aktualizacje tylko przez klienta F-Droid.

NaviLas pobiera informacje o nowej wersji z publicznego repozytorium **NaviLas-releases**.

## Architektura

| Element | Lokalizacja |
|---------|-------------|
| Kod aplikacji | `Woszik/NaviLas` (publiczne) |
| APK + `latest.json` | `Woszik/NaviLas-releases` (publiczne) |
| URL manifestu | `BuildConfig.UPDATE_MANIFEST_URL` (flavor `github`) |
| Build CI | `./gradlew :app:assembleGithubRelease` |

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

### 4. Pierwszy release ręczny (smoke test)

1. Podnieś `versionCode` i `versionName` w `app/build.gradle.kts`.
2. Zbuduj i opublikuj APK:

```bash
./gradlew :app:assembleRelease
sha256sum app/build/outputs/apk/release/app-release.apk
```

3. Utwórz release w `NaviLas-releases` z assetem `navilas-X.Y.Z.apk`.
4. Wrzuć `latest.json` (wzór: `releases/latest.json.example`) na gałąź `main`.

## Publikacja kolejnych wersji (CI)

1. Zaktualizuj w `app/build.gradle.kts`:
   - `versionCode` — zawsze +1
   - `versionName` — np. `0.5.1`
2. Commit + tag:

```bash
git tag v0.5.1
git push origin main --tags
```

3. Workflow `.github/workflows/release.yml`:
   - buduje release APK,
   - liczy SHA-256,
   - publikuje release w `NaviLas-releases`,
   - aktualizuje `latest.json` na `main`.

## Zachowanie aplikacji

- **Start:** zawsze sprawdzenie manifestu po ~2 s. Brak update → cisza. Jest update → dialog.
- **Ręcznie:** ekran Wyszukiwanie → „Sprawdź aktualizacje” (przy braku update: komunikat „masz najnowszą”).
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
