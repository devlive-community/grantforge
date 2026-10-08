---
title: Revisione periodica dei permessi
description: Verificare periodicamente chi possiede quali ruoli; chi revisiona decide uno per uno se mantenerli o revocarli, e al completamento del ciclo le assegnazioni revocate vengono rimosse.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controllo degli accessi → Revisione dei permessi**: verificare periodicamente chi possiede quali ruoli; chi revisiona decide uno per uno se mantenerli o revocarli, e al completamento del ciclo le assegnazioni revocate vengono rimosse.

![Revisione dei permessi](/screenshots/access-reviews.png)

## Piano di revisione

| Impostazione | Spiegazione |
| --- | --- |
| Nome, descrizione | per esempio “Revisione trimestrale dei ruoli di finanza” |
| Ruoli | i ruoli da revisionare; ogni ciclo elenca tutte le loro assegnazioni attuali |
| Durata di ogni ciclo | da 1 a 90 giorni; alla scadenza il ciclo si completa automaticamente |
| Intervallo di ripetizione | lascialo vuoto per avviare i cicli solo a mano; altrimenti il ciclo successivo inizia automaticamente secondo l’intervallo |
| Elementi non revisionati | gli elementi rimasti senza decisione alla fine del ciclo: **mantieni** o **revoca** |
| Attivo | influisce solo sull’avvio automatico dei cicli secondo il piano |

## Un ciclo di revisione

1. Fai clic su **Avvia ora**, oppure attendi che il piano lo avvii. GrantForge genera un elemento di revisione per ogni assegnazione (fatte salve i ruoli di sistema degli account di sistema).
2. Chi revisiona sceglie **Mantieni** o **Revoca** elemento per elemento o in blocco; per la revoca si può scrivere una nota e la decisione si può annullare prima della fine del ciclo.
3. Non si possono revisionare i ruoli che si possiedono per assegnazione diretta, tramite gruppi di utenti, dipartimenti o posizioni.
4. Un amministratore può **completare il ciclo** (applicare tutte le decisioni e trattare quelle mancanti secondo le impostazioni del piano) oppure **annullare il ciclo** (senza cambiare nulla). Un ciclo scaduto e non completato si completa automaticamente.

Le assegnazioni revocate vengono eliminate al completamento del ciclo. Ogni passo viene registrato nel registro di audit, utile come traccia per gli audit di controllo interno.
