# MCP Inspector (Android)

Natív Android alkalmazás **MCP (Model Context Protocol) szerverek** teszteléséhez
és felfedezéséhez — az `npx @modelcontextprotocol/inspector` mobil megfelelője.

## Mi ez?

Az MCP egy nyílt protokoll, amivel AI-alkalmazások (pl. Claude) különféle
eszközöket ("tool"-okat) érhetnek el egy szerveren keresztül. Ez az app
lehetővé teszi, hogy bármilyen MCP szerverhez telefonról/tabletről
kapcsolódj, megnézd, milyen tool-okat kínál, és ezeket ki is próbáld —
mindezt anélkül, hogy kódot kellene írnod vagy terminált kellene nyitnod.

Kifejezetten fejlesztőknek és MCP szerver-üzemeltetőknek hasznos: gyors
ellenőrzés, hogy a szerver helyesen válaszol-e, milyen sémával várja a
paramétereket, és pontosan mi megy át a hálón — a nyers protokollüzenetek
szintjén.

## Funkciók

- **Szerverprofilok** mentése: név, URL, tetszőleges HTTP fejléc
  (Bearer token, API kulcs, cookie, stb. — kényelmi gomb a Bearer tokenhez)
- **Kapcsolódás** egy MCP Streamable HTTP szerverhez, a szerver
  képességeinek (`capabilities`) megjelenítése
- **Tool-lista** lekérése és böngészése (`tools/list`)
- **Séma-vezérelt űrlap**: a tool `inputSchema`-ja alapján automatikusan
  generált beviteli mezők (szöveg, legördülő enum, szám, kapcsoló, illetve
  nyers JSON mező összetettebb paraméterekhez)
- **Tool meghívása** (`tools/call`) és a válasz megtekintése formázott
  vagy nyers JSON nézetben, másolással
- **Napló**: minden kérés és válasz (fejlécekkel, body-val együtt)
  időrendben visszanézhető, érzékeny fejlékek (pl. `Authorization`)
  maszkolva jelennek meg

## Képernyők

1. **Szerverlista** — mentett profilok, hozzáadás/szerkesztés/törlés
2. **Szerver részletei** — kapcsolódás, capabilities, tool-lista
3. **Tool képernyő** — leírás, generált űrlap, hívás, válaszpanel
4. **Napló** — nyers kérés/válasz előzmények

## Technológia

| Réteg | Választás |
|---|---|
| Nyelv | Kotlin |
| UI | Jetpack Compose + Material 3 |
| HTTP | Ktor client (CIO engine) |
| JSON | kotlinx.serialization |
| Tárolás | Jetpack DataStore |
| MCP protokoll | kézzel implementált JSON-RPC 2.0 over Streamable HTTP |

Az MCP protokollt (JSON-RPC üzenetek, session kezelés, `text/event-stream`
válaszok feldolgozása) az app szándékosan nem egy kész SDK-val kezeli,
hanem közvetlenül — így a nyers protokollüzenetek is láthatók maradnak,
ami egy inspector-jellegű eszköznél kifejezetten a lényeg.

## Build

A projekt fordítása GitHub Actionsben történik (lásd
`.github/workflows/build.yml`): minden push-ra lefut a unit teszt suite,
majd elkészül a debug APK, amit workflow artifact-ként lehet letölteni.

Helyi fordításhoz Android SDK és a szokásos `./gradlew assembleDebug`
parancs szükséges.

## Használat röviden

1. Szerverlistán `+` gomb → add meg a szerver nevét, URL-jét és (ha kell)
   a hitelesítéshez szükséges fejléceket
2. Nyisd meg a profilt, majd nyomd meg a **Kapcsolódás** gombot
3. Válassz egy tool-t a listából
4. Töltsd ki a generált űrlapot, majd nyomd meg a **Hívás** gombot
5. Nézd meg a választ formázott vagy nyers nézetben, esetleg másold ki
6. A **Napló** képernyőn bármikor visszanézhető, mi ment ki és mi jött vissza

## Scope

Ez egy MVP: a fő MCP funkciókra (kapcsolódás, tool-lista, tool-hívás,
napló) fókuszál. Amit (egyelőre) nem tud: OAuth 2.1/PKCE bejelentkezés,
`resources`/`prompts`/`sampling`, illetve beágyazott objektum/tömb típusú
paraméterek kényelmes szerkesztése (ezekhez a nyers JSON mező használható).
