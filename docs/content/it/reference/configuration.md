---
title: Riferimento alla configurazione
description: Tutte le opzioni di configurazione, i valori predefiniti e le variabili d’ambiente corrispondenti.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

La configurazione può essere scritta in `configure/application.properties` oppure sovrascritta con variabili d’ambiente. Valgono anche le regole di binding flessibile di Spring Boot: `grantforge.security.mfa.step-up-window` si può scrivere come variabile d’ambiente `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW`. Le durate si esprimono in forme come `30m`, `12h` o `90d`.

## Servizio e database

| Opzione di configurazione | Variabile d’ambiente | Valore predefinito | Descrizione |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | Porta HTTP |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | database H2 a file incorporato | Indirizzo JDBC, vedi [Database](/it/deploy/databases/) |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | Utente del database |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | vuota | Password del database |
| — | `GRANTFORGE_HOME` | directory di installazione | Directory in cui si trovano dati e log di H2 |
| — | `GRANTFORGE_ID_NODE` | automatico | Numero di nodo univoco per ciascuna istanza nel cluster (0–1023) |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | Dimensione massima del file di importazione CSV |

## Inizializzazione e registrazione

| Opzione di configurazione | Valore predefinito | Descrizione |
| --- | --- | --- |
| `grantforge.setup.token` | vuoto | Token di inizializzazione fisso (`GRANTFORGE_SETUP_TOKEN`); se vuoto, ne viene generato uno casuale e stampato nel log |
| `grantforge.security.registration-enabled` | `false` | Se consentire ai visitatori di registrarsi autonomamente |
| `grantforge.security.registration-tenant` | `default` | Tenant a cui appartengono gli account che si registrano autonomamente |

## Password e blocco

| Opzione di configurazione | Valore predefinito | Descrizione |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | Lunghezza minima, almeno 8 |
| `grantforge.security.password.max-length` | `128` | Lunghezza massima, fino a 1024 |
| `grantforge.security.password.required-character-classes` | `1` | Numero di classi di caratteri da combinare (minuscole, maiuscole, cifre, altri), 1–4 |
| `grantforge.security.password.history-size` | `0` | La nuova password non può coincidere con le ultime N, 0–24 |
| `grantforge.security.password.max-age` | senza scadenza | Validità della password; alla scadenza va cambiata obbligatoriamente all’accesso |
| `grantforge.security.lockout.max-attempts` | `5` | Numero di tentativi consecutivi falliti dopo i quali scatta il blocco |
| `grantforge.security.lockout.duration` | `15m` | Durata del blocco |

La password non può contenere il nome utente.

## Sessioni e cookie

| Opzione di configurazione | Valore predefinito | Descrizione |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | Timeout di inattività della sessione (`GRANTFORGE_SESSION_TIMEOUT`) |
| `grantforge.security.sessions.max-per-account` | `0` | Numero massimo di sessioni contemporanee per account; 0 significa senza limiti |
| `grantforge.security.sessions.activity-interval` | `1m` | Intervallo con cui viene registrata l’ultima attività della sessione |
| `grantforge.security.cookie-secure` | `false` | Impostare su `true` quando TLS termina sul proxy (`GRANTFORGE_COOKIE_SECURE`) |

## Autenticazione a due fattori

| Opzione di configurazione | Valore predefinito | Descrizione |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | Per quanto tempo una verifica in due passaggi copre le operazioni sensibili, da 1 minuto a 12 ore |
| `grantforge.security.mfa.required-for-sensitive` | `false` | Se le operazioni sensibili richiedono che l’account abbia attivato l’autenticazione a due fattori |

## Crittografia e server di autorizzazione

| Opzione di configurazione | Valore predefinito | Descrizione |
| --- | --- | --- |
| `grantforge.security.encryption-key` | generata automaticamente | Chiave Base64 a 32 byte con cui vengono cifrati i segreti memorizzati; in produzione va impostata tassativamente |
| `grantforge.oauth.issuer` | indirizzo della richiesta | Emittente OIDC, ad esempio `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | Periodo di rotazione automatica delle chiavi di firma; 0 la disattiva |
| `grantforge.oauth.signing-key-retention` | `2d` | Periodo per cui le chiavi precedenti continuano a essere pubblicate; deve essere superiore alla validità di qualsiasi token |

## Audit, plug-in e agenti

| Opzione di configurazione | Valore predefinito | Descrizione |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | Periodo di conservazione dei log di audit |
| `grantforge.audit.archive-directory` | vuota | Directory in cui archiviare gli audit scaduti prima dell’eliminazione |
| `grantforge.access-audit.retention` | `90d` | Periodo di conservazione dell’audit di accesso segnalato dagli agenti |
| `grantforge.plugins.directory` | `plugins` | Directory dei plug-in |
| `grantforge.plugins.call-timeout` | `10s` | Timeout delle chiamate ai plug-in (test di connessione, ricerca risorse) |
| `grantforge.agents.refresh-interval` | `30s` | Intervallo consigliato con cui gli agenti recuperano le policy |

## Osservabilità

| Opzione di configurazione | Valore predefinito | Descrizione |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | Se `/actuator/prometheus` è accessibile senza accesso (`GRANTFORGE_PROMETHEUS_PUBLIC`) |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | Impostare su `ecs` o `logstash` per emettere log in JSON |
