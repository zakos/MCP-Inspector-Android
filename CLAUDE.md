# MCP Inspector Android — projekt kontextus

Natív Android alkalmazás MCP (Model Context Protocol) szerverek teszteléséhez.
Az `npx @modelcontextprotocol/inspector` mobil megfelelője, MVP szinten.

## Cél és scope

**MVP (ez a fázis):**
- Szerverprofilok mentése (név, URL, tetszőleges HTTP fejlécek)
- Kapcsolódás: `initialize` + capabilities megjelenítés
- `tools/list` — tool nevek, leírás, inputSchema
- Séma-vezérelt űrlap: paraméterek kitöltése kézzel
- `tools/call` + nyers JSON válasz megjelenítés
- Request/response napló, másolás/megosztás

**NEM része az MVP-nek (később):**
- OAuth 2.1 / PKCE flow
- `resources`, `prompts`, `sampling`, progress notifications
- Régi HTTP+SSE transport (csak Streamable HTTP kell)
- Beágyazott objektum/tömb paraméterek a formban

## Build környezet — FONTOS

A fordítás **CI-ban történik**, nincs lokális Android SDK vagy Android Studio.

Ebből következik:
- Soha ne feltételezz lokálisan futtatható `./gradlew` parancsot
- Minden Gradle, AGP és SDK verzió explicit, pinnelve — nincs interaktív SDK manager
- A CI workflow a projekt része, ne legyen külön kézi lépés
- Kis, gyakori commitok — a CI a fordítási visszajelzés
- `gradle-wrapper.jar` legyen commitolva, különben a CI nem indul

## Technológiai döntések

| Réteg | Választás | Indok |
|---|---|---|
| Nyelv | Kotlin | |
| UI | Jetpack Compose + Material 3 | |
| minSdk / targetSdk | 26 / 35 | |
| JDK | 17 (Temurin) | CI-ban is ez |
| HTTP | Ktor client (CIO engine) | SSE stream olvasásához |
| JSON | kotlinx.serialization | `JsonElement` dinamikus sémához |
| Tárolás | DataStore (Preferences) | néhány szerverprofil, nem kell Room |
| MCP protokoll | **kézzel implementálva** | lásd lent |

### Miért nem a hivatalos Kotlin SDK?

`io.modelcontextprotocol:kotlin-sdk` létezik, de:
- verziói gyorsan mozognak, a CI-ban pinnelni kell
- az MVP-hez ~200 sor kézi JSON-RPC elég
- itt pont a **nyers** protokollüzenetet kell megjeleníteni, amit az SDK elrejt

Ha később kell (OAuth, sampling), akkor váltunk.

## MCP Streamable HTTP — amit implementálni kell

Minden kérés: `POST <szerver URL>`

Fejlécek:
```
Content-Type: application/json
Accept: application/json, text/event-stream
Mcp-Session-Id: <session>            # initialize válaszából, utána mindig
MCP-Protocol-Version: 2025-06-18
<profilban megadott egyedi fejlécek>  # pl. Authorization, API kulcs
```

**Kapcsolódási sorrend:**
1. `initialize` → válaszból kimented a `Mcp-Session-Id` fejlécet és a `capabilities`-t
2. `notifications/initialized` (notification, nincs `id`, nincs válasz)
3. `tools/list` → `result.tools[]`, mindegyiken `name`, `description`, `inputSchema`
4. `tools/call` → `params: { name, arguments }`

**Válaszkezelés — ez a fő buktató:**
A szerver `Content-Type`-tól függően kétféleképp válaszol:
- `application/json` → egyszerű JSON body
- `text/event-stream` → SSE, `event: message` / `data: {...}` sorok, több eventben is jöhet

Mindkettőt kezelni kell. Az SSE-nél a `data:` sorok JSON-ját kell összegyűjteni,
és az `id` alapján párosítani a kéréshez.

**Hibák:** JSON-RPC `error` objektum (`code`, `message`, `data`) — ezt is nyersen
jelenítsd meg, ne nyeld el. HTTP 4xx/5xx esetén is mutasd a body-t.

## Autentikáció

A profil tartalmazzon **tetszőleges számú kulcs-érték fejlécpárt**, ne csak egy
token mezőt. Így Bearer token, egyedi API kulcs fejléc és cookie is működik
kódmódosítás nélkül. Kényelmi gomb: „Bearer token hozzáadása", ami
`Authorization: Bearer <érték>` párt szúr be.

A tokenek `EncryptedSharedPreferences`-be vagy legalább nem naplózott
DataStore mezőbe kerüljenek, és a naplóképernyőn maszkolva jelenjenek meg.

## Séma-vezérelt űrlap

Az `inputSchema` JSON Schema. Az MVP ennyit kezel:
- `type: string` → TextField
- `type: string` + `enum` → Dropdown
- `type: number` / `integer` → numerikus TextField
- `type: boolean` → Switch
- `required: []` → kötelező mezők jelölése, üresen nem küldhető
- `default` → előtöltés
- `description` → helper text a mező alatt

Minden más típusnál (`object`, `array`) legyen nyers JSON TextField fallback,
amit a felhasználó kézzel tölt ki.

## Képernyők

1. **Szerverlista** — profilok, + gomb, hosszú nyomás: szerkesztés/törlés
2. **Szerver részletei** — connect gomb, capabilities kártya, tool lista
3. **Tool képernyő** — leírás, generált űrlap, „Hívás" gomb, válaszpanel
4. **Napló** — időrendi request/response lista, egy elemre kattintva nyers JSON

A válaszpanelen legyen kapcsoló: „formázott" / „nyers", és másolás gomb.

## Projektstruktúra

```
app/src/main/java/com/example/mcpinspector/
├── data/
│   ├── ServerProfile.kt
│   └── ProfileStore.kt          # DataStore
├── log/
│   └── LogStore.kt              # in-memory kérés/válasz napló + fejléc-maszkolás
├── mcp/
│   ├── McpClient.kt             # kapcsolat, session, hívások
│   ├── JsonRpc.kt               # kérés/válasz modellek
│   ├── SseParser.kt             # text/event-stream feldolgozás
│   └── SchemaForm.kt            # inputSchema → mezőmodell
├── ui/
│   ├── AppViewModel.kt          # profilok, kapcsolat- és tool-hívás állapot
│   ├── ServerListScreen.kt      # lista + profil szerkesztő dialógus
│   ├── ServerDetailScreen.kt
│   ├── ToolScreen.kt
│   ├── LogScreen.kt
│   └── theme/
└── MainActivity.kt              # NavHost
```

## CI — .github/workflows/build.yml

```yaml
name: build
on: [push, workflow_dispatch, pull_request]
jobs:
  apk:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4
      - run: chmod +x gradlew
      - run: ./gradlew testDebugUnitTest --stacktrace
      - run: ./gradlew assembleDebug --stacktrace
      - uses: actions/upload-artifact@v4
        with:
          name: mcp-inspector-debug
          path: app/build/outputs/apk/debug/*.apk
```

## Teendők

- [x] Repo init, Gradle projekt váz, wrapper commitolva
- [x] CI workflow (első zöld build a CI-ban ellenőrizendő — lásd Haladásnapló)
- [x] `ServerProfile` + DataStore mentés/betöltés
- [x] `JsonRpc.kt` modellek
- [x] `SseParser.kt` + unit teszt mintaválaszokkal
- [x] `McpClient.initialize()` + session kezelés
- [x] `tools/list` + tool lista UI
- [x] `SchemaForm.kt` — séma → mezőlista
- [x] Tool képernyő, űrlap renderelés
- [x] `tools/call` + válaszpanel (formázott/nyers)
- [x] Naplóképernyő, másolás/megosztás
- [x] Ikon, app név, verziószám
- [ ] Valós MCP szerverrel végzett manuális teszt (SSE + JSON válasz is)
- [ ] `ProfileStore` esetleges migrálása `EncryptedSharedPreferences`-re, ha a
      sima DataStore-t nem tartjuk elégségesnek

## Tesztelés

Fejlesztés közbeni ellenőrzéshez bármelyik nyilvános vagy helyi
Streamable HTTP MCP szerver megfelel. Az `SseParser` és a `SchemaForm`
unit tesztelhető rögzített mintaválaszokkal, hálózat nélkül — ezekre
mindenképp írj tesztet, mert ez a két pont hibázik legtöbbet.

## Haladásnapló

<!-- Claude Code ide írja a session végén, mi készült el -->

### 2026-09-16 — MVP első implementációja

Az egész MVP első verzióját megírtam egy session alatt, a repo korábban
üres volt (csak README). Nincs helyi Android SDK, ezért a teljes
`./gradlew assembleDebug` build-et **nem lehetett lokálisan lefuttatni** —
ez a CI feladata lesz az első push után.

**Amit helyben leellenőriztem:** a tiszta Kotlin logikát (`JsonRpc.kt`,
`SseParser.kt`, `SchemaForm.kt`, `McpClient.kt`, `LogStore.kt`,
`ServerProfile.kt`) egy különálló, Android SDK-t nem igénylő JVM Gradle
projektben lefordítottam a valódi Ktor/kotlinx.serialization függőségekkel,
és mind a 20 unit teszt (8 SseParser + 12 SchemaForm) lefutott zöld
eredménnyel. A Jetpack Compose UI réteget (ToolScreen, ServerListScreen,
ServerDetailScreen, LogScreen, MainActivity, téma fájlok) **nem lehetett
így ellenőrizni**, mert a Compose Android artefaktumok AGP-t igényelnek —
ezeket csak a CI fogja ténylegesen lefordítani.

**Elkészült:**
- Gradle projektváz: version catalog (`gradle/libs.versions.toml`), root és
  app `build.gradle.kts`, AGP 8.6.0 / Kotlin 2.0.21 / Gradle 8.9 wrapper
  (a `gradle-wrapper.jar` commitolva van)
- CI workflow: JDK 17, unit tesztek + `assembleDebug`, APK artifact feltöltés
- Adaptive app ikon (egyszerű nagyító-motívum), app név "MCP Inspector",
  versionName 0.1.0
- `ServerProfile` + `ProfileStore`: DataStore Preferences alapú CRUD,
  tetszőleges fejléc-map támogatással
- `JsonRpc.kt`: request/notification/response/error modellek, `ToolDescriptor`,
  `InitializeResult`
- `SseParser.kt`: `text/event-stream` parser (több `data:` sor összefűzése,
  több event egy streamben, hiányzó záró üres sor kezelése) + 8 unit teszt
- `SchemaForm.kt`: `inputSchema` → `FormField` lista (string/enum/number/
  boolean/raw-json fallback) + `buildToolArguments` validáció + 12 unit teszt
- `McpClient.kt`: kézzel írt JSON-RPC over Streamable HTTP — `initialize`,
  `notifications/initialized`, `tools/list`, `tools/call`, session-id
  kezelés, JSON és SSE válaszág, minden HTTP kérés/válasz `McpExchange`-ként
  jelentve egy callbacken keresztül
- `LogStore.kt`: in-memory napló (nem perzisztens), fejléc-maszkolás
  (Authorization/Cookie) a naplóképernyőhöz
- UI: `AppViewModel` (profilok, kapcsolat állapot, tool-hívás állapot),
  `ServerListScreen` (lista + hosszú nyomásos szerkesztés/törlés + profil
  szerkesztő dialógus dinamikus fejléc-sorokkal és "Bearer token
  hozzáadása" gombbal), `ServerDetailScreen` (connect, capabilities kártya,
  tool lista), `ToolScreen` (séma-vezérelt űrlap, "Hívás" gomb, válaszpanel
  formázott/nyers kapcsolóval és másolással), `LogScreen` (kibontható napló
  bejegyzések, teljes exchange másolása), Material3 téma, `MainActivity`
  Navigation Compose-zal

**Ismert kockázatok / amit érdemes lesz megnézni az első CI futás után:**
- A `menuAnchor()` (no-arg) az enum dropdown mezőben deprecated a használt
  Compose BOM-ban (2024.09.03) — fordítási warningot ad, de nem hibát;
  ha zavaró, cseréld `menuAnchor(MenuAnchorType.PrimaryNotEditable)`-re.
- Valós MCP szerver ellen még nem lett tesztelve sem az SSE, sem a sima
  JSON válaszág — ezt manuálisan érdemes elvégezni az első build után.
