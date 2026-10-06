---
title: Start in fünf Minuten
description: Starte GrantForge mit dem Release-Paket oder mit Docker, führe die Initialisierung durch, lege einen Benutzer an und vergib die erste Rolle.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Dieser Artikel startet GrantForge auf dem eigenen Rechner mit der standardmäßig eingebetteten H2-Datenbank. Für Produktivumgebungen siehe [Release-Paket installieren](/de/deploy/installation/) und [Datenbanken](/de/deploy/databases/).

## 1. Dienst starten

Du benötigst Java 17 oder höher. Lade das Release-Paket von [GitHub Releases](https://github.com/devlive-community/grantforge/releases) herunter oder baue es im Quellverzeichnis mit `./mvnw -DskipTests package` selbst (das Ergebnis liegt unter `dist/grantforge-release.tar.gz`), entpacke es und starte es:

```bash
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh
```

Du kannst auch Docker verwenden: baue zuerst ein Image aus dem Release-Paket und starte es dann mit dem Compose-Beispiel:

```bash
docker build -f deploy/docker/Dockerfile -t grantforge dist
docker compose -f deploy/compose/h2.yml up -d
```

Der Dienst hört standardmäßig auf Port `9999`. Beim ersten Start werden die Datenbanktabellen angelegt, und in der Logdatei wird ein einmaliges **Initialisierungs-Token** ausgegeben:

```text
WARN  ... First-run setup is pending. Open the console and enter this setup token: 7rV2...
```

Die Logdatei des Release-Pakets liegt unter `logs/grantforge.log`; bei Docker siehst du die Ausgabe mit `docker compose logs`.

## 2. Initialisierung abschließen

Öffne im Browser http://127.0.0.1:9999/; die Konsole wechselt automatisch zur Initialisierungsseite. Trage das Token aus der Logdatei, den Namen der Organisation sowie Benutzernamen und Passwort des ersten Administrators ein (mindestens 12 Zeichen).

![Erstinitialisierung und Anmeldeseite](/screenshots/login.png)

> [!TIP]
> Bei einer automatisierten Installation kannst du das Token vorab über die Umgebungsvariable `GRANTFORGE_SETUP_TOKEN` festlegen, siehe [Konfigurationsreferenz](/de/reference/configuration/).

Nach Abschluss der Initialisierung hält dieser Administrator gleichzeitig die beiden Systemrollen **Mandantenadministrator** und **Plattformadministrator** und kann alle Funktionen der Konsole nutzen. Die Initialisierungsseite wird danach dauerhaft geschlossen.

## 3. Benutzer anlegen

Gehe zu **Zugriffskontrolle → Benutzerverwaltung**, klicke auf „Benutzer anlegen“ und trage Benutzername, Anfangspasswort und Hauptabteilung ein. Ein neuer Benutzer muss sein Passwort bei der ersten Anmeldung ändern.

## 4. Rolle anlegen und berechtigen

1. Gehe zu **Zugriffskontrolle → Rollenverwaltung**, klicke auf „Neue Rolle“, zum Beispiel „Auditor mit Nur-Lese-Zugriff“.
2. Klicke in der Zeile der Rolle auf „Berechtigen“ und aktiviere im Berechtigungsraster die Seite „Audit-Protokoll“. Das Raster übernimmt automatisch die APIs, die diese Seite benötigt.
3. Klicke auf „Zuweisen“ und weise die Rolle dem soeben angelegten Benutzer zu.

![Rollenverwaltung](/screenshots/roles.png)

## 5. Wirkung prüfen

Melde dich mit dem neuen Benutzer an: Im linken Menü erscheint nur „Audit-Protokoll“. Wechsle zurück zum Administratorkonto und klicke in der Zeile des Benutzers auf das Symbol „Effektive Berechtigungen ansehen“; dort siehst du, woher jede einzelne Berechtigung stammt.

## Nächste Schritte

- Mache dich mit den [Grundbegriffen](/de/start/concepts/) vertraut.
- Lerne die Menüs anhand des [Rundgangs durch die Konsole](/de/guide/console/) kennen.
- Binde deine Anwendung [an GrantForge an](/de/integration/overview/).
