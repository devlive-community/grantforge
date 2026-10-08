---
title: Permessi sui dati
description: "Determinare quali righe di ogni tipo di dati chi possiede il ruolo può leggere, modificare, eliminare ed esportare: le proprie, quelle del proprio dipartimento, di dipartimenti indicati o in base a una condizione."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

I permessi sui dati determinano **quali righe** di ogni tipo di dati chi possiede il ruolo può leggere, modificare, eliminare ed esportare. Fai clic su **Permessi sui dati** sulla riga di un ruolo per configurarli.

![Permessi sui dati](/screenshots/role-data.png)

## Regole

Ogni regola è composta da quattro parti:

| Parte | Opzioni |
| --- | --- |
| Entità di dati | Utenti, dipartimenti, gruppi di utenti, posizioni, eventi di audit e le entità dichiarate dalle applicazioni di business (come `shop:order`) |
| Azione | Visualizza, modifica, elimina, esporta |
| Ambito | Tutti i tenant (solo per i ruoli del tenant piattaforma), tutto il tenant attuale, il mio dipartimento e i subordinati, il mio dipartimento, dipartimenti indicati, solo io, in base a una condizione |
| Effetto | Consenti o nega |

Combinazione delle regole:

- **In assenza di regole di consenso, i dati non sono visibili.**
- **La negazione ha la precedenza sul consenso**: qualsiasi riga interessata da una regola di negazione non è disponibile.
- Le regole dei diversi ruoli di una persona agiscono insieme: i consensi si uniscono e anche le negazioni si uniscono.
- I ruoli di sistema implicano l’ambito corrispondente (l’amministratore tenant ha tutto il tenant attuale), a eccezione delle entità delle applicazioni di business.

## In base a una condizione

Quando l’ambito è “In base a una condizione”, combina le condizioni con l’editor delle condizioni:

- Confronta i campi dell’entità, per esempio “Stato uguale a Attivo” oppure “Ultimo accesso precedente a ora”. Il testo supporta contiene e inizia con; i numeri e le date supportano il confronto di maggiore e minore; sono supportati anche appartiene, non appartiene, è vuoto e non è vuoto.
- Il valore può essere fisso oppure un **attributo dell’utente attuale**: il proprio ID, il nome utente, il dipartimento, i gruppi di utenti, le posizioni ricoperte e il momento attuale.
- Le condizioni possono essere raggruppate con “Tutte soddisfatte” / “Almeno una soddisfatta”, possono essere negate e l’annidamento arriva al massimo a 4 livelli.

## Anteprima

Nell’editor, seleziona un utente e fai clic su **Anteprima** per vedere quante righe, ed esattamente quali, le regole di questo ruolo gli consentono di vedere.

## Dove si applica

Gli elenchi e i dettagli di utenti, dipartimenti, gruppi di utenti e posizioni della console, l’importazione ed esportazione e i registri di audit rispettano tutti i permessi sui dati; le righe che non puoi vedere non compaiono nell’elenco e, se vi accedi direttamente tramite ID, la risposta è “Non esiste”. Le applicazioni di business ottengono le stesse regole tramite l’[SDK Java](/it/integration/java/) o l’[API aperta](/it/integration/open-api/).
