---
title: Dipartimenti, gruppi e posizioni
description: "Gestire l’albero dei dipartimenti, creare gruppi di utenti al bisogno, amministrare le posizioni: tutti utilizzabili come destinatari di assegnazione dei ruoli e come ambito dei dati."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Struttura dell’organizzazione

**Controllo degli accessi → Struttura dell’organizzazione** gestisce la gerarchia dei dipartimenti.

![Struttura dell’organizzazione](/screenshots/org.png)

- I dipartimenti hanno al massimo 16 livelli; è possibile trascinarli oppure usare “Sposta in…” per cambiarne la posizione, ma non possono essere spostati sotto i propri subordinati.
- Ogni account ha un dipartimento principale e può appartenere a più dipartimenti.
- I dipartimenti sono un riferimento importante per i permessi sui dati: “Il mio dipartimento”, “Il mio dipartimento e i subordinati” e “Dipartimenti indicati” vengono calcolati in base a questa struttura.
- È possibile eliminare solo i dipartimenti che non hanno dipartimenti subordinati.

## Gruppi di utenti

**Controllo degli accessi → Gruppi di utenti**: riunisci nello stesso gruppo gli account che necessitano degli stessi permessi e successivamente assegna i ruoli al gruppo. I gruppi di utenti sono indipendenti dalla struttura dell’organizzazione e sono adatti a insiemi che attraversano più dipartimenti, come un “gruppo di reperibilità” o un “gruppo di progetto”. I membri possono essere aggiunti e rimossi in blocco, fino a un massimo di 500 persone per operazione.

![Gruppi di utenti](/screenshots/groups.png)

## Posizioni

**Controllo degli accessi → Posizioni**: gestisci le posizioni dell’organizzazione (per esempio “Responsabile finanziario”) e assegnane una o più a un utente in fase di modifica. Anche alle posizioni possono essere assegnati ruoli: quando una persona cambia incarico, è sufficiente modificarne la posizione e i permessi cambiano di conseguenza.

![Posizioni](/screenshots/positions.png)
