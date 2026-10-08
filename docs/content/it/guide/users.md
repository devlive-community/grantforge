---
title: Utenti
description: Creare, cercare, modificare, disattivare, bloccare ed eliminare account, reimpostare password e autenticazione a due fattori, consultare i permessi in vigore.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controllo degli accessi → Gestione utenti** gestisce gli account dell’organizzazione: il dipartimento di appartenenza, la possibilità di accedere e la reimpostazione della password.

![Gestione utenti](/screenshots/users.png)

## Ricerca

Cerca per nome utente, nome visualizzato o indirizzo e-mail, filtra per stato (attivo, disattivato, bloccato, in attesa di cambio password) e per dipartimento, e scegli se includere i dipartimenti subordinati. L’elenco mostra solo gli account che i tuoi permessi sui dati consentono di vedere; campi come l’indirizzo e-mail possono essere nascosti o mascherati in base ai tuoi permessi sui campi.

## Creazione e modifica

“Crea utente” richiede il nome utente (da 3 a 64 lettere, cifre o `._@-`, univoco su tutta la piattaforma), la password iniziale, il nome visualizzato, l’indirizzo e-mail, il dipartimento principale, i dipartimenti secondari e le posizioni. I nuovi account devono cambiare la password al primo accesso.

## Operazioni sull’account

| Operazione | Effetto |
| --- | --- |
| Disattiva / Attiva | Una volta disattivato non può accedere e le sessioni aperte terminano immediatamente |
| Blocca / Sblocca | Il blocco applicato da un amministratore non si rimuove automaticamente; all’accesso viene chiesto di contattare un amministratore; si usa quando si sospetta un uso illecito |
| Reimposta password | Imposta una nuova password iniziale, l’utente deve cambiarla all’accesso successivo e le sessioni aperte terminano |
| Reimposta autenticazione a due fattori | Per la perdita dell’autenticatore: disattiva l’autenticazione a due fattori dell’account e ne chiude le sessioni |
| Visualizza ruoli | I ruoli posseduti dall’account e l’origine di ciascuno (assegnazione diretta, gruppo di utenti, dipartimento, posizione) |
| Visualizza permessi in vigore | Vedi [Spiegazione, simulazione e audit](/it/guide/explain/) |
| Elimina | Elimina l’account e le sue relazioni con i dipartimenti; i record di audit vengono conservati |

L’account di sistema (l’amministratore creato in fase di inizializzazione) e il proprio account non possono essere disattivati, bloccati né eliminati.

## Registrazione autonoma

Disattivata per impostazione predefinita. Dopo aver impostato `grantforge.security.registration-enabled=true`, nella pagina di accesso compare la voce “Crea account”; gli account registrati entrano nel tenant indicato da `grantforge.security.registration-tenant` e sono account ordinari privi di qualsiasi ruolo.
