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
| Claim del telefono | Legge `phone` per impostazione predefinita (Casdoor); per altri provider si può indicare `phone_number`. Per sincronizzare il numero di telefono lo scope deve includere `phone`. Se all’accesso il provider non restituisce un numero, resta quello esistente |
| Indirizzo dell’avatar | Sincronizzato dalla claim OIDC standard `picture`; lo scope deve includere `profile`. Se non viene restituito, resta l’indirizzo esistente |
| Parametri aggiuntivi della richiesta di autorizzazione | Oggetto JSON facoltativo i cui valori devono essere stringhe. Con Casdoor, ad esempio, `{"provider_hint":"github"}` sceglie un provider configurato. I parametri compaiono nell’indirizzo del browser, quindi non inserirvi mai segreti |

GrantForge gestisce i parametri essenziali del protocollo (compresi `state`, `nonce`, `redirect_uri` e quelli di PKCE); i parametri aggiuntivi non possono sovrascriverli.

Una volta abilitata, nella pagina di accesso compare il pulsante “Accedi con <nome>”. L’utente accede presso il fornitore e torna in GrantForge; gli account con l’autenticazione a due fattori attivata devono inserire anche un codice di verifica.

## Regole sugli account

- L’identificatore univoco dell’utente della fonte di identità (`entryUUID`/`objectGUID` in LDAP, `sub` in OIDC) corrisponde a un account GrantForge; il cambio di nome non altera questa corrispondenza.
- Se esiste già un account locale con lo stesso nome, l’associazione **non viene effettuata automaticamente**: l’accesso viene rifiutato e un amministratore deve prima rinominare o eliminare l’account locale. Questo evita che un utente omonimo presente nella directory prenda possesso di un account esistente.
- Una fonte di identità che non crea account in automatico consente l’accesso solo agli utenti già associati.
- Dopo la disattivazione di una fonte di identità, i suoi utenti non possono più accedere; una fonte di identità ancora utilizzata da degli account non può essere eliminata.
- Agli account di una fonte di identità è possibile assegnare ruoli e attivare l’autenticazione a due fattori come di consueto.

La sincronizzazione della struttura organizzativa si configura in un’origine dati separata, senza modificare le impostazioni di accesso OIDC.

## Origini dati e sincronizzazione dell’organizzazione

Le origini dati sincronizzano la struttura organizzativa, mentre le fonti di identità continuano a gestire l’accesso OIDC o LDAP. Le due si configurano separatamente: disattivare un’origine dati non influisce sull’accesso OIDC.

1. Configura l’accesso OpenID Connect esistente in “Controllo accessi → Fonti di identità”.
2. In “Controllo accessi → Origini dati” aggiungi un’origine DingTalk e scegli la fonte di identità OIDC collegata.
3. Inserisci CorpId, AppKey, AppSecret e l’ID del reparto radice (di solito `1`). L’AppSecret è cifrato a parte, l’API non lo restituisce mai in chiaro e, se in modifica il campo resta vuoto, si mantiene il valore precedente.
4. L’origine può essere montata sotto un reparto locale. L’intervallo di sincronizzazione predefinito è di 30 minuti e ammette da 15 a 10080 minuti; un campo vuoto significa solo sincronizzazione manuale.
5. Dopo il salvataggio, verifica la connessione ed esegui la prima sincronizzazione. L’applicazione DingTalk deve poter leggere i reparti e la rubrica dei dipendenti, e il suo ambito autorizzato deve coprire il sottoalbero scelto.
6. Quando un utente accede tramite SSO, il dipendente viene trovato tramite l’indirizzo e-mail OIDC attendibile e i suoi reparti, compreso quello principale, vengono compilati automaticamente. L’indirizzo deve essere gestito da un amministratore, non deve consentire a un dipendente di spacciarsi per un altro e deve coincidere con quello di DingTalk. Se manca l’indirizzo del dipendente, si usa quello aziendale.

L’accesso legge solo l’istantanea locale della rubrica e non interroga DingTalk. Con l’MFA, l’appartenenza viene aggiornata dopo il superamento del secondo passaggio. Gli account che accedono prima della prima sincronizzazione conservano l’indirizzo attendibile e vengono collegati al termine della sincronizzazione; l’indirizzo del profilo, modificabile localmente, non viene mai usato per l’abbinamento. Indirizzi mancanti, duplicati o già collegati a un altro account non creano nuovi collegamenti.

Il collegamento a un dipendente si basa sul suo ID esterno e non passa a un altro dipendente quando cambia l’indirizzo. La sincronizzazione gestisce l’appartenenza a più reparti; il reparto principale resta quello precedente finché è valido, altrimenti viene scelto secondo l’ordine stabile degli ID di reparto. Reparti locali, gruppi di utenti, posizioni e assegnazioni dirette di ruoli vengono mantenuti. Assegnando un ruolo a un reparto sincronizzato, gli utenti lo ereditano secondo le regole esistenti, e un trasferimento aggiorna i permessi.

Non appena esistono un’istantanea dell’organizzazione o account in attesa di collegamento, la fonte di identità collegata, l’azienda, l’applicazione, il reparto radice e il punto di montaggio restano fissi. Reparti e appartenenze sincronizzati sono in sola lettura e non possono essere sovrascritti a mano dalla gestione degli utenti o dell’organizzazione. I reparti scomparsi vengono mantenuti per proteggere le autorizzazioni esistenti; quando un dipendente esce dalla rubrica autorizzata, le appartenenze gestite da questa origine vengono rimosse, ma l’account non viene disattivato automaticamente. Un’origine dati che possiede reparti non può essere eliminata direttamente, ma può essere disattivata.

La sincronizzazione recupera tutte le pagine prima di applicare il risultato in modo atomico. In caso di errori di lettura, gerarchie anomale o modifiche dell’ambito autorizzato si mantiene l’ultima istantanea riuscita; gli amministratori verificano il risultato tramite il riepilogo della sincronizzazione, l’elenco delle anomalie e il registro di audit. Se l’ambito è cambiato, ripristina prima l’ambito approvato e poi sincronizza di nuovo.

L’accesso usa l’[SDK server ufficiale di DingTalk](https://open.dingtalk.com/document/development/download-the-server-side-sdk), senza chiamate HTTP scritte a mano. Gli adattatori dei fornitori producono istantanee neutre di reparti e dipendenti, così un futuro adattatore Feishu potrà riutilizzare la stessa logica di sincronizzazione, collegamento e aggiornamento dei permessi.
