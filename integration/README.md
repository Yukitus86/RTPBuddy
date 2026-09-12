# Integration — RTPBuddy mit einer zweiten Mod verbinden

Alles, was gebraucht wird, um eine **zweite client-seitige Fabric-Mod** auf
derselben Minecraft- und Fabric-Version an RTPBuddy anzuschließen — und um mit
dieser zweiten Mod bei null anzufangen.

Die beiden Mods bleiben getrennte Projekte mit getrennter Versionsnummer und
getrennter Git-Historie. Sie teilen sich nur eine schmale Schnittstelle. Es geht
hier ausschließlich um Mod ↔ Mod im laufenden Client; der Desktop-Viewer hat
damit nichts zu tun und kommt hier nicht vor.

---

## Was hier liegt

| | |
|---|---|
| `README.md` | dieses Dokument — der Vertrag und die Begründung dazu |
| `new-mod.py` | erzeugt aus `template/` ein neues Mod-Projekt in einem eigenen Ordner |
| `install-rtpbuddy-jar.py` | kopiert RTPBuddys gebautes JAR in `libs/` eines Partner-Projekts |
| `template/` | ein vollständiges, lauffähiges Gerüst für die zweite Mod |

Die Schnittstelle selbst liegt **nicht** hier, sondern in RTPBuddys eigenem
Quelltext unter `src/main/java/dev/rtpbuddy/api/` und damit im ausgelieferten
JAR. Das ist Absicht: zur Laufzeit darf es die Klassen genau einmal geben.

---

## Wie die Verbindung funktioniert

Die Partner-Mod implementiert `dev.rtpbuddy.api.RTPBuddyPlugin` und trägt die
Klasse in ihrer `fabric.mod.json` unter dem Einstiegspunkt `rtpbuddy` ein:

```json
"entrypoints": {
  "client":   [ "dev.beispiel.BeispielClient" ],
  "rtpbuddy": [ "dev.beispiel.RTPBuddyLink" ]
}
```

RTPBuddy fragt den Loader beim Start nach allen `rtpbuddy`-Einstiegspunkten und
ruft jeden einmal auf:

```java
public class RTPBuddyLink implements RTPBuddyPlugin {
    @Override
    public void onRTPBuddyReady(RTPBuddyApi api) {
        api.addLandingListener(landing ->
                System.out.println(landing.number() + " @ " + landing.x()));
    }
}
```

### Warum ein Einstiegspunkt und keine Abfrage

Der Loader lädt eine Einstiegspunkt-Klasse erst dann, wenn jemand nach diesem
Einstiegspunkt fragt — und nach `rtpbuddy` fragt nur RTPBuddy. Ist RTPBuddy
nicht installiert, wird die Klasse nie geladen, also werden die
`dev.rtpbuddy.api`-Typen darin nie nachgeschlagen, also gibt es keinen
`NoClassDefFoundError`, gegen den man sich absichern müsste.

Daraus folgt die einzige Regel, die man beim Bauen der zweiten Mod im Kopf haben
muss:

> **Jede Erwähnung von `dev.rtpbuddy.api` bleibt in den Klassen, die vom
> Einstiegspunkt aus erreicht werden.**

Ein Feld vom Typ `Landing` im eigenen `ClientModInitializer` hebt die ganze
Konstruktion wieder auf. Im Gerüst steht dafür `LandingLog` zwischen den beiden
Hälften: eine schlichte Klasse ohne eine einzige RTPBuddy-Zeile, in die der Link
hineinschreibt und aus der der Rest der Mod liest.

Deshalb braucht die zweite Mod auch kein `depends` auf RTPBuddy, keine
`isModLoaded`-Abfrage und keine Reflection. `suggests` genügt.

---

## Was die Schnittstelle anbietet

`RTPBuddyApi` — alle Aufrufe laufen im Client-Thread, alle Listen sind
Momentaufnahmen.

| | |
|---|---|
| `apiVersion()` / `modVersion()` | was installiert ist |
| `dataDirectory()` | `.minecraft/config/rtpbuddy` |
| `landings()` | alle aufgezeichneten Landungen, älteste zuerst |
| `landingsOf(id)` / `landingsOfCurrentSitting()` | eine Sitzung davon |
| `lastLanding()` / `landingCount()` | ohne die Liste zu bauen |
| `sittings()` / `currentSitting()` | die Sitzungen |
| `addLandingListener` / `removeLandingListener` | Landungen, sobald sie aufgezeichnet sind |
| `autoRtpRunning()` | läuft die Auto-RTP-Schleife |
| `stopEverything(grund)` | Not-Aus, mit einer Zeile, die der Spieler liest |
| `cellNumberAt(x, z)` | Nummer der Server-Zelle, 0 wo kein Raster liegt |

`Landing` und `Sitting` sind eigene Records und **nicht** RTPBuddys interne
`RtpSample`/`SessionRecord`. Die internen Typen sind das Speicherformat und
ändern sich, sobald etwas Neues aufgezeichnet wird; die Partner-Mod soll deshalb
nicht dagegen übersetzt sein.

### Was es bewusst nicht gibt

Keinen Weg, einen Befehl zu senden, den Spieler zu bewegen oder die
Auto-RTP-Schleife zu **starten**. Die Schleife ist aus, bis der Spieler sie von
Hand startet — das ist ihr ganzer Sinn, und eine zweite Mod, die sie starten
könnte, würde dieses Versprechen auf eine Art brechen, die man RTPBuddys eigenen
Bildschirmen nicht ansieht. **Stoppen** geht, denn Stoppen ist immer sicher.

### Versionen

`RTPBuddyApi.API_VERSION` steigt nur, wenn sich hier etwas so ändert, dass eine
dagegen übersetzte Mod nicht mehr läuft. Eine Methode oder ein Record-Feld
dazuzubekommen ist das nicht. Die Versionsnummer der Mod läuft getrennt davon
und sagt darüber nichts aus.

Die Konstante ist in die Partner-Mod einkompiliert, `api.apiVersion()` ist das,
was tatsächlich installiert ist — die beiden zu vergleichen ist die ganze
Prüfung, die es braucht.

---

## Die zweite Mod anlegen

```bash
python integration/new-mod.py --to ../MeineMod --id meinemod
```

Optional `--name "Meine Mod"` und `--package dev.meinemod`. Das Skript kopiert
das Gerüst in den Zielordner, benennt Paket, Assets-Ordner, Klassen und alle
Vorkommen um, legt den Gradle-Wrapper dazu und kopiert RTPBuddys JAR nach
`libs/`, falls es gebaut ist.

Danach:

```bash
cd ../MeineMod
git init
gradlew build
```

Das Gerüst wird **nicht** an Ort und Stelle gebaut. `template/` ist Quelltext in
RTPBuddys Repository; das zweite Projekt gehört in einen eigenen Ordner mit
eigener Historie — genau darum geht es.

### Was im Gerüst schon drin ist

* `<Prefix>Client` — der eigene Einstiegspunkt, ohne eine Zeile RTPBuddy. Er
  registriert `/meinemod`, das den Zustand ausgibt.
* `LandingLog` — die Wand. Keine RTPBuddy-Typen, nur Zustand.
* `RTPBuddyLink` — der Einstiegspunkt `rtpbuddy`. Meldet sich an, prüft die
  API-Version, hängt einen Listener ein.

Ohne RTPBuddy sagt `/meinemod`, dass RTPBuddy nicht installiert ist. Mit
RTPBuddy zeigt derselbe Befehl Version, gezählte Landungen und die letzte.
Damit sind beide Fälle einmal durchgespielt.

---

## Zusammen bauen und testen

Beide Projekte brauchen dieselben vier Zeilen in `gradle.properties`:

```properties
minecraft_version=1.21.11
yarn_mappings=1.21.11+build.6
loader_version=0.19.3
fabric_version=0.141.6+1.21.11
```

Ein Unterschied hier fällt beim Bauen **nicht** auf, sondern erst, wenn das
Spiel nicht startet.

RTPBuddys JAR liegt im Partner-Projekt unter `libs/` und ist dort

* `modCompileOnly` — dagegen übersetzen, aber niemals mitliefern. Eine zweite
  Kopie der API-Klassen im eigenen JAR ist der klassische Weg zu einem Fehler,
  der sagt, eine Klasse sei keine Instanz ihrer selbst.
* `modLocalRuntime` — im eigenen Dev-Client mitladen, damit `gradlew runClient`
  die Verbindung wirklich durchspielt. Diese eine Zeile auskommentieren zeigt,
  was die Mod ohne RTPBuddy tut.

Nach jedem neuen RTPBuddy-Build:

```bash
python integration/install-rtpbuddy-jar.py ../MeineMod
```

Das Skript liest `rtpbuddy_version` aus dem Zielprojekt, kopiert genau dieses
JAR und wirft alte Versionen aus `libs/` — die übersetzen sonst sauber und laden
zur Laufzeit die falsche Schnittstelle.

Im Spiel prüfen: beide JARs in `mods/`, Log auf

```
[RTPBuddy] connected mod 'meinemod'
[RTPBuddy] api v1 open to 1 mod(s)
```

Eine Partner-Mod, die beim Anmelden oder in einem Listener eine Exception wirft,
wird mit ihrem Mod-Namen geloggt und übersprungen. Sie kann RTPBuddy nicht
mitreißen und keine Aufzeichnung verhindern.

---

**English** — everything needed to connect a second client-side Fabric mod to
RTPBuddy on the same Minecraft and Fabric version, and to start that mod from
scratch. The partner mod implements `dev.rtpbuddy.api.RTPBuddyPlugin` and
declares it under the `rtpbuddy` entrypoint; the loader only loads that class
when RTPBuddy asks for the entrypoint, so the partner mod needs no dependency on
RTPBuddy and runs fine without it — as long as every mention of
`dev.rtpbuddy.api` stays in the classes reached from that entrypoint. The api
hands out landings, sittings and live state, and can stop the auto-RTP loop but
never start it. `python integration/new-mod.py --to <folder> --id <modid>`
copies `template/` into a project of its own, renames it and brings the gradle
wrapper and RTPBuddy's jar along.
