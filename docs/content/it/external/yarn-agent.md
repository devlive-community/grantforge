---
title: Agente Apache Hadoop YARN
description: Installare il provider di autorizzazione nei ResourceManager YARN per decidere con le policy GrantForge chi può inviare quali applicazioni a quale coda, amministrare le code e il cluster, e da quali reti.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agente YARN è un provider di autorizzazione del ResourceManager: YARN gli chiede chi può inviare a una coda, amministrare una coda e amministrare il cluster, e lui risponde dalle policy firmate del servizio dati `yarn`. La distribuzione lo fornisce come JAR compatibile con Java 8, `agents/yarn/grantforge-agent-yarn-<version>.jar`, con le dipendenze incluse e rilocate.

I test di integrazione Testcontainers del repository eseguono il JAR nei ResourceManager di Hadoop `3.2.4`, `3.3.6`, `3.4.3` e `3.5.0`. Ciò non certifica tutte le altre versioni. Il plug-in di servizio è descritto in [Apache Hadoop YARN](/it/plugins/yarn/).

## Funzionamento

- **Ogni controllo è deciso in ogni coda del percorso.** Come in YARN, un permesso su una coda copre quelle sottostanti, quindi l’agente decide un invio a `root.sales.etl` in `root`, `root.sales` e `root.sales.etl`: un diniego in una qualsiasi rifiuta, un consenso in una qualsiasi concede.
- **Gli invii sono decisi anche in base al nome dell’applicazione.** In ognuna di queste code l’agente chiede anche della coda con il nome dell’applicazione, così una policy può accettare solo alcune applicazioni, per esempio `payroll-*`, in una coda e in quelle sottostanti.
- **Le policy possono valere solo per alcune reti.** Ogni controllo porta l’indirizzo del client, dietro un proxy il primo inoltrato, e l’agente valuta la condizione **Indirizzi dei client** con gli elenchi di host di Hadoop stesso.
- **L’amministrazione del cluster è soggetta alle policy.** Le operazioni di amministrazione di YARN, che altrimenti concede `yarn.admin.acl`, richiedono `admin` sul cluster.
- **I rifiuti sono registrati con il motivo.** YARN formula il rifiuto che riceve il client; il log del ResourceManager nomina la policy GrantForge che ha negato, o che nessuna lo consente.
- **Le decisioni sono conservate e gli accessi raggruppati.** Le decisioni dello snapshot corrente sono conservate, per impostazione predefinita 100.000 per 10 secondi, e un nuovo snapshot ricomincia da zero. Gli accessi consentiti identici entro una finestra, per impostazione predefinita 10 secondi, sono segnalati come un unico evento di accesso che indica quanti erano; i rifiuti sono segnalati uno per uno e subito. Ogni evento nomina l’applicazione, la coda e l’indirizzo del client.
- **Il ResourceManager non è controllato.** L’utente del ResourceManager stesso, per esempio `yarn`, non è né controllato né sottoposto ad audit.

| Operazione | Accesso richiesto |
| --- | --- |
| Inviare un’applicazione | `submit_app` sulla sua coda, o sulla sua coda con il nome dell’applicazione; basta anche `admin_queue` sulla coda |
| Terminare, spostare, cambiare la priorità o vedere tutti i dettagli dell’applicazione di un altro utente | `admin_queue` sulla sua coda, e per spostarla anche `submit_app` su quella nuova; il proprietario dell’applicazione può sempre farlo, come chi è consentito dalle sue ACL |
| `yarn rmadmin`: ricaricare code, nodi, gruppi di utenti e ACL, modificare etichette e attributi dei nodi e le altre operazioni di amministrazione | `admin` sul cluster |

## Distribuzione

1. Aggiungete un servizio dati `yarn` e configuratene le policy. Aprite **Permessi sui dati → Agenti**, scegliete il servizio ed emettete un token di agente. È mostrato una sola volta; salvatelo in un file leggibile solo dall’account di YARN.
2. Copiate il JAR in `share/hadoop/yarn/lib` **di ogni ResourceManager**, per esempio `/opt/hadoop/share/hadoop/yarn/lib`.
3. Aggiungete le impostazioni al `yarn-site.xml` di ogni ResourceManager, sostituendo l’indirizzo, i percorsi, l’utente e il nome di istanza dell’esempio:

   ```xml
   <configuration>
     <property>
       <name>yarn.acl.enable</name>
       <value>true</value>
     </property>
     <property>
       <name>yarn.authorization-provider</name>
       <value>org.devlive.grantforge.yarn.agent.GrantForgeYarnAuthorizer</value>
     </property>
     <property>
       <name>yarn.admin.acl</name>
       <value>yarn</value>
     </property>
     <property>
       <name>grantforge.server.url</name>
       <value>https://grantforge.example.com</value>
     </property>
     <property>
       <name>grantforge.token.file</name>
       <value>/etc/hadoop/grantforge.token</value>
     </property>
     <property>
       <name>grantforge.instance</name>
       <value>resourcemanager-1</value>
     </property>
     <property>
       <name>grantforge.cache.dir</name>
       <value>/var/lib/hadoop-yarn/grantforge-cache</value>
     </property>
   ```

   `yarn.admin.acl` deve nominare solo l’utente del ResourceManager stesso: YARN lascia ai suoi amministratori fare qualsiasi cosa con qualsiasi applicazione prima di chiedere all’agente della coda. La directory di cache deve essere scrivibile dall’account di YARN.
4. Riavviate i ResourceManager uno dopo l’altro. Finché l’agente non ha il primo snapshot, le richieste diverse da quelle del ResourceManager sono rifiutate.
5. **Trasferite prima i permessi esistenti:** le ACL di coda della configurazione dello scheduler, per esempio `acl_submit_applications` e `acl_administer_queue`, non si applicano più. Create prima le policy corrispondenti.

| Impostazione | Scopo |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obbligatorie: il server GrantForge, il file del token di agente, un nome per questo ResourceManager e una directory scrivibile per lo snapshot firmato e gli eventi in attesa |
| `grantforge.signing.key.file` | File facoltativo che fissa la chiave di firma degli snapshot; copiate la chiave pubblica X.509 Ed25519 codificata in Base64 dalla pagina Agenti. Senza di esso, la chiave è ottenuta dal server al primo contatto |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Timeout di connessione e lettura verso GrantForge, predefiniti `5000` / `8000` millisecondi |
| `grantforge.refresh.interval.ms` | Ogni quanto l’agente chiede nuove policy, predefinito `30000`, almeno `1000` millisecondi |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Eventi di accesso: dimensione del lotto (predefinita `500`), dimensione della coda (predefinita `10000`), intervallo di invio (predefinito `5000` millisecondi) e limite dello spool (predefinito 64 MiB) |
| `grantforge.audit.fold.window.ms` | Per quanto tempo gli accessi consentiti identici sono raggruppati in un evento, predefinito `10000`, fino a `3600000` millisecondi; `0` li segnala tutti |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Numero massimo di decisioni conservate, predefinito `100000`, fino a `10000000`, `0` non ne conserva; e per quanto tempo, predefinito `10000`, `1..3600000` millisecondi |

## Utenti, gruppi e ruoli

Le policy nominano il nome breve che YARN dà al chiamante: il nome utente dell’autenticazione semplice, o il nome breve di un principal Kerberos secondo `hadoop.security.auth_to_local`. I gruppi sono quelli che la mappatura dei gruppi di Hadoop dà all’utente sul ResourceManager, insieme ai gruppi in cui GrantForge colloca l’utente; i ruoli sono quelli che GrantForge gli assegna.

## Decisioni e limiti

- Senza uno snapshot utilizzabile tutto è rifiutato, tranne le richieste del ResourceManager stesso. Un servizio non usato lascia YARN senza controlli, come con `yarn.acl.enable` disattivato.
- Un token legato a un servizio il cui tipo non è `yarn` rifiuta tutto.
- YARN chiede della propria amministrazione senza l’indirizzo del client, quindi una condizione Indirizzi dei client non vale mai su `cluster`.
- Il proprietario di un’applicazione e gli utenti nominati dalle sue ACL la gestiscono indipendentemente dalle policy, come in YARN.
- I controlli di altri tipi di accesso, per esempio le ACL di priorità delle applicazioni, sono rifiutati.
- Un nome host in una condizione Indirizzi dei client costa una risoluzione del nome del client ogni volta che una decisione è presa anziché conservata.
- Le decisioni sono conservate per `grantforge.decision.cache.ttl.ms`, quindi una policy inizia o smette di valere fino a quel tempo dopo.

## Guide correlate

- [Apache Hadoop YARN](/it/plugins/yarn/)
- [Servizi dati, policy e agenti](/it/external/data-services/)
