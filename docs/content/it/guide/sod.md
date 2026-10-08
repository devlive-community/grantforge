---
title: Separazione dei compiti
description: Configurare i ruoli che una stessa persona non può possedere contemporaneamente, rifiutare o limitarsi a segnalare, e consultare i conflitti attuali.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controllo degli accessi → Separazione dei compiti** configura i ruoli che una stessa persona non può possedere contemporaneamente (per esempio pagamento e approvazione) e mostra gli account che violano ora i vincoli.

![Separazione dei compiti](/screenshots/sod.png)

## Vincoli

| Impostazione | Spiegazione |
| --- | --- |
| Ruoli mutuamente esclusivi | da 2 a 50 ruoli |
| Massimo per account | 1 per impostazione predefinita; si può definire come “due su tre al massimo” |
| Modalità | **Obbligatoria**: rifiuta le assegnazioni e le ereditarietà di ruoli che creerebbero un conflitto; **Solo segnalazione**: consente la modifica e la mostra solo nell’elenco dei conflitti |
| Attivo | un vincolo disattivato non rifiuta e non segnala |

“Possedere” un ruolo include tutte le vie: assegnazione diretta, ottenimento tramite gruppi di utenti, dipartimenti o posizioni, ed ereditarietà dai ruoli. Le assegnazioni fuori dal loro periodo di validità e i ruoli disattivati non contano.

## Quando viene applicata

In modalità obbligatoria, le seguenti modifiche verificano, prima di salvare, ogni account che toccano:

- assegnare un ruolo, oppure modificare il periodo di validità di un’assegnazione o se include i dipartimenti subordinati;
- modificare le relazioni di ereditarietà di un ruolo;
- approvare richieste di accesso (vedi [Richieste di accesso e approvazioni](/it/guide/access-requests/)).

Vengono rifiutati solo i conflitti **che crea questa modifica**, e viene indicato chi è, quali ruoli e quale vincolo viene violato. I conflitti che esistevano già prima che il vincolo entrasse in vigore non bloccano altre modifiche senza relazione con loro: restano nell’elenco dei conflitti e vanno risolti a mano.

> [!WARNING]
> Questa verifica non viene fatta quando si aggiunge una persona a un gruppo di utenti, a un dipartamento o a una posizione. I conflitti che sorgono da queste vie compaiono nell’elenco dei conflitti; consultalo con regolarità.

## Elenco dei conflitti

A destra sono elencati tutti gli account che violano un vincolo attivo (in qualsiasi modalità): account, vincolo, ruoli posseduti e limite consentito.
