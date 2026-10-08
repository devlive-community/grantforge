---
title: Applicazioni di esempio
description: "I due esempi completi del repository: un negozio a cui il browser si collega direttamente e un’applicazione di note con accesso lato server."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

In `samples/` si trovano due applicazioni eseguibili che, insieme a GrantForge, sono coperte da test end-to-end e rappresentano il miglior riferimento in fase di integrazione.

| Esempio | Porta | Forma di integrazione mostrata |
| --- | --- | --- |
| `samples/shop` | 19081 | il browser accede in modo cross-origin con un client pubblico + PKCE; i pulsanti vengono mostrati in base alle risorse; il backend verifica le API con `@RequirePermission`; `@GrantForgeEntity` dichiara l’entità degli ordini e le query vengono eseguite con gli ambiti dati “solo io” e “tutto il tenant attuale” |
| `samples/notes` | 19082 | il server accede con l’`oauth2Login` di Spring Security (client confidenziale + PKCE); un `AccessTokenResolver` personalizzato recupera il token dalla sessione; “Scrivi nota” viene mostrato solo a chi ne ha il permesso |

## Eseguirli

Gli esempi sono build Maven indipendenti che dipendono dallo starter e dall’SDK JavaScript di questo repository:

```bash
./mvnw -N install
./mvnw -pl sdk/grantforge-spring-boot-starter install
(cd sdk/grantforge-js && pnpm install && pnpm build)
./mvnw -f samples/pom.xml package
```

Tutta la configurazione degli esempi arriva da variabili d’ambiente:

| Variabile | Spiegazione |
| --- | --- |
| `GRANTFORGE_URL` | l’indirizzo di GrantForge |
| `SHOP_BROWSER_CLIENT_ID` | il client pubblico del browser del negozio |
| `SHOP_CLIENT_ID` / `SHOP_CLIENT_SECRET` | il client confidenziale che il backend del negozio usa per dichiarare le entità di dati (scope `catalog`) |
| `SHOP_SDK_DIRECTORY` | la directory con l’output di build dell’SDK JavaScript (`sdk/grantforge-js/dist`) |

In GrantForge è necessario creare risorse, client, ruoli e policy sui dati per le due applicazioni; lo script di preparazione dei test end-to-end `core/grantforge-web/tests/samples/setup.ts` mostra tutti questi passaggi e può essere usato direttamente come riferimento.

## Test end-to-end

`script/ci/e2e_fullstack.sh` costruisce e avvia i due esempi dopo i test full-stack e verifica: l’accesso PKCE cross-origin e CORS, la visibilità dei pulsanti, il 403 delle interfacce, gli ambiti dati “solo io” e “tutto il tenant attuale”, l’eliminazione e l’uscita, oltre all’accesso lato server dell’applicazione di note. Impostando `GRANTFORGE_E2E_SKIP_SAMPLES=1` si possono saltare.
