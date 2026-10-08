---
title: Importazione ed esportazione in massa
description: Importare o esportare in massa utenti e struttura dell’organizzazione con file CSV, prima con un controllo preliminare e poi con la scrittura.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controllo degli accessi → Importazione ed esportazione** consente di importare o esportare in massa utenti e struttura dell’organizzazione tramite file CSV.

![Importazione ed esportazione](/screenshots/transfer.png)

## Importazione

1. Fai clic su **Scarica modello** e compilalo seguendo la “Descrizione delle colonne”. L’intestazione non distingue tra maiuscole e minuscole e le colonne in eccesso vengono ignorate; il dipartimento e la posizione degli utenti vanno indicati tramite codice e, se sono più di uno, separati da punto e virgola.
2. Carica il file e fai clic su **Controllo preliminare**: GrantForge lo esamina riga per riga e segnala ogni problema (numero di riga, colonna e motivo).
3. Quando tutto è in regola, fai clic su **Conferma importazione di N righe**. Se una qualsiasi riga presenta un problema, non viene scritto nulla, per evitare importazioni parziali.

Regole:

- La codifica del file può essere UTF-8 o GBK (i CSV in cinese salvati da Excel sono GBK per impostazione predefinita) e viene riconosciuta automaticamente.
- Un file può contenere al massimo 1000 utenti o 5000 dipartimenti e non può superare i 2 MB (configurabile).
- I dipartimenti vengono importati ordinandosi automaticamente in base alla relazione di subordinazione: nel file un dipartimento superiore può comparire dopo uno subordinato; se all’interno del file si forma un ciclo, questo viene segnalato.
- La password iniziale degli utenti deve soddisfare la politica sulle password e gli utenti importati devono cambiarla al primo accesso.
- È possibile importare solo nei dipartimenti e nelle posizioni che i tuoi permessi sui dati ti consentono di vedere.

## Esportazione

Esporta gli utenti che corrispondono alle condizioni attuali oppure tutti i dipartimenti; il nome del file riporta la data, per esempio `users-2026-10-05.csv`. Anche l’esportazione rispetta i permessi sui dati e i permessi sui campi: le righe che non puoi vedere non vengono esportate e i campi limitati vengono nascosti o mascherati secondo le rispettive regole. Alle celle che iniziano con `=`, `+`, `-` o `@` viene aggiunto un prefisso, per evitare che il software di foglio di calcolo le esegua come formule.
