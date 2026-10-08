---
title: Accesso, account e autenticazione a due fattori
description: Accesso e sessioni, centro personale, cambio della password, autenticazione a due fattori e codici di recupero, oltre alla verifica aggiuntiva per le operazioni sensibili.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Accesso

![Pagina di accesso](/screenshots/login.png)

Il nome utente non distingue tra maiuscole e minuscole. Dopo 5 inserimenti errati della password consecutivi (configurabile) l’account viene bloccato per 15 minuti; un account bloccato da un amministratore deve essere sbloccato da un amministratore. Quando sono abilitate le fonti di identità, nella pagina di accesso compare anche il pulsante “Accedi con X”; vedi [Fonti di identità](/it/guide/identity-sources/).

Le sessioni sono salvate sul server e il browser conserva solo un cookie di sessione HttpOnly. Dopo 30 minuti di inattività (configurabile) la sessione scade ed è necessario accedere di nuovo.

## Centro personale

Fai clic sull’avatar in alto a destra per entrare nel **centro personale**:

![Centro personale](/screenshots/account.png)

- **Dati di base**: modificare il nome visualizzato e l’indirizzo e-mail. Il nome utente e l’organizzazione di appartenenza sono gestiti dall’amministratore.
- **Cambio della password**: è necessario inserire la password attuale. Dopo il cambio terminano tutte le sessioni aperte su altri dispositivi. Gli account che accedono tramite una fonte di identità non trovano qui il cambio della password, perché la password è gestita dalla fonte di identità.
- **Autenticazione a due fattori**: vedi più avanti.
- **I miei dispositivi di accesso**: l’elenco dei browser connessi, da cui è possibile chiudere le sessioni non riconosciute.
- **Registro degli accessi recenti**: gli ultimi 10 accessi, disconnessioni e tentativi falliti, compresi i tentativi di altre persone di accedere con il proprio nome utente.

Dopo che un amministratore ha reimpostato la password o che la password è scaduta, l’accesso successivo entra prima nella schermata “Cambio della password” e nessun’altra funzione è disponibile fino a quando la password non viene cambiata.

## Autenticazione a due fattori

Una volta attivata, per accedere serve, oltre alla password, il codice a 6 cifre generato da un’app di autenticazione (Google Authenticator, Microsoft Authenticator, 1Password, ecc.).

1. Nel centro personale, fai clic su **Configura autenticatore** nella sezione “Autenticazione a due fattori”.
2. Aggiungi l’account nell’app di autenticazione: inserisci la chiave mostrata nella pagina oppure apri il collegamento otpauth su un dispositivo con l’app installata.
3. Inserisci il codice mostrato dall’app e fai clic su **Attiva**.
4. La pagina mostra **10 codici di recupero**, una sola volta. Conservali con cura: se perdi l’autenticatore, ogni codice di recupero permette di accedere una volta al posto del codice di verifica.

Una volta attivata, è possibile rigenerare i codici di recupero (quelli vecchi scadono immediatamente) oppure disattivare l’autenticazione a due fattori; entrambe le operazioni richiedono l’inserimento di un codice di verifica. Se perdi l’autenticatore e non hai i codici di recupero, chiedi a un amministratore di reimpostare l’autenticazione a due fattori dell’account in **Gestione utenti**.

> [!TIP]
> Ogni codice di verifica può essere usato una sola volta. Gli errori consecutivi nel codice di verifica concorrono al blocco esattamente come gli errori di password.

## Verifica aggiuntiva per le operazioni sensibili

Per gli account con l’autenticazione a due fattori attivata, le seguenti operazioni richiedono una verifica effettuata negli ultimi 10 minuti (configurabile): ruotare la chiave di firma, creare un client o ruotarne la chiave, creare o disattivare un tenant, reimpostare la password o l’autenticazione a due fattori di un’altra persona, assegnare ruoli, modificare le autorizzazioni, aggiungere o modificare fonti di identità, approvare richieste di accesso.

Se queste operazioni vengono eseguite oltre quel limite di tempo, la console apre la finestra di dialogo “Conferma identità” e, dopo l’inserimento del codice di verifica, prosegue automaticamente con l’operazione avviata. L’impostazione `grantforge.security.mfa.required-for-sensitive=true` richiede che gli account che eseguono queste operazioni abbiano attivato l’autenticazione a due fattori.

## Sessioni attive

In **Controllo degli accessi → Sessioni attive** gli amministratori vedono tutti i browser connessi di questo tenant (account, IP, browser, ora di accesso, attività recente) e possono chiudere le sessioni sospette. Disattivare, bloccare un account o reimpostarne la password chiude immediatamente tutte le sessioni di quell’account.

![Sessioni attive](/screenshots/sessions.png)
