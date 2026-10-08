---
title: Modello dei permessi
description: La semantica esatta di risorse, derivazione delle autorizzazioni, ereditarietà, assegnazioni, regole su dati e campi, oltre allo snapshot dei permessi e alle versioni.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Questo articolo descrive le regole esatte della valutazione. Per una prima presa di contatto con i concetti, vedi [Concetti fondamentali](/it/start/concepts/).

## Albero delle risorse e tipi

Le risorse appartengono a un’applicazione, sono organizzate ad albero e hanno al massimo 15 livelli. Il tipo determina dove possono essere collocate:

| Tipo | Padre consentito |
| --- | --- |
| Modulo | Primo livello, modulo |
| Menu, pagina | Primo livello, modulo, menu |
| Scheda | Pagina, scheda |
| Pulsante | Pagina, scheda |
| API, entità di dati | Primo livello, modulo |
| Campo | Entità di dati |

Tra le risorse si possono dichiarare dipendenze: “necessaria” (quando si concede una risorsa vengono concesse anche le risorse da cui dipende) oppure “opzionale” (si limita ad avvisare l’amministratore, senza concederla automaticamente); le dipendenze non possono formare cicli. Le pagine, i pulsanti e le API di cui hanno bisogno nella console sono dichiarati dall’elenco dei permessi del frontend e, all’avvio, il server li sincronizza come risorse integrate.

## Derivazione delle autorizzazioni

Per un account, il valutatore determina prima i **ruoli effettivi**:

1. I ruoli assegnati direttamente all’account;
2. i ruoli assegnati ai gruppi di utenti, ai dipartimenti (incluse le assegnazioni di dipartimenti superiori con “subordinati inclusi”) e alle posizioni di cui l’account fa parte o che ricopre;
3. si prendono solo le assegnazioni valide al momento attuale e i ruoli abilitati;
4. si espande l’ereditarietà: tutti i ruoli antenati di un ruolo (un antenato disabilitato non trasmette nulla).

Poi si combinano le autorizzazioni di questi ruoli:

- Concedere una risorsa equivale a concedere la risorsa stessa, i suoi antenati nell’albero (per renderla visibile) e le risorse da cui dipende come “necessarie” (ricorsivamente).
- Negare una risorsa agisce su di essa e su tutti i suoi subordinati e **prevale su qualsiasi concessione**.
- Una risorsa disabilitata e i suoi subordinati non hanno effetto.
- Un ruolo di sistema equivale a concedere l’intero sottoalbero del proprio modulo (per esempio `system`, `data`, `platform`).

Il risultato è l’insieme delle risorse utilizzabili e i codici di permesso corrispondenti alle risorse API tra esse.

## Protezione dall’elevazione dei privilegi

- Quando si concede una “concessione”, chi la concede deve poter usare egli stesso quella risorsa (chi detiene un ruolo di sistema non è soggetto a questo limite nei confronti delle applicazioni di business).
- Quando si assegna un ruolo o si imposta un’ereditarietà, chi concede deve coprire tutte le risorse che quel ruolo copre in ciascuna applicazione.
- Il ruolo di amministratore della piattaforma può essere assegnato, modificato o rimosso solo dagli attuali detentori.
- La “negazione” non ha restrizioni: chiunque abbia il permesso di autorizzazione può stringere i permessi.

## Regole sui dati

Le policy sui dati appartengono ai ruoli e ne definiscono l’ambito secondo “entità × azione × effetto”: `ALL` (solo per il tenant piattaforma), `TENANT`, `ORG_AND_CHILDREN`, `ORG`, `CUSTOM_ORGS`, `SELF`, `CONDITION`. Una riga è utilizzabile se e solo se soddisfa almeno una regola di concessione e non soddisfa alcuna regola di negazione.

Le condizioni sono JSON strutturato, non eseguibile, che viene validato prima di essere salvato nel database:

```json
{ "and": [
  { "field": "status", "op": "eq", "value": "ACTIVE" },
  { "not": { "field": "orgUnitId", "op": "in", "value": { "var": "subject.orgUnitIds" } } }
] }
```

Le variabili sono solo `subject.id`, `subject.username`, `subject.orgUnitIds`, `subject.groupCodes`, `subject.positionCodes` e `now`; gli operatori di confronto sono limitati in base al tipo del campo; l’annidamento non supera i 5 livelli e i nodi non superano le 50 unità. Il server traduce le regole in `Specification` JPA e l’SDK le traduce con la stessa semantica nell’applicazione di business.

## Regole sui campi

Le policy sui campi definiscono la modalità di lettura (visibile, mascherato, nascosto) e la modalità di scrittura (modificabile, sola lettura); con più ruoli si adotta l’impostazione più permissiva e, in assenza di qualsiasi impostazione, il campo è completamente aperto. Le regole di lettura vengono applicate in fase di serializzazione JSON (lo stesso DTO si presenta in modo diverso a persone diverse), quelle di scrittura nello strato di servizio; la modifica di un campo in sola lettura restituisce `GF-FIELD-001` e indica il campo.

## Snapshot e versione

Il risultato della valutazione è uno **snapshot dei permessi**: risorse utilizzabili, codici di permesso, regole sui dati e regole sui campi. Lo snapshot viene messo in cache per account; qualsiasi cambiamento nelle autorizzazioni, nelle assegnazioni, nell’ereditarietà, nel catalogo delle risorse, nelle policy o nelle relazioni di appartenenza incrementa il numero di versione dell’ambito interessato (il catalogo o il tenant), invalidando la cache. Ogni interfaccia che richiede permessi restituisce la versione corrente nell’header di risposta `X-Authorization-Version`, e la console decide in base a essa se ricaricare il menu; l’API aperta esprime la stessa versione tramite ETag.
