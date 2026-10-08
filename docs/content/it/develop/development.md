---
title: Sviluppo, test e CI
description: Compilazione locale, test, regole di codice e controlli di CI.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Ambiente

- JDK 21 (gli artefatti sono bytecode Java 17; il motore delle policy è Java 8)
- Node.js 22 e pnpm 8.10.2
- Docker (test di integrazione dei database, benchmark prestazionali e immagini)
- Python 3.12 (script di CI)

## Comandi più usati

```bash
./mvnw verify                          # compila e testa tutti i moduli Java (inclusa la console)
./mvnw verify -DskipFrontend           # salta la compilazione della console
bash script/ci/web.sh test             # test unitari della console
bash script/ci/web.sh e2e              # test del browser della console (backend simulato)
bash script/ci/e2e_fullstack.sh        # impacchetta, avvia il servizio reale ed esegue i test full-stack
bash script/ci/db_integration.sh postgres:17   # esegue i test di integrazione sul database indicato
bash script/ci/perf_benchmark.sh smoke # benchmark prestazionale su piccola scala
```

Durante lo sviluppo della console, esegui `pnpm dev` (`core/grantforge-web`); Vite inoltra le richieste come `/api` al servizio su `localhost:9999`.

## Avvio dall’IDE

Esegui direttamente `org.devlive.grantforge.server.GrantForge` (modulo `grantforge-server`); per impostazione predefinita usa il database H2. Il server carica automaticamente i moduli plug-in già compilati che si trovano in `plugins/` nel repository (vedi [Plug-in e tipi di servizio](/it/develop/plugins/)); prima di usare un modulo plug-in per la prima volta, esegui una volta `./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests` per copiarne le dipendenze.

## Regole di codice

- Backend: Error Prone + NullAway (non nullo per impostazione predefinita, con `@Nullable` di JSpecify nei punti nullable), Checkstyle, PMD, SpotBugs; ArchUnit protegge le convenzioni comuni (niente iniezione via campi, niente SQL nativo, le entità non compaiono nelle API, `Optional.get()` non consentito, ecc.).
- Frontend: modalità strict di TypeScript, ESLint con zero avvisi; tutti i testi passano per i18n, le chiavi devono essere letterali e le chiavi in cinese e in inglese coincidono esattamente.
- Ogni file sorgente ha l’intestazione di licenza MIT; ogni classe del codice principale ha la relativa classe di test (le eccezioni sono registrate in `script/ci/test_mapping_exclusions.txt`).
- Le soglie di copertura sono definite per modulo (`script/ci/coverage_thresholds.txt`).
- Le migrazioni del database sono YAML Liquibase, una modifica per file, e si possono solo aggiungere, non modificare; i tipi usano proprietà cross-database come `${text}`.
- I messaggi di commit seguono le Conventional Commits e il titolo non supera i 72 caratteri.

## CI

| Job | Contenuto |
| --- | --- |
| Repository hygiene | Intestazioni di licenza, percorsi vietati, mappatura dei test, i18n, manifest dei permessi, formato dei file, controlli di script e flussi di lavoro |
| Commit messages | Formato dei messaggi di commit |
| CI script unit tests | Test unitari degli script di CI stessi |
| Java 17 / 21 / 25 / latest | Compilazione e test di tutti i moduli Java, verifica della versione del bytecode |
| Java static analysis | Copertura, Checkstyle, SpotBugs, PMD |
| Frontend | Coerenza tra tipi dell’API e contratto, verifica dei tipi e compilazione, ESLint, test unitari, test del browser |
| JavaScript SDK | Verifica dei tipi, compilazione, ESLint, test unitari |
| Database | Migrazioni e test di integrazione su H2, PostgreSQL 14/17, MySQL 8.0/8.4, MariaDB 10.11/11.4, Oracle 23 e SQL Server 2022 |
| Plugin API compatibility | Confronto del contratto dei plug-in con l’ultima versione pubblicata |
| Full-stack acceptance | Impacchettamento, avvio del servizio ed esecuzione dei test del browser full-stack su PostgreSQL |
| Docs | Verifica, test e compilazione del sito di documentazione |

Ogni notte vengono eseguiti inoltre i benchmark prestazionali; il flusso di lavoro di sicurezza analizza dipendenze e segreti.

## Pubblicazione

Il numero di versione è `anno.versione minore.revisione` (per esempio `2026.0.0`) e le versioni candidate aggiungono `-rc.N`. Le versioni in tutti i pom, nei pacchetti npm, nell’appVersion dell’Helm Chart, nella barra laterale della console e nel README devono coincidere, e la CI lo verifica con `check_versions.py`.

Per pubblicare dal ramo `dev`, un solo comando:

```bash
bash script/release/tag.sh 2026.1.0 --dry-run          # controlla e visualizza solo le note di rilascio, senza modificare nulla
bash script/release/tag.sh 2026.1.0 --next 2026.2.0    # imposta la versione, crea il tag v2026.1.0 e lo invia, poi sposta dev sulla versione successiva
```

Lo script richiede che l’area di lavoro sia pulita, che il ramo locale non sia indietro rispetto al remoto e che il tag non esista; dopo la conferma, esegue il commit `chore(release): prepare <versione>`, crea il tag con annotazione e lo invia. Il tag attiva `release.yml`:

- `script/ci/release.sh` compila il pacchetto di rilascio, una SBOM CycloneDX contenente solo le dipendenze di rilascio, e `SHA256SUMS`;
- le immagini multi-architettura vengono inviate a `ghcr.io/devlive-community/grantforge`;
- gli artefatti Maven (con il pacchetto dei sorgenti e il Javadoc) vengono pubblicati su GitHub Packages; quando il repository ha configurato `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` (token del Central Portal), `GPG_PRIVATE_KEY` e `GPG_PASSPHRASE`, dopo la firma vengono pubblicati su Maven Central;
- viene creata una GitHub Release il cui corpo raccoglie tutti i commit dall’ultima versione pubblicata (un tag `v*` o numerico come `1.0.6`), raggruppati per nuove funzionalità, correzioni, prestazioni, ecc., con i link ai commit.

Le versioni candidate vengono contrassegnate come prerelease e non aggiornano il `latest` delle immagini. Con il profile `central` attivato in locale, per impostazione predefinita non si pubblica (`central.skip=true`); solo il flusso di lavoro di pubblicazione passa esplicitamente `-Dcentral.skip=false`.

## Manifest dei permessi

Le pagine della console, i pulsanti e le API di cui hanno bisogno sono dichiarati in `core/grantforge-web/src/permissions/`. `check_permission_manifest.py` garantisce che ogni API dichiarata esista e che ogni interfaccia che richiede permessi sia coperta da qualche pulsante o pagina (le eccezioni delle chiamate dirette sono registrate in `script/ci/permission_direct_apis.txt`).

## Documentazione

Il sito si trova in `docs/` e usa l’esportazione statica di Next.js e Tailwind CSS:

```bash
cd docs
pnpm install
pnpm dev        # http://localhost:3100
pnpm check      # verifica di pagine, link e immagini
pnpm build      # output in docs/out
```

Le pagine sono Markdown in `docs/content/` e la navigazione è in `docs/lib/navigation.ts`. Il riferimento dell’API e i codici di errore vengono generati in fase di compilazione dal contratto e dal codice sorgente. Le schermate vengono generate da `script/docs/screenshots.sh`, che avvia il servizio reale, scrive i dati di esempio e poi usa Playwright.
