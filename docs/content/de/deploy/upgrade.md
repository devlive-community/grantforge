---
title: Aktualisieren und von alten Versionen migrieren
description: Upgrades zwischen 2.x-Versionen sowie die Migration von Konten, Rollen und Menüs aus 1.x (AuthX / GrantForge 1.x).
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Upgrade zwischen 2.x-Versionen

1. Sichere die Datenbank (sowie `plugins/` und `configure/`).
2. Halte den Dienst an: `bin/shutdown.sh`.
3. Ersetze `lib/` und `bin/` durch die des neuen Release-Pakets.
4. Starte: `bin/startup.sh`. Liquibase führt die Datenbankmigration der neuen Version automatisch aus; die Bereitschaftsprüfung gibt erst nach Abschluss der Migration 200 zurück.

Bei einem Cluster-Upgrade halte zuerst alle Instanzen an und starte dann die neue Version, damit alte und neue Version nicht gleichzeitig lesen und schreiben. Bereits veröffentlichte Migrationen werden nicht mehr geändert, und für jede Version ist das „Upgrade von der Vorgängerversion“ auf allen unterstützten Datenbanken geprüft.

## Migration aus 1.x

1.x speichert die Daten in einem anderen Satz Tabellen, den 2.x nicht liest. So migrierst du: Installiere 2.x auf einer **neuen Datenbank** und schließe die Initialisierung ab, halte den Dienst an und importiere dann Konten, Rollen und Menüs der alten Datenbank in einen Mandanten:

```bash
# Zuerst eine Vorabprüfung: nur den Bericht erzeugen, nichts schreiben
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default
# Nach Prüfung des Berichts endgültig importieren
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default --apply
```

Beide Befehle schreiben `logs/legacy-import-report.json`. Er listet, was bereits (oder demnächst) erzeugt wird, was übersprungen wird und warum, sowie die neue ID für jedes alte Objekt. Den JDBC-Treiber der alten Datenbank legst du in `drivers/` ab, das Passwort wird über `GRANTFORGE_LEGACY_SOURCE_PASSWORD` angegeben (fehlt es, fragt das Skript danach). Ein wiederholter Import ergänzt nur fehlende Inhalte.

Importregeln:

- **Konten** behalten das bisherige Passwort und wechseln bei der ersten Anmeldung automatisch auf den neuen Hash-Algorithmus. Konten, die der Benutzernamenregel von 2.x nicht entsprechen (3–64 Buchstaben, Ziffern oder `._@-`), kein Passwort haben oder deren Benutzername bereits von einem anderen Mandanten belegt ist, werden übersprungen.
- **Rollen** behalten den Namen, die Kodierung wird in Kleinbuchstaben umgewandelt (`GLY` → `gly`).
- **Menüs** werden Ressourcen der Anwendung `legacy`: Menüs mit der Adresse `#` werden Gruppen, andere Adressen werden Seiten, Menüs unter einer Seite werden Schaltflächen. Die Menüadresse und ihre HTTP-Methode werden zur API-Ressource `api:<Methode>:<Pfad>`; Adressen, die auf `*` enden, werden zu `<Pfad>/**` und nach Pfadsegmenten statt nach Zeichenpräfix verglichen. Der Bericht listet sie einzeln zur Prüfung auf.
- Es werden nur **explizite Berechtigungen** migriert: 1.x erlaubte jedem den Zugriff auf Adressen, die als Menü eingetragen waren; 2.x tut das nicht.

> [!WARNING]
> Prüfe vor dem Import im Bericht die übersprungenen Konten und die Platzhalter-Adressen, bevor du `--apply` ausführst.
