---
title: Installare il pacchetto di rilascio
description: Installa, avvia, ferma e aggiorna il pacchetto di rilascio di GrantForge su una macchina fisica o virtuale.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Requisiti dell’ambiente

| Voce | Requisito |
| --- | --- |
| Java | 17 o superiore (il pacchetto di rilascio è compilato con Java 17; si consiglia 21) |
| Memoria | almeno 1 GB; per la produzione almeno 2 GB |
| Database | H2 incorporata per le prove; in produzione PostgreSQL, MySQL, MariaDB, Oracle o SQL Server, vedi [Database](/it/deploy/databases/) |
| Browser | Chrome, Edge, Firefox o Safari nelle due ultime versioni principali |

## Struttura delle cartelle

Dopo aver estratto `grantforge-release.tar.gz` si ottiene la cartella `grantforge/`:

| Cartella | Contenuto |
| --- | --- |
| `bin/` | `startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh` e `import-legacy.sh` |
| `configure/` | `application.properties`, dove sovrascrivere la configurazione predefinita |
| `lib/` | i jar del server e delle sue dipendenze |
| `drivers/` | driver JDBC aggiuntivi (quello per MySQL va inserito a mano) |
| `plugins/` | plug-in per tipi di servizio, vedi [Plug-in e tipi di servizio](/it/develop/plugins/) |
| `agents/` | i jar degli agenti da distribuire nei sistemi di destinazione, come l’[agente NameNode Apache Hadoop HDFS](/it/external/hdfs-agent/) |
| `data/` | i file del database H2 incorporato (creati al primo avvio) |
| `logs/` | `grantforge.log`; `console.out` registra l’output emesso prima dell’avvio del sistema di log |

## Avvio e arresto

```bash
bin/startup.sh     # avvia in background e scrive il pid nel suo file
bin/shutdown.sh    # arresto graceful in base al file pid
bin/restart.sh     # arresta e poi riavvia
bin/debug.sh       # gira in primo piano, con i log anche sulla console; Ctrl+C lo ferma
```

Gli script si possono eseguire da qualsiasi cartella: la cartella di installazione è quella un livello sopra a quella dello script e si può indicare anche con la variabile d’ambiente `GRANTFORGE_HOME`.

## Scegliere il database

Per impostazione predefinita viene usato il database H2 su file in `data/grantforge`, adatto alle prove. In produzione indica il database in `configure/application.properties` o tramite variabili d’ambiente:

```bash
export GRANTFORGE_DB_URL=jdbc:postgresql://db.example.com:5432/grantforge
export GRANTFORGE_DB_USER=grantforge
export GRANTFORGE_DB_PASSWORD=******
bin/startup.sh
```

Alla prima connessione GrantForge crea automaticamente tutte le tabelle con Liquibase; a ogni avvio successivo vengono eseguite le migrazioni non ancora applicate.

## Prima inizializzazione

Al primo avvio nel log viene stampato una sola volta il token di inizializzazione: apri la console, inserisci il token e crea il primo amministratore. La procedura è descritta in [Configurazione in cinque minuti](/it/start/quick-start/).

## Health check e monitoraggio

| Indirizzo | Uso |
| --- | --- |
| `/actuator/health/liveness` | sonda di liveness |
| `/actuator/health/readiness` | sonda di readiness: restituisce 200 quando il database è disponibile e le migrazioni sono state applicate |
| `/actuator/prometheus` | metriche di Prometheus; per impostazione predefinita richiede l’accesso, ma si può aprire a una rete fidata con `GRANTFORGE_PROMETHEUS_PUBLIC=true` |

Se servono log strutturati, imposta `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` (oppure `logstash`). Ogni riga di log riporta l’ID della richiesta, che corrisponde al `requestId` delle risposte di errore delle interfacce.

## Distribuzione in cluster

Più istanze possono condividere un unico database ed erogare il servizio contemporaneamente: le sessioni sono salvate nel database, quindi qualsiasi istanza può gestire qualsiasi richiesta. Ogni istanza necessita di un `GRANTFORGE_ID_NODE` diverso (0–1023), che determina il numero di nodo usato nella generazione degli ID. Il bilanciatore di carico non richiede persistenza delle sessioni.

## Aggiornamento

Ferma il servizio, sostituisci il vecchio `lib/` con quello della nuova versione (conservando `configure/`, `data/`, `drivers/` e `plugins/`) e riavvia: le migrazioni del database vengono eseguite automaticamente. Fai un backup del database prima dell’aggiornamento. Per passare dalla 1.x, vedi [Aggiornare e migrare da versioni precedenti](/it/deploy/upgrade/).
