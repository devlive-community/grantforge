---
title: OAuth 2.1 e OpenID Connect
description: Gli endpoint del server di autorizzazione, i tipi di client, le regole sui token e la chiave di firma.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge integra un server di autorizzazione basato su Spring Authorization Server che rispetta i requisiti di sicurezza di OAuth 2.1: supporta solo il flusso di autorizzazione con codice (con PKCE obbligatorio), i refresh token e le credenziali client; non supporta il flusso implicito né la modalità con password.

![Server di autorizzazione](/screenshots/oauth.png)

## Documento di discovery ed endpoint

Il documento di discovery si trova in `<GrantForge>/.well-known/openid-configuration` e nella pagina **Gestione della piattaforma → Server di autorizzazione** può essere copiato direttamente. L’issuer è per impostazione predefinita l’indirizzo da cui arriva la richiesta; usare `grantforge.oauth.issuer` per fissarlo quando GrantForge viene distribuito dietro un proxy inverso.

| Endpoint | Descrizione |
| --- | --- |
| `/oauth2/authorize` | flusso di autorizzazione con codice; tutti i client devono usare PKCE (S256) |
| `/oauth2/token` | codice di autorizzazione, refresh token e credenziali client. Il refresh token viene sostituito a ogni uso; se il token vecchio ricompare, l’intera autorizzazione viene revocata |
| `/oauth2/revoke` | revoca dei token |
| `/oauth2/jwks` | chiave pubblica di firma (RS256) |
| `/userinfo` | `sub`, `tid` e `preferred_username`; con lo scope `profile` restituisce anche `name` e con lo scope `email` restituisce anche `email` |

L’access token e l’ID token contengono `tid` (l’ID del tenant) e `preferred_username`; il campo `auth_time` dell’ID token è il momento in cui l’utente ha effettuato l’accesso alla console.

## Client

In **Gestione della piattaforma → Catalogo di risorse** seleziona l’applicazione e fai clic su “Client OAuth” per gestirne i client.

| Impostazione | Regola |
| --- | --- |
| Tipo | il **client pubblico** è destinato alle applicazioni che non possono conservare un segreto, come quelle per browser o per dispositivi mobili; il **client confidenziale** è destinato alle applicazioni server e dispone di un segreto |
| Indirizzi di callback | fino a 10, indirizzi assoluti, senza caratteri jolly né frammento; devono essere https, oppure http in locale (localhost, 127.0.0.1, [::1]), oppure il protocollo personalizzato di un’applicazione nativa |
| scope | `openid`, `profile`, `email`, `permissions` (consultare i permessi), `catalog` (dichiarare entità di dati, solo con credenziali client) |
| Modalità di autorizzazione | codice di autorizzazione, refresh token (richiede il codice di autorizzazione e lo ottengono solo i client confidenziali), credenziali client (solo client confidenziali) |
| Validità dei token | access token da 1 minuto a 24 ore (15 minuti per impostazione predefinita), refresh token da 1 ora a 90 giorni (30 giorni per impostazione predefinita) |

Il segreto del client confidenziale viene mostrato una sola volta, al momento della registrazione o della rotazione, e GrantForge ne conserva solo l’hash. In fase di rotazione è possibile impostare un periodo di grazia (massimo 7 giorni) durante il quale il segreto vecchio e quello nuovo restano entrambi validi, per facilitare l’adozione graduale.

## Regole sui token

- I token sono salvati come hash: nemmeno con una fuga di dati dal database si ottiene un token utilizzabile.
- I token già emessi non vengono più rinnovati dopo il verificarsi di una qualsiasi delle seguenti circostanze: il client viene disattivato o eliminato, l’account viene disattivato o bloccato, l’account deve cambiare la password, il tenant viene disattivato.
- Chiamate cross-origin dal browser: GrantForge consente all’origine degli indirizzi di callback dei client abilitati di chiamare cross-origin gli endpoint dei token e l’API aperta, senza inviare cookie.

## Chiave di firma

La chiave di firma è generata con RSA 2048 e la chiave privata viene conservata cifrata. Per impostazione predefinita viene ruotata automaticamente ogni 90 giorni (`grantforge.oauth.signing-key-rotation`) e la chiave pubblica precedente continua a essere pubblicata nel JWKS per 2 giorni (`signing-key-retention`), in modo che i token emessi prima della rotazione possano ancora essere verificati. All’occorrenza è possibile ruotarla immediatamente dalla pagina del server di autorizzazione: si tratta di un’operazione sensibile e gli account con l’autenticazione a due fattori attivata devono effettuare una nuova verifica.

## Usare GrantForge per accedere a sistemi diversi dalla console

Qualsiasi sistema che supporti OpenID Connect (Grafana, GitLab, Jenkins, ecc.) può usare GrantForge come IdP: creagli nel catalogo di risorse un’applicazione e un client confidenziale e inserisci l’indirizzo del documento di discovery, il `client_id` e il segreto nella sua configurazione OIDC. Viceversa, GrantForge può usare a sua volta altri IdP per l’accesso; vedi [Fonti di identità](/it/guide/identity-sources/).
