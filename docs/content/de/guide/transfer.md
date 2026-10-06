---
title: Massenimport und -export
description: Importiere oder exportiere Benutzer und die Organisationsstruktur stapelweise über CSV-Dateien; geschrieben wird erst, wenn eine Vorabprüfung erfolgreich war.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Zugriffskontrolle → Import/Export** importiert oder exportiert Benutzer und die Organisationsstruktur stapelweise über CSV-Dateien.

![Import/Export](/screenshots/transfer.png)

## Import

1. Klicke auf **Vorlage herunterladen** und fülle sie anhand der „Spaltenerläuterung“ aus. Die Kopfzeile unterscheidet nicht zwischen Groß- und Kleinschreibung, überzählige Spalten werden ignoriert; Abteilung und Stelle eines Benutzers werden als Code eingetragen, mehrere durch Semikolon getrennt.
2. Lade die Datei hoch und klicke auf **Vorabprüfung**: GrantForge prüft zeilenweise und meldet jedes Problem (Zeilennummer, Spalte und Ursache).
3. Wenn alles bestanden ist, klicke auf **Import von N Zeilen bestätigen**. Schon ein Problem in einer einzigen Zeile verhindert das Schreiben vollständig, damit ein Import nie auf halbem Weg stehen bleibt.

Regeln:

- Die Dateikodierung kann UTF-8 oder GBK sein (beim Speichern einer CSV mit chinesischem Inhalt ist GBK der Standard) und wird automatisch erkannt.
- Eine Datei enthält höchstens 1000 Benutzer oder 5000 Abteilungen und ist höchstens 2 MB groß (konfigurierbar).
- Abteilungen werden beim Import anhand der Überordnung automatisch sortiert; eine übergeordnete Abteilung darf in der Datei unterhalb ihrer Unterabteilungen stehen; ein Zyklus innerhalb der Datei wird gemeldet.
- Das Anfangspasswort eines Benutzers muss die Passwortrichtlinie erfüllen, und importierte Benutzer müssen ihr Passwort bei der ersten Anmeldung ändern.
- Importiert werden kann nur in Abteilungen und Stellen, die im Rahmen deiner Datenberechtigungen sichtbar sind.

## Export

Exportiere die Benutzer der aktuellen Filterung oder alle Abteilungen; der Dateiname enthält das Datum, zum Beispiel `users-2026-10-05.csv`. Der Export beachtet Datenberechtigungen und Feldberechtigungen genauso: nicht sichtbare Zeilen werden nicht exportiert, eingeschränkte Felder werden nach ihren Regeln verborgen oder maskiert. Zellen, die mit `=`, `+`, `-` oder `@` beginnen, wird ein Präfix vorangestellt, damit Tabellenprogramme sie nicht als Formel ausführen.
