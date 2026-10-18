---
title: Apache Kafka
description: Installare e configurare il plug-in di servizio Kafka, accedere con PLAIN, SCRAM o Kerberos in chiaro o via TLS, cercare topic, gruppi, ID transazionali e utenti e gestire policy di accesso nei termini di Kafka.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in Kafka cerca topic, gruppi di consumer, ID transazionali, utenti SCRAM e token di delega, e gestisce policy di accesso nei termini di Kafka: i suoi tipi di risorsa sono i livelli di risorsa e le sue operazioni ACL i tipi di accesso. ID del plug-in e tipo di servizio sono entrambi `kafka`. Il plug-in di servizio è eseguito nel server GrantForge; l’[agente Kafka](/it/external/kafka-agent/), installato nei broker e nei controller, applica le policy.

I test di integrazione Testcontainers del repository coprono Kafka `3.7.2`, `3.9.2`, `4.1.2` e `4.3.1` con accessi SASL PLAIN e SCRAM e l’autorizzatore di Kafka, oltre a Kerberos (keytab e password) e TLS su `4.3.1`; ciò non certifica ogni altra versione.

## Installazione

La distribuzione include il plug-in in `plugins/kafka`. Verifica che `kafka` sia abilitato in **Gestione della piattaforma → Plug-in**; dopo un aggiornamento, ripeti la scansione della directory dei plug-in. Il plug-in comunica con i broker tramite il client Admin di Kafka (`kafka-clients` 4.3.1), in un class loader proprio.

## Aggiungere un servizio dati

1. Apri **Permessi sui dati → Servizi dati**, aggiungi un servizio e seleziona Apache Kafka (`kafka`).
2. Inserisci i server di bootstrap e come è protetto il listener dei broker.
3. Inserisci l’utente di ricerca, prova la connessione e salva il servizio. La prova accede e descrive il cluster: il suo ID e quanti broker ha.

Le impostazioni usano i nomi dei client di Kafka, quindi si possono copiare da una configurazione client. L’utente di ricerca permette a GrantForge di scoprire le risorse; è distinto dai client che producono e consumano. Una prova di connessione riuscita non significa che le richieste siano già soggette alle policy.

| Impostazione | Scopo |
| --- | --- |
| `bootstrap.servers` | Broker di partenza obbligatori, `host:porta` separati da virgole, come `kafka1:9092,kafka2:9092` |
| `security.protocol` | `PLAINTEXT` (predefinito), `SSL`, `SASL_PLAINTEXT` o `SASL_SSL` |
| `sasl.mechanism` | Solo protocolli SASL: `PLAIN`, `SCRAM-SHA-256` o `SCRAM-SHA-512` con password, `GSSAPI` per Kerberos |
| `username` | SASL: l’utente di ricerca; con Kerberos il principal completo, come `grantforge@EXAMPLE.COM` |
| `password` | La password dell’utente o, senza keytab, la password Kerberos del principal; memorizzata cifrata |
| `keytab` | Kerberos: percorso della keytab del principal, leggibile sul **server GrantForge** |
| `kerberos.kdc` | Kerberos: KDC del realm del principal, `host[:porta]` separati da virgole; vuoto usa il `krb5.conf` del server |
| `sasl.kerberos.service.name` | Kerberos: la prima parte dei principal dei broker, predefinito `kafka` |
| `ssl.truststore.location`, `ssl.truststore.password`, `ssl.truststore.type` | TLS: il truststore che verifica i broker (`JKS`, `PKCS12` o `PEM`); vuoto si fida di ciò di cui si fida Java |
| `ssl.keystore.location`, `ssl.keystore.password`, `ssl.key.password` | TLS: il certificato di GrantForge, per i broker che ne chiedono uno ai client |
| `ssl.verify.hostname` | TLS: se i certificati dei broker devono nominare gli host a cui ci si connette, predefinito `true` |
| `request.timeout.ms` | Timeout delle richieste, predefinito `30000`, intervallo `1..600000` |

Le impostazioni SASL sono rifiutate con `PLAINTEXT` e `SSL`, quelle TLS con `PLAINTEXT` e `SASL_PLAINTEXT`. `PLAIN` e SCRAM richiedono una password; `GSSAPI` richiede una password o una keytab.

### Kerberos

Con `GSSAPI`, GrantForge fa accedere il principal tramite il login Kerberos di Kafka stesso, con la keytab o con la password, che GrantForge consegna direttamente al login; nient’altro sul server GrantForge richiede impostazioni Kerberos. I realm i cui KDC sono inseriti in `kerberos.kdc` vengono dichiarati per l’intero server GrantForge, come fanno i plug-in HDFS e Hive, così più servizi possono usare realm diversi fianco a fianco.

### Cosa vede l’utente di ricerca

Kafka mostra a un utente solo ciò che può descrivere, quindi l’utente di ricerca ha bisogno di `describe` sui topic, sui gruppi e sugli ID transazionali da suggerire, e di `describe` sul cluster per gli utenti SCRAM. Kafka omette i topic e i gruppi che non può descrivere, perciò i suggerimenti restano vuoti invece di essere rifiutati. I topic interni, come `__consumer_offsets`, vengono suggeriti quando il testo digitato inizia con `_`. Un cluster senza token di delega, o senza SCRAM, non ne suggerisce.

Quando l’[agente Kafka](/it/external/kafka-agent/) applica le policy, queste decidono anche cosa l’utente di ricerca può descrivere: assegnagli una policy che consenta `describe`, oppure indicalo in `super.users` dei broker.

## Risorse e tipi di accesso

| Livello | Risorsa Kafka | Tipi di accesso |
| --- | --- | --- |
| `topic` | Topic | `read`, `write`, `create`, `delete`, `alter`, `describe`, `describe_configs`, `alter_configs`, `all` |
| `group` | Gruppi di consumer | `read`, `delete`, `describe`, `all` |
| `transactional_id` | ID transazionali | `write`, `describe`, `all` |
| `cluster` | Il cluster, sempre chiamato `kafka-cluster` | `create`, `alter`, `describe`, `cluster_action`, `describe_configs`, `alter_configs`, `idempotent_write`, `all` |
| `delegation_token` | Token di delega, per ID del token | `describe`, `all` |
| `user` | Utenti i cui token di delega vengono creati o descritti | `create_tokens`, `describe_tokens`, `all` |

Ogni livello è indipendente; una policy ne nomina uno. I valori distinguono maiuscole e minuscole: `*` corrisponde a un numero qualsiasi di caratteri e `?` a uno. I tipi di accesso ne implicano altri come le operazioni ACL di Kafka: `read`, `write`, `delete` e `alter` implicano `describe`, `alter_configs` implica `describe_configs`, e `all` implica tutti gli altri.

Le implicazioni valgono anche per i divieti: una policy che nega `write` su un topic nega anche il `describe` che `write` implica, così l’utente non vede più il topic. Le ACL di Kafka applicano le implicazioni solo alle operazioni consentite.

L’editor delle policy suggerisce nomi dal cluster per prefisso; per `cluster` suggerisce `kafka-cluster`.

## Esempio di policy

Supponi che i producer dell’utente `alice` scrivano nei topic il cui nome inizia con `orders`, e che il gruppo `analysts` li legga con il gruppo di consumer `billing`. Crea tre policy di accesso abilitate nello stesso servizio dati:

| Risorse | Regola |
| --- | --- |
| `topic=orders*` | Consentire `write` all’utente `alice` |
| `topic=orders*` | Consentire `read` al gruppo `analysts` |
| `group=billing` | Consentire `read` al gruppo `analysts` |

Quando l’agente applica il nuovo snapshot, `alice` può produrre in `orders-eu`, anche con un producer idempotente e senza `idempotent_write` sul cluster; i membri di `analysts` lo consumano come `billing`; gli altri topic e gruppi li rifiutano entrambi. `kafka-acls --list` mostra le tre policy come ACL con prefisso e letterali.

## Applicare le policy

Il plug-in di servizio gestisce le policy e cerca le risorse. Per applicarle serve l’agente Kafka in ogni broker e controller. Vedi l’[agente Kafka](/it/external/kafka-agent/) per la distribuzione e i controlli.

## Guide correlate

- [Servizi dati, policy e agenti](/it/external/data-services/)
- [Agente Kafka](/it/external/kafka-agent/)
- [Plug-in e tipi di servizio](/it/develop/plugins/)
