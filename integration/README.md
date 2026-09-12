# integration/

Dieser Ordner macht RTPBuddy in ein fremdes Fabric-Projekt übernehmbar, **ohne
dass der Empfänger dieses Repository braucht**. Er enthält den vollständigen
Quelltext als echte Kopie, den Auftrag dazu und ein Nachschlagewerk. Auf einen
anderen Rechner kopieren und dort auspacken genügt.

**Stand: RTPBuddy 1.6.24, 12.09.2026.**

## Inhalt

| Datei | Wofür |
|---|---|
| [PROMPT.md](PROMPT.md) | Der Auftrag für das fremde Projekt. Steht für sich allein. |
| [ANHANG.md](ANHANG.md) | Nachschlagewerk. Für den einfachen Einbau nicht nötig. |
| `quelltext/1.6.24/java/` | 72 Java-Dateien, Ordnerstruktur 1:1 wie `src/main/java/` |
| `quelltext/1.6.24/resources/` | 6 Ressourcen-Dateien, 1:1 wie `src/main/resources/` |
| `vorlagen/fabric.mod.json.1.6.24` | RTPBuddys eigene Metadaten — **nur zum Vergleichen**, nicht zum Überschreiben |
| `PRUEFSUMMEN.txt` | SHA-256 aller 79 kopierten Dateien, Pfade relativ zu diesem Ordner |

## Benutzung in 3 Schritten

1. **Prüfen.** In diesem Ordner `sha256sum -c PRUEFSUMMEN.txt` laufen lassen.
   Erwartet: 79-mal `OK`.
2. **Übergeben.** Diesen Ordner in das Zielprojekt legen und dort
   [PROMPT.md](PROMPT.md) als Auftrag geben. Der Auftrag setzt kein Wissen über
   RTPBuddy oder dieses Repository voraus und nennt jeden Quellpfad wörtlich.
3. **Abnehmen.** Die Prüfliste am Ende von [PROMPT.md](PROMPT.md) abhaken.

## Danach

**Nach dem Einbau wird dieser Ordner gelöscht.** Er ist eine Momentaufnahme von
1.6.24 und veraltet ab der nächsten Änderung am Quelltext — eine zweite Kopie
des Quelltextes, die niemand pflegt, ist schlimmer als keine.
