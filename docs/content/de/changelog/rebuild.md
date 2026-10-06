---
title: 2026.0.0 (Neuaufbau)
description: "GrantForge von Grund auf neu geschrieben: mehrmandantenfähige Identität, Ressourcen und Rollenberechtigungen, Daten- und Feldberechtigungen, Anbindung über Standardprotokolle, Governance für Unternehmen und Berechtigungen für externe Systeme über Plug-ins."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

2026.0.0 ist eine vollständige Neufassung und ersetzt 1.x (AuthX). Sie ist kein Admin-Template mehr, sondern eine eigenständig bereitgestellte Plattform für Identität und Berechtigungen. Konten, Rollen und Menüs aus 1.x können importiert werden, siehe [Upgrade und Migration von Altversionen](/de/deploy/upgrade/).

## Plattform

- Spring Boot 4, Java-17-Laufzeit; ein Release-Paket enthält Server und Konsole, zusätzlich gibt es Docker-Images, Compose und ein Helm Chart.
- Unterstützt H2, PostgreSQL, MySQL, MariaDB, Oracle und SQL Server; Migrationen werden von Liquibase verwaltet, jeder Commit wird auf neun Datenbankversionen getestet.
- Ein Initialisierungs-Assistent beim ersten Start erstellt mit einem Einmal-Token aus dem Server-Log den Plattform-Administrator.
- Cluster-Bereitstellung: Sitzungen werden in der Datenbank gespeichert, IDs sind zeitlich geordnete TSIDs.
- Eine neue Konsole: Vue 3 und Tailwind CSS, helles und dunkles Thema, Chinesisch und Englisch.

## Identität und Organisation

- Mehrmandantenfähigkeit: Konten, Organisation und Berechtigungen jedes Mandanten sind vollständig voneinander getrennt.
- Benutzer, Abteilungsbaum, Gruppen, Stellen, CSV-Import und -Export in großen Mengen.
- Argon2id-Passwörter, konfigurierbare Passwortrichtlinie und Sperrung, Sitzungsverwaltung, TOTP-Zwei-Faktor-Authentifizierung mit Wiederherstellungscodes sowie erneute Bestätigung sensibler Vorgänge.
- Identitätsquellen für LDAP / Active Directory und OIDC, mit Synchronisierung und föderierter Anmeldung.

## Berechtigungen

- Ressourcenkatalog: Module, Menüs, Seiten, Labels, Schaltflächen, APIs, Datenentitäten und Felder sowie deren Abhängigkeiten. Die Seiten, Schaltflächen und APIs der Konsole selbst stehen ebenfalls im Katalog und werden genauso mit Berechtigungen versehen.
- Rollen und Berechtigungen: Erlauben und Verweigern, Vererbung, Zuweisung nach Benutzer / Gruppe / Abteilung / Stelle, Gültigkeitsdauer.
- Datenberechtigungen: sichtbare Zeilen nach Organisationsbereich oder strukturierter Bedingung einschränken.
- Feldberechtigungen: Felder je Rolle verbergen, maskieren oder schreibgeschützt machen.
- Berechtigungserklärung, Simulation pro Benutzer, vollständiges Audit-Protokoll sowie eine Prüfung auf ungültige Konfiguration.

## Governance

- Funktionstrennung: Sich ausschließende Rollen werden bei Zuweisung, Vererbung und Antrag abgelehnt; bereits bestehende Konflikte werden aufgedeckt.
- Berechtigungsanträge: Benutzer beantragen die beantragbaren Rollen; nach der Freigabe durch den Genehmiger gelten sie befristet und laufen zum Ablaufdatum automatisch ab.
- Regelmäßige Berechtigungsprüfung.

## Anbindung von Anwendungen

- Integrierter OAuth-2.1- / OpenID-Connect-Autorisierungsserver: Autorisierungscode mit PKCE, Client-Anmeldedaten, Rotation von Refresh-Tokens und Signaturschlüsseln.
- Offene API für Berechtigungsabfragen, mit Versionierung und ETag.
- Spring Boot Starter und JavaScript SDK, mit lauffähigen Beispielanwendungen.

## Berechtigungen für externe Systeme

- Diensttypen über Plug-ins: Plug-ins definieren Ressourcenhierarchie, Zugriffsarten, Maskierung und Zeilenfilter; jedes Plug-in wird unabhängig geladen.
- Allgemeiner Richtlinien-Editor, mit Ed25519 signierte Richtlinien-Snapshots, Agent-Heartbeats und Zugriffs-Audit.
- Beispiel-Plug-in, Diensttyp HDFS und Hadoop-3.5.0-NameNode-Agent; das Hive-Plug-in und Agenten für andere Hadoop-Versionen sind in Entwicklung.

## Qualität

- Leistungs-Benchmarks im Umfang von einer Million Konten laufen jede Nacht, bei Überschreiten einer Obergrenze schlagen sie fehl.
- Statische Analyse (NullAway, Error Prone, Checkstyle, PMD, SpotBugs, ArchUnit), Abdeckungsschwellen und Full-Stack-Browsertests.
- Die Dokumentationsseite wurde mit Next.js und Tailwind CSS neu aufgebaut.
