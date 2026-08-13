# Minecraft Rival

Minecraft Rival besteht aus zwei Teilen für **Minecraft Java 1.20.1**:

- `plugin`: Spigot-1.20.1-kompatibles Server-Plugin (auch für Paper/Mohist) und alleinige Quelle für Herzen, Combat, Gräber, Spielzeit, Mitteltrennung, Endkampf, Erzfeinde, Clans, Regeln, Moderation und Administration.
- `mod`: zusätzlich obfuskierte NeoForge-Clientmod für Server-Handshake, texturbasiertes HUD, reduziertes F3 und den eigenen Rival-Startbildschirm.

## Voraussetzungen und Build

- Java 17 oder neuer
- Paper 1.20.1 oder Mohist 1.20.1 auf dem Server
- NeoForge 47.1.106 für Minecraft 1.20.1 auf den Clients

```bash
./gradlew clean build
```

Danach entstehen:

- `plugin/build/libs/RivalPlugins-1.0.jar`
- `mod/build/libs/RivalMod-1.0.jar` (obfuskiert; an Spieler verteilen)
- `mod/build/libs/RivalMod-unobfuscated-1.0.jar` (nur für Entwicklung)

Die Obfuskations-Zuordnung in `mod/build/obfuscation-map.txt` darf nicht verteilt werden.

## Installation

1. Den Server stoppen und den Projektwelt-Ordner exakt `rival_main` nennen. Er liegt **zusätzlich** neben der normalen Hauptwelt (`world` oder ein beliebiger anderer `level-name`).
2. In `server.properties` `allow-nether=false` setzen; in `bukkit.yml` zusätzlich `settings.allow-end: false` setzen. `level-name` muss nicht geändert werden.
3. Plugin-JAR in den `plugins/`-Ordner des Paper-/Mohist-Servers kopieren. Das Plugin erkennt eine bereits geladene `rival_main` oder lädt den vorhandenen zusätzlichen Weltordner selbst.
4. Server einmal starten und `plugins/MinecraftRival/config.yml` prüfen. Alte gespeicherte Projektpositionen werden automatisch auf `rival_main` migriert.
5. NeoForge 47.1.106 für Minecraft 1.20.1 installieren und die obfuskierte Mod-JAR in den Client-Ordner `mods/` kopieren. Eine zusätzliche Fabric API wird nicht benötigt.
6. Mit `/admin mode` den Admin-Modus aktivieren.
7. Im Warteraum `/admin setlocation waiting` ausführen.
8. Auf der Nether-Insel `/admin zone nether pos1` und an der gegenüberliegenden Ecke `/admin zone nether pos2` ausführen. Dasselbe mit `end`. Die Y-Höhe ist dabei egal: Beide Zonen gelten vertikal unbegrenzt.
9. Für jede Seite genügend unterschiedliche Startpunkte mit `/admin spawn negative add` beziehungsweise `/admin spawn positive add` setzen.
10. Spieler mit `/admin player side <Spieler> <-1|1>` zuweisen. Mit Wert `0` wird eine Zuweisung wieder entfernt.
11. Optional `/admin project schedule 2026-08-11T20:00:00` setzen oder mit `/admin project start` manuell starten.

> **Wichtig:** Für die Spiellogik wird serverseitig nur `RivalPlugins-1.0.jar` benötigt. Auf Mohist darf die aktuelle `RivalMod-1.0.jar` zusätzlich im Serverordner `mods/` bleiben; ihr Dist-Schutz verhindert dort das Laden von Minecraft-Clientklassen. Auf jedem Spieler-Client muss die Mod weiterhin installiert sein.

Das vorinstallierte Handshake-Secret stimmt in Plugin und Mod überein. Vor einer echten Veröffentlichung sollte es sowohl in `config.yml` als auch in `RivalClient.secret()` geändert und die Mod danach neu gebaut werden. Ein Secret in einer Clientmod kann trotz Obfuskation grundsätzlich extrahiert werden. Die Lösung erschwert Manipulation durch ProGuard und Challenge/HMAC mit zufälliger Nonce, kann aber keine mathematisch perfekte Geheimhaltung auf einem fremden Client garantieren.

## Spielregeln

### Herzen, Combat und Gräber

- Jeder Spieler startet mit drei Projekt-Herzen. Die gelieferten blauen Originaltexturen wurden auf ihren sichtbaren Alpha-Bereich zugeschnitten und erscheinen sauber zentriert unmittelbar über der Hotbar.
- Direkter PvP-Schaden, Geschosse, gezündetes TNT und gezähmte Tiere markieren Angreifer und Opfer 30 Sekunden lang als im Kampf.
- Jeder Tod während dieses Tags kostet ein Projekt-Herz; ein Tod außerhalb des Combat-Tags kostet keines.
- Bei null Herzen wird der Spieler dauerhaft projektintern gesperrt: `Du hast alle Herzen verloren. Danke für deine Teilnahme`.
- Bei jedem Tod werden Hauptinventar, Hotbar, komplette Rüstung, Nebenhand und Cursor-Item in einen Kopf mit Hologramm gelegt, auch bei `keepInventory` oder Fluch des Verschwindens.
- Rechtsklick öffnet ein Grab. Schleichen + Rechtsklick/Schlag löscht Grab und Inhalt. Leere Gräber verschwinden sofort, alle anderen nach 24 Stunden. Gräber und Items überstehen Neustarts.

### Client-Menü und reduziertes F3

- Der Client ersetzt den Vanilla-Titelbildschirm durch das Rival-Design und zeigt ausschließlich Multiplayer, Optionen und Spiel beenden. Singleplayer und Realms sind nicht erreichbar.
- Das bereitgestellte Hintergrundmotiv wird vollständig eingepasst; das mittige Logo oben wird von keinem Bedienelement überdeckt.
- Das native Fenster-/Taskleisten-Icon verwendet das abgerundete Rival-Motiv in 16, 32, 48, 128 und 256 Pixeln.
- Unter 3,5 Vanilla-Herzen erscheint eine rote Randvignette, deren Stärke mit sinkender Gesundheit zunimmt.
- F3 schaltet ausschließlich eine kleine Anzeige links oben mit XYZ-Koordinaten, Clan und verbleibender Tages-Spielzeit. Das Vanilla-Debugfenster wird dabei unterdrückt.

### Spielzeit

- `/spielzeit` zeigt die heute bereits gespielte und die verbleibende Tageszeit; `/spielzeit anzeige` schaltet die Live-Bossbar um.
- Die Spielzeit zählt nur während eines gestarteten Projekts für zugewiesene, aktive Teilnehmer. `/spielzeit` zeigt zusätzlich, ob der Timer läuft oder warum er pausiert ist; F3 zeigt gespielt und übrig.
- Warnungen erscheinen bei 30/15/5/3/1 Minuten und 30/10/5/4/3/2/1 Sekunden.
- Nach Ablauf verhindert das Plugin weitere Logins bis zum nächsten Kalendertag in der konfigurierten Zeitzone.
- Im Warteraum wird nicht gezählt. Im Admin-Modus und Vanish wird ebenfalls nicht gezählt. Die Funktion kann vollständig deaktiviert werden.

### Warteraum und Projektstart

- Vor Projektstart befinden sich alle Spieler im festgelegten Warteraum und sehen eine Countdown-Bossbar.
- Nicht zugewiesene Spieler bleiben auch nach dem Projektstart im Warteraum und sehen `Warte auf Seitenzuweisung …`.
- Ein Start ist nur möglich, wenn Warteraum, Nether-/End-Zone, mindestens zwei Teilnehmer, alle Online-Zuweisungen und genügend unterschiedliche Seiten-Spawns vorhanden sind.
- Beim Start werden die Erzfeinde zurückgesetzt, die Mittel-Border aktiviert und alle Spieler auf unterschiedliche Spawnpunkte ihrer Hauptinselseite verteilt.

### Border und Endkampf

- Die Mitteltrennung ist eine zusätzliche, sichtbare Partikelwand und blockiert serverseitig unerlaubte Bewegungen, Fahrzeuge und Teleports an der exakten Trennkoordinate.
- Sie setzt weder eine persönliche noch die globale Vanilla-Worldborder. Eine bereits vorhandene normale Worldborder bleibt während des regulären Projekts vollständig unverändert und sichtbar.
- Achse, Koordinate, Kapazität und Aktivierungs-/Deaktivierungszeit sind konfigurierbar. Zeitformat: `YYYY-MM-DDTHH:MM`, zum Beispiel `2026-08-11T20:00`.
- Bei zwei verbleibenden, online befindlichen Teilnehmern meldet das System nur die Finalbereitschaft. Der Endkampf startet **ausschließlich manuell** mit `/admin endfight start`.
- Erst im aktiven Endkampf wird die echte Worldborder vorübergehend auf 100×100 gesetzt. Ihr vollständiger vorheriger Zustand wird persistent gesichert und bei Ende, Plugin-Disable oder nach einem unterbrochenen Serverstart wiederhergestellt.

### Inselzonen, Mobs und Dimensionen

- Die Rival-Spiellogik läuft ausschließlich in der zusätzlichen Projektwelt `rival_main`. Die normale Server-Hauptwelt darf beliebig heißen und bleibt bestehen; Projektspieler werden sicher nach `rival_main` geführt. Nether-/End-Ziele, Portale und Gateways bleiben für das Projekt blockiert.
- Nether- und End-Insel sind zwei rechteckige X/Z-Zonen auf derselben Karte. Zwei Adminpunkte legen die Fläche fest; die Zone gilt über **alle Y-Höhen**.
- Jeder Mob erhält beim Spawn seine Ursprungszone und Hauptkartenseite. Mobbewegung, Teleports und von Mobs abgeschossene Projektile dürfen weder eine Inselzonengrenze noch die zentrale Seitentrennung passieren.
- Außerhalb der beiden Spezialzonen gilt automatisch `overworld`. Welche normalen Mobs erscheinen können, bestimmt weiterhin das jeweilige Biom.

### Projektkarte und X-Ray-Schutz

- Die mitgelieferte Projektkarte öffnet sich standardmäßig mit `J`. Die Belegung ist unter Optionen → Steuerung → Minecraft Rival frei änderbar.
- Das Mausrad zoomt weich; mit gedrückter linker Maustaste lässt sich die Karte verschieben. `R` setzt Zoom und Position zurück.
- Bekannte X-Ray-/Cheat-Mods und entsprechend benannte Ressourcenpakete werden beim Beitritt abgewiesen. Zusätzlich rendert die Mod vollständig verdeckte Erze nicht, sodass transparente X-Ray-Texturen sie nicht sichtbar machen.
- Die natürliche Spawnrate ist für `nether`, `end` und `overworld` separat zwischen 0 und 100 Prozent einstellbar. 100 Prozent entspricht der normalen biomabhängigen Rate.
- Villager und Wandering Trader spawnen nicht. Vorhandene werden entfernt, Transformationen zu Villagern und alle Merchant-Handelsfenster werden blockiert.

### Erzfeind und Clans

- Vor dem Reveal zeigt die Mod einen schwarzen Block mit `?`. `/admin erzfeind` ordnet allen lebenden Spielern ein Ziel zu und zeigt dessen Kopf.
- Wer seinen aufgedeckten Erzfeind als zugeordneter Combat-Gegner besiegt, erhält bei höchstens zwei Herzen ein Herz zurück; drei bleiben das Maximum.
- Clans sind reine Anzeigegruppen. Standardmaximum sind vier Mitglieder; ein Spieler kann technisch und über sämtliche Befehle nur einem Clan gleichzeitig angehören.
- PlaceholderAPI-Placeholder: `%rival_clan%`, `%rival_clan_name%`, `%rival_clan_tag%`, `%rival_clan_color%`, `%rival_hearts%`.
- `%rival_clan%` liefert leer oder `&8[<Clanfarbe><Clantag>&8]` und kann direkt in einem Tablist-Plugin verwendet werden.

### YouTube, Regeln und Nachrichten

- `/youtube` fordert zunächst eine anklickbare Bestätigung an. Nach der Bestätigung wird der Aufnahmemodus aktiviert oder deaktiviert und serverweit angekündigt.
- Während einer Aufnahme folgt dem Spieler ein kleiner, farbiger `ʏᴏᴜᴛᴜʙᴇ`-Hinweis direkt unter seinem Namen. Beim Verlassen endet der Modus automatisch.
- `/rules` und `/regeln` zeigen die dauerhaft gespeicherten, nummerierten Projektregeln.
- Alle Systemnachrichten nutzen den konfigurierten Rival-Prefix. Normale Texte sind `&7`, Fehler `&c` und eingesetzte Werte/Placeholder `&6`.
- Die Spieler- und Adminhilfe ist nach Themen geordnet; Infoelemente tragen darunter den grauen Hinweis `by pluginsmc.com`.

## Spielerbefehle

| Kategorie | Befehle |
|---|---|
| Hilfe | `/help` |
| Spielzeit | `/spielzeit`, `/spielzeit anzeige` |
| Clan | `/clan create <Name>`, `invite <Spieler>`, `accept`, `kick <Spieler>`, `leave`, `color <0-9/a-f>`, `tag <2-6 Zeichen>`, `info`, `help` |
| YouTube | `/youtube`, danach `/youtube bestätigen` oder die anklickbare Bestätigung |
| Regeln | `/rules`, `/regeln` |

## Adminbefehle

Alle Adminfunktionen benötigen `rival.admin`. Ein berechtigter Spieler agiert trotzdem zunächst vollständig als normaler Spieler. Erst `/admin mode` aktiviert Adminbefehle, pausiert die Spielzeit und erlaubt die Admin-GUI. Erneutes `/admin mode` beendet den Modus; aktiver Vanish wird dabei sicher beendet.

| Kategorie | Befehle |
|---|---|
| GUI/System | `/admin mode`, `/admin`, `/admin help`, `/admin reload`, `/admin vanish` |
| Spielerzentrale | `/admin players [Seite]`: online, Teilnahme, Herzen, Seite, Clan, YouTube, Combat, Admin/Vanish und Spielzeit |
| Playtime-Ranking | `/admin playtime ranking`: heute gespielte Zeit aller bekannten Spieler, absteigend sortiert |
| Projekt | `/admin project <start|stop|schedule> [Datum]`, `/admin setlocation waiting` |
| Seiten-Spawns | `/admin spawn <negative|positive> <add|clear>` |
| Inselzonen | `/admin zone <nether|end> <pos1|pos2|clear|info>` |
| Mobraten | `/admin mobrate <nether|end|overworld> <0-100>` |
| Border | `/admin border <on|off|toggle>` |
| Finale | `/admin endfight <status|start|stop>`; Status listet verbleibende Spieler und Herzen |
| Erzfeind | `/admin erzfeind` |

| Spieler | `/admin player <hearts|revive|eliminate|timereset|side> <Spieler> [Wert]` |
| Gräber | `/admin graves <count|deleteall|near|player> [Radius/Spieler]` |
| Clans | `/admin clan <create|add|remove|owner|color|tag|info|disband> ...` |
| Broadcast | `/admin broadcast <Text>`; `\n` erzeugt mehrere Zeilen mit jeweils eigenem Prefix |
| Regeln | `/admin rules <add|remove|list> [Text/ID]` |
| Banns | `/admin ban <Spieler> <Dauer|permanent> <Grund>`, `/admin unban <Spieler>` |
| Verwarnungen | `/admin warn <Spieler> <Grund>`, `/admin warnings <Spieler> [list|clear]` |
| Jede Config-Option | `/admin config <config.yml-Pfad> <Wert>` |

`/admin` öffnet immer das Spielleitungs-Dashboard. Dort wird der Admin-Modus direkt aktiviert, anschließend führen Untermenüs durch Karte einrichten, Spielerzentrale, Projektstart und Erzfeind-Reveal. In der **Spielerzentrale** zeigt jeder Spielerkopf den vollständigen Status. Spielerkopf → **Herzen verwalten** öffnet die Herz-Unterseite; dort lassen sich 0, 1, 2 oder 3 Herzen direkt festlegen oder einzeln hinzufügen beziehungsweise abziehen. Null Herzen markiert den Spieler als ausgeschieden, ein späterer positiver Wert aktiviert ihn wieder.

Unter „Karte einrichten“ erhält man einen Setup-Stick: Rechtsklick setzt den gewählten Punkt, Linksklick wechselt zum nächsten Modus. Warteraum, beide vertikal unendlichen Inselzonen, X-/Z-Trennlinie, Seitenspawns und Finalmitte lassen sich damit ohne Koordinatenbefehle konfigurieren.

Ein `/admin broadcast`, der bei deaktiviertem Admin-Modus eingegeben wird, landet dauerhaft in der persönlichen Warteschlange. Beim nächsten `/admin mode` werden alle wartenden Broadcasts in ihrer ursprünglichen Reihenfolge gesendet. Zeitangaben für Banns können kombiniert werden, beispielsweise `30m`, `12h`, `5d` oder `1w2d`. Standardmäßig führt jede dritte Verwarnung zu einem automatischen Bann für fünf Tage; `moderation.warns-before-ban` und `moderation.auto-ban-days` sind konfigurierbar.

Beispiele:

```text
/admin config playtime.daily-minutes 180
/admin config playtime.enabled false
/admin config clans.maximum-members 4
/admin config border.side-capacity 20
/admin config border.split-coordinate 0
/admin config border.deactivate-at 2026-08-11T20:00
/admin mobrate nether 75
/admin mobrate end 50
/admin mobrate overworld 100
/admin player hearts Steve 2
```

## Permissions

| Permission | Standard | Zweck |
|---|---:|---|
| `rival.player` | alle | Bündelt alle Spielerrechte |
| `rival.player.help` | alle | Spielerhilfe |
| `rival.player.playtime` | alle | Spielzeitbefehle |
| `rival.player.clan` | alle | Clanbefehle |
| `rival.player.youtube` | alle | YouTube-Aufnahmemodus |
| `rival.player.rules` | alle | Projektregeln anzeigen |
| `rival.admin` | OP | Erlaubt das Aktivieren des zustandsgebundenen Admin-Modus |

## Wichtige Dateien und Betrieb

- `config.yml`: Regeln und Texte
- `data.yml`: Spieler, Herzen, Seiten, Erzfeinde und Clans
- `graves.yml`: persistente Grabpositionen und serialisierte Items
- `rules.yml`: nummerierte Projektregeln
- `moderation.yml`: temporäre/permanente Banns und Verwarnungen
- `broadcast-queue.yml`: noch nicht gesendete Admin-Broadcasts
- `admin-snapshots.yml`: ausfallsichere Inventar-/Cursor-/Statussicherung während Vanish
- `endfight-state.yml`: Wiederherstellungszustand der normalen Worldborder

Vor Updates sollten `data.yml` und `graves.yml` gesichert werden. Änderungen an gespeicherten Daten sollten nur bei gestopptem Server erfolgen. Die automatische Tagesgrenze verwendet `general.timezone`, standardmäßig `Europe/Vienna`.
