---
title: Panoramica dell’integrazione
description: "GrantForge è al tempo stesso server di autorizzazione e centro dei permessi: le applicazioni di business fanno accedere i propri utenti con protocolli standard e ne consultano i permessi."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge ricopre due ruoli nei confronti delle applicazioni di business:

- **Server di autorizzazione** (OAuth 2.1 / OpenID Connect): l’utente accede in GrantForge e l’applicazione ottiene l’access token e l’ID token.
- **Centro dei permessi**: con lo stesso token, l’applicazione chiede a GrantForge quali ruoli, quali risorse (menu, pagine, pulsanti), quali permessi API e quale ambito sui dati quell’utente ha in questa applicazione.

## Corrispondenza dei concetti

| Concetto | Dove si configura | Descrizione |
| --- | --- | --- |
| Applicazione | Gestione della piattaforma → Catalogo di risorse | un sistema di business, per esempio `shop` |
| Risorsa | l’albero delle risorse dell’applicazione nel catalogo di risorse | moduli, menu, pagine, pulsanti, API. Le pagine e i pulsanti controllano l’interfaccia, mentre le risorse API sono i codici di permesso delle API (per esempio `orders.read`) |
| Client | Catalogo di risorse → “Client OAuth” dell’applicazione | l’identità con cui l’applicazione fa accedere gli utenti e ottiene i token. Le applicazioni per browser usano un **client pubblico**, le applicazioni server un **client confidenziale** |
| scope | le impostazioni del client | `openid`, `profile` ed `email` servono per l’accesso; `permissions` consente al token di consultare i permessi; `catalog` consente all’applicazione di dichiarare entità di dati a proprio nome |
| Ruoli e autorizzazioni | Controllo degli accessi → Gestione dei ruoli | si concedono le risorse dell’applicazione a un ruolo e poi il ruolo viene assegnato a utenti, gruppi, dipartimenti o posizioni |
| Policy sui dati | Ruolo → Permessi sui dati | le entità dichiarate dall’applicazione (`<codice-applicazione>:<entità>`) si configurano come le entità proprie della console: tutti, tutto il tenant attuale, solo io, il mio dipartimento, dipartimenti indicati o in base a una condizione |

Gli amministratori tenant (chi possiede il ruolo di sistema) possono concedere qualsiasi risorsa delle applicazioni di business ai ruoli del proprio tenant; per i permessi della console stessa è invece possibile concedere solo la parte che si possiede.

## Flusso

```mermaid
sequenceDiagram
  participant B as Browser
  participant A as Applicazione di business
  participant G as GrantForge
  B->>G: /oauth2/authorize (PKCE)
  G-->>B: se non ha effettuato l’accesso, viene portato alla pagina di accesso della console; dopo l’accesso torna alla richiesta di autorizzazione
  G-->>B: torna all’indirizzo di callback dell’applicazione con il code
  B->>G: /oauth2/token (code + code_verifier)
  G-->>B: access token, ID token
  B->>G: /api/v1/open/me/authorization (Bearer)
  G-->>B: ruoli, risorse, permessi API (ETag)
  B->>A: chiama l’API dell’applicazione (Bearer)
  A->>G: /api/v1/open/me/authorization, /data-access (lo stesso token)
  A-->>B: restituisce solo i dati che l’utente può usare
```

## Passi per l’integrazione

1. Crea una nuova applicazione in **Gestione della piattaforma → Catalogo di risorse** e realizzane le pagine, i pulsanti e le risorse API.
2. Registra un client per l’applicazione: scegli “Pubblico” se l’applicazione è per browser e “Confidenziale” se è server; come indirizzo di callback indica quello di accesso dell’applicazione; tra gli scope seleziona almeno `openid` e `permissions`. Il segreto del client confidenziale viene mostrato una sola volta.
3. In **Controllo degli accessi → Gestione dei ruoli** crea il ruolo, concedi le risorse e assegnalo agli utenti.
4. Integra l’SDK nell’applicazione: per Java vedi l’[SDK Java (Spring Boot)](/it/integration/java/); per il browser vedi l’[SDK JavaScript](/it/integration/javascript/); per i dettagli del protocollo vedi [OAuth 2.1 e OpenID Connect](/it/integration/oauth/) e l’[API aperta di consultazione dei permessi](/it/integration/open-api/).

Nel repository, `samples/` contiene due esempi completi (un negozio e un blocco note), coperti da test end-to-end; vedi [Applicazioni di esempio](/it/integration/samples/).
