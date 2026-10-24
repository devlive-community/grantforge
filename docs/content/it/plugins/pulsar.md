---
title: Apache Pulsar
description: Installare e configurare il plug-in di servizio Pulsar, raggiungere uno o più broker, accedere con un token, una password, OAuth 2.0 o un certificato client, cercare tenant, namespace, topic e sottoscrizioni e gestire chi può produrre, consumare e amministrare cosa.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in Pulsar cerca tenant, namespace, topic, sottoscrizioni e cluster, e gestisce le policy di accesso su ciò che Pulsar decide: produrre, consumare, functions, sources, sinks e packages, e amministrare topic, namespace, tenant e cluster. L’ID del plug-in e il tipo di servizio sono entrambi `pulsar`. Il plug-in di servizio è eseguito nel server GrantForge; l’[agente Pulsar](/it/external/pulsar-agent/) installato nei broker applica le policy.

I test di integrazione Testcontainers del repository coprono i broker di Pulsar `3.0.17`, `3.3.9`, `4.0.14` e `4.2.5`; ciò non certifica tutte le altre versioni.

## Installazione

La distribuzione include il plug-in in `plugins/pulsar`. Verificate in **Gestione della piattaforma → Plug-in** che `pulsar` sia abilitato; dopo un aggiornamento, ripetete la scansione della directory dei plug-in. Il plug-in comunica con il servizio web dei broker tramite il client di amministrazione di Pulsar stesso, versione 4.2.5, nel proprio class loader.

## Aggiungere un servizio dati

1. Aprite **Permessi sui dati → Servizi dati**, aggiungete un servizio e scegliete Apache Pulsar (`pulsar`).
2. Inserite l’URL del servizio web di un broker o di più broker.
3. Scegliete come accede il ruolo di ricerca, inseritene le credenziali, testate la connessione e salvate il servizio. Il test accede e descrive il cluster: i suoi cluster e quanti tenant ha.

Le impostazioni portano i nomi del `client.conf` di Pulsar e si possono copiare da lì. Il ruolo di ricerca permette a GrantForge di scoprire le risorse; è distinto dai ruoli che producono e consumano. Un test di connessione riuscito non significa che l’accesso sia già soggetto alle policy.

| Impostazione | Scopo |
| --- | --- |
| `webServiceUrl` | Obbligatoria: il servizio web di un broker, `http://host:port` o `https://host:port`, porta predefinita `8080` o `8443`; più broker separati da virgole, per esempio `http://broker1:8080,broker2:8080` |
| `authentication` | `none` (predefinito), `token`, `basic`, `oauth2` o `tls` |
| `token` | token: il token del ruolo di ricerca; memorizzato cifrato |
| `username`, `password` | basic: nome utente e password del ruolo di ricerca; la password è memorizzata cifrata |
| `issuerUrl`, `privateKey`, `audience`, `scope` | oauth2: il server di autorizzazione, il percorso del file JSON di credenziali con `client_id` e `client_secret` sul **server GrantForge**, e l’audience e lo scope, se i broker li richiedono |
| `tlsCertificateFilePath`, `tlsKeyFilePath` | tls: percorsi del certificato client e della sua chiave privata, PEM, sul **server GrantForge** |
| `tlsTrustCertsFilePath` | https: i certificati CA con cui si verificano quelli dei broker, PEM; vuoto usa quelli della JVM |
| `tlsEnableHostnameVerification` | https: se il certificato di un broker deve nominare il suo host, predefinito `true` |
| `timeout` | Quanto può durare una richiesta, predefinito `30000`, intervallo `1..600000` millisecondi |

Le impostazioni di un modo di accesso sono rifiutate con un altro, così un servizio dice esattamente come accede. Un accesso rifiutato, un ruolo che non può vedere qualcosa e un broker irraggiungibile, anche dietro un proxy che risponde al suo posto, sono segnalati come tali.

### Cosa vede il ruolo di ricerca

Pulsar lascia elencare cluster e tenant solo ai suoi superutenti. Nominate il ruolo di ricerca in `superUserRoles` dei broker oppure, installato l’agente, consentitegli `admin` sul cluster, il che lo rende a sua volta superutente. Namespace, topic e sottoscrizioni che il ruolo di ricerca non può vedere restano fuori dai suggerimenti.

## Risorse e tipi di accesso

| Livello | Corrispondenza | Tipi di accesso |
| --- | --- | --- |
| `tenant` | Carattere jolly, distingue le maiuscole | Tutti tranne `admin` |
| `namespace` | Carattere jolly, distingue le maiuscole; sotto un tenant; il nome proprio del namespace, per esempio `orders` | `produce`, `consume`, `functions`, `sources`, `sinks`, `packages`, `admin_topic`, `admin_namespace` |
| `topic` | Carattere jolly, distingue le maiuscole; sotto un namespace; il nome proprio del topic, per esempio `payments` | `produce`, `consume`, `admin_topic` |
| `subscription` | Carattere jolly, distingue le maiuscole; sotto un topic; il nome della sottoscrizione | `consume` |
| `cluster` | Carattere jolly, distingue le maiuscole; il nome del cluster, per esempio `standalone` | `admin`: l’accesso da superutente di Pulsar |

| Tipo di accesso | Implica |
| --- | --- |
| `produce`, `consume`, `functions`, `sources`, `sinks`, `packages` | — |
| `admin_topic`: terminare, compattare, scaricare sullo storage a livelli, scaricare dal broker e troncare i topic, e cambiarne le policy | `produce`, `consume` |
| `admin_namespace`: creare ed eliminare topic, e cambiare le policy del namespace | `admin_topic` |
| `admin_tenant`: creare, elencare ed eliminare namespace | `admin_namespace`, `functions`, `sources`, `sinks`, `packages` |
| `admin`: tutto ciò che possono i superutenti di Pulsar | `admin_tenant` |

Come con i permessi di Pulsar stesso, un permesso su un tenant o un namespace copre ciò che contiene: `produce` sul namespace `orders` del tenant `sales` produce in ognuno dei suoi topic. Un controllo è deciso in sequenza sul tenant, sul namespace, sul topic e sulla sottoscrizione, e un diniego su uno qualsiasi lo rifiuta; così una policy può consentire un namespace e negare uno dei suoi topic. Sotto un topic, un livello sottoscrizione indica tramite quali sottoscrizioni un ruolo può consumare: `consume` sul topic `*` con la sottoscrizione `billing-*` lascia consumare a un ruolo ogni topic del namespace, ma solo tramite sottoscrizioni il cui nome inizia con `billing-`.

Un topic è deciso dal suo nome proprio, senza il dominio, così un topic persistente e uno non persistente con lo stesso nome condividono le policy; una partizione di un topic partizionato è decisa come il topic partizionato. Nei valori delle policy, `*` corrisponde a un numero qualsiasi di caratteri e `?` a uno solo. L’editor delle policy suggerisce i cluster, i tenant, e i namespace, topic e sottoscrizioni di ciò che una policy nomina.

### Indirizzi dei client

Un elemento di policy può valere solo per alcuni client: la sua condizione **Indirizzi dei client** (`ip-range`) accetta indirizzi e intervalli come `10.1.0.0/16` o `2001:db8::/32`. I nomi host non vengono risolti. Dietro un proxy il client è il proxy, a meno che inoltri l’indirizzo del client stesso: con il protocollo HAProxy per producer e consumer, e con `X-Forwarded-For` per il servizio web quando i broker se ne fidano.

## Esempio di policy

Supponete che il tenant `sales` abbia i namespace `orders` e `returns`. Create queste policy di accesso abilitate nello stesso servizio dati:

| Risorse | Regola |
| --- | --- |
| `tenant=sales`, `namespace=orders` | Consentire `produce` al gruppo `analysts` |
| `tenant=sales`, `namespace=orders`, `topic=audit` | Negare `produce` all’utente `alice` |
| `tenant=sales`, `namespace=orders`, `topic=*`, `subscription=billing-*` | Consentire `consume` all’utente `alice` |
| `tenant=sales` | Consentire `admin_tenant` all’utente `bob` |
| `tenant=public` | Consentire `produce` al gruppo `ingest`, da `10.0.0.0/8` |
| `cluster=standalone` | Consentire `admin` al gruppo `pulsar-admins` |

Quando l’agente applica il nuovo snapshot, i membri di `analysts` producono in ogni topic di `sales/orders`, `alice` in tutti tranne `audit`; `alice` vi consuma solo tramite le sottoscrizioni `billing-`; `bob` crea e amministra namespace e topic in `sales`; i membri di `ingest` producono nel tenant `public` solo dalla rete interna; e i membri di `pulsar-admins` sono superutenti di Pulsar.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Per applicarle serve l’agente Pulsar in ogni broker. La distribuzione e i controlli sono descritti nell’[agente Pulsar](/it/external/pulsar-agent/).

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente Pulsar](/it/external/pulsar-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
