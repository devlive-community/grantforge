---
title: Progetto di sicurezza
description: Il progetto di sessioni, CSRF, password e blocco, autenticazione a due fattori e riverifica, archiviazione cifrata, token e audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Sessioni della console

- Le sessioni vengono salvate nel database (Spring Session JDBC) e il browser conserva solo il cookie `GRANTFORGE_SESSION`: HttpOnly, SameSite=Lax e con Secure sotto HTTPS (o forzato con `grantforge.security.cookie-secure`).
- All’accesso, al completamento dell’autenticazione a due fattori e al completamento dell’accesso federato vengono rinnovati sia l’ID di sessione sia il token CSRF, per impedire il session fixation.
- Le richieste che modificano lo stato devono portare l’header `X-XSRF-TOKEN`, il cui valore proviene dal cookie `XSRF-TOKEN`.
- Gli amministratori possono elencare e chiudere qualsiasi sessione; disattivare, bloccare o reimpostare la password chiude immediatamente tutte le sessioni di quell’account; modificare la password chiude le sessioni sugli altri dispositivi.

## Password

- Le nuove password vengono hashate con Argon2id; gli hash BCrypt e quelli importati dalla versione 1.x possono ancora essere verificati e vengono aggiornati all’algoritmo corrente all’accesso successivo.
- La policy su lunghezza, categorie di caratteri, cronologia e validità è configurabile e la password non può contenere il nome utente.
- Dopo aver raggiunto il numero di tentativi consecutivi falliti l’account viene bloccato; anche per un nome utente sconosciuto viene eseguito un confronto di hash, così che il tempo di risposta non riveli quali nomi utente esistono; un account o un tenant disattivati vengono segnalati solo dopo che la password risulta corretta.

## Autenticazione a due fattori e riverifica

- TOTP (RFC 6238, SHA-1, 6 cifre, 30 secondi, con un passo temporale di tolleranza prima e dopo); il codice di uno stesso passo temporale si può usare una sola volta e i 10 codici di recupero monouso vengono salvati con SHA-256.
- In un account con l’autenticazione a due fattori attivata, dopo che la password risulta corretta la sessione resta in stato “in attesa di completamento” per 5 minuti e non è considerata attiva finché il secondo passo non viene completato.
- Le interfacce sensibili sono annotate con `@RequireStepUp`: un account con l’autenticazione a due fattori attivata deve aver effettuato la verifica entro una finestra temporale configurabile, altrimenti viene restituito `GF-SECURITY-006` e la console riprova dopo la conferma in una finestra modale.

## Archiviazione cifrata

Le password di binding delle fonti di identità e i segreti dei client, le chiavi dei verificatori, la configurazione sensibile dei servizi di dati e la chiave privata di firma del server di autorizzazione vengono salvati cifrati con AES-GCM. La chiave proviene da `grantforge.security.encryption-key`; se non è configurata, viene generata automaticamente e salvata nel database (adatta solo per le prove). I token degli agenti e i segreti dei client OAuth vengono salvati solo come hash.

## Token

- Il server di autorizzazione conserva l’hash dei token, non i token stessi.
- Il refresh token viene sostituito a ogni uso e, quando quello vecchio viene riproposto, l’intera autorizzazione viene revocata.
- Account disattivato, bloccato o con obbligo di cambio password, client disattivato e tenant disattivato impediscono tutti il rinnovo del token; l’API aperta verifica a ogni chiamata che il token sia ancora valido.
- Le snapshot delle policy sono firmate con Ed25519 e gli agenti le usano solo dopo aver verificato la firma.

## Protezione delle interfacce

- Ogni interfaccia deve dichiarare la propria modalità di accesso e un’interfaccia senza dichiarazione impedisce l’avvio; le interfacce che richiedono permessi vengono verificate a ogni chiamata sullo snapshot più recente.
- Le chiamate rifiutate vengono scritte nel registro di audit (senza bloccare la richiesta).
- Un account locale con lo stesso nome di una fonte di identità esterna non viene collegato automaticamente, per impedire l’account takeover.
- Nell’esportazione CSV alle celle che iniziano con `=`, `+`, `-` o `@` viene aggiunto un prefisso, per impedire l’iniezione di formule.

## Audit

Tutte le operazioni di amministrazione, i cambiamenti nelle autorizzazioni, gli eventi di accesso e le chiamate rifiutate vengono registrati nel registro di audit; le modifiche relative ai permessi e il loro audit vengono confermati nella stessa transazione, così che se la modifica viene annullata non resta traccia neppure dell’audit.
