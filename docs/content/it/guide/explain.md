---
title: Spiegazione, simulazione e audit
description: Consultare che cosa può fare una persona e perché, simulare le modifiche ai ruoli, cercare ed esportare i registri di audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Permessi in vigore

In **Gestione utenti**, fai clic su “Visualizza permessi in vigore” sulla riga di un utente per vedere tutto ciò che può fare in questo momento: ruoli, menu e pulsanti, API, ambito sui dati e campi limitati.

![Permessi in vigore](/screenshots/user-permissions.png)

Fai clic su **Spiegazione** su una qualsiasi di queste voci e GrantForge risponderà “perché può farlo”: da quale ruolo, attraverso quali assegnazioni (diretta, gruppo di utenti, dipartimento, posizione) e da quali risorse viene derivato; quando non può farlo, indica se manca l’autorizzazione oppure se a bloccarlo è una qualche negazione.

## Simulazione delle modifiche

Apri **Simula modifiche** all’interno dei permessi in vigore: ipotizza di aggiungere o rimuovere alcuni ruoli a quell’utente e osserva quali menu, pulsanti e API guadagnerebbe o perderebbe. La simulazione si limita al calcolo, non salva nulla, ed è utile per verificare l’effetto prima di intervenire sui permessi.

## Registro di audit

**Controllo degli accessi → Registro di audit** registra chi ha fatto cosa e quando: accessi, modifiche alle autorizzazioni, operazioni di amministrazione e chiamate rifiutate.

![Registro di audit](/screenshots/audit.png)

- Filtra per evento, esito, operatore, oggetto e data; il risultato può essere esportato in CSV.
- Ogni evento riporta l’IP di origine, il browser e l’ID della richiesta, che corrisponde al log del server.
- Vengono mostrati solo gli eventi che i tuoi permessi sui dati ti consentono di vedere.
- L’audit viene conservato per 365 giorni per impostazione predefinita (`grantforge.audit.retention`) e può essere configurato per archiviarlo in una directory prima dell’eliminazione.

Gli eventi registrati comprendono: accessi riusciti e falliti, blocchi, disconnessioni, termine delle sessioni, cambi di password; modifiche a tenant, dipartimenti, utenti, gruppi di utenti e posizioni; modifiche al catalogo delle risorse e delle API; modifiche a ruoli, autorizzazioni, ereditarietà e assegnazioni; modifiche alle policy sui dati e sui campi; ogni passaggio della separazione dei compiti, delle richieste di accesso e delle revisioni; modifiche alle fonti di identità e ai client OAuth, oltre alle chiamate API rifiutate.
