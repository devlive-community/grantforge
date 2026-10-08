---
title: Aggiornare e migrare da versioni precedenti
description: L’aggiornamento tra versioni 2.x e la migrazione di account, ruoli e menu dalla 1.x (AuthX / GrantForge 1.x).
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Aggiornare tra versioni 2.x

1. Fai il backup del database (e di `plugins/` e `configure/`).
2. Ferma il servizio: `bin/shutdown.sh`.
3. Sostituisci il vecchio `lib/` e `bin/` con quelli del pacchetto di rilascio della nuova versione.
4. Avvia: `bin/startup.sh`. Liquibase esegue automaticamente le migrazioni del database della nuova versione e la sonda di readiness restituisce 200 solo al termine delle migrazioni.

In caso di aggiornamento di un cluster, ferma prima tutte le istanze e poi avvia la nuova versione, per evitare che la versione vecchia e quella nuova leggano e scrivano contemporaneamente. Le migrazioni già pubblicate non vengono mai modificate e ogni versione è stata verificata con il passaggio dalla versione precedente su tutti i database supportati.

## Migrare dalla 1.x

La 1.x salva i dati in un altro insieme di tabelle e la 2.x non le legge. La modalità di migrazione è: installa la 2.x su un **nuovo database** e completa l’inizializzazione, ferma il servizio e poi importa in un tenant gli account, i ruoli e i menu del vecchio database:

```bash
# prima una simulazione: genera solo il report, non scrive
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default
# importazione effettiva una volta confermato il report
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default --apply
```

Entrambi i comandi scrivono `logs/legacy-import-report.json`, che elenca ciò che è stato (o sarà) creato, ciò che è stato saltato e per quale motivo, oltre al nuovo ID corrispondente a ogni vecchio oggetto. Il driver JDBC del vecchio database va messo in `drivers/` e la password viene fornita tramite `GRANTFORGE_LEGACY_SOURCE_PASSWORD` (se non la fornisci, lo script la chiede). Eseguire l’importazione una seconda volta si limita ad aggiungere ciò che manca.

Regole di importazione:

- Gli **account** conservano la password originale, che viene sostituita automaticamente dal nuovo algoritmo di hash al primo accesso. Vengono saltati gli account che non rispettano le regole sui nomi utente della 2.x (da 3 a 64 lettere, numeri o `._@-`), quelli senza password e quelli il cui nome utente è già occupato in un altro tenant.
- I **ruoli** conservano il nome e il codice viene convertito in minuscolo (`GLY` → `gly`).
- I **menu** diventano risorse dell’applicazione `legacy`: un menu con indirizzo `#` diventa un gruppo, gli altri indirizzi diventano pagine e i menu che dipendono da una pagina diventano pulsanti. L’indirizzo del menu e il suo metodo HTTP diventano la risorsa API `api:<metodo>:<percorso>`; un indirizzo che termina con `*` diventa `<percorso>/**`, che viene confrontato per segmenti di percorso anziché per prefisso di caratteri, e il report li elenca uno per uno per consentirne la verifica.
- Vengono migrate solo le **autorizzazioni esplicite**: la 1.x permetteva a chiunque di accedere agli indirizzi registrati come menu, la 2.x non lo fa.

> [!WARNING]
> Prima di importare, verifica nel report gli account saltati e gli indirizzi con caratteri jolly, e solo allora esegui `--apply`.
