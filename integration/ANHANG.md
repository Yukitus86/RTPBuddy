# ANHANG — Nachschlagewerk

Für den einfachen Einbau nach [PROMPT.md](PROMPT.md) wird nichts hiervon
gebraucht. Das hier ist für den Fall, dass etwas nicht passt, dass nur ein Teil
übernommen werden soll, oder dass jemand wissen will, warum eine Änderung an
einer Stelle an einer ganz anderen wehtut.

Stand: RTPBuddy 1.6.24, 12.09.2026. Alle Zahlen sind gemessen.

---

## 1. Bausteine: Zeilen und Abhängigkeiten

`hängt ab von` zählt nur Abhängigkeiten innerhalb von `dev.rtpbuddy`, und zwar
einschließlich der Klassen im selben Paket — die brauchen keinen `import` und
werden bei einer Zählung über die import-Zeilen übersehen.

| Baustein | Zeilen | hängt ab von |
|---|---:|---|
| `RTPBuddy` | 25 | — |
| `RTPBuddyClient` | 309 | RTPBuddy, capture.AutoRtpController, capture.RtpCaptureController, command.RTPBuddyCommands, config.ConfigManager, config.RTPBuddyConfig, data.RtpSample, data.SampleStore, +15 weitere |
| `api.Landing` | 58 | — |
| `api.LandingListener` | 20 | api.Landing |
| `api.RTPBuddyApi` | 130 | api.Landing, api.LandingListener, api.Sitting |
| `api.RTPBuddyPlugin` | 42 | api.RTPBuddyApi |
| `api.Sitting` | 35 | — |
| `capture.AutoRtpController` | 619 | RTPBuddy, capture.FindRule, capture.RtpCaptureController, config.AutoRtpConfig, config.RTPBuddyConfig, config.RegionPreset, data.RtpSample, stats.CellCoverage, +2 weitere |
| `capture.FindRule` | 227 | config.AutoRtpConfig, data.RtpSample, stats.CellCoverage, util.Biomes, util.Lang, util.Numbers |
| `capture.RtpCaptureController` | 456 | RTPBuddy, config.RTPBuddyConfig, config.RegionPreset, data.RtpSample, data.SampleStore, session.SessionManager, util.Lang, util.Worlds |
| `command.RTPBuddyCommands` | 339 | RTPBuddyClient, capture.FindRule, data.RtpSample, stats.CellCoverage, stats.SampleStats, ui.AutoRtpDialog, ui.CellBoard, ui.HudLayoutScreen, +4 weitere |
| `config.AutoRtpConfig` | 147 | — |
| `config.CaptureConfig` | 54 | — |
| `config.ConfigManager` | 142 | RTPBuddy, config.RTPBuddyConfig, config.RegionPreset, util.FileOps |
| `config.GuardSettings` | 87 | util.Worlds |
| `config.MapConfig` | 373 | — |
| `config.RTPBuddyConfig` | 160 | config.AutoRtpConfig, config.CaptureConfig, config.GuardSettings, config.MapConfig, config.RegionPreset, data.RtpSample, region.ServerRegions, ui.MapPalette |
| `config.RegionPreset` | 77 | — |
| `data.CsvExporter` | 74 | data.RtpSample, util.Numbers |
| `data.RtpSample` | 103 | data.SessionRecord |
| `data.SampleStore` | 414 | RTPBuddy, data.CsvExporter, data.RtpSample, data.SessionRecord, util.FileOps |
| `data.SchemaMigrator` | 210 | RTPBuddy, data.RtpSample, data.SampleStore, data.SessionRecord, util.FileOps |
| `data.SessionRecord` | 46 | — |
| `hud.CaptureHud` | 291 | RTPBuddyClient, capture.FindRule, capture.RtpCaptureController, config.MapConfig, data.RtpSample, hud.HudAnchor, region.ServerRegions, ui.SampleCard, +3 weitere |
| `hud.HudAnchor` | 109 | util.Lang |
| `hud.Minimap` | 587 | RTPBuddyClient, config.GuardSettings, config.MapConfig, data.RtpSample, hud.HudAnchor, hud.MinimapPlate, region.ServerRegions, ui.ArgbTexture, +8 weitere |
| `hud.MinimapPlate` | 655 | data.RtpSample, region.ServerRegions, ui.GapScheme, ui.MapPalette, ui.MapViewState |
| `input.RTPBuddyKeys` | 41 | — |
| `integration.RTPBuddyApiImpl` | 267 | RTPBuddy, RTPBuddyClient, api.Landing, api.LandingListener, api.RTPBuddyApi, api.RTPBuddyPlugin, api.Sitting, data.RtpSample, +3 weitere |
| `mixin.MinecraftClientMixin` | 36 | ui.ScreenKeeper |
| `region.ServerRegions` | 211 | — |
| `session.SessionManager` | 144 | RTPBuddy, data.SampleStore, data.SessionRecord |
| `stats.CellCoverage` | 291 | data.RtpSample, region.ServerRegions |
| `stats.DensityGrid` | 91 | data.RtpSample |
| `stats.GapField` | 602 | data.RtpSample |
| `stats.Histogram` | 117 | util.Numbers |
| `stats.SampleStats` | 430 | config.GuardSettings, data.RtpSample, stats.CellCoverage, stats.DensityGrid, stats.Histogram, util.Biomes, util.Numbers |
| `stats.StatsEngine` | 36 | config.GuardSettings, data.RtpSample, stats.SampleStats |
| `ui.ArgbTexture` | 104 | — |
| `ui.AutoRtpDialog` | 150 | RTPBuddyClient, capture.FindRule, config.AutoRtpConfig, config.RegionPreset, ui.MapPalette, ui.PanicKey, ui.UiDraw, util.Lang |
| `ui.CellBoard` | 140 | RTPBuddyClient, region.ServerRegions, stats.CellCoverage, ui.ArgbTexture, ui.CellPlate, ui.MapPalette, ui.UiDraw, util.Lang, +1 weitere |
| `ui.CellPlate` | 115 | region.ServerRegions, stats.CellCoverage |
| `ui.ColorMode` | 38 | util.Lang |
| `ui.ConfirmDialog` | 96 | ui.MapPalette, ui.RtpButton, ui.Theme, ui.UiDraw, util.Lang |
| `ui.DropdownMenu` | 237 | ui.Theme, ui.UiDraw |
| `ui.FrameLimiter` | 49 | — |
| `ui.GapScheme` | 121 | — |
| `ui.HudLayoutScreen` | 496 | RTPBuddyClient, config.MapConfig, hud.CaptureHud, hud.HudAnchor, hud.Minimap, ui.MapPalette, ui.RtpButton, ui.Theme, +2 weitere |
| `ui.MapCanvas` | 2051 | config.GuardSettings, config.MapConfig, data.RtpSample, region.ServerRegions, stats.GapField, ui.ArgbTexture, ui.ColorMode, ui.GapScheme, +11 weitere |
| `ui.MapPalette` | 142 | — |
| `ui.MapScreen` | 2035 | RTPBuddyClient, config.GuardSettings, config.MapConfig, config.RTPBuddyConfig, data.RtpSample, data.SessionRecord, stats.SampleStats, stats.StatsEngine, +23 weitere |
| `ui.MapViewState` | 191 | data.RtpSample |
| `ui.MarkerMode` | 42 | util.Lang |
| `ui.PanicKey` | 31 | RTPBuddyClient |
| `ui.PlotPlate` | 260 | data.RtpSample |
| `ui.RtpButton` | 270 | ui.Theme, ui.UiDraw |
| `ui.SampleCard` | 274 | RTPBuddyClient, data.RtpSample, region.ServerRegions, ui.MapPalette, ui.Theme, ui.UiDraw, util.Lang, util.Numbers, +1 weitere |
| `ui.SampleFilter` | 207 | RTPBuddyClient, data.RtpSample, util.Biomes, util.Lang, util.Numbers, util.Worlds |
| `ui.ScreenGrid` | 103 | — |
| `ui.ScreenKeeper` | 140 | RTPBuddyClient, ui.MapScreen |
| `ui.SessionListPanel` | 687 | RTPBuddyClient, data.RtpSample, data.SessionRecord, stats.SampleStats, ui.MapPalette, ui.SampleFilter, ui.Theme, ui.UiDraw, +3 weitere |
| `ui.SettingsScreen` | 1203 | RTPBuddyClient, capture.FindRule, config.AutoRtpConfig, config.CaptureConfig, config.GuardSettings, config.MapConfig, config.RegionPreset, data.RtpSample, +18 weitere |
| `ui.StatsPanel` | 405 | RTPBuddyClient, region.ServerRegions, stats.CellCoverage, stats.GapField, stats.Histogram, stats.SampleStats, ui.CellBoard, ui.MapPalette, +5 weitere |
| `ui.TextPromptDialog` | 135 | ui.MapPalette, ui.PanicKey, ui.RtpButton, ui.Theme, ui.UiDraw, util.Lang |
| `ui.Theme` | 193 | ui.UiDraw |
| `ui.UiDraw` | 181 | stats.Histogram, ui.MapPalette, ui.Theme |
| `util.BiomeCatalog` | 79 | util.Biomes |
| `util.Biomes` | 213 | util.Lang |
| `util.FileOps` | 62 | RTPBuddy |
| `util.Lang` | 48 | — |
| `util.Numbers` | 57 | — |
| `util.Worlds` | 92 | util.Lang |

**Gesamt: 72 Klassen, 18702 Zeilen.**

### Auf Paketebene

| Paket | Dateien | Zeilen | hängt ab von |
|---|---:|---:|---|
| `(Wurzel)` | 2 | 334 | capture, command, config, data, hud, input, integration, region, session, stats, ui, util |
| `api` | 5 | 285 | — |
| `capture` | 3 | 1302 | (Wurzel), config, data, session, stats, util |
| `command` | 1 | 339 | (Wurzel), capture, data, stats, ui, util |
| `config` | 7 | 1040 | (Wurzel), data, region, ui, util |
| `data` | 5 | 847 | (Wurzel), util |
| `hud` | 4 | 1642 | (Wurzel), capture, config, data, region, ui, util |
| `input` | 1 | 41 | — |
| `integration` | 1 | 267 | (Wurzel), api, data, region, util |
| `mixin` | 1 | 36 | ui |
| `region` | 1 | 211 | — |
| `session` | 1 | 144 | (Wurzel), data |
| `stats` | 6 | 1567 | config, data, region, util |
| `ui` | 28 | 10096 | (Wurzel), capture, config, data, hud, region, stats, util |
| `util` | 6 | 551 | (Wurzel) |

Die drei Pakete ohne ausgehende Kante — `api`, `input`, `region` — sind die
einzigen, die sich einzeln herauslösen lassen.

---

## 2. Was der Einstiegspunkt beim Start anmeldet

`dev/rtpbuddy/RTPBuddyClient.java`, Methode `onInitializeClient()` und die von
ihr gerufene `registerEvents()`. Zeile für Zeile, in Ausführungsreihenfolge:

| Zeile | Was |
|---:|---|
| 41 | Datenverzeichnis `config/rtpbuddy/` bestimmt |
| 42 | Verzeichnis des Vorgängers `config/rtpmapper/` bestimmt (nur gelesen) |
| 43 | Ausfuhrverzeichnis `.minecraft/rtpbuddy/exports/` bestimmt |
| 46 | beide Verzeichnisse angelegt |
| 52 | `ConfigManager` gebaut |
| 53 | `config.json` gelesen, sonst mit dem Preset `donutsmp` angelegt |
| 57 | Regionsschlüssel der Statistik gesetzt (statischer Haken) |
| 61 | Zellenauflöser der Abdeckung gesetzt (statischer Haken) |
| 63 | `SampleStore` gebaut |
| 64 | CSV-Spiegel nach Konfiguration an oder aus |
| 65 | `rtp_samples.json` gelesen |
| 67 | `SessionManager` gebaut |
| 68 | `RtpCaptureController` gebaut |
| 69 | `AutoRtpController` gebaut |
| 70 | Rückruf für jede aufgezeichnete Landung gesetzt |
| 73 | einmaliger Import der Daten des Vorgängers |
| 77 | Schnittstelle veröffentlicht, fremde Mods unter `rtpbuddy` angemeldet |
| 79 | die 5 Tastenbelegungen angemeldet |
| 80 | `/rtpbuddy` angemeldet (clientseitig) |
| 81 | die vier Ereignisse unten angemeldet |
| 83 | HUD-Element `rtpbuddy:capture_hud` |
| 84 | HUD-Element `rtpbuddy:minimap` |
| 87 | Abschalthaken: Sitzung beenden, Speicher schließen |
| 99 | Ereignis: der Spieler hat einen Befehl abgeschickt |
| 101 | Ereignis: Welt betreten |
| 108 | Ereignis: Verbindung weg |
| 119 | Ereignis: Client-Tick |

Alles davon hängt an dieser einen Klasse. Wer sie ersetzt, muss jede Zeile
dieser Tabelle irgendwo unterbringen.

---

## 3. Statische Kopplung

Die Mod reicht ihre Bausteine nicht durch Konstruktoren, sondern über statische
Zugänge. Das ist der Grund, warum `RTPBuddyClient` nicht einfach ersetzt werden
kann, und die Zahl der Aufrufstellen ist der Preis dafür.

| Zugang | Aufrufe | in Dateien |
|---|---:|---:|
| `RTPBuddyClient.config()` | 55 | 14 |
| `RTPBuddyClient.store()` | 50 | 7 |
| `RTPBuddyClient.sessions()` | 17 | 5 |
| `RTPBuddyClient.configManager()` | 21 | 4 |
| `RTPBuddyClient.autoRtp()` | 19 | 5 |
| `RTPBuddyClient.capture()` | 8 | 4 |
| `RTPBuddyClient.panicStop(` | 3 | 2 |
| `RTPBuddyClient.anythingRunning(` | 2 | 2 |
| `Lang.t(` | 414 | 24 |
| `Lang.tOrNull(` | 1 | 1 |
| `Theme.` | 182 | 12 |
| `ServerRegions.` | 79 | 13 |
| `Numbers.` | 87 | 13 |
| `Worlds.` | 42 | 13 |
| `RTPBuddy.LOGGER` | 43 | 9 |

---

## 4. Vollständige Umbenennungsliste

Nötig ist nichts davon (siehe PROMPT.md, *Der entscheidende Befund*). Wer
trotzdem umbenennt, braucht diese Liste vollständig — und die letzte Spalte
sagt, was passiert, wenn eine Zeile vergessen wird.

| Was | Wo | Wirkung, wenn vergessen |
|---|---|---|
| `package dev.rtpbuddy…` | 72 Java-Dateien, jeweils Zeile 1 | übersetzt nicht mehr — Paket und Ordner müssen zusammenpassen |
| `import dev.rtpbuddy…` | in allen Dateien, die fremde eigene Klassen nutzen | übersetzt nicht mehr |
| `"package"` in `rtpbuddy.mixins.json` | `rtpbuddy.mixins.json`, Zeile 4 | Mixin wird nicht gefunden, Karte blinkt bei jedem Teleport weg |
| Dateiname `rtpbuddy.mixins.json` | `fabric.mod.json` des Zielprojekts | Mixin wird gar nicht geladen |
| `MOD_ID = "rtpbuddy"` | `RTPBuddy.java`, Zeile 12 | `RTPBuddyApi.modVersion()` liefert `"unknown"`; HUD-Bezeichner behalten den alten Namensraum |
| `MOD_NAME = "RTPBuddy"` | `RTPBuddy.java`, Zeile 13 | nur der Name im Protokoll bleibt alt |
| `CONFIG_DIR = "rtpbuddy"` | `RTPBuddy.java`, Zeile 16 | Daten bleiben unter `config/rtpbuddy/` — meist gewollt, sonst ist der alte Bestand weg |
| `LEGACY_CONFIG_DIR = "rtpmapper"` | `RTPBuddy.java`, Zeile 19 | der einmalige Import des Vorgängers findet nichts mehr |
| `Identifier.of("rtpbuddy", "main")` | `input/RTPBuddyKeys.java`, Zeile 13 | Tastenkategorie bleibt im alten Namensraum — folgenlos, solange er frei ist |
| `Identifier.of("rtpbuddy", name)` | `ui/ArgbTexture.java`, Zeile 40 | Texturnamen bleiben alt — folgenlos, solange sie frei sind |
| `PREFIX = "rtpbuddy."` | `util/Lang.java`, Zeile 19 | jeder der 695 Schlüssel wird als roher Schlüsseltext gezeichnet |
| Schlüssel `rtpbuddy.*` | `assets/rtpbuddy/lang/de_de.json` und `en_us.json`, 695 je Datei | wie oben — Präfix und Schlüssel müssen zusammen umgezogen werden |
| Schlüssel `key.rtpbuddy.*` | beide Sprachdateien, 6 je Datei | die Tasten heißen in den Steuerungseinstellungen wie ihr Schlüssel |
| Ordner `assets/rtpbuddy/` | Ressourcenbaum | Sprachdateien werden nicht gefunden, alle Texte erscheinen als Schlüssel |
| `"/rtpbuddy/presets/"` | `config/ConfigManager.java`, Zeile 128 | das Preset `donutsmp` wird nicht gefunden, die Regionsliste bleibt leer |
| Befehl `literal("rtpbuddy")` | `command/RTPBuddyCommands.java`, Zeile 38 | der Befehl heißt weiter `/rtpbuddy` |

---

## 5. Vollständige Liste der Fremdimporte

Zusammen **101 verschiedene** Importe. Keiner davon verlangt eine Zeile in
`build.gradle`, die über die Fabric API hinausgeht.

**Brigadier (kommt mit Minecraft)** — 1 verschiedene

| Import | in Dateien |
|---|---:|
| `com.mojang.brigadier.arguments.StringArgumentType` | 1 |

**Fabric API** — 9 verschiedene

| Import | in Dateien |
|---|---:|
| `net.fabricmc.fabric.api.client.command.v2.ClientCommandManager` | 1 |
| `net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback` | 1 |
| `net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource` | 1 |
| `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents` | 1 |
| `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper` | 1 |
| `net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents` | 1 |
| `net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents` | 1 |
| `net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement` | 2 |
| `net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry` | 1 |

**Fabric Loader** — 3 verschiedene

| Import | in Dateien |
|---|---:|
| `net.fabricmc.api.ClientModInitializer` | 1 |
| `net.fabricmc.loader.api.FabricLoader` | 2 |
| `net.fabricmc.loader.api.entrypoint.EntrypointContainer` | 1 |

**Gson (kommt mit Minecraft)** — 8 verschiedene

| Import | in Dateien |
|---|---:|
| `com.google.gson.Gson` | 2 |
| `com.google.gson.GsonBuilder` | 2 |
| `com.google.gson.JsonArray` | 1 |
| `com.google.gson.JsonElement` | 1 |
| `com.google.gson.JsonObject` | 1 |
| `com.google.gson.JsonParser` | 1 |
| `com.google.gson.JsonSyntaxException` | 3 |
| `com.google.gson.reflect.TypeToken` | 1 |

**JDK** — 42 verschiedene

| Import | in Dateien |
|---|---:|
| `java.io.IOException` | 4 |
| `java.io.InputStream` | 1 |
| `java.io.InputStreamReader` | 1 |
| `java.lang.reflect.Type` | 1 |
| `java.math.BigDecimal` | 1 |
| `java.nio.charset.StandardCharsets` | 2 |
| `java.nio.file.Files` | 2 |
| `java.nio.file.Path` | 9 |
| `java.nio.file.StandardCopyOption` | 1 |
| `java.time.Instant` | 4 |
| `java.time.LocalDateTime` | 1 |
| `java.time.ZoneId` | 4 |
| `java.time.format.DateTimeFormatter` | 5 |
| `java.util.ArrayList` | 17 |
| `java.util.Arrays` | 5 |
| `java.util.Collection` | 1 |
| `java.util.Comparator` | 4 |
| `java.util.EnumMap` | 1 |
| `java.util.HashMap` | 6 |
| `java.util.HashSet` | 1 |
| `java.util.LinkedHashMap` | 2 |
| `java.util.LinkedHashSet` | 1 |
| `java.util.List` | 32 |
| `java.util.Locale` | 7 |
| `java.util.Map` | 9 |
| `java.util.Random` | 1 |
| `java.util.Set` | 2 |
| `java.util.TreeMap` | 1 |
| `java.util.UUID` | 1 |
| `java.util.concurrent.CopyOnWriteArrayList` | 1 |
| `java.util.concurrent.Executors` | 1 |
| `java.util.concurrent.ScheduledExecutorService` | 1 |
| `java.util.concurrent.ScheduledFuture` | 1 |
| `java.util.concurrent.TimeUnit` | 1 |
| `java.util.concurrent.atomic.AtomicBoolean` | 1 |
| `java.util.function.Consumer` | 4 |
| `java.util.function.Function` | 3 |
| `java.util.function.Supplier` | 5 |
| `java.util.function.ToIntFunction` | 1 |
| `java.util.regex.Matcher` | 1 |
| `java.util.regex.Pattern` | 2 |
| `java.util.regex.PatternSyntaxException` | 2 |

**JOML (kommt mit Minecraft)** — 1 verschiedene

| Import | in Dateien |
|---|---:|
| `org.joml.Matrix3x2fStack` | 3 |

**LWJGL (kommt mit Minecraft)** — 1 verschiedene

| Import | in Dateien |
|---|---:|
| `org.lwjgl.glfw.GLFW` | 6 |

**Minecraft** — 30 verschiedene

| Import | in Dateien |
|---|---:|
| `net.minecraft.client.MinecraftClient` | 17 |
| `net.minecraft.client.font.TextRenderer` | 7 |
| `net.minecraft.client.gl.RenderPipelines` | 1 |
| `net.minecraft.client.gui.Click` | 4 |
| `net.minecraft.client.gui.DrawContext` | 18 |
| `net.minecraft.client.gui.screen.MessageScreen` | 1 |
| `net.minecraft.client.gui.screen.ProgressScreen` | 1 |
| `net.minecraft.client.gui.screen.ReconfiguringScreen` | 1 |
| `net.minecraft.client.gui.screen.Screen` | 9 |
| `net.minecraft.client.gui.screen.narration.NarrationMessageBuilder` | 1 |
| `net.minecraft.client.gui.tooltip.Tooltip` | 1 |
| `net.minecraft.client.gui.widget.ButtonWidget` | 1 |
| `net.minecraft.client.gui.widget.ClickableWidget` | 2 |
| `net.minecraft.client.gui.widget.TextFieldWidget` | 3 |
| `net.minecraft.client.input.KeyInput` | 4 |
| `net.minecraft.client.network.ClientPlayerEntity` | 2 |
| `net.minecraft.client.network.ServerInfo` | 1 |
| `net.minecraft.client.option.KeyBinding` | 1 |
| `net.minecraft.client.render.RenderTickCounter` | 2 |
| `net.minecraft.client.resource.language.I18n` | 1 |
| `net.minecraft.client.texture.NativeImage` | 1 |
| `net.minecraft.client.texture.NativeImageBackedTexture` | 1 |
| `net.minecraft.client.util.InputUtil` | 2 |
| `net.minecraft.client.world.ClientWorld` | 2 |
| `net.minecraft.registry.Registry` | 1 |
| `net.minecraft.registry.RegistryKeys` | 1 |
| `net.minecraft.text.Text` | 10 |
| `net.minecraft.util.Identifier` | 3 |
| `net.minecraft.util.math.BlockPos` | 2 |
| `net.minecraft.world.Heightmap` | 1 |

**Mixin (kommt mit dem Loader)** — 4 verschiedene

| Import | in Dateien |
|---|---:|
| `org.spongepowered.asm.mixin.Mixin` | 1 |
| `org.spongepowered.asm.mixin.injection.At` | 1 |
| `org.spongepowered.asm.mixin.injection.Inject` | 1 |
| `org.spongepowered.asm.mixin.injection.callback.CallbackInfo` | 1 |

**SLF4J (kommt mit dem Loader)** — 2 verschiedene

| Import | in Dateien |
|---|---:|
| `org.slf4j.Logger` | 1 |
| `org.slf4j.LoggerFactory` | 1 |

---

## 6. Sinnvolle Schnitte

Transitive Hülle ab den genannten Klassen, gemessen. Jeder Schnitt übersetzt
für sich, wenn man ihm einen eigenen Einstiegspunkt gibt.

| Schnitt | Klassen | Zeilen |
|---|---:|---:|
| Aufzeichnung ohne Oberfläche | 21 | 3124 |
| Aufzeichnung + Auto-RTP | 25 | 4474 |
| nur Statistik | 14 | 2424 |
| irgendein Bildschirm oder das HUD | 71 | 18666 |
| alles | 72 | 18702 |

### Aufzeichnung ohne Oberfläche — 21 Klassen, 3124 Zeilen

Landungen erkennen, speichern, JSON und CSV, Sitzungen, Konfiguration, Regionsraster. Keine Zeile Oberfläche außer `ui.MapPalette`, an dem `config.RTPBuddyConfig` für die Farbwahl hängt.

```
RTPBuddy
capture.RtpCaptureController
config.AutoRtpConfig
config.CaptureConfig
config.ConfigManager
config.GuardSettings
config.MapConfig
config.RTPBuddyConfig
config.RegionPreset
data.CsvExporter
data.RtpSample
data.SampleStore
data.SchemaMigrator
data.SessionRecord
region.ServerRegions
session.SessionManager
ui.MapPalette
util.FileOps
util.Lang
util.Numbers
util.Worlds
```

### Aufzeichnung + Auto-RTP — 25 Klassen, 4474 Zeilen

dazu die Schleife, die Suchregeln und die Zellenabdeckung, die die Suchregeln brauchen.

```
RTPBuddy
capture.AutoRtpController
capture.FindRule
capture.RtpCaptureController
config.AutoRtpConfig
config.CaptureConfig
config.ConfigManager
config.GuardSettings
config.MapConfig
config.RTPBuddyConfig
config.RegionPreset
data.CsvExporter
data.RtpSample
data.SampleStore
data.SchemaMigrator
data.SessionRecord
region.ServerRegions
session.SessionManager
stats.CellCoverage
ui.MapPalette
util.Biomes
util.FileOps
util.Lang
util.Numbers
util.Worlds
```

### nur Statistik — 14 Klassen, 2424 Zeilen

Kennzahlen, Histogramme, Zellenabdeckung, Lückenfeld. Rechnet über eine übergebene Liste von `RtpSample` und braucht weder Speicher noch Aufnahme.

```
config.GuardSettings
data.RtpSample
data.SessionRecord
region.ServerRegions
stats.CellCoverage
stats.DensityGrid
stats.GapField
stats.Histogram
stats.SampleStats
stats.StatsEngine
util.Biomes
util.Lang
util.Numbers
util.Worlds
```

### irgendein Bildschirm oder das HUD — 71 Klassen, 18666 Zeilen

zieht praktisch alles. Siehe unten.

### Warum die Oberfläche nicht teilbar ist

Ein Kreis über drei Pakete hält sie zusammen:

* `ui.SettingsScreen` und `ui.HudLayoutScreen` stellen HUD-Elemente ein und
  hängen deshalb an `hud.CaptureHud`, `hud.HudAnchor` und `hud.Minimap`.
* `hud.Minimap` zeichnet mit `ui`-Werkzeug (`ArgbTexture`, `GapScheme`,
  `Theme`) und hängt deshalb an `ui`.
* `config.RTPBuddyConfig` hängt an `ui.MapPalette`.

Damit zieht jeder Einstieg in `ui` oder `hud` **71 der 72 Klassen** nach sich.
Die einzige Ausnahme ist `mixin.MinecraftClientMixin`, die von keinem Code
gerufen wird — sie hängt an `rtpbuddy.mixins.json`.

