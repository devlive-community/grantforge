---
title: Richieste di accesso e approvazioni
description: Gli utenti richiedono ruoli a tempo limitato, chi approva li concede, li rifiuta o li revoca prima della scadenza, e alla scadenza vengono ritirati automaticamente.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Amministratore: aprire i ruoli che si possono richiedere

In **Controllo degli accessi → Approvazione dei permessi** fai clic su **Ruoli richiedibili** e scegli quali ruoli personalizzati si possono richiedere e per quanti giorni al massimo si può richiedere ciascuno (1–365). I ruoli di sistema non si possono richiedere.

## Utente: richiedere

Ogni utente che ha effettuato l’accesso vede in **Spazio di lavoro → Le mie richieste** i ruoli che può richiedere. Scegli il ruolo, scrivi il motivo e il numero di giorni e invia; prima dell’approvazione la richiesta si può ritirare. Non si può ripetere una richiesta se si possiede già il ruolo o se ce n’è già una in attesa di approvazione.

![Le mie richieste](/screenshots/requests.png)

Un utente appena creato deve prima modificare la password iniziale per poter usare questa pagina.

## Chi approva: concedere, rifiutare, revocare

**Controllo degli accessi → Approvazione dei permessi** elenca le richieste in attesa, quelle già concesse o tutte.

![Approvazione dei permessi](/screenshots/access-approvals.png)

- **Concedi**: si possono ridurre i giorni e scrivere un commento. Concedendola, l’utente ottiene il ruolo immediatamente e alla scadenza perde automaticamente efficacia.
- **Rifiuta**: si può scrivere il motivo.
- **Revoca**: ritira il ruolo prima della scadenza su una richiesta già concessa.

Concedere una richiesta equivale all’assegnazione del ruolo da parte di chi approva, quindi valgono le stesse regole:

- non si può concedere un ruolo che vada oltre i permessi di chi approva;
- si rispettano i vincoli della [Separazione dei compiti](/it/guide/sod/);
- non si può approvare la propria richiesta;
- chi approva e ha attivato l’autenticazione a due fattori deve aver effettuato una verifica negli ultimi 10 minuti.

## Ritiro alla scadenza

Il ruolo concesso perde efficacia nel momento della scadenza. In background le assegnazioni scadute vengono ripulite ogni 5 minuti e la richiesta viene contrassegnata come “scaduta”. Tutto il processo (richiesta, ritiro, concessione, rifiuto, revoca, scadenza) viene registrato nel registro di audit.
