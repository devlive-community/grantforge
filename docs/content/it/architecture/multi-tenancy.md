---
title: Multi-tenant e isolamento dei dati
description: Come i tenant isolano i dati, quali dati sono condivisi dalla piattaforma e quali vincoli valgono per le operazioni tra tenant.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Modalità di isolamento

A eccezione dei dati condivisi dalla piattaforma, ogni tabella di business ha una colonna `tenant_id`. Quando entra una richiesta, la catena di filtri associa al thread corrente il tenant dell’account connesso, il filtro tenant di Hibernate aggiunge automaticamente la condizione di tenant alle query e, in scrittura, compila automaticamente il `tenant_id`. Il codice di business non può “dimenticare” di aggiungere la condizione di tenant.

I pochi scenari che devono attraversare i tenant (per esempio cercare un account in base al nome utente al momento del login, contare gli account di ciascun tenant, i job pianificati in background) devono entrare esplicitamente nel “contesto di sistema”, così da essere individuabili a colpo d’occhio durante la revisione del codice.

## Condiviso e indipendente

| Condiviso dalla piattaforma | Indipendente in ogni tenant |
| --- | --- |
| Catalogo di applicazioni e risorse, catalogo delle API, client OAuth, plug-in | Account, dipartimenti, gruppi di utenti, posizioni, ruoli, autorizzazioni, assegnazioni, policy di dati e di campi, separazione dei compiti, richieste e revisioni, fonti di identità, servizi di dati, audit |

Il nome utente è unico in tutta la piattaforma, quindi al login non è necessario scegliere il tenant.

## Il tenant piattaforma

Il tenant piattaforma viene creato in fase di inizializzazione e non può essere disattivato. I suoi amministratori gestiscono i dati condivisi dalla piattaforma e gli altri tenant; solo i ruoli del tenant piattaforma possono usare l’ambito dati “tutti i tenant”.

## ID

Tutte le chiavi primarie sono TSID: 64 bit, ordinate nel tempo, con unicità garantita nel cluster dal numero di nodo. Sono maggiori degli interi che JavaScript può rappresentare con precisione, quindi in JSON vengono sempre trasmesse come stringa. Ogni istanza del cluster richiede un `GRANTFORGE_ID_NODE` diverso.
