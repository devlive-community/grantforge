---
title: Fonti di identità (LDAP e OIDC)
description: Consentire agli utenti di accedere con la directory aziendale (LDAP/AD) o con un fornitore OpenID Connect, creando gli account in automatico e sincronizzandoli.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controllo degli accessi → Fonti di identità** configura le directory o i fornitori di identità con cui gli utenti possono accedere. La password di questo tipo di account è salvata nella fonte di identità: GrantForge non la salva e non può modificarla.

![Fonti di identità](/screenshots/identity-sources.png)

## LDAP / Active Directory

Fai clic su “Aggiungi fonte di identità” e scegli il tipo “Directory LDAP”:

| Impostazione | Descrizione |
| --- | --- |
| Indirizzo della directory | `ldap://` o `ldaps://`; più indirizzi separati da spazi per il failover |
| Base DN degli utenti | Per esempio `ou=people,dc=example,dc=com` |
| Account di ricerca | L’account (Bind DN) e la relativa password usati per cercare gli utenti; se lasciato vuoto, la ricerca è anonima |
| Filtro utenti | Per impostazione predefinita `(&(objectClass=person)(uid={0}))`; `{0}` è il nome inserito dall’utente |
| Attributi | Gli attributi di nome utente, nome visualizzato, indirizzo e-mail e identificatore univoco; per impostazione predefinita sono adatti a OpenLDAP (`uid`, `cn`, `mail`, `entryUUID`); per Active Directory indica `sAMAccountName` e `objectGUID` |
| Creazione automatica degli account per i nuovi utenti | Se attivata, l’account viene creato al primo accesso di un utente presente nella directory |
| Intervallo di sincronizzazione | Se lasciato vuoto, la sincronizzazione è solo manuale; il minimo è 15 minuti |
| Disattivazione degli account degli utenti non più presenti nella directory | In fase di sincronizzazione vengono disattivati gli utenti che non esistono più nella directory |

Dopo il salvataggio, fai clic su **Verifica connessione** per confermare che la configurazione è corretta.

In fase di accesso, GrantForge cerca prima l’utente con l’account di ricerca e poi verifica la password legandosi alla directory con l’identità di quell’utente e la password inserita.

La **sincronizzazione** crea gli account per i nuovi utenti della directory, aggiorna nome e indirizzo e-mail degli account esistenti e, secondo la configurazione, disattiva gli utenti che hanno lasciato la directory (chiudendone anche le sessioni). Il risultato della sincronizzazione viene mostrato nella scheda della fonte di identità.

## OpenID Connect

Scegli il tipo “OpenID Connect” per collegare, per esempio, Keycloak, Azure AD, Okta o un’altra istanza di GrantForge:

| Impostazione | Descrizione |
| --- | --- |
| Indirizzo dell’issuer | L’indirizzo dell’issuer del fornitore; GrantForge ottiene gli endpoint e le chiavi dal relativo documento di discovery |
| ID / segreto del client | Il client registrato presso il fornitore; in assenza di segreto viene usato come client pubblico con PKCE |
| Indirizzo di callback | L’indirizzo `<GrantForge>/api/v1/auth/federated/callback/<codifica>` mostrato nella pagina deve essere registrato nel client del fornitore |
| Scope e claim | Per impostazione predefinita `openid profile email`; nome utente, nome visualizzato e indirizzo e-mail vengono letti rispettivamente dalle claim `preferred_username`, `name` ed `email` |

Una volta abilitata, nella pagina di accesso compare il pulsante “Accedi con <nome>”. L’utente accede presso il fornitore e torna in GrantForge; gli account con l’autenticazione a due fattori attivata devono inserire anche un codice di verifica.

## Regole sugli account

- L’identificatore univoco dell’utente della fonte di identità (`entryUUID`/`objectGUID` in LDAP, `sub` in OIDC) corrisponde a un account GrantForge; il cambio di nome non altera questa corrispondenza.
- Se esiste già un account locale con lo stesso nome, l’associazione **non viene effettuata automaticamente**: l’accesso viene rifiutato e un amministratore deve prima rinominare o eliminare l’account locale. Questo evita che un utente omonimo presente nella directory prenda possesso di un account esistente.
- Una fonte di identità che non crea account in automatico consente l’accesso solo agli utenti già associati.
- Dopo la disattivazione di una fonte di identità, i suoi utenti non possono più accedere; una fonte di identità ancora utilizzata da degli account non può essere eliminata.
- Agli account di una fonte di identità è possibile assegnare ruoli e attivare l’autenticazione a due fattori come di consueto.
