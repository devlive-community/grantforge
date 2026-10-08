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

Il supporto dei sistemi esterni comprende il framework dei plug-in, l’editor delle policy, la distribuzione firmata, l’audit degli accessi, il tipo di servizio HDFS e agenti NameNode numerati per Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 e 3.5. Il plug-in Hive è ancora in sviluppo.

## Versioni di destinazione degli agenti HDFS

| Base Hadoop | Java del container | Directory dell’agente |
| --- | --- | --- |
| 2.7.7 | Java 8 | `agents/hdfs/2.7/` |
| 2.10.2 | Java 8 | `agents/hdfs/2.10/` |
| 3.2.4 | Java 8 | `agents/hdfs/3.2/` |
| 3.3.6 | Java 8 (immagine amd64) | `agents/hdfs/3.3/` |
| 3.4.3 | Java 11 | `agents/hdfs/3.4/` |
| 3.5.0 | Java 17 | `agents/hdfs/3.5/` |

Scegli `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar` in `agents/hdfs/<line>/` in base alla linea Hadoop del cluster. Il codice di enforcement condiviso è compilato per Java 8 e l’adattatore 3.5 per Java 17.

Hadoop 2.7, 2.10, 3.2 e 3.3 non offrono la callback di autorizzazione per superutente usata dall’agente. Hadoop mantiene il controllo di quegli accessi da superutente; usa utenti ordinari per l’accesso ai dati governato da GrantForge.

## Come funziona: due piani

- **Piano di gestione**: il server di GrantForge (Spring Boot 4.1, bytecode Java 17) e la console Vue 3 possiedono tenant, account, organizzazione, ruoli, concessioni, audit, servizi di dati e policy.
- **Piano dei dati**: agenti inseriti nel sistema da proteggere. Un agente scarica con il proprio token snapshot di policy firmate con Ed25519 e le conserva in cache locale, decide ogni accesso prima che avvenga (negandolo quando nessuna policy è disponibile) e rimanda al server gli eventi di accesso per l’audit.

Il tuo sistema non deve seguire il modello di HDFS. Le applicazioni ordinarie valutano i permessi nel proprio processo tramite l’API aperta o lo Spring Boot starter; solo i sistemi che devono intercettare l’accesso dentro un database, un file system o un archivio simile hanno bisogno di un agente scritto contro `core/grantforge-agent-core`.

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

## Struttura del progetto

Coordinata Maven radice: `org.devlive.grantforge:grantforge:2026.0.0`. Prefisso dei package Java: `org.devlive.grantforge`. Classe principale: `org.devlive.grantforge.server.GrantForge`.

`core/` contiene il server e l’infrastruttura condivisa, `plugins/` contiene i plug-in dei tipi di servizio caricati dal server, e `agents/` contiene gli agenti distribuiti dentro i sistemi che proteggono. La libreria condivisa `grantforge-agent-core` resta in `core/`; l’agente NameNode di HDFS è in `agents/grantforge-agent-hdfs-*`.

| Modulo | Responsabilità |
| --- | --- |
| `core/grantforge-server` | Punto di ingresso di Spring Boot: API REST, configurazione di sicurezza, API aperta, e ospita la console web |
| `core/grantforge-web` | Console in Vue 3 / TypeScript / Tailwind CSS |
| `core/grantforge-common` | Codici di errore e problem details, CSV, annotazioni di accesso sugli endpoint |
| `core/grantforge-persistence` | Entità, filtro per tenant, TSID, Liquibase, SPI dei permessi sui dati e sui campi |
| `core/grantforge-audit` | Registrazione, interrogazione, conservazione e archiviazione degli eventi di audit |
| `core/grantforge-identity` | Tenant, account, reparti, gruppi, posizioni, accesso e sessioni, autenticazione a due fattori, sorgenti di identità |
| `core/grantforge-authz` | Catalogo delle risorse, ruoli, permessi, assegnazioni e valutazione, policy sui dati e sui campi, separazione dei compiti, richieste e revisioni |
| `core/grantforge-plugin-api` / `core/grantforge-plugin-host` | Contratto dei plug-in per tipi di servizio, oltre a caricamento, isolamento e invocazione dei plug-in |
| `core/grantforge-policy-engine` | Motore di valutazione delle policy per sistemi esterni (API Java 8, incorporabile negli agenti) |
| `core/grantforge-agent-core` | Codice condiviso degli agenti: impostazioni, snapshot firmate, decisioni di accesso, invio dell’audit |
| `core/grantforge-service` | Servizi di dati, firma e distribuzione degli snapshot di policy, agenti e audit degli accessi |
| `core/grantforge-oauth` | Server OAuth 2.1 / OIDC costruito su Spring Authorization Server |
| `plugins/grantforge-plugin-hdfs` | Plug-in del tipo di servizio HDFS: gestione delle policy e consultazione delle risorse |
| `plugins/grantforge-plugin-example` | Plug-in di esempio per un tipo di servizio personalizzato |
| `agents/grantforge-agent-hdfs-common` | Enforcement HDFS condiviso, configurazione, snapshot e audit (Java 8) |
| `agents/grantforge-agent-hdfs-native` | Modulo Maven di produzione per gli adattatori HDFS nativi condivisi (base Java 8 / Hadoop 2.7.7) |
| `agents/grantforge-agent-hdfs-*` | Agenti NameNode numerati per Hadoop 2.7, 2.10, 3.2, 3.3, 3.4 e 3.5: autorizzazione e audit degli accessi |
| `sdk/grantforge-spring-boot-starter`, `sdk/grantforge-js` | SDK Java e JavaScript per integrare le applicazioni |
| `script/ci`, `deploy/` | Script di verifica della CI (gli stessi in locale e in CI) e risorse di distribuzione (Dockerfile, Compose, Helm) |

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
