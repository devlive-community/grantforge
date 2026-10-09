---
title: Plug-ins und Diensttypen
description: "Mit Plug-ins für Diensttypen die Richtlinienverwaltung von GrantForge auf externe Datensysteme ausweiten: Vertrag, Paketierung, Isolation und Verbreitung."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Ein Plug-in für einen Diensttyp beschreibt ein externes System: welche Ressourcenebenen es hat, welche Zugriffsarten es kennt, ob Maskierung und Zeilenfilter möglich sind und welche Konfiguration eine Verbindung braucht. GrantForge stellt für solche Systeme daraufhin Datendienste, einen allgemeinen Richtlinien-Editor, Richtlinien-Snapshots und Zugriffs-Audit bereit (siehe [Datendienste und Richtlinien](/de/external/data-services/)).

## Abhängigkeiten

Ein Plug-in hängt nur von `grantforge-plugin-api` ab (das wiederum nur vom JDK und von JSpecify) und wird mit dem Scope `provided` eingebunden:

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## ServiceTypeProvider implementieren

```java
public final class ExampleProvider implements ServiceTypeProvider
{
    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder("example").label("Example warehouse")
                .resources(ResourceDefinition.builder("database").label("Database").lookupSupported(true).validLeaf(true)
                                .excludesSupported(false).build(),
                        ResourceDefinition.builder("table").label("Table").parent("database").lookupSupported(true).validLeaf(true).build(),
                        ResourceDefinition.builder("column").label("Column").parent("table").accessTypes("select").build(),
                        ResourceDefinition.builder("path").label("Path").matcher(MatcherType.PATH).recursiveSupported(true).build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("all", "All", "select", "update"))
                .dataMask(new DataMaskDefinition(Set.of("column"), List.of(new MaskTypeDefinition("redact", "Redact", "redact({col})"))))
                .rowFilter(new RowFilterDefinition(Set.of("table")))
                .conditions(ConditionDefinition.of("ip-range", "Client addresses", "ip-range"))
                .configFields(ConfigField.builder("url").label("Address").type(ConfigFieldType.STRING).mandatory().pattern("example://.+").build(),
                        ConfigField.builder("timeout").label("Timeout (seconds)").type(ConfigFieldType.INTEGER).defaultValue("30").build(),
                        ConfigField.builder("password").label("Password").type(ConfigFieldType.SECRET).mandatory().build())
                .build();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        return "example".equals(config.get("password")) ? ConnectionResult.succeeded()
                : ConnectionResult.failed("the example warehouse refused the password");
    }

    @Override
    public List<String> lookup(LookupRequest request)
    {
        // Gibt Kandidatenwerte unter der Ebene request.resource() zurück, die mit request.userInput() beginnen, höchstens request.limit() Stück
        return List.of();
    }
}
```

Das oben stehende Beispiel stammt aus dem Beispiel-Plug-in `plugins/grantforge-plugin-example` und kann direkt als Vorlage kopiert werden.

Die Definition wird beim Konstruieren in einem Durchgang geprüft und meldet alle Probleme auf einmal: unbekannte oder zyklische übergeordnete Ebenen, doppelte Namen, Verweise auf nicht deklarierte Zugriffsarten oder Ressourcen und Weiteres. Namen müssen auf `[a-z][a-z0-9_-]{0,63}` passen.

| Bestandteil | Beschreibung |
| --- | --- |
| Ressourcen | Hierarchie, Vergleichsart (exakt, Platzhalter, Pfad, Regex), Groß-/Kleinschreibung, ob Pflicht, ob Ausschlüsse und Rekursion unterstützt werden (nur Pfad), ob Suche unterstützt wird, ob sie Blatt sein kann |
| Zugriffsarten | Name, Anzeigename, die weiteren implizierten Zugriffsarten (zum Beispiel impliziert `all` auch `select`), optional auf Ressourcen begrenzt |
| Maskierung, Zeilenfilter | Welche Ressourcen sie unterstützen und welche Maskierungsarten es gibt; die Durchsetzung erfolgt im Zielsystem |
| Bedingungen | Bedingungen, die eine Richtlinie anfügen kann (zum Beispiel ein IP-Bereich), ausgewertet vom Condition-SPI der Richtlinien-Engine |
| Konfigurationsfelder | Zeichenkette, Langtext, Ganzzahl, Boolescher Wert, Geheimnis, Auswahlliste; Geheimnisfelder werden verschlüsselt gespeichert und dürfen keinen Standardwert haben |

Ein Provider braucht einen öffentlichen parameterlosen Konstruktor und muss thread-sicher sein. `validateConfig`, `testConnection` und `lookup` haben Standardimplementierungen und werden bei Bedarf überschrieben.

### Wenn eine Suche fehlschlägt

Schlägt `lookup` fehl, werfen Sie eine `LookupException` mit einem Grund: Die Konsole zeigt den Grund an und lässt den Benutzer erneut suchen, statt keine Werte anzuzeigen. Halten Sie die Meldung einzeilig und ohne Geheimnisse; der Server entfernt außerdem die geheimen Einstellungen des Dienstes daraus.

- `NOT_FOUND`: der zu durchsuchende Ort existiert nicht, etwa das konfigurierte Suchverzeichnis
- `ACCESS_DENIED`: das Zielsystem hat den Suchbenutzer abgewiesen
- `UNREACHABLE`: das Zielsystem ist nicht erreichbar
- `AUTHENTICATION_FAILED`: die Anmeldung am Zielsystem ist fehlgeschlagen
- `LIMIT_EXCEEDED`: es gibt zu viele Werte; die Suche muss eingegrenzt werden
- `INVALID_INPUT`: die Eingabe kann nicht gesucht werden, etwa ein Pfad außerhalb des erlaubten Verzeichnisses
- `FAILED`: jeder andere Fehler

Jede andere Ausnahme eines Plug-ins wird als `FAILED` behandelt, daher müssen Plug-ins für API 1.0 nicht geändert werden. `LookupException` gibt es seit API 1.1.0.

## Deskriptor und Paketierung

Lege `grantforge-plugin.yaml` in das Wurzelverzeichnis des Plug-ins:

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.1"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

Ein Plug-in kann sein:

- eine jar mit dem Deskriptor im Wurzelverzeichnis der jar;
- ein Verzeichnis oder ein zip: `grantforge-plugin.yaml`, `classes/` und `lib/*.jar`.

Lege es in `grantforge.plugins.directory` (Standard `plugins`) und klicke in der Konsole auf der Seite „Plug-ins“ auf „Neu einlesen“; ein Neustart ist nicht nötig.

Hat ein Plug-in Abhängigkeiten, baue es wie `plugins/grantforge-plugin-hdfs` mit assembly zu einem zip der Klassifikation `plugin` (Deskriptor auf oberster Ebene, `classes/`, `lib/`) und kopiere die Laufzeit-Abhängigkeiten in der Phase `generate-resources` nach `target/plugin-lib`:

```xml
<plugin>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions>
    <execution>
      <id>plugin-lib</id>
      <phase>generate-resources</phase>
      <goals><goal>copy-dependencies</goal></goals>
      <configuration>
        <includeScope>runtime</includeScope>
        <outputDirectory>${project.build.directory}/plugin-lib</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Beim Start aus dem Quellcode

Wird `org.devlive.grantforge.server.GrantForge` direkt in der IDE gestartet, stammen die Klassen des Servers aus den `target/classes` der Module. Ist dann weder `grantforge.plugins.directory` konfiguriert noch gibt es im Arbeitsverzeichnis ein `plugins`-Verzeichnis, wird das `plugins/`-Verzeichnis des Repositorys verwendet: Dort gebaute Plug-in-Module (ein Deskriptor in `target/classes` und ein von der Build erzeugtes `target/plugin-lib`) werden direkt als Plug-ins geladen, mit Klassen aus `target/classes` und Abhängigkeiten aus `target/plugin-lib`; Module, die kein `plugin-lib` erzeugen (wie das Beispiel-Plug-in für Tests), werden nicht geladen. Nach einer Änderung am Plug-in-Code lässt du die IDE neu kompilieren und liest die Plug-ins auf der Seite „Plug-ins“ der Konsole neu ein. Baue ein Plug-in-Modul vor der ersten Verwendung einmal mit Maven, um die Abhängigkeiten zu kopieren:

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## Kompatibilität

`apiVersion` gibt die Vertragsversion an, die ein Plug-in braucht. Der Host stellt derzeit `1.1.0` bereit; geladen wird ein Plug-in nur, wenn die Hauptversion übereinstimmt und die bereitgestellte Version nicht unter der angeforderten liegt, sonst wird es als „inkompatibel“ markiert. Jede Änderung am Vertrag erhöht die Version, und CI vergleicht sie mit japicmp gegen das vorherige Release (`script/ci/check_plugin_api_compat.py`); inkompatible Änderungen müssen die Hauptversion erhöhen.

GrantForge 2026.1.0 stellt die Plug-in-API 1.1.0 bereit. Ein Plug-in mit `apiVersion: "1.1"` benötigt einen Host ab 2026.1.0; ein Plug-in mit `1.0` läuft unverändert auf dem neuen Host. Produktversion und Plug-in-API-Version sind unabhängig: Die API-Version steigt nur, wenn sich der Vertrag ändert.

## Isolation

- Jedes Plug-in bekommt einen eigenen Klassenlader, dessen Vater der Plattform-Klassenlader ist; nur `org.devlive.grantforge.plugin.api.` und `org.jspecify.annotations.` werden an den Host delegiert. Ein Plug-in sieht weder Spring- noch Serverklassen und darf Abhängigkeiten in beliebigen Versionen mitbringen.
- Ein Lesefehler, eine inkompatible Version, ein Duplikat, ein Konstruktionsfehler oder ein Timeout markiert nur dieses Plug-in als fehlgeschlagen und hält den Grund fest; der Server läuft normal weiter.
- Jeder Aufruf in ein Plug-in hat ein Timeout (`grantforge.plugins.call-timeout`, standardmäßig 10 Sekunden).

## Agenten und Snapshots

Der Quellcode der Plug-ins für Diensttypen liegt in `plugins/` und wird vom GrantForge-Server geladen; der Quellcode der konkreten Agenten liegt in `agents/` und wird nach der Paketierung in den Zielsystemen bereitgestellt, zum Beispiel `agents/grantforge-agent-hdfs-*` auf den HDFS NameNode. Die gemeinsame Agenten-Infrastruktur liegt in `core/grantforge-agent-core`.

Agenten in Zielsystemen greifen mit einem Agent-Token auf `/api/v1/agent/**` zu:

| Schnittstelle | Zweck |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | Meldet den Zustand des Agenten und die aktuelle Snapshot-Version |
| `GET /api/v1/agent/policies` | Lädt den Richtlinien-Snapshot; gibt 304 zurück, wenn sich nichts geändert hat; die Antwort trägt eine Ed25519-Signatur |
| `GET /api/v1/agent/signing-key` | Der öffentliche Schlüssel zum Prüfen der Signatur |
| `POST /api/v1/agent/access-events` | Meldet Zugriffsereignisse stapelweise, die ins Zugriffs-Audit gehen |

Agenten werten mit `grantforge-policy-engine` (einer Java-8-API, die sich in ältere Systeme einbetten lässt) lokal aus und müssen GrantForge nicht bei jedem Zugriff aufrufen.

Agenten müssen diese Protokolle nicht selbst implementieren; `core/grantforge-agent-core` (Java 8) kapselt sie bereits:

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // Bedingungsauswerter, nach Namen

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- Sendet Heartbeats im vom Server verlangten Intervall; lädt bei einer Änderung der Richtlinienversion einen Snapshot herunter (304, wenn der ETag unverändert ist) und prüft ihn mit dem öffentlichen Ed25519-Schlüssel des Servers (der Schlüssel kann in den Einstellungen festgenagelt werden, sonst wird er beim ersten Kontakt vom Server geholt und behalten). Erst wenn die Prüfung gelingt, wird die lokale Kopie ersetzt und im Cache-Verzeichnis gespeichert; ist der Server beim Start nicht erreichbar, wird der letzte Snapshot weiterverwendet.
- Der Snapshot klappt Rollen und Gruppen bis zu den Benutzern auf, und `decide` ergänzt die Anfrage um die Rollen und Gruppen des Benutzers aus dem Snapshot. Ist der Server deaktiviert oder gibt es noch keinen Snapshot, ist das Ergebnis `NOT_DETERMINED`, und der Agent entscheidet, ob er auf die eigene Prüfung des Systems zurückfällt oder abweist.
- Zugriffsereignisse gehen in eine begrenzte Warteschlange (voll: verwerfen und zählen, blockiert aber nie das System) und werden in Stapeln gesendet; ist der Server nicht erreichbar, werden sie unter `audit-spool/` im Cache-Verzeichnis geschrieben und nach der Erholung nachgesendet, wobei über der Grenze die ältesten verworfen werden.
- Hängt von Jackson 2 und Bouncy Castle ab (Ed25519 gibt es vor JDK 15 nicht); Zielsysteme bringen andere Versionen dieser Bibliotheken mit, daher müssen sie beim Paketieren des Agenten mit shade umgelagert werden.

## Beispiel

`plugins/grantforge-plugin-example` ist ein vollständiges Plug-in: Typ `example` (database → table → column und path), die Zugriffsarten select, update und all, Maskierung von Spalten, Zeilenfilter für Tabellen, eine Bedingung für IP-Bereiche sowie die Konfiguration url, timeout und password (der Verbindungstest gelingt, wenn das Passwort `example` ist); es kann außerdem Beispiel-Datenbanken und -Tabellen nachschlagen. Der Fullstack-End-to-End-Test geht mit ihm den ganzen Weg: „Dienst hinzufügen → Richtlinie schreiben → Token ausstellen → Agent lädt → Zugriffs-Audit“.
