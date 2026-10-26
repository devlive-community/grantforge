<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="Logo di GrantForge" />

# GrantForge

Piattaforma unificata di permessi · utenti, ruoli, menu, API, righe e campi di dati · sistemi esterni di dati

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [한국어](README.ko.md) · [日本語](README.ja.md) · [Deutsch](README.de.md) · [Français](README.fr.md) · [Español](README.es.md) · [Português](README.pt-BR.md) · Italiano

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.1.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

GrantForge (in precedenza AuthX) è una piattaforma unificata di permessi open source (MIT). Risponde in un unico posto a due domande: **chi può fare cosa** (permessi funzionali) e **chi può vedere quali dati** (permessi sui dati e sui campi). I permessi si definiscono, si spiegano e si controllano nella console, le applicazioni di business si integrano tramite protocolli standard, e i sistemi esterni di dati (HDFS, per esempio) rientrano nello stesso modello di policy tramite plug-in e agenti.

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="Console di GrantForge" />
</p>

## Funzionalità

| Ambito | Cosa fa |
| --- | --- |
| Identità e organizzazione | Multi-tenant, albero dei reparti, gruppi e posizioni; importazione ed esportazione in massa in CSV; accesso e sincronizzazione con LDAP / Active Directory, federazione OIDC |
| Sicurezza degli account | Gestione delle sessioni e disconnessione forzata, politica delle password con blocco per tentativi, autenticazione a due fattori TOTP con codici di recupero, seconda verifica per le operazioni sensibili |
| Permessi funzionali | Catalogo delle risorse (moduli, menu, pagine, schede, pulsanti, API), ereditarietà dei ruoli, matrice dei permessi, analisi dell’impatto prima di concedere |
| Permessi sui dati | Righe visibili limitate da condizioni (se stessi, il proprio reparto e i suoi discendenti, reparti indicati, condizioni personalizzate), con lettura e scrittura controllate separatamente |
| Permessi sui campi | Un campo può essere nascosto, mascherato (e-mail, numero di telefono, numero di documento) o reso di sola lettura |
| Spiegabilità e audit | Spiegazione dei permessi (da dove nasce ciascuno), simulazione di una concessione, interrogazione ed esportazione del registro di audit |
| Governanza | Vincoli di separazione dei compiti (SOD), richiesta di accesso con approvazione, revisione periodica degli accessi |
| Integrazione delle applicazioni | Server di autorizzazione OAuth 2.1 / OIDC, API aperta di consultazione dei permessi, SDK Java (Spring Boot starter) e JavaScript |
| Sistemi esterni | Tipi di servizio tramite plug-in e motore delle policy: servizi di dati, policy di accesso, agenti e audit degli accessi |
| Distribuzione | Un’unica versione pubblicata eseguibile, immagine Docker, esempi Compose, chart Helm; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

Il supporto dei sistemi esterni comprende il framework dei plug-in, l’editor delle policy, la distribuzione firmata, l’audit degli accessi e i plug-in di servizio per HDFS, ZooKeeper, MySQL, Hive, Kafka, HBase, Trino, YARN, Pulsar, Solr e ClickHouse. HDFS usa agenti NameNode numerati, ZooKeeper un agente integrato nel server, MySQL un proxy di rete autonomo, Hive un autorizzatore dentro HiveServer2, Kafka un autorizzatore dentro broker e controller, HBase un coprocessore dentro master e region server, Trino un controllo degli accessi di sistema dentro il coordinator, YARN un provider di autorizzazione dentro i ResourceManager, Pulsar un provider di autorizzazione dentro i broker, Solr un plug-in di autorizzazione dentro i nodi e ClickHouse un servizio accanto ai server che allinea i ruoli e le row policy di ClickHouse stesso. Connessione, ricerca di database, tabelle e colonne e funzionalità delle policy sono descritte nella [guida del plug-in MySQL](https://grantforge.devlive.org/it/plugins/mysql/); distribuzione e limiti di applicazione nella [guida dell’agente proxy MySQL](https://grantforge.devlive.org/it/external/mysql-agent/). Hive è descritto nella [guida del plug-in Hive](https://grantforge.devlive.org/it/plugins/hive/) e nella [guida dell’agente Hive](https://grantforge.devlive.org/it/external/hive-agent/). Kafka è descritto nella [guida del plug-in Kafka](https://grantforge.devlive.org/it/plugins/kafka/) e nella [guida dell’agente Kafka](https://grantforge.devlive.org/it/external/kafka-agent/). HBase è descritto nella [guida del plug-in HBase](https://grantforge.devlive.org/it/plugins/hbase/) e nella [guida dell’agente HBase](https://grantforge.devlive.org/it/external/hbase-agent/). Trino è descritto nella [guida del plug-in Trino](https://grantforge.devlive.org/it/plugins/trino/) e nella [guida dell’agente Trino](https://grantforge.devlive.org/it/external/trino-agent/). YARN è descritto nella [guida del plug-in YARN](https://grantforge.devlive.org/it/plugins/yarn/) e nella [guida dell’agente YARN](https://grantforge.devlive.org/it/external/yarn-agent/). Pulsar è descritto nella [guida del plug-in Pulsar](https://grantforge.devlive.org/it/plugins/pulsar/) e nella [guida dell’agente Pulsar](https://grantforge.devlive.org/it/external/pulsar-agent/). Solr è descritto nella [guida del plug-in Solr](https://grantforge.devlive.org/it/plugins/solr/) e nella [guida dell’agente Solr](https://grantforge.devlive.org/it/external/solr-agent/). ClickHouse è descritto nella [guida del plug-in ClickHouse](https://grantforge.devlive.org/it/plugins/clickhouse/) e nella [guida dell’agente ClickHouse](https://grantforge.devlive.org/it/external/clickhouse-agent/).


## Componenti e versioni supportate

Le versioni seguenti sono quelle eseguite dai test di integrazione Testcontainers del repository. Altre versioni non sono certificate.

| Componente | Plugin di servizio | Versioni verificate | Agente di applicazione | Versioni dell'agente | Java dell'agente |
| --- | --- | --- | --- | --- | --- |
| Apache Hadoop HDFS | `plugins/grantforge-plugin-hdfs` | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 | Agente NameNode | 2.7, 2.10, 3.2, 3.3, 3.4, 3.5 | 8 (3.5: 17) |
| Apache Hadoop YARN | `plugins/grantforge-plugin-yarn` | 3.2.4, 3.3.6, 3.4.3, 3.5.0 | Provider di autorizzazione del ResourceManager | Testato sulle stesse linee Hadoop | 8 |
| Apache Hive | `plugins/grantforge-plugin-hive` | HiveServer2 3.1.3, 4.0.1, 4.1.0 | Autorizzatore di HiveServer2 | Testato sulle stesse linee HiveServer2 | 8 |
| Apache Kafka | `plugins/grantforge-plugin-kafka` | 3.7.2, 3.9.2, 4.1.2, 4.3.1 | Autorizzatore di broker e controller | Testato sulle stesse linee Kafka | 8 |
| Apache Pulsar | `plugins/grantforge-plugin-pulsar` | 3.0.17, 3.3.9, 4.0.14, 4.2.5 | Provider di autorizzazione dei broker | Testato sulle stesse linee Pulsar | 17 |
| Apache Solr | `plugins/grantforge-plugin-solr` | 9.8.1, 9.10.1, 9.11.0, 10.0.0 | Plug-in di autorizzazione dei nodi | Testato sulle stesse linee Solr | 11 |
| ClickHouse | `plugins/grantforge-plugin-clickhouse` | 24.8, 25.3, 25.8, 26.3, 26.8 | Servizio che allinea ruoli e row policy | Testato sulle stesse linee ClickHouse | 17 |
| Apache HBase | `plugins/grantforge-plugin-hbase` | 2.5.16, 2.6.7, 3.0.0 | Coprocessore di master e region server | Testato sulle stesse linee HBase | 8 |
| Trino | `plugins/grantforge-plugin-trino` | 470, 476, 479, 484 | Controllo di accesso di sistema | 470 (da 470 a 478), 479 (479 e successive) | 23 / 25 |
| MySQL | `plugins/grantforge-plugin-mysql` | 8.0, 8.4 (MySQL 5.7 e server MariaDB non sono supportati) | Proxy di rete autonomo | JAR proxy singolo | 17 |
| Apache ZooKeeper | `plugins/grantforge-plugin-zookeeper` | Non elencato nei test del repository | Agente interno al server | 3.6.4 (destinazione di build) | 8 |

La colonna **Java dell'agente** indica il target di bytecode di ciascun agente. Gli agenti HDFS si scelgono in base alla linea Hadoop (ad esempio `agents/hdfs/2.10/` per Hadoop 2.10.2); vedi la [guida dell'agente HDFS](https://grantforge.devlive.org/it/external/hdfs-agent/). Hadoop 2.7, 2.10, 3.2 e 3.3 non forniscono il callback di autorizzazione del superutente, quindi su quelle linee l'accesso del superutente resta controllato da Hadoop.

## Come funziona: due piani

- **Piano di gestione**: il server di GrantForge (Spring Boot 4.1, bytecode Java 17) e la console Vue 3 possiedono tenant, account, organizzazione, ruoli, concessioni, audit, servizi di dati e policy.
- **Piano dei dati**: agenti inseriti nel sistema da proteggere o distribuiti come proxy di rete autonomi. Scaricano con i propri token snapshot di policy firmate con Ed25519, li conservano nella cache locale, autorizzano l’accesso alle risorse e inviano eventi di audit. Se il server è temporaneamente irraggiungibile, mantengono lo snapshot già applicato; ogni guida dell’agente descrive l’ambito dei controlli.

Il tuo sistema non deve seguire il modello di HDFS. Le applicazioni ordinarie valutano i permessi nel proprio processo tramite l’API aperta o lo Spring Boot starter; solo i sistemi che devono intercettare l’accesso dentro un database, un file system o un archivio simile, oppure lungo il relativo percorso di accesso di rete hanno bisogno di un agente scritto contro `core/grantforge-agent-core`.

## Integrare la tua applicazione

- **OAuth 2.1 / OpenID Connect**: GrantForge è esso stesso un server di autorizzazione, quindi le applicazioni vi fanno accedere i propri utenti; anche le sorgenti di identità esistenti (LDAP / AD / OIDC) possono essere collegate.
- **Applicazioni Java**: `sdk/grantforge-spring-boot-starter` aggiunge `@RequirePermission` sugli endpoint, `@GrantForgeEntity` sulle entità di dati e `GrantForgeDataScopes.scope(...)` per tradurre i permessi sui dati della piattaforma in `Specification` JPA.
- **Applicazioni front-end**: `@grantforge/client` fa accedere l’utente con OIDC + PKCE dalla tua stessa origine e ne consulta i permessi.
- **API aperta**: `/api/v1/open/me/authorization`, `/api/v1/open/me/data-access`, `/api/v1/open/catalog/data-entities`.
- **Esempi eseguibili**: `samples/shop` e `samples/notes` si integrano nel modo in cui lo farebbe un terzo.

## Avvio rapido

Serve Java 17 o una versione successiva. Il servizio resta in ascolto sulla porta `9999` e al primo avvio stampa un **token di inizializzazione** valido una sola volta; apri <http://127.0.0.1:9999/> nel browser, inserisci il token e crea il primo amministratore.

```bash
# Dalla versione pubblicata (o compilala dai sorgenti con ./mvnw clean package, output in dist/)
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# Oppure con Docker
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# Oppure con Compose contro un database
docker compose -f deploy/compose/postgres.yml up -d

# Oppure su Kubernetes
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

Il database a file H2 incorporato è quello predefinito, quindi non serve alcuna configurazione per avviare. Il driver MySQL non è distribuito con la versione pubblicata per la sua licenza GPL; inseriscilo tu in `drivers/`. Installazione, configurazione al primo avvio e prima concessione di permessi sono descritti nella [documentazione](https://grantforge.devlive.org).

## Database

Quello predefinito è il database a file H2 incorporato (`${GRANTFORGE_HOME}/data`), quindi non serve alcuna configurazione per avviare. In produzione il cambio avviene tramite variabili d’ambiente e lo schema delle tabelle è gestito da Liquibase:

| Database | Versioni (verificate in CI) | Esempio di `GRANTFORGE_DB_URL` |
| --- | --- | --- |
| PostgreSQL | 14, 17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0, 8.4 | `jdbc:mysql://host:3306/grantforge` (inserisci tu `mysql-connector-j` in `lib/`; la sua licenza GPL lo esclude dalla versione pubblicata) |
| MariaDB | 10.11, 11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

Imposta anche `GRANTFORGE_DB_USER` e `GRANTFORGE_DB_PASSWORD`; in un’installazione in cluster ogni istanza deve ricevere il proprio `GRANTFORGE_ID_NODE` (0-1023).


## Gestione e osservabilità

- Sonde di salute: `/actuator/health/liveness`, `/actuator/health/readiness` (solo lo stato, senza dettagli; la sonda di prontezza restituisce 200 quando il database è raggiungibile e le migrazioni sono state eseguite).
- Metriche: `/actuator/prometheus` (con etichetta `application="grantforge"`, richiedono l’accesso per impostazione predefinita; `GRANTFORGE_PROMETHEUS_PUBLIC=true` le apre alle reti fidate).
- Log: per impostazione predefinita testo leggibile, con un ID di richiesta per riga; imposta `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (oppure `logstash`) per ottenere log in JSON.
- Script della versione pubblicata: `bin/startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` e `import-legacy.sh`.

## Sviluppo e verifica

La compilazione richiede JDK 17 o successivo. Il server e l’adattatore Hadoop 3.5 puntano a Java 17; il motore delle policy, il nucleo dell’agente e gli adattatori Hadoop 2.7–3.4 puntano a Java 8. Error Prone + NullAway si attivano da JDK 21. Il front-end usa Vue 3.5, Tailwind CSS 4, Node.js 22.12+ e pnpm 8.10.2.

```sh
# Compilazione Java e test unitari (saltando la compilazione della console)
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# Test di integrazione della persistenza su un database dato (serve Docker, tranne che per h2)
bash script/ci/db_integration.sh postgres:17

# Confezionamento della versione pubblicata (compresa la compilazione della console), output in dist/
./mvnw clean package

# Sviluppo e verifiche del front-end
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# Applicazioni di esempio e SDK
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# Contratto delle API: rigenera openapi.json e i tipi del front-end dopo una modifica al server (la CI controlla entrambi)
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# Sito di documentazione (docs/, Next.js + Tailwind CSS)
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # rigenera le schermate con un servizio reale e dati di esempio

# Verifiche del repository (le stesse che esegue la CI)
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## Collegamenti

- [Repository](https://github.com/devlive-community/grantforge)
- [Documentazione](https://grantforge.devlive.org): avvio rapido, guida all’uso, integrazione e riferimenti tecnici, con i sorgenti in [`docs/`](docs/)
- [Come contribuire](CONTRIBUTING.md) · [Codice di condotta](CODE_OF_CONDUCT.md) · [Registro delle modifiche](CHANGELOG)
