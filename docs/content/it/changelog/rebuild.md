---
title: 2026.0.0 (ricostruzione)
description: "GrantForge riscritto da zero: identità multi-tenant, autorizzazioni su risorse e ruoli, permessi sui dati e sui campi, integrazione tramite protocolli standard, governance aziendale e permessi sui sistemi esterni tramite plug-in."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

2026.0.0 è una riscrittura completa che sostituisce la versione 1.x (AuthX). Non è più un modello di backend, bensì una piattaforma di identità e permessi distribuita in modo indipendente. Gli account, i ruoli e i menu della versione 1.x possono essere importati; vedi [Aggiornare e migrare da versioni precedenti](/it/deploy/upgrade/).

## Piattaforma

- Spring Boot 4, runtime Java 17; un pacchetto di rilascio contiene il server e la console, e sono inoltre forniti immagini Docker, Compose e Helm Chart.
- Supporto di H2, PostgreSQL, MySQL, MariaDB, Oracle e SQL Server, con migrazioni gestite da Liquibase e test di ogni commit su nove versioni di database.
- Procedura guidata di inizializzazione al primo avvio, che crea l’amministratore piattaforma tramite un token monouso riportato nel log del servizio.
- Distribuzione in cluster: le sessioni sono salvate nel database e gli ID sono TSID ordinati temporalmente.
- Console completamente nuova: Vue 3, Tailwind CSS, tema chiaro e scuro, cinese e inglese.

## Identità e organizzazione

- Multi-tenant: gli account, l’organizzazione e le autorizzazioni di ogni tenant sono completamente isolati.
- Utenti, albero dei dipartimenti, gruppi di utenti, posizioni e importazione ed esportazione in massa tramite CSV.
- Password Argon2id, politica sulle password e blocco configurabili, gestione delle sessioni, autenticazione a due fattori con TOTP e codici di recupero, nuova verifica per le operazioni sensibili.
- Fonti di identità LDAP / Active Directory e OIDC, con supporto della sincronizzazione e dell’accesso federato.

## Autorizzazione

- Catalogo di risorse: moduli, menu, pagine, etichette, pulsanti, API, entità di dati e campi, oltre alle dipendenze tra di essi. Anche le pagine, i pulsanti e le API della console stessa sono nel catalogo e sono soggetti alle stesse autorizzazioni.
- Ruoli e autorizzazioni: consenso e negazione, ereditarietà, assegnazione per utente/gruppo di utenti/dipartimento/posizione, periodo di validità.
- Permessi sui dati: limitazione delle righe visibili in base all’ambito dell’organizzazione o a condizioni strutturate.
- Permessi sui campi: campi nascosti, mascherati o in sola lettura in base al ruolo.
- Spiegazione dei permessi, simulazione per utente, registro di audit completo e verifica delle configurazioni non valide.

## Governance

- Separazione dei compiti: i ruoli mutualmente esclusivi vengono rifiutati in fase di assegnazione, ereditarietà e richiesta, ed è possibile individuare i conflitti già esistenti.
- Richieste di accesso: gli utenti richiedono i ruoli richiedibili e, una volta approvati, hanno effetto per un periodo limitato e vengono revocati automaticamente alla scadenza.
- Revisione periodica dei permessi.

## Integrazione delle applicazioni

- Server di autorizzazione OAuth 2.1 / OpenID Connect integrato: codice di autorizzazione + PKCE, credenziali client, rotazione dei refresh token, rotazione della chiave di firma.
- API aperta di consultazione dei permessi, con versione ed ETag.
- Spring Boot Starter e SDK JavaScript, corredati di applicazioni di esempio pronte all’uso.

## Permessi sui sistemi esterni

- Tipi di servizio tramite plug-in: il plug-in definisce la gerarchia delle risorse, i tipi di accesso, il mascheramento e il filtraggio delle righe; ogni plug-in viene caricato in modo indipendente.
- Editor di policy generico, snapshot delle policy firmate con Ed25519, heartbeat degli agenti e audit degli accessi.
- Plug-in di esempio, tipo di servizio HDFS e agente NameNode per Hadoop 3.5.0; il plug-in Hive e gli agenti per le altre versioni di Hadoop sono in fase di sviluppo.

## Qualità

- Benchmark prestazionali sulla scala di un milione di account eseguiti ogni notte, con errore al superamento dei limiti.
- Analisi statica (NullAway, Error Prone, Checkstyle, PMD, SpotBugs, ArchUnit), soglie di copertura e test del browser full-stack.
- Il sito della documentazione è stato ricostruito con Next.js e Tailwind CSS.
