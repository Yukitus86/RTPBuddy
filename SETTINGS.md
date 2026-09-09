# RTPBuddy — Einstellungen im Detail

Jede Einstellung, was sie wirklich tut und wann du sie anfassen solltest.
Menü öffnen mit `O`, dem Knopf **Optionen** auf der Karte oder
`/rtpbuddy config`. Alles landet in `.minecraft/config/rtpbuddy/config.json`.

Die Kurzfassung steht inzwischen auch im Spiel: Maus auf einen Schalter, und der
Tooltip erklärt ihn. Diese Seite ist die lange Fassung.

Der Befehl `/rtp` selbst gehört nicht zu RTPBuddy: den liefert der Server. Diese
Mod schaut nur zu, welche Befehle du tippst, und wo du danach landest.

---

## Tab: Aufnahme

Steuert, wie RTPBuddy einen Teleport erkennt und aufzeichnet.

### Aufnahme — `capture.enabled` (Standard: an)

Hauptschalter für die automatische Aufzeichnung. Aus heißt: getippte Befehle
werden nicht mehr beobachtet, es entstehen keine neuen Samples. Vorhandene Daten
bleiben unangetastet.

Die manuelle Aufnahme mit `K` läuft trotzdem weiter — sie geht nicht durch diese
Prüfung.

### Aufnahme bei Befehlen — `capture.autoCapture` (Standard: an)

Ob ein passender Befehl eine Aufnahme scharfstellt. Aus heißt: du zeichnest nur
noch von Hand mit `K` auf. Sinnvoll, wenn ein Server Befehle benutzt, deren
Muster ständig danebengreifen.

### Bei Dimensionswechsel — `capture.captureOnDimensionChange` (Standard: an)

Zählt ein Dimensionswechsel während der Wartephase als geglückter Teleport.

**Das ist der Schalter für `/rtp nether` und `/rtp end`.** Beim Wechsel in eine
andere Dimension kann der horizontale Sprung winzig sein — ohne diesen Schalter
greift nur die Distanzschwelle, und solche RTPs landen im Zähler „verpasst".

### Kurze Sprünge verwerfen — `capture.rejectShortJumps` (Standard: an)

Prüft nach der Landung: liegt der horizontale Sprung unter der Teleport-Schwelle
**und** ist die Dimension dieselbe geblieben, wird das Sample verworfen (Zähler
„verworfen").

Schützt davor, dass ein abgelehnter RTP — Server sagt nein, du stehst weiter da —
als Messpunkt in die Statistik rutscht.

### CSV-Spiegel schreiben — `capture.writeCsvMirror` (Standard: an)

Hält `rtp_samples.csv` parallel zur JSON aktuell. Die JSON ist maßgeblich, die
CSV ist reiner Komfort für Excel und Co. Aus spart Schreibvorgänge.

### Teleport-Schwelle — `capture.teleportDistanceThreshold` (Standard: 200 Blöcke)

Ab welcher horizontalen Distanz ein Positionssprung als Teleport gilt.
Auswahl: 50 · 100 · 200 · 500 · 1000 · 2000.

- **Zu niedrig:** Elytra-Flug oder ein Netherportal lösen Fehlmessungen aus.
- **Zu hoch:** nahe RTPs werden nicht als Teleport erkannt.

Dieselbe Zahl entscheidet auch, was „Kurze Sprünge verwerfen" als zu kurz ansieht.

### Wartezeit-Limit — `capture.armTimeoutTicks` (Standard: 15 s / 300 Ticks)

Wie lange nach dem Befehl auf den Teleport gewartet wird. Läuft die Zeit ab, geht
die Aufnahme still aus und der Zähler „verpasst" steigt um eins.
Auswahl: 5 · 10 · 15 · 30 · 60 s.

**Höher setzen, wenn der Server einen Countdown hat** („Teleport in 3… 2… 1…").
15 s reichen für einen sofortigen RTP, nicht für eine 30-Sekunden-Aufwärmphase.

### Beruhigungszeit — `capture.settleTicks` (Standard: 10 Ticks)

Nach dem erkannten Sprung muss die Position so viele Ticks ruhig bleiben, bevor
das Sample genommen wird. Ruhig heißt: unter 0,05 Blöcke Drift pro Tick — jede
echte Bewegung setzt den Zähler auf null zurück.
Auswahl: 0 · 5 · 10 · 20 · 40 Ticks (20 Ticks = 1 Sekunde).

Verhindert, dass eine Nachkorrektur des Servers als endgültige Position
festgehalten wird. Auf 0 nur, wenn du sofort loslaufen willst und die letzte
Nachkommastelle egal ist.

### Speicher-Verzögerung — `capture.saveDebounceMillis` (Standard: 2000 ms)

Sammelt Schreibvorgänge: eine Serie RTPs wird zu einem einzigen Dateischreiben
zusammengefasst. Auswahl: 0 · 1000 · 2000 · 5000 · 10000 ms.

0 schreibt sofort — mehr Plattenzugriffe, dafür kein Verlustfenster. Bei einem
Absturz gehen höchstens die Samples aus diesem Fenster verloren; die Datei selbst
kann nie halb geschrieben zurückbleiben (temporäre Datei plus Umbenennen).

### Unbekanntes /rtp aufnehmen — `capture.captureUnknownRtp` (Standard: an)

Nimmt auch ein `/rtp` auf, dessen Ziel in der Regionsliste fehlt. Das Argument
wird unverändert als angeforderte Region gespeichert (`/rtp mars` → Region
`mars`), nicht auf die nächstbeste konfigurierte Region gebogen. Ohne diesen
Schalter verschwindet so eine Landung wortlos — sie wird schlicht nicht
aufgezeichnet.

Was als RTP-Befehl gilt, steht in `capture.rtpCommandPattern`
(Standard `^(rtp|wild|wilderness)(?: +(.*))?$`). Andere Befehle wie `/tpa`
lösen nichts aus.

### DonutSMP-Preset neu anwenden

Kein Schalter, sondern eine Aktion: setzt die Regionsliste auf das mitgelieferte
Preset zurück — `overworld`, `nether`, `end` sowie die sechs Serverzonen
`asia`, `east`, `west`, `eu central`, `eu west` und `oceania` mit ihren Mustern
und Befehlen.

Nimm das, wenn du die Muster in der `config.json` kaputt editiert hast. Die Zeile
**Regionen:** unten im Tab zeigt, was geladen ist, und markiert fehlerhafte
Muster mit `(Muster fehlerhaft)`.

---

## Tab: Auto-RTP

Die Schleife, die `/rtp` für dich wiederholt. Aus nach jedem Start; der laufende
Zustand wird nie auf die Platte geschrieben.

### Auto-RTP starten… / stoppen

Öffnet den Startdialog. Der nennt die komplette Reihenfolge, die Wartezeit samt
Streuung und jede Bedingung, die den Lauf beendet — gesendet wird nichts, bevor
du auf **Auto-RTP starten** drückst. Grau bleibt der Knopf nur dann, wenn keine
gewählte Region einen Befehl hat, denn dann würde die Schleife nichts senden.

### Auto-RTP anhalten / fortsetzen

Erscheint nur, solange ein Lauf existiert. Anhalten beendet ihn **nicht**: es
wird nichts gesendet, die Wartezeit und die Laufzeit bleiben stehen, und Schaden
oder Schutzzonen stoppen währenddessen nichts. Fortsetzen macht mit der
Wartezeit weiter, die beim Anhalten übrig war — samt Zähler und Position in der
Regionen-Reihenfolge.

Dafür gedacht, kurz stehen zu bleiben und sich anzusehen, wo der letzte Teleport
gelandet ist, ohne den Lauf zu verlieren. Taste: `H` — dieselbe, die den Lauf
gestartet hat. Im Chat geht es mit `/rtpbuddy auto pause` und
`/rtpbuddy auto resume`, auf der Karte zeigt der Auto-Knopf dann *Auto hält*
statt eines eingefrorenen Countdowns.

### Regionen — `autoRtp.regions` (Standard: leer)

Ein Schalter je konfigurierter Region, beschriftet mit dem Befehl, der
tatsächlich gesendet wird (`Asia  /rtp asia`). Angehakt heißt: diese Region
gehört zum Lauf.

Sind mehrere an, nimmt jeder Teleport die nächste — `/rtp asia`, Wartezeit,
`/rtp eu central`, Wartezeit, `/rtp east`, dann von vorn. Auf einem Server, der
bei einem nackten `/rtp` immer dieselbe Zone zurückgibt, ist das der einzige Weg,
den Rest der Karte zu füllen. Unter den Schaltern steht als Notiz die komplette
Reihenfolge, und der Startdialog nennt sie ebenfalls, bevor irgendetwas gesendet
wird.

Ist nichts angehakt, gilt weiterhin `autoRtp.region` (Standard `overworld`) —
eine Config aus einer älteren Version verhält sich also unverändert.

### Reihenfolge — `autoRtp.order` (Standard: Reihum)

Nur sichtbar, wenn mehr als eine Region an ist. **Reihum** geht die Liste der
Reihe nach durch, sodass jede Zone gleich viele Samples bekommt; **Zufall**
würfelt jedes Mal neu.

### Wartezeit — `autoRtp.cooldownSeconds` (Standard: 60 s)

Abstand zwischen zwei Teleports, in Sekunden **frei eintragbar** — keine feste
Auswahl mehr, weil keine Leiter aus Stufen das Intervall trifft, das ein
bestimmter Server tatsächlich will. Hart nach unten auf **2 s** begrenzt.

**Gemessen wird zwischen Landungen, nicht zwischen Absendungen.** Ein langsamer
Teleport verschiebt also den nächsten Befehl, statt Befehle aufzustauen.

Verkürzt niemals eine Server-Wartezeit: lehnt der Server ab, wartet der nächste
Versuch schlicht dasselbe Intervall erneut ab.

### Streuung — `autoRtp.jitterSeconds` (Standard: +0–5 s)

Zufälliger Aufschlag von 0 bis n Sekunden auf jede Wartezeit, damit der Abstand
nicht maschinell exakt ist. Ebenfalls frei eintragbar; 0 schaltet sie ab.

### Manuell auslösen — `autoRtp.manualStep` (Standard: aus)

Schaltet die Uhr ab. Der Lauf bleibt ein Lauf — Regionen-Reihenfolge, Zähler,
Obergrenzen, Laufzeit-Limit und jede Stopp-Bedingung verhalten sich exakt wie im
getakteten Betrieb — aber gesendet wird erst auf die **Auto-RTP-Taste**, ein
Teleport je Druck. Für die andere Hälfte der Arbeit: eine Karte füllen und sich
ansehen wo man gelandet ist wollen verschiedene Rhythmen, und den zweiten
bestimmst du, statt eine Rate im Voraus raten zu müssen.

Wartezeit und Streuung gelten dann nicht mehr, bis auf eine harte Untergrenze
von 2 Sekunden zwischen zwei Befehlen — eine gedrückt gehaltene Taste darf kein
Befehlsfeuer werden. Einen Countdown gibt es nicht, also wird auch keiner
angezeigt: das HUD schreibt `MANUELL`, der Kartenknopf *Auto manuell*.

Ein Druck, der nicht ausgeführt werden kann, **lehnt ab statt zu merken**, und
sagt warum: die letzte Landung wird noch aufgezeichnet, die Untergrenze ist noch
nicht um, der Lauf ist angehalten. Ein still nachgereichter Teleport wäre
schlimmer als gar keiner — zu wissen wann der Befehl rausging ist der ganze Sinn
des manuellen Takts. Ein Druck, der geklappt hat, sagt nichts: der Befehl steht
schon im Chat, der Server antwortet darauf, und das HUD zählt ihn.
`/rtpbuddy auto step` macht dasselbe aus dem Chat.

### Stopp nach — `autoRtp.stopAfterMinutes` (Standard: 30 Min)

Laufzeitgrenze. Auswahl: kein Limit · 10 · 30 · 60 · 120 Min.

### Max pro Lauf — `autoRtp.maxPerSession` (Standard: kein Limit)

Obergrenze an Teleports pro Lauf. Auswahl: kein Limit · 25 · 50 · 100 · 250 · 500.

### Auf die Landung warten — `autoRtp.waitForCapture` (Standard: an)

Solange eine Aufnahme noch läuft (wartend oder beim Beruhigen), wird die
Wartezeit neu gestartet, statt den nächsten Befehl abzuschicken.

An lassen, solange du aufzeichnest — sonst überholt die Schleife die eigene
Messung.

### Stopp bei Schaden — `autoRtp.stopOnDamage` (Standard: an)

Hält sofort an, sobald deine Lebensanzeige fällt. Der eine Schalter, der dich vor
dem Tod im unbeaufsichtigten Lauf bewahrt.

### Stopp außerhalb der Zone — `autoRtp.stopOnGuardViolation` (Standard: aus)

Hält an, wenn eine Landung außerhalb des Border-Puffers oder innerhalb des
Spawn-Puffers liegt. Nur einschalten, wenn die Werte im Tab **Schutzzonen**
wirklich zu deinem Server passen — sonst stoppt die Schleife grundlos.

### Stopp bei Spieler in der Nähe — `autoRtp.stopOnPlayerNearby` (Standard: aus)

Beendet den Lauf, sobald ein anderer Spieler innerhalb des Radius auftaucht.
Dazu ein Glockenton und die Entfernung im Chat — ein Stopp, der nur auf einem
Bildschirm steht, den du gerade nicht ansiehst, warnt niemanden.

Greift erst, wenn der Lauf dich einmal weggeteleportiert hat: ein Sprung über
die Aufnahme-Schwelle oder ein Dimensionswechsel. Das ist keine Einschränkung,
sondern das, was die Einstellung benutzbar macht. Sie hält an, weil eine
Landung dich neben jemanden gesetzt hat — und dieser Jemand steht danach immer
noch da. Ab dem ersten Tick scharf würde genau der Lauf, mit dem du wegkommen
willst, an dem Spieler sterben, vor dem du weg willst, und diese Stelle hätte
keinen Ausgang mehr. Wer neben dir steht, wenn du Start drückst, ist deine
Sache; wer da steht, wo die Schleife dich hingeworfen hat, ist ihre.

Gelesen wird die Spielerliste des Clients, also genau das, was der Server
geschickt hat. Wer außerhalb der Server-Sichtweite steht oder versteckt ist,
taucht hier nicht auf. Ein Grund zum Anhalten — keine Garantie, dass du allein
bist.

### Spieler-Radius — `autoRtp.playerNearbyRadius` (Standard: 64 Blöcke)

Ab welcher Entfernung ein anderer Spieler als nah gilt, in drei Dimensionen
gemessen: 16 · 32 · 64 · 128 · 256 Blöcke. Größer als die Sichtweite deines
Servers bringt nichts — weiter entfernte Spieler kommen gar nicht erst beim
Client an. Zuschauer zählen nicht.

**Immer und ohne Schalter stoppt die Schleife bei:** Verbindungstrennung,
Weltwechsel, `End`-Taste, `/rtpbuddy auto stop`. `H` gehört nicht dazu: solange
ein Lauf auf der Uhr läuft, hält es ihn an statt ihn zu beenden — die eine nicht
rücknehmbare Aktion liegt auf einer eigenen Taste.

### Abschnitt: Suchauftrag

Alle Stopps oben beenden einen Lauf, weil etwas schiefging. Dieser beendet ihn,
weil er gefunden hat, wofür er losgeschickt wurde: der Lauf teleportiert weiter,
bis eine Landung passt, hält dann **auf dieser Landung** an, spielt einen kurzen
Glockenton und sagt im Chat, welche Bedingung ausgelöst hat.

Er sendet dadurch nie mehr, nie schneller und nie anders. Ein Suchauftrag kann
einen Lauf nur früher beenden, als er ohnehin geendet hätte.

Solange der Hauptschalter aus ist, wird nichts darunter überhaupt gebaut — sechs
Bedingungen und achtzehn Biomfamilien sind eine Menge Zeilen für die vielen
Läufe, die einfach nur eine Karte füllen.

| Einstellung | Schlüssel | Standard | Wirkung |
|---|---|---|---|
| Suchauftrag | `autoRtp.findEnabled` | aus | Hauptschalter. Aus heißt: der Lauf hält nur aus den üblichen Gründen an — Schaden, Zeitlimit, Anzahl, Schutzzone, Spieler in der Nähe |
| Neue Zelle | `autoRtp.findNewCell` | aus | Hält bei der ersten Landung in einer Zelle, in der noch nie eine aufgezeichnete Landung lag. Das ist die Bedingung, die das Zellenbrett füllt — genau die Würfe, die dich weiterbringen |
| Bestimmte Zellen | `autoRtp.findCells` | leer | Eine getippte Liste wie `11 12 20`. Trenner sind egal; alles, was keine Zahl von 1 bis 81 ist, wird verworfen statt abgelehnt — das Feld wird getippt, und ein halbfertiger Eintrag darf nicht den ganzen Auftrag lahmlegen |
| Distanz ab / bis | `autoRtp.findMinDistance` / `…MaxDistance` | 0 / 0 | Ein Band, gemessen vom Weltursprung. 0 schaltet die jeweilige Grenze ab |
| Zielpunkt X / Z | `autoRtp.findNearX` / `…NearZ` | 0 / 0 | Der Punkt, in dessen Nähe gehalten werden soll. Wirkt erst mit einem Radius |
| Zielradius | `autoRtp.findNearRadius` | 0 | Hält, sobald eine Landung näher als dieser Radius am Zielpunkt liegt. 0 schaltet die Bedingung ab |
| Zielpunkt = meine Position | — | Aktion | Übernimmt deine Koordinaten und setzt den Radius auf 500, falls noch keiner steht |
| Biome | `autoRtp.findFamilies` / `…findBiomes` | leer | Achtzehn Zeilen, eine je Familie. Ein Klick klappt die Biome dahinter auf: obenauf die ganze Familie, darunter jedes einzelne Biom mit der Zahl deiner bisherigen Landungen darin. Mehrere gleichzeitig sind erlaubt, das Menü bleibt beim Ankreuzen offen |
| Verknüpfung | `autoRtp.findMatchAll` | eine reicht | Ob eine einzelne erfüllte Bedingung reicht oder alle gleichzeitig zutreffen müssen. *Alle zusammen* wird schnell sehr selten — dann lohnt die Aufgabegrenze |
| Aufgeben nach | `autoRtp.findGiveUpAfter` | kein Limit | Beendet den Lauf nach so vielen Landungen ohne Treffer. Ohne Grenze läuft ein Auftrag mit seltenem Ziel, bis ihn etwas anderes stoppt — genau die Art Sache, die kein Standard sein sollte |
| Ton bei Treffer | `autoRtp.findSound` | an | Ein kurzer Glockenton. Der Sinn eines Suchauftrags ist, nicht zusehen zu müssen — dann muss das Ende auch ohne Blick auf den Bildschirm ankommen |

Die Biomliste kommt aus der Biomliste des Servers, nicht aus deinen Landungen:
sonst stünde ausgerechnet das, was du suchst, nicht darin. Deshalb ist die **0**
hinter einem Biom die interessante Zahl — dort war der Suchauftrag noch nie.
Eine Familie hält bei jeder Landung in ihrer Farbe, ein einzelnes Biom nur genau
dort: *Blassgarten* statt *irgendwas Seltenes*.

Während ein Lauf sucht, steht im HUD, wonach gesucht wird und wieviele Landungen
er schon verworfen hat. `/rtpbuddy find` sagt dasselbe im Chat, `/rtpbuddy find
on` und `off` schalten den Hauptschalter.

---

## Tab: Schutzzonen

Diese Werte beschreiben die Karte deines Servers. Sie blockieren **nie** eine
Aufnahme. Ihre Wirkung: Overlays auf der Karte, ein Warnhinweis am Sample, die
Abdeckungs-Statistik und optional der Auto-RTP-Stopp oben.

### Border-Radius — `guards.borderRadius` (Standard: 30 000 000)

Radius der Weltgrenze der **Oberwelt** in Blöcken, gemessen vom
Border-Mittelpunkt. Der Standard ist die Vanilla-Grenze; auf einem SMP trägst du
hier den echten Wert ein. Für DonutSMP sind das **225 000** — die Grenze liegt
225 000 Blöcke von 0,0 entfernt, in jede Richtung, die Welt ist also
450 000 × 450 000 Blöcke groß. Dazu **Eckige Border** anlassen.

Ist auch die Basis der Statistik **Abdeckung**: getroffene Dichtezellen geteilt
durch die Zellen innerhalb der Grenze.

### Border-Radius Nether / End — `guards.netherBorderRadius`, `guards.endBorderRadius` (Standard: 0)

Jede Dimension kann ihre eigene Grenze haben, und die meisten Server machen den
Nether kleiner. **0** heißt: Radius der Oberwelt benutzen.

Für DonutSMP: Nether **28 500**, End **87 500**. Das Wiki nennt die Werte als
Kantenlänge — Nether 57 k × 57 k, End 175 k × 175 k — der Radius ist die Hälfte
davon. Beim Nether passt das genau zum 1:8-Verhältnis: 450 000 ∕ 8 = 56 250, also
die 57 k. Sollte das Wiki doch Radien meinen, verdoppelst du die zwei Zahlen.

Auf der Karte bekommt jede Dimension ihr eigenes Rechteck **in der Farbe der
Dimension** und, sobald mehr als eine zu sehen ist, ihren Namen an der Ecke.

### Border-Puffer — `guards.borderGuard` (Standard: 1000)

Sicherheitsabstand nach innen. Nutzbarer Bereich = Radius − Puffer. Genau dieser
innere Rand wird auf der Karte gezeichnet und für die Warnung geprüft.

### Spawn X / Spawn Z — `guards.spawnX` / `guards.spawnZ` (Standard: 0 / 0)

Mittelpunkt der Spawn-Zone.

### Spawn-Puffer — `guards.spawnGuard` (Standard: 500)

Radius um diesen Punkt, der als „zu nah am Spawn" gilt.

### Eckige Border — `guards.squareBorder` (Standard: an)

Behandelt die Grenze als Quadrat (wie Vanilla) statt als Kreis. Ändert die Form
des Overlays **und** die Flächenformel hinter der Abdeckungs-Statistik.

### Meine Position als Spawn

Aktion: übernimmt deine aktuellen X/Z als Spawn-Mittelpunkt.

**Nur in der `config.json`, nicht im Menü:** `guards.borderCenterX` und
`guards.borderCenterZ` — der Mittelpunkt der Weltgrenze, falls dein Server nicht
auf 0/0 zentriert ist.

---

## Tab: Karte

Reine Darstellung. Nichts davon beeinflusst, was aufgezeichnet wird.

| Einstellung | Schlüssel | Standard | Wirkung |
|---|---|---|---|
| Ansicht merken | `map.rememberView` | an | Verschiebung und Zoom überleben das Schließen, je Umfang getrennt. Aus = beim Öffnen immer auf alle Samples einpassen |
| Filter merken | `map.rememberFilter` | an | Dimension, Region, Aufnahmemodus **und die in der Sessionliste angehakten Sitzungen** stehen beim nächsten Öffnen noch. Ohne das ging jedes Öffnen wieder auf *alle* zurück, obwohl vorher z. B. *Oberwelt* gewählt war. Gespeichert in `map.filterDimension`, `map.filterRegion`, `map.filterCaptureMode`, `map.filterSearch`, `map.filterSessions` und `map.filterSessionsActive` |
| Leere Sitzungen ausblenden | `map.hideEmptySessions` | an | Sitzungen ohne eine einzige Landung erscheinen nicht in der Sessionliste. Jedes Einloggen öffnet eine Sitzung, also sammeln sich die von selbst an |
| Bildrate auf der Karte | `map.mapFpsLimit` | 60 | Deckel für die Bildrate, solange die Karte offen ist: aus · 30 · 60 · 90 · 120. Minecraft bremst ein Fenster nicht, solange eine Welt dahinter liegt — ein stehendes Bild 200-mal je Sekunde neu zu zeichnen heizt nur die CPU |
| Gitter | `map.showGrid` | an | Koordinatengitter mit Achsenbeschriftung |
| Border anzeigen | `map.showBorder` | an | Zeichnet den inneren Border-Rand (Radius − Puffer) |
| Spawn-Zone anzeigen | `map.showSpawnGuard` | an | Zeichnet den Spawn-Puffer-Kreis |
| Spielermarker | `map.showPlayer` | an | Deine Position samt Blickrichtung: ein Ring mit dunklem Kragen, damit er sich von jedem Untergrund abhebt, und ein kurzer einpixeliger Strich aus der Mitte als Blickrichtung |
| Spielermarker pulsiert | `map.playerPulse` | an | Ein langsam auslaufender Ring um deine Position. Bewegung ist das Einzige, was sonst nichts auf der Karte macht — nach einem Teleport findest du dich damit sofort wieder |
| Ursprungsmarker | `map.showOrigin` | an | Der Punkt 0 / 0 |
| Linie zur letzten Landung | `map.showLastLeg` | an | Die rote Linie von dem Punkt, an dem der jüngste Teleport startete, zu der Stelle, an der er landete — mit Pfeil in Laufrichtung und einem Ring am Start. Gezogen aus dem **aufgezeichneten Ursprung** dieses Samples, nicht aus dem vorherigen Sample: dazwischen läufst du herum, fällst, nimmst ein Portal, und nur der gespeicherte Ursprung ist eine Aussage über *diesen* Teleport. Beim Zuschauen beantwortet sie ohne Klick, von welchem der Punkte du gerade gekommen bist. Über Dimensionen hinweg weggelassen |
| Sample-Nummern | `map.showSampleNumbers` | an | Nummern an den Markern — **erst ab dem 5k-Maßstab**. Maßgebend ist der Balken unten links: sagt er mehr als 5.000 Blöcke, bleiben die Nummern weg. Näher dran kommt die Enge dazu — teilen sich mehr als ein Fünftel der Marker eine Beschriftungsfläche, bleiben sie ebenfalls weg. Dazu weiterhin höchstens 250 Marker im Bild. Alles zählt nur, was gerade auf dem Schirm liegt |
| Serverregionen-Raster | `map.showServerRegions` | an | Das 9×9-Raster der Serverregionen unter der Karte, jede Zelle in der Farbe der Region, der sie gehört. Nur bei Samples von einem Server mit diesem Raster |
| Farblegende | `map.showLegend` | an | Das Feld in der Ecke der Karte, das sagt, wofür die Markerfarben stehen — die Regionen, Dimensionen oder Sessions, die gerade zu sehen sind. Im **Advanced**-Modus ersetzt sie ein eigener Farbverlauf von *dicht* nach *leer*, weil die Farbe dort eine Messung ist und keine Aufzählung |
| Maßstabsbalken | `map.showScaleBar` | an | Der Balken unten links |
| Runde Marker | `map.roundMarkers` | an | Zeichnet Landungen als Kreis statt als Quadrat, in allen Ansichten |
| Zellenbrett | `map.showCellBoard` | an | Das 9×9-Brett oben in der Kennzahlenspalte: eine Fläche je Serverzelle, gefüllt in der Zonenfarbe, je mehr Landungen desto kräftiger. Goldener Rand = genau einmal getroffen, grüner Rand = die Zelle, in der du stehst. Darunter Balken je Zone mit den Nummern der noch fehlenden Zellen. `/rtpbuddy cells` schreibt dasselbe in den Chat |
| Zellnummern im Brett | `map.cellBoardNumbers` | an | Schreibt die Nummer in jede Zelle, in der eine Landung liegt. Aus lässt nur die Farbflächen stehen — ruhiger, und in einer schmalen Spalte ohnehin lesbarer |
| Zeichenbudget | `map.densityBudget` | 500 | Auswahl: aus · 250 · 500 · 1000 · 2000 · 5000. Ab wievielen gleichzeitig sichtbaren Markern die Karte die ganze Punkt- und Routenebene in eine Textur backt statt sie Rechteck für Rechteck zu zeichnen. Neu gebacken wird nur bei einer Änderung an Ansicht, Filter, Modus, Farbe oder Auswahl — nicht bei Mausbewegung. Ist die Zeichenfläche größer als 2,2 Mio. Pixel, lohnt die Textur nicht mehr; dann werden die Marker stattdessen ausgedünnt: einer je markergroßem Fleck, in den Ballungen eckig, freistehende Punkte und der gewählte Marker bleiben rund. `Aus` zeichnet jede Landung einzeln, egal wieviele |
| Markergröße | `map.markerRadius` | 2 (= 5 px) | Radius eines Markers: 1 · 2 · 3 · 4 · 5 · 6. Klein hält dichte Bereiche lesbar, groß macht einzelne Landungen weit herausgezoomt sichtbar. Der Viewer liest denselben Wert |
| Dichtezelle | `map.densityCellSize` | 512 Blöcke | Kantenlänge einer Dichtezelle: 64 · 128 · 256 · 512 · 1024 · 2048. Zeichnet nichts mehr — sie ist die Grundlage der Statistik **Abdeckung** |
| Lücken-Suchfeld | `map.gapMaskTile` | 16.000 Blöcke | Streifenbreite, mit der der Advanced-Modus abgrenzt, wo überhaupt schon aufgezeichnet wurde: 4.000 · 8.000 · 16.000 · 32.000 · 64.000. Die Landungen werden in so hohe und so breite Streifen geschnitten; ein Punkt gilt als erkundet, wenn er zwischen der linkesten und rechtesten Landung seines Zeilenstreifens **und** zwischen der obersten und untersten seines Spaltenstreifens liegt. Alles außerhalb wird abgedunkelt und nicht bewertet. Breiter ist gröber, aber robuster; schmaler liegt enger an, zerfällt aber irgendwann, und dann fällt ein großes Loch ganz aus der Maske |
| Kachelgröße | `map.gapRasterTile` | 10.000 Blöcke | Boden, für den eine gezählte Kachel steht: 2.500 · 5.000 · 10.000 · 25.000 · 50.000. Jede dieser Größen teilt eine Server-Region (50.000 Blöcke) glatt auf, und gezählt wird von der Ecke der Weltgrenze aus — deshalb liegt keine Kachel je über einer Regionslinie oder über dem Weltrand hinaus. Gehalten, bis der Maßstab 100k überschreitet |
| Gelistete Lücken | `map.gapTopCount` | 5 | Wie viele Löcher der Lücken-Modus einkreist, nummeriert und in der Statistik-Spalte auflistet: 3 · 5 · 8 · 12 |
| Karte über Teleports offen halten | `map.reopenAfterTeleport` | an | Ein Teleport über Welten hinweg blendet den Ladebildschirm des Spiels ein und schließt dabei alles Offene. Mit diesem Schalter wird dieser eine Wechsel abgelehnt: es ist dieselbe Karte, die stehen bleibt — kein Blinken, kein Neuaufbau, Filter, Zoom, Scrollstand und Auswahl unverändert. Nur die Ladebildschirme des Spiels werden abgelehnt; alles, was du oder der Server absichtlich öffnet (Trennungsmeldung voran), kommt durch. Escape schließt weiterhin endgültig |
| Seitenbreiten zurücksetzen | — | — | Aktion: beide Seitenpanels zurück auf 24 % / 26 % der Fensterbreite |

Zur **Nummerierung**: die Samples zählen **innerhalb einer Sitzung ab 1**. Eine
neue Sitzung fängt also wieder bei `#1` an, statt bei der Nummer weiterzumachen,
die gestern erreicht wurde. Das richtet sich danach, was tatsächlich auf der
Karte liegt, nicht nach dem Umfang: hakst du in *Alle Sessions* genau eine
Sitzung an, zählt sie auch dort ab 1. Erst wenn zwei Sitzungen gleichzeitig zu
sehen sind, übernimmt die globale Nummer — nur die bleibt dann eindeutig, und
genau die meinen CSV, Viewer und der Löschen-Dialog. Keine Einstellung; die
gespeicherte Nummer ändert sich so oder so nie.

Zum **Advanced**-Modus (die Lückenkarte): er beantwortet die umgekehrte Frage — nicht
wo Landungen liegen, sondern wo keine liegen. Jeder Punkt der Karte wird nach
dem Abstand zur nächsten Landung eingefärbt, hell heißt weit weg von allem. Bis
zum 5k-Maßstab schattiert er, darüber zählt er feste Weltkacheln in vier Bändern
(0 · 1 · 2–3 · 4+) — dieselbe 5k-Marke wie bei den Sample-Nummern, damit es eine
Regel bleibt statt zwei. Die größten Löcher stehen mit Koordinate und Radius in
der Statistik-Spalte, der Radius immer abgerundet: die Fläche ist *mindestens*
so leer.

Wichtig dabei: **vorher auf eine Region filtern**. Die Messung selbst rechnet
richtig — ein Ort, den zwei Regionen erreichen, ist nur dann selten, wenn er in
beiden selten ist. Aber ein Loch der Region, die dich interessiert, wird von
Landungen einer Region zugeschüttet, die du dort nie angefragt hast.

Zwei Regeln halten die Kacheln beim Verschieben ehrlich. Die Maske wird in
**Weltkoordinaten** gefragt, nie über einen Bildschirmpixel: über den
beschnittenen Mittelpunkt einer halb aus dem Bild ragenden Kachel gefragt,
blinkten die äußere Zeile und Spalte beim Ziehen ein und aus, weil der Prüfpunkt
durch die Kachel wanderte während die Kachel stillstand. Und eine Kachel mit
einer Landung wird gezeichnet, egal was die Maske sagt — die Maske ist nicht auf
das Kachelraster ausgerichtet, also kann eine Kachel, deren Mitte knapp draußen
liegt, in einer Ecke trotzdem Landungen halten, und ein Marker auf nacktem Boden
ohne Kachel darunter sieht aus wie ein Fehler.

Ein Loch am Rand der Aufzeichnung wird trotzdem gelistet. Das Feld steigt jenseits
der Maske weiter an, also ist dort keine Zelle innerhalb je ein lokales Maximum;
ein Nachbar außerhalb der Maske zählt deshalb nicht als höher. Ohne das
verschwindet ausgerechnet das größte Loch der Karte aus der Liste.

Zur **Dichtezelle**: sie zeichnet seit 1.6.8 nichts mehr — die Heatmap ist weg —
aber sie ist weiterhin die Grundlage der Statistik **Abdeckung**: getroffene
Zellen geteilt durch die Zellen innerhalb der Grenze. Kleinere Zellen rechnen
feiner, lassen die Abdeckung aber prozentual absacken — bei 130 Samples und
512er-Zellen sind das schon nur 0,000 %. Für einen Vergleich also die Zellgröße
festhalten, sonst vergleichst du Äpfel mit Birnen.

---

## Tab: HUD

Die beiden Einblendungen im Spiel: das Text-Overlay mit Aufnahmestatus, Zählern
und letztem Sample, und die Minikarte. Sie haben einen eigenen Tab bekommen, weil
der Spielbildschirm geteilt wird: RTPBuddy hat kein Mitspracherecht darüber, was
andere Mods oben links oder oben rechts zeichnen, also muss die eigene Anzeige
dorthin geschoben werden können, wo Platz ist.

| Einstellung | Schlüssel | Standard | Wirkung |
|---|---|---|---|
| HUD-Overlay | `map.hudEnabled` | an | Zeigt die Einblendung überhaupt |
| Overlays platzieren | — | — | Aktion: öffnet den Platzierungs-Bildschirm für **beide** Kästen. Sie werden mit der Maus gezogen, die übrige HUD bleibt sichtbar dahinter. Tab wechselt zwischen Overlay und Minikarte, Pfeiltasten schieben um 1 px, mit Shift um 10; die Kästen rasten an Rändern und Bildmitte ein |
| Ecke | `map.hudAnchor` | `TOP_LEFT` | An welcher Ecke die Anzeige hängt: oben/unten × links/mittig/rechts |
| Abstand X / Y | `map.hudX` / `map.hudY` | 4 / 4 | Abstand von dieser Ecke nach innen, in GUI-Pixeln |
| Größe | `map.hudScale` | 1,0 | Skaliert das ganze Overlay: 50 % bis 200 %, unabhängig von der GUI-Skalierung des Spiels |
| Hintergrundfläche | `map.hudBackground` | an | Die dunkle Fläche hinter dem Text |
| Deckkraft | `map.hudBackgroundOpacity` | 60 % | Wie undurchsichtig diese Fläche ist: 0 · 20 · 40 · 60 · 80 · 100 |
| Textschatten | `map.hudTextShadow` | aus | Lesbarkeit ohne Hintergrundfläche |
| Zeile: Auto-RTP | `map.hudShowAuto` | an | Countdown und Anzahl gesendeter Befehle, nur während die Schleife läuft; angehalten steht dort grau ANGEHALTEN mit der Restzeit |
| Zeile: Status | `map.hudShowState` | an | beobachtet / scharf / landet / pausiert |
| Zeile: Zähler | `map.hudShowCounters` | an | Aufgenommen, gesamt, verpasst, verworfen |
| Zeile: Letztes Sample | `map.hudShowLast` | an | Die letzte Landung als kleiner Block: Nummer und Region (in der Farbe dieser Region, bei DonutSMP mit Servernummer), darunter die Koordinaten mit der Distanz zum Ursprung |
| Zeile: Region & Biom | `map.hudShowContext` | an | Eine Zeile mehr unter der Nummer: Biom und Dimension der letzten Landung |
| Zeile: Meldungen | `map.hudShowStatus` | an | Kurzmeldungen der Aufnahme, sechs Sekunden lang |
| HUD zurücksetzen | — | — | Aktion: Ecke, Abstände, Größe, Fläche und Schatten des Text-Overlays auf die Standardwerte |
| Minikarte zurücksetzen | — | — | Aktion: Ecke, Abstände, Kantenlänge und Deckkraft der Minikarte auf die Standardwerte |

Position und Größe werden als **Ecke plus Abstand** gespeichert, nicht als rohe
Bildschirmposition. Eine an Skalierung 2 gewählte Position läge bei Skalierung 4
sonst halb außerhalb des Bildes.

### Abschnitt: Minikarte

Die kleine Karte am Bildschirmrand. Sie zeigt genau den Ausschnitt, den die große
Karte mit `F` wählt — die gewählten Landungen, eingerahmt — und darin die eigene
Position. Der Ausschnitt steht dabei **fest**: er wandert nicht mit, weil eine
Karte, die sich bei jedem Schritt neu zentriert, nichts darüber sagt, wo man
innerhalb der Aufzeichnung steht. Wer aus dem Rechteck herausläuft, wird als
gelber Punkt an den Rand geheftet.

Ohne aufgezeichnete Landungen rahmt sie stattdessen das Serverfeld ein, in dem
man gerade steht.

| Einstellung | Schlüssel | Standard | Wirkung |
|---|---|---|---|
| Minikarte | `map.minimapEnabled` | an | Zeigt die Minikarte überhaupt. Auch `/rtpbuddy minimap [on\|off]`; `place` öffnet den Platzierungs-Bildschirm |
| Ecke | `map.minimapAnchor` | `BOTTOM_RIGHT` | An welcher Ecke die Karte hängt. Unten rechts als Standard, weil oben links das Text-Overlay sitzt und oben rechts meist die Minikarte einer anderen Mod |
| Abstand X / Y | `map.minimapX` / `map.minimapY` | 4 / 4 | Abstand von dieser Ecke nach innen, in GUI-Pixeln |
| Kantenlänge | `map.minimapSize` | 104 px | Kantenlänge der quadratischen Platte: 64 · 80 · 104 · 128 · 160 · 192. Ab etwa 104 px passen die Feldnummern hinein |
| Deckkraft | `map.minimapOpacity` | 78 % | Wie stark die Platte die Welt dahinter verdeckt: 30 · 50 · 65 · 78 · 90 · 100. Der Rand bleibt immer voll sichtbar, damit die Karte auch bei 30 % eine Kante hat |
| Datensatz | `map.minimapScope` | `SESSION` | Welche Landungen eingerahmt werden: nur diese Sitzung oder alle |

### Abschnitt: Minikarte-Inhalt

| Einstellung | Schlüssel | Standard | Wirkung |
|---|---|---|---|
| Serverfelder | `map.minimapShowRegions` | an | Die 50 000er Felder des Servers, jeweils in der Farbe ihrer Region. Das Feld, in dem man steht, ist deutlich heller und hat einen kräftigen Rand — das ist die eigentliche Antwort auf *wo im Sektor bin ich* |
| Feldnummern | `map.minimapShowCellNumbers` | an | Die Servernummer in jedes Feld schreiben, sobald die Felder mindestens 26 px breit sind. Darunter wäre die Zahl größer als das Feld |
| Nullachsen | `map.minimapShowAxes` | an | Die beiden Linien durch x = 0 und z = 0 |
| Letzter Sprung | `map.minimapShowLastLeg` | an | Die rote Linie zur neuesten Landung: woher dieser Teleport ging. Dieselbe Linie, die auch die große Karte zeichnet |
| Beschriftung | `map.minimapShowCaption` | an | Der Streifen unter der Platte: Region und Feldnummer der eigenen Position, dazu die Breite des Ausschnitts |

**Zur Last auf der Bildrate.** Die Platte wird einmal in eine Textur gemalt und
danach als *ein* Viereck gezeichnet. Tausend Landungen wären sonst tausend
`fill`-Aufrufe in jedem einzelnen Bild — derselbe Fehler, an dem die Lückenkarte
fast gescheitert wäre, nur dauerhaft, weil eine Minikarte die ganze Sitzung über
sichtbar ist. Neu gemalt wird nur, wenn sich das Bild wirklich ändert: eine neue
Landung, eine andere Sitzung, ein umgelegter Schalter, oder das Überqueren einer
Feldgrenze. Ein solches Neumalen dauert mit zweitausend Landungen unter 3 ms.
Pro Bild bleiben der eigene Marker, bis zu sechzehn Feldnummern und die
Beschriftung — rund fünfzig Rechtecke, etwa so viel wie ein einzelnes
Vanilla-HUD-Element.

---

## Nicht im Menü — nur in der `config.json`

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `capture.defaultCategory` | `rtp` | Kategorie-Stempel auf neuen Samples, für spätere Auswertungen |
| `capture.manualFallbackRegion` | `unknown` | Region, die eine `K`-Aufnahme ohne Angabe bekommt |
| `guards.borderCenterX` / `…Z` | 0 / 0 | Mittelpunkt der Weltgrenze |
| `map.markerMode` / `map.colorMode` | POINTS / DIMENSION | Werden von der unteren Leiste der Karte gesetzt |
| `map.filterBiomeFamily` | leer | Die in der unteren Leiste gewählte Biomfamilie. Als Familienname gespeichert, nicht als angezeigte Beschriftung — die kommt aus einer Übersetzung und würde einen Sprachwechsel nicht überleben |
| `map.filterSearch` | leer | Der Inhalt des Suchfelds im Kopf der Karte. Wird wie der übrige Filter nur gespeichert, solange **Filter merken** an ist |
| `map.sessionCenter…` / `map.allCenter…` | — | Die gemerkte Ansicht je Umfang |
| `map.filterSessions` / `…Active` | leer / aus | Die in der Sessionliste angehakten Sitzungen und ob überhaupt eingegrenzt wird. Beides nötig: eine leere Liste mit gesetztem Schalter heißt *keine anzeigen*, was nicht dasselbe ist wie *kein Sessionfilter* |
| `map.scope` | SESSION | Umfang, mit dem `M` die Karte aufmacht: `SESSION` oder `ALL`. Umgestellt im Kopf der Karte, und die Einstellung bleibt |
| `map.gapRasterTile` | 10.000 | Boden, für den eine gezählte Kachel im Lücken-Modus steht, in Blöcken. Umschaltbar unter **Karte → Kachelgröße**. Ein Wert, der eine Server-Region nicht glatt teilt, wird auf die nächste passende Größe gerundet. Ab 100k Maßstab greift wieder die mit dem Zoom mitwachsende Kachel, weil die gehaltene dort unter drei Pixel fällt |
| `map.sidebarFraction` | 0,28 | Höhe der Sessionliste in der Seitenleiste, als Anteil der Panelhöhe. Der Trenner darunter ist ziehbar |
| `map.panelLeftFraction` / `…Right…` | 0,24 / 0,26 | Breite der Seitenpanels als Anteil der Fensterbreite |
| `map.panelMode` | BOTH | Welche Seitenpanels die Karte aufmacht: `BOTH` · `LEFT_ONLY` (nur Statistik) · `RIGHT_ONLY` (nur Liste) · `NONE` (nur Karte). Gesetzt von `Tab` und der Panel-Taste in der unteren Leiste, und ab 1.6.15 gespeichert — vorher stand jede Karte wieder auf *beide*. Ein zu schmales Fenster zeigt weniger Panels als gewählt, schreibt das aber nicht zurück |
| `regions[]` | Preset | Muster und Befehl je Region |
| `legacyImportDone` / `…At` / `…Count` | — | Marker für den einmaligen Import aus dem Vorgänger `rtpmapper` |

---

## Untere Leiste der Karte

Keine gespeicherten Einstellungen im engeren Sinn, aber dieselbe Art Schalter —
die ersten beiden merkt sich die Config.

| Knopf | Wirkung |
|---|---|
| **Punkte / Pfad / Advanced** | Wie Samples gezeichnet werden. **Advanced** zeigt statt der Landungen den Abstand zur nächsten — die Lückenkarte |
| **Dimension / Region / Aktualität / Session / Biom** | Was die Markerfarbe bedeutet. **Biom** färbt nach Biom*familie* — knapp fünfzig Biome sind aufgezeichnet, und fünfzig Farbtöne auf einem Bild sind Konfetti |
| **alle / Oberwelt / …** | Filter auf eine Dimension |
| **Alle Biome / Wald / Ebene / …** | Klappt eine Liste der Biomfamilien auf, die im gewählten Umfang wirklich vorkommen, jede mit ihrer Farbe und der Anzahl, *Alle Biome* obenauf. Ein Klick wählt, ein Klick daneben schließt, `Esc` auch. Achtzehn Familien durchzuschalten wäre kein Filter: nach der falschen Wahl lägen siebzehn Klicks zwischen dir und wieder allen. Ein einzelnes Biom findest du über das Suchfeld — dort zieht sowohl die Id als auch der Name, den das Spiel anzeigt |
| **Einpassen** (`F`) | Zoomt auf alle sichtbaren Samples. Mit **Shift** stattdessen auf die ganze Weltgrenze — das ist der einzige Weg zu sehen, wie viel der Welt die Landungen abdecken |
| **Reset** (`R`) | Verschiebung und Zoom zurücksetzen |
| **Export** | Schreibt die sichtbaren Samples als CSV mit Zeitstempel nach `.minecraft/rtpbuddy/exports/` |
| **Leeren** | Setzt alle Filter zurück |
| **Löschen** (`Entf`) | Löscht die ausgewählten Samples. Ein Sample per Klick auswählen oder mit **Shift** ein Rechteck ziehen; bei mehreren fragt ein Dialog mit Anzahl und Nummernbereich nach |
| **Zurückholen** (`Strg+Z`) | Holt den letzten Löschvorgang zurück. Nur der letzte, und nur bis das Spiel beendet wird |
| **Panels** (`Tab`) | Schaltet zwischen beiden Seitenleisten, nur Stats, nur Liste, keine |
| **Trenner in der Seitenleiste** | Ziehen teilt die Seitenleiste zwischen Sessionliste und Sampleliste auf; Doppelklick setzt zurück |
| **Diese Session / Alle Sessions** (oben rechts) | Der Umfang. In *Alle Sessions* erscheint die Sessionliste in der Seitenleiste; Häkchen dort blenden einzelne Sitzungen aus, dann heißt der Knopf *Auswahl (n)* |
| **Neue Sitzung starten** (in der Sitzungs-Karte) | Beendet die laufende Sitzung und öffnet eine neue, ohne aus- und wieder einzuloggen. Die Samples zählen danach wieder ab 1, die Zähler der Karte ebenso. Nichts wird gelöscht: die bisherigen Landungen behalten ihre Sitzung. Eine Sitzung ohne Landung wird nicht ersetzt — dabei käme nur dieselbe leere Sitzung unter neuer Kennung heraus |
| **Leere Sitzungen löschen (n)** (in der Sessionliste) | Erscheint nur, solange es Sitzungen ohne eine einzige Landung gibt, und löscht genau diese. Die laufende Sitzung ist nie dabei, und Samples werden dabei nie angefasst |
| **Scrollleiste der Sampleliste** | Ziehbar. Sie liegt in einem eigenen Streifen rechts, damit ein Griff danach nicht mehr die Zeile dahinter auswählt |
| **Optionen** (`O`) | Dieses Einstellungsmenü |
| **Auto aus / Auto 42s / Auto hält** | Startet oder stoppt die Auto-RTP-Schleife, dient gleichzeitig als Countdown. Mit `H` angehalten steht dort *Auto hält* — ein Countdown, der sich nicht bewegt, sähe aus wie ein hängendes Spiel |
| **Sitzung umbenennen** *(in der Sessionliste)* | Erscheint, sobald genau eine Sitzung angehakt ist — auch die laufende. Gibt ihr einen eigenen Namen (max. 32 Zeichen), statt dass jede Zeile die Serveradresse wiederholt. Steht danach in der Sessionliste, in der Sitzungskarte und im Viewer; leer lassen setzt ihn zurück. Gespeichert als `label` am Sitzungseintrag |
| **Sitzungen zusammenführen (n)** *(in der Sessionliste)* | Erscheint, sobald mindestens zwei Sitzungen angehakt sind. Fasst sie zu einer zusammen: Ziel ist die älteste, deren Farbe, Name und Startzeit bleiben. Es wird nichts gelöscht — jede Landung bleibt, gehört danach nur zu einer Sitzung, und die Nummern laufen in Landereihenfolge ab 1 durch. Die anderen Sitzungseinträge verschwinden, darum fragt vorher ein Dialog; rückgängig geht es nicht. Über Server hinweg abgelehnt |
| **Sitzung fortsetzen: TT-MM SS:MM** *(in der Sessionliste)* | Erscheint, sobald genau eine Sitzung angehakt ist und es nicht die laufende ist. Setzt sie fort statt eine neue zu öffnen: jede weitere Landung bekommt ihre Nummer, die Zählung läuft also weiter statt bei 1 anzufangen. Die laufende Sitzung wird davor sauber beendet — und verworfen, wenn sie nichts aufgezeichnet hat. Über Server hinweg abgelehnt: eine Sitzung ist ein Stück Spielzeit auf einem Server |
