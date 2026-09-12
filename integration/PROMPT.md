# Auftrag: RTPBuddy in dieses Projekt übernehmen

Dieser Text steht für sich allein. Er setzt kein Wissen über RTPBuddy und keinen
Zugriff auf dessen Repository voraus. Alles, was gebraucht wird, liegt im Ordner
neben dieser Datei.

---

## Was die Mod ist

RTPBuddy ist eine **reine Client-Mod für Fabric**. Sie zeichnet auf, wo der
Server einen Spieler nach einem `/rtp`-Befehl absetzt, und macht aus den
Aufzeichnungen eine Karte: Vollbildkarte mit Statistikspalte, Minikarte im HUD,
Zellenraster des Servers, Filter, CSV-Ausfuhr. Dazu eine Auto-RTP-Schleife, die
den eingestellten Befehl im eingestellten Takt wiederholt und aus bleibt, bis
der Spieler sie von Hand startet.

Das Aufzeichnen ist passiv: die Mod liest die Befehle mit, die der Spieler
selbst tippt, und die eigene Client-Position. Sie verschickt keine Pakete, die
der Spieler nicht selbst ausgelöst hat.

| | |
|---|---|
| Java-Dateien | **72** |
| Java-Zeilen | **18 702** |
| Pakete | **15**, alle unter `dev.rtpbuddy` |
| Ressourcen-Dateien | **6** (davon 1 binär: `icon.png`), zusammen 1 524 Textzeilen |
| Größte Dateien | `ui/MapCanvas.java` 2 051, `ui/MapScreen.java` 2 035, `ui/SettingsScreen.java` 1 203 |
| Sprachschlüssel | **701** je Sprache, Deutsch und Englisch, identische Schlüsselmengen |

Verteilung über die Pakete:

| Paket | Dateien | Zeilen |
|---|---:|---:|
| `dev.rtpbuddy` (Wurzel) | 2 | 334 |
| `dev.rtpbuddy.api` | 5 | 285 |
| `dev.rtpbuddy.capture` | 3 | 1 302 |
| `dev.rtpbuddy.command` | 1 | 339 |
| `dev.rtpbuddy.config` | 7 | 1 040 |
| `dev.rtpbuddy.data` | 5 | 847 |
| `dev.rtpbuddy.hud` | 4 | 1 642 |
| `dev.rtpbuddy.input` | 1 | 41 |
| `dev.rtpbuddy.integration` | 1 | 267 |
| `dev.rtpbuddy.mixin` | 1 | 36 |
| `dev.rtpbuddy.region` | 1 | 211 |
| `dev.rtpbuddy.session` | 1 | 144 |
| `dev.rtpbuddy.stats` | 6 | 1 567 |
| `dev.rtpbuddy.ui` | 28 | 10 096 |
| `dev.rtpbuddy.util` | 6 | 551 |

---

## Ausgangslage

Der Quelltext ist gegen genau diese Fassungen übersetzt und gelaufen:

| | |
|---|---|
| Minecraft | **1.21.11** |
| Yarn-Mappings | **1.21.11+build.6** |
| Fabric Loader | **0.19.3** |
| Fabric API | **0.141.6+1.21.11** |
| Java | **21** (`options.release = 21`) |

**Weicht das Zielprojekt bei Minecraft oder den Mappings ab, ist das kein
Kopieren mehr, sondern eine Portierung.** Die Mod fasst 30 verschiedene
Minecraft-Klassen an, darunter `DrawContext` in 18 Dateien und
`MinecraftClient` in 17 — an einer anderen Minecraft-Fassung bricht das an
vielen Stellen gleichzeitig. In dem Fall zuerst die Fassungen angleichen, dann
diesen Auftrag noch einmal lesen.

Was mitkommen muss, und was das an Hürden bedeutet:

| | |
|---|---|
| **Mixin** | **Ja, einer.** `dev/rtpbuddy/mixin/MinecraftClientMixin.java`, 36 Zeilen, greift `MinecraftClient.setScreen` am Kopf ab und kann den Aufruf abbrechen. Dazu die Konfigurationsdatei `rtpbuddy.mixins.json`, die in der `fabric.mod.json` des Zielprojekts angemeldet werden muss (Schritt 2). |
| **Access Widener** | Nein, keiner. |
| **Fremdbibliotheken** | Keine. Alles, was importiert wird, kommt bereits mit Minecraft, dem Loader oder der Fabric API — Gson, SLF4J, JOML, LWJGL/GLFW, Brigadier, Mixin. Keine Zeile in `build.gradle` außer der Fabric API selbst. |
| **Serverteil** | Nein. Reiner Client. `environment` ist `"client"`, und zwei Drittel des Codes fassen Klassen aus `net.minecraft.client` an. Ist das Zielprojekt auf `"*"` gesetzt, darf der Einstiegspunkt trotzdem nur als `client`-Einstiegspunkt eingetragen werden, sonst stürzt ein dedizierter Server beim Laden ab. |

---

## Der entscheidende Befund: umbenannt werden muss nichts

Der Java-Paketname `dev.rtpbuddy` und die Namensraum-Zeichenkette `"rtpbuddy"`
können unverändert in ein Projekt mit beliebiger Mod-ID übernommen werden. Vier
Gründe, jeder einzeln nachprüfbar:

1. **Paketname ≠ Mod-ID.** Der Loader liest den Einstiegspunkt als
   vollqualifizierten Klassennamen. `dev.rtpbuddy.RTPBuddyClient` lädt in einem
   Projekt, das `meinemod` heißt, genauso.
2. **Sprachdateien.** Minecraft lädt `lang/<code>.json` aus *jedem* Namensraum,
   den ein Ressourcenpaket mitbringt, nicht nur aus dem der eigenen Mod-ID.
   `assets/rtpbuddy/lang/` bleibt also, wo es ist, und alle 701 Schlüssel lösen
   auf.
3. **Mixin-Konfiguration.** `rtpbuddy.mixins.json` zeigt mit
   `"package": "dev.rtpbuddy.mixin"` auf den *Paketnamen*. Bleibt der Paketname,
   bleibt die Datei unverändert.
4. **Identifier.** `Identifier.of("rtpbuddy", …)` steht an 4 Stellen. Der
   Namensraum eines Identifiers muss die Mod-ID nicht sein — er muss nur gültig
   und nicht doppelt vergeben sein.

**Die eine Ausnahme, und sie ist eine Zeile.** `RTPBuddy.MOD_ID`
(`dev/rtpbuddy/RTPBuddy.java`, Zeile 12, Wert `"rtpbuddy"`) wird an genau einer
Stelle gegen den Loader gehalten:

```
dev/rtpbuddy/integration/RTPBuddyApiImpl.java:146
    return FabricLoader.getInstance().getModContainer(RTPBuddy.MOD_ID)
```

Stimmt die Konstante nicht mit der Mod-ID des Zielprojekts überein, liefert
`RTPBuddyApi.modVersion()` die Zeichenkette `"unknown"`. Mehr passiert nicht.
Wer die Schnittstelle für Fremdmods nicht nutzt, kann die Konstante lassen.

Wer sie doch anpasst, muss wissen: `MOD_ID` ist auch der Namensraum der beiden
HUD-Bezeichner (`RTPBuddyClient.java`, Zeilen 83 und 84). Aus
`rtpbuddy:capture_hud` und `rtpbuddy:minimap` wird dann
`<neue-id>:capture_hud` und `<neue-id>:minimap`. Das ist folgenlos, solange die
neuen Namen frei sind. Der Ordner unter `config/` hängt **nicht** daran, der
steht getrennt in `CONFIG_DIR` (Zeile 16).

---

## Schritt 1 — Quelltext kopieren

Wörtlich, ohne Umschreiben. Die Ordnerstruktur unter `quelltext/1.6.24/`
entspricht `src/main/` eins zu eins.

| Quelle (relativ zu diesem Ordner) | Ziel im Projekt |
|---|---|
| `quelltext/1.6.24/java/dev/rtpbuddy/` | `src/main/java/dev/rtpbuddy/` |
| `quelltext/1.6.24/resources/assets/rtpbuddy/` | `src/main/resources/assets/rtpbuddy/` |
| `quelltext/1.6.24/resources/rtpbuddy/presets/` | `src/main/resources/rtpbuddy/presets/` |
| `quelltext/1.6.24/resources/rtpbuddy.mixins.json` | `src/main/resources/rtpbuddy.mixins.json` |
| `quelltext/1.6.24/resources/fabric.mod.json` | **nicht kopieren** |

Die letzte Zeile ist wichtig: das ist RTPBuddys *eigene* Metadatendatei. Sie
würde die des Zielprojekts überschreiben. Dieselbe Datei liegt zum Nachschlagen
noch einmal als `vorlagen/fabric.mod.json.1.6.24`.

`assets/rtpbuddy/icon.png` wird nur von RTPBuddys eigener `fabric.mod.json`
gebraucht und kann weggelassen werden. Schadet aber nicht.

---

## Schritt 2 — Einstiegspunkt und Mixin in `fabric.mod.json` anhängen

In die vorhandene `fabric.mod.json` des Zielprojekts **anhängen**, nichts
ersetzen:

```json
{
  "entrypoints": {
    "client": [
      "dev.meinemod.MeinModClient",
      "dev.rtpbuddy.RTPBuddyClient"
    ]
  },
  "mixins": [
    {
      "config": "meinemod.mixins.json",
      "environment": "client"
    },
    {
      "config": "rtpbuddy.mixins.json",
      "environment": "client"
    }
  ]
}
```

Die ersten Einträge in beiden Listen stehen für das, was schon da ist. Gibt es
im Zielprojekt noch keinen `mixins`-Block, kommt er neu dazu.

RTPBuddy bringt außerdem einen **eigenen** Einstiegspunkt-Schlüssel `rtpbuddy`
mit, unter dem sich fremde Mods anmelden können (siehe `dev/rtpbuddy/api/`).
Für den Einbau ist er ohne Bedeutung; er muss nirgends eingetragen werden.

---

## Schritt 3 — Abhängigkeiten prüfen

Gebraucht wird **Fabric API**. Wenn das Zielprojekt bereits
`modImplementation "net.fabricmc.fabric-api:fabric-api:…"` in voller Breite
einbindet, ist an `build.gradle` nichts zu tun. Wer einzelne Module einbindet,
braucht diese sechs:

| Fabric-API-Modul | Importierte Klassen | Wofür |
|---|---|---|
| `fabric-command-api-v2` | `ClientCommandManager`, `ClientCommandRegistrationCallback`, `FabricClientCommandSource` | `/rtpbuddy` mit 26 weiteren Literalen (`command/RTPBuddyCommands.java`, Zeilen 38–103) |
| `fabric-lifecycle-events-v1` | `ClientTickEvents` | der Tick, auf dem Aufnahme und Auto-RTP laufen |
| `fabric-key-binding-api-v1` | `KeyBindingHelper` | die 5 Tasten |
| `fabric-message-api-v1` | `ClientSendMessageEvents` | Mitlesen der selbst getippten Befehle — die Aufnahme hängt daran |
| `fabric-networking-api-v1` | `ClientPlayConnectionEvents` | Welt betreten, Verbindung weg |
| `fabric-rendering-v1` | `HudElementRegistry`, `HudElement` | HUD und Minikarte |

Java-Zielfassung im `build.gradle` muss **21** sein. Der Quelltext nutzt
Records, Schaltermuster (`switch` mit Pfeilen und Typmustern) und Textblöcke.

Nichts sonst. Vollständige Liste aller 101 verschiedenen Fremdimporte in
[ANHANG.md](ANHANG.md).

---

## Schritt 4 — die eigenständige Jar entfernen

**Aus `mods/` muss jede eigenständige `rtpbuddy-*.jar` verschwinden.** Bleibt
sie liegen, passiert Folgendes:

* Der Loader lädt `dev.rtpbuddy.RTPBuddyClient` zweimal — einmal aus der Jar des
  Zielprojekts, einmal aus der eigenständigen. Es laufen zwei Aufnahmen, zwei
  HUD-Elemente, zwei Minikarten.
* Beide schreiben in dasselbe Verzeichnis `config/rtpbuddy/`. Der letzte Schreiber
  gewinnt, und zwischendurch überschreiben sie einander die Aufzeichnungen.
* Die Mixin-Konfiguration `rtpbuddy.mixins.json` liegt doppelt im Klassenpfad.

Die aufgezeichneten Daten in `config/rtpbuddy/` bleiben beim Entfernen der Jar
liegen und werden von der eingebauten Kopie weiterverwendet — das ist gewollt
und der Grund, warum `CONFIG_DIR` nicht mit umbenannt werden sollte.

---

## Schritt 5 — übersetzen und prüfen

```bash
gradlew build
```

Erwartet: grün, ohne Warnung über einen nicht angewandten Mixin.

Dann `gradlew runClient` und im Protokoll nachsehen:

```
[RTPBuddy] no config found, seeding with 'donutsmp' preset (9 regions)
[RTPBuddy] no sample file yet, starting empty
[RTPBuddy] ready: 0 samples, 0 sessions
```

Diese drei Zeilen beim ersten Start beweisen, dass Einstiegspunkt, Ressourcen
und Konfiguration zusammenpassen. Kommt statt der ersten Zeile nichts, wurde
`rtpbuddy/presets/donutsmp.json` nicht mitkopiert.

---

## Konflikte, die vorher zu prüfen sind

Alles hier kollidiert nur, wenn das Zielprojekt denselben Namen schon belegt.
Nachsehen kostet jeweils eine Minute, ein Zusammenstoß kostet einen Abend.

**Tastenbelegung** — `input/RTPBuddyKeys.java`:

| Taste | Schlüssel | Zeile |
|---|---|---|
| `M` | `key.rtpbuddy.session_map` | 25 |
| `K` | `key.rtpbuddy.manual_capture` | 26 |
| `H` | `key.rtpbuddy.toggle_auto_rtp` | 31 |
| `O` | `key.rtpbuddy.settings` | 32 |
| `Ende` | `key.rtpbuddy.panic` | 34 |

Kategorie: `Identifier.of("rtpbuddy", "main")`, Zeile 13. Alle fünf sind in den
Vanilla-Steuerungseinstellungen umbelegbar; eine Doppelbelegung ist unschön,
aber nicht tödlich. `O` und `M` sind die wahrscheinlichsten Zusammenstöße.

**HUD- und Textur-Bezeichner** — vier Stellen insgesamt:

| Bezeichner | Datei, Zeile |
|---|---|
| `rtpbuddy:capture_hud` | `RTPBuddyClient.java` 83 |
| `rtpbuddy:minimap` | `RTPBuddyClient.java` 84 |
| `rtpbuddy:main` (Tastenkategorie) | `input/RTPBuddyKeys.java` 13 |
| `rtpbuddy:<name>` (Texturen zur Laufzeit) | `ui/ArgbTexture.java` 40 |

**Konfigurationsdateiname** — `RTPBuddy.java` Zeilen 16 und 19:

```
.minecraft/config/rtpbuddy/config.json
.minecraft/config/rtpbuddy/rtp_samples.json
.minecraft/config/rtpbuddy/rtp_samples.csv
.minecraft/rtpbuddy/exports/rtp_data_<zeitstempel>.csv
```

Dazu einmalig gelesen, nie geschrieben: `.minecraft/config/rtpmapper/rtp_samples.json`
(Vorgängermod, Zeile 19).

**Sprachschlüssel** — 701 je Sprache:

* **695** beginnen mit `rtpbuddy.` Das Präfix setzt `util/Lang.java` Zeile 19
  automatisch davor; im Aufruf steht es nicht (`Lang.t("word.on")` findet
  `rtpbuddy.word.on`).
* **6** beginnen mit `key.` — fünf `key.rtpbuddy.*` und
  `key.category.rtpbuddy.main`.

**Befehl** — `/rtpbuddy` (`command/RTPBuddyCommands.java` Zeile 38). Rein
clientseitig registriert, es geht nie ein Paket an den Server.

---

## Abnahme

- [ ] `sha256sum -c PRUEFSUMMEN.txt` in diesem Ordner: 79-mal `OK`
- [ ] 72 `.java`-Dateien liegen unter `src/main/java/dev/rtpbuddy/`
- [ ] `assets/rtpbuddy/lang/de_de.json` und `en_us.json` sind da, je 701 Schlüssel
- [ ] `rtpbuddy/presets/donutsmp.json` ist da
- [ ] `rtpbuddy.mixins.json` liegt in `src/main/resources/`
- [ ] RTPBuddys eigene `fabric.mod.json` wurde **nicht** über die des Projekts kopiert
- [ ] `dev.rtpbuddy.RTPBuddyClient` steht im `client`-Einstiegspunkt
- [ ] `rtpbuddy.mixins.json` steht im `mixins`-Block, `environment` auf `client`
- [ ] `gradlew build` läuft durch, keine Mixin-Warnung
- [ ] keine eigenständige `rtpbuddy-*.jar` mehr in `mods/`
- [ ] `runClient`: die drei Startzeilen aus Schritt 5 stehen im Protokoll
- [ ] `M` öffnet die Karte, `O` die Einstellungen
- [ ] `/rtpbuddy` wird vervollständigt
- [ ] Karte einmal öffnen, schließen, wieder öffnen — keine Ausnahme im Protokoll

---

## Wenn nur Teile übernommen werden sollen

Gemessen, nicht geraten: das ist die transitive Hülle ab den genannten Klassen.

| Schnitt | Klassen | Zeilen | Was man bekommt |
|---|---:|---:|---|
| Aufzeichnung ohne Oberfläche | **21** | **3 124** | Landungen erkennen, speichern, JSON + CSV, Sitzungen, Konfiguration |
| Aufzeichnung + Auto-RTP | **25** | **4 474** | dazu die Schleife und die Suchregeln |
| nur Statistik | **14** | **2 424** | Kennzahlen, Histogramme, Zellenabdeckung, Lückenfeld |
| alles | **72** | **18 702** | |

Die drei Schnitte sind echte Schnitte — sie übersetzen für sich. Der erste
enthält unter anderem `data/SampleStore.java`, `capture/RtpCaptureController.java`,
`config/ConfigManager.java`, `session/SessionManager.java`,
`region/ServerRegions.java` und die sechs Klassen aus `util/`. Die vollständigen
Klassenlisten stehen in [ANHANG.md](ANHANG.md).

**Was sich nicht schneiden lässt: die Oberfläche.** Jeder Einstieg in `ui` oder
`hud` zieht 71 der 72 Klassen nach sich. Grund ist keine Schlamperei, sondern
ein Kreis über drei Pakete: `ui` → `hud` → `ui` (`SettingsScreen` und
`HudLayoutScreen` stellen HUD-Elemente ein, die Minikarte zeichnet mit
`ui`-Werkzeug), dazu `config.RTPBuddyConfig` → `ui.MapPalette`. Wer die Karte
will, nimmt alles. Halbe Oberfläche gibt es nicht, und der Versuch kostet mehr
Zeit als der ganze Einbau.

Die einzige Klasse, die niemand aufruft, ist `mixin/MinecraftClientMixin.java` —
die hängt an `rtpbuddy.mixins.json`, nicht am Code.

---

## Regeln für Änderungen am übernommenen Code

* **`en_us.json` bleibt vollständig.** Das ist der Rückfall. Ein Schlüssel, den
  nur `de_de.json` hat, erscheint im englischen Spiel als der Schlüssel selbst —
  `rtpbuddy.settings.map.grid.tip` mitten auf dem Bildschirm. Beide Dateien
  halten dieselbe Schlüsselmenge, heute je 701.
* **Präfix nicht doppeln.** `Lang.t` hängt `rtpbuddy.` selbst davor. In der
  JSON-Datei steht das Präfix, im Aufruf nicht.
* **Große Flächen bleiben Texturen.** Siehe Fallen, Punkt 1.
* **Den Mixin nicht wegoptimieren.** Siehe Fallen, Punkt 2.
* **`SampleStore.samplesOf(...)` nie pro Bild aufrufen.** Die Methode läuft über
  alle Aufzeichnungen. Wer das in eine Zeichenroutine setzt, merkt es erst auf
  einem vollen Datenbestand.
* **Statische Zugänge bleiben, wo sie sind.** `RTPBuddyClient.config()` wird an
  55 Stellen in 14 Dateien gerufen, `RTPBuddyClient.store()` an 50 Stellen in 7.
  Das gegen Konstruktorübergabe zu tauschen ist eine Woche Arbeit und ändert
  nichts am Verhalten.

---

## Fallen, die schon Zeit gekostet haben

1. **`DrawContext.fill` ist auf 1.21.11 teuer.** Jeder Aufruf legt ein
   Render-State-Objekt und eine Kopie der Matrix an. Deshalb gehen große Flächen
   durch `ui/ArgbTexture.java` statt durch tausend `fill`-Aufrufe pro Bild.
   Nutzer sind `hud/Minimap.java`, `ui/CellBoard.java` und `ui/MapCanvas.java`.
   Wer das auf `fill` zurückbaut, verliert Bilder pro Sekunde und findet die
   Ursache nicht, weil jeder einzelne Aufruf harmlos aussieht.

2. **Die Karte wird offen *gehalten*, nicht neu geöffnet.** Ein Teleport über
   Weltgrenzen lässt den Client seinen eigenen Ladebildschirm setzen und wieder
   wegnehmen; was offen war, ist dann weg. Der Mixin lehnt genau diesen Tausch
   ab — nur die flüchtigen Ladebildschirme, nur solange die Karte oben ist. Der
   erste Versuch, die Karte danach wieder zu öffnen, war schlechter: sie blinkte
   trotzdem weg und kam als neuer Bildschirm zurück, der seinen Dimensionsfilter
   vergessen hatte. Die ausführliche Begründung steht im Kopf von
   `mixin/MinecraftClientMixin.java`, die genaue Regel in `ui/ScreenKeeper.java`.

3. **Auf QWERTZ meldet GLFW die Z-Taste als `GLFW_KEY_Y`.** Strg+Z tat deshalb
   schlicht nichts. `ui/MapScreen.java` Zeile 1904 fängt darum beide Codes ab.
   Wer dort aufräumt, baut den Fehler wieder ein.

4. **Zwei Installationen teilen sich das Datenverzeichnis.** Siehe Schritt 4.
   Der Fehler sieht aus wie Datenverlust und ist keiner: zwei Schreiber auf
   derselben Datei.

5. **Der Loader lädt eine Einstiegspunkt-Klasse erst, wenn jemand nach dem
   Einstiegspunkt fragt.** Darauf beruht `dev/rtpbuddy/api/` — fremde Mods können
   sich unter dem Schlüssel `rtpbuddy` anmelden, ohne von RTPBuddy abzuhängen.
   Wer diese Mechanik anfasst, muss wissen, dass eine Erwähnung von
   `dev.rtpbuddy.api` außerhalb der vom Einstiegspunkt erreichten Klassen die
   ganze Konstruktion aufhebt.
