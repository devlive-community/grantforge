---
title: Presentazione del prodotto
description: Che cos’è GrantForge, quali problemi risolve e in cosa si differenzia dalle soluzioni più comuni.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge è una piattaforma di permessi unificata open source (MIT). Centralizza «chi può fare cosa e quali dati può vedere»: nella console si gestiscono utenti e organizzazione, si definiscono i ruoli e si concedono ai ruoli menu, pulsanti, API, righe di dati e campi; l’applicazione, dal canto suo, fa accedere gli utenti tramite OAuth 2.1 / OpenID Connect e determina i permessi con l’API aperta o un SDK.

![Panoramica della console di GrantForge](/screenshots/dashboard.png)

## Quali problemi risolve

Quando un sistema raggiunge una certa dimensione, i permessi tendono a disperdersi: i menu sono scritti nella configurazione del frontend, ogni endpoint esegue i propri controlli tramite annotazioni, l’ambito dei dati dipende da condizioni scritte a mano nell’SQL e, quando una persona lascia l’azienda o cambia posizione, nessuno è in grado di dire con certezza cosa possa ancora fare. GrantForge unifica tutto questo in un unico modello:

- **Definire una volta, valido ovunque**: le pagine della console, i pulsanti, gli endpoint REST, le entità di dati e i campi sono tutti «risorse»; i ruoli concedono autorizzazioni sulle risorse e la stessa autorizzazione guida contemporaneamente la visibilità nel frontend e l’intercettazione nel backend.
- **Permessi visibili**: in qualsiasi momento è possibile rispondere a «perché questa persona vede questa pagina» e «chi sarà interessato se modifico questo ruolo»; ogni modifica alle autorizzazioni ha un’anteprima e lascia traccia nell’audit.
- **Conforme ai requisiti di governance**: separazione dei compiti, richieste di accesso a tempo determinato, revisione periodica e autenticazione a due fattori soddisfano i requisiti più comuni della protezione classificata e degli audit di controllo interno.
- **Integrazione con protocolli standard**: l’applicazione non deve incorporare un proprio sistema di utenti; è sufficiente accedere tramite OIDC e consultare i permessi con il token di accesso.

## Funzioni in sintesi

| Area | Funzione |
| --- | --- |
| Identità e organizzazione | Multi-tenant, albero dei dipartimenti, gruppi, posizioni; importazione ed esportazione in massa in CSV; accesso e sincronizzazione LDAP/AD, accesso federato OIDC |
| Sicurezza degli account | Gestione delle sessioni, politica delle password e blocco, autenticazione a due fattori TOTP e codici di recupero, verifica aggiuntiva per le operazioni sensibili |
| Autorizzazione delle funzioni | Catalogo delle risorse (moduli, menu, pagine, schede, pulsanti, API), ereditarietà dei ruoli, matrice delle autorizzazioni, analisi di impatto |
| Permessi sui dati | Limitazione delle righe visibili per condizione (se stessi, il proprio dipartimento e i dipartimenti figli, dipartimenti specificati, condizione personalizzata); lettura e scrittura controllate separatamente |
| Permessi sui campi | Campi nascosti, mascherati (e-mail, numero di telefono, numero di documento, ecc.) o in sola lettura |
| Spiegabilità e audit | Spiegazione dei permessi, simulazione delle autorizzazioni, consultazione ed esportazione del registro di audit |
| Governance | Vincoli di separazione dei compiti, richieste di accesso e approvazioni, revisione periodica dei permessi |
| Integrazione delle applicazioni | Server di autorizzazione OAuth 2.1 / OIDC, API aperta di consultazione dei permessi, SDK per Java (Spring Boot) e JavaScript |
| Sistemi esterni | Tipi di servizio con plug-in e motore delle policy (simile ad Apache Ranger), servizi di dati, policy di accesso e agenti |
| Consegna | Pacchetto di rilascio unico, immagine Docker, esempi Compose, Helm Chart; H2, PostgreSQL, MySQL, MariaDB, Oracle, SQL Server |

## Differenze rispetto alle soluzioni più comuni

> [!NOTE]
> GrantForge non è una libreria che si limita a fare RBAC, né un IdP che si limita al single sign-on. Riunisce identità, autorizzazione e governance in un unico modello e rende spiegabile ogni autorizzazione.

- **Rispetto alla scrittura manuale dei permessi nel codice**: le regole sui permessi si gestiscono nella console, modificare un’autorizzazione non richiede la pubblicazione dell’applicazione; prima di autorizzare si vede l’ambito di impatto e dopo rimane l’audit.
- **Rispetto a un IdP che si limita all’autenticazione (Keycloak e simili)**: GrantForge include un modello di autorizzazione che arriva fino a pulsanti, righe di dati e campi, oltre a funzioni di governance come la separazione dei compiti e la revisione; al tempo stesso può fungere da IdP oppure integrare un LDAP o un OIDC esistente come fonte di identità.
- **Rispetto ad Apache Ranger**: GrantForge riprende da Ranger i tipi di servizio, le policy e l’architettura con agenti per gestire i permessi dei sistemi di dati esterni; prima di tutto, però, è una piattaforma di permessi per le applicazioni di business.

## Passaggi successivi

- [Configurazione in cinque minuti](/it/start/quick-start/): scaricare, avviare, completare l’inizializzazione e concedere il primo ruolo.
- [Concetti fondamentali](/it/start/concepts/): la relazione tra risorse, ruoli, autorizzazioni, assegnazioni e valutazione.
- [Panoramica dell’integrazione delle applicazioni](/it/integration/overview/): fai usare GrantForge alla tua applicazione.
