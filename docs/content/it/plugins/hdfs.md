---
title: Apache Hadoop HDFS
description: Installare e configurare il plug-in HDFS, esplorare le directory e gestire le policy di accesso ai percorsi.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in Apache Hadoop HDFS fornisce connessioni ai cluster, ricerca dei percorsi e gestione delle policy in GrantForge. L’ID del plug-in e il tipo di servizio sono entrambi `hdfs`.

## Installazione

La distribuzione include il plug-in in `plugins/hdfs`. Verifica che `hdfs` sia abilitato in **Gestione della piattaforma → Plug-in**; ripeti la scansione della directory dopo un aggiornamento.

## Aggiungere un servizio di dati

1. Apri **Permessi sui dati → Servizi di dati**, aggiungi un servizio e seleziona HDFS (`hdfs`).
2. Inserisci l’URI del cluster e l’utente di consultazione. Hadoop 2.x usa `webhdfs://namenode:50070`; 3.x può usare `hdfs://namenode:8020` o `webhdfs://namenode:9870`. Per HTTPS usa `swebhdfs://` con la porta effettiva del cluster.
3. Imposta la directory di consultazione e prova la connessione: la directory deve esistere e poter essere elencata. Poi salva il servizio.

| Impostazione | Utilizzo |
| --- | --- |
| `fs.default.name` | URI obbligatorio del cluster, senza sottodirectory, credenziali o parametri; HA può usare `hdfs://nameservice1` con le proprietà aggiuntive corrispondenti |
| `username` | Utente di consultazione obbligatorio; con Kerberos, un principal come `grantforge@EXAMPLE.COM` |
| `hadoop.security.authentication` | Predefinito `simple`; scegli `kerberos` per un cluster Kerberos |
| `hadoop.security.authorization` | Se Hadoop verifica i permessi, predefinito `false`; coerente con il core-site.xml del cluster |
| `hadoop.security.auth_to_local` | Regole di corrispondenza tra principal Kerberos e nomi utente, coerenti con il core-site.xml del cluster |
| `password` / `keytab` | Password Kerberos o percorso di un file keytab sul server GrantForge |
| `dfs.namenode.kerberos.principal`, `dfs.datanode.kerberos.principal`, `dfs.secondary.namenode.kerberos.principal` | Principal dei componenti del cluster con Kerberos, come `nn/_HOST@EXAMPLE.COM`, coerenti con la configurazione del cluster |
| `lookup.path` | Directory iniziale di ricerca ed esplorazione, predefinita `/`, ad esempio `/data`; l’esplorazione resta al suo interno |
| `lookup.max.entries` | Limite delle scansioni complete, predefinito `10000`, intervallo `1..100000` |
| `hadoop.config` | Un `key=value` per riga per HA e altre proprietà Hadoop; sovrascrive le impostazioni di connessione omonime |
| `hadoop.rpc.protection` | `authentication`, `integrity` o `privacy`, secondo il cluster |

Con l’estensione degli attributi NameNode abilitata in Hadoop 2.7.7, gli utenti ordinari che interrogano il percorso radice `/` causano una `NullPointerException` upstream confermata; imposta `lookup.path` su una directory esistente come `/data` (vedi la [guida dell’agente](/it/external/hdfs-agent/)).

Kerberos richiede anche un KDC raggiungibile, il `krb5.conf` del server e regole `hadoop.security.auth_to_local` e principal di servizio coerenti. L’account di consultazione recupera i metadati delle directory.

La configurazione viene validata al salvataggio: in `hadoop.config`, `fs.defaultFS` e `fs.default.name` sono alias, quindi configurarne uno solo; l’URI del cluster non deve contenere credenziali, percorso, query o frammento; `kerberos` richiede una `password` o un `keytab`; `lookup.path` deve essere un percorso assoluto senza `..`; `lookup.max.entries` deve essere compreso tra `1` e `100000`. Le proprietà aggiuntive sovrascrivono le impostazioni di connessione omonime, e sia la validazione sia l’accesso usano i valori sovrascritti.

## Esplorare i percorsi

Seleziona il servizio in **Permessi sui dati → Policy** e usa **Esplora** accanto a `path`.

- Apri le directory, naviga nel percorso o torna alla directory padre e carica le pagine successive quando necessario.
- Consulta gli indicatori file/directory, proprietario, gruppo, permessi, dimensione e data di modifica.
- Seleziona più file o directory, oppure la directory corrente, da aggiungere alla policy; la digitazione dei percorsi continua a proporre suggerimenti.

RPC e gli endpoint WebHDFS con elenchi in batch usano la paginazione nativa. Gli endpoint meno recenti leggono le directory entro il limite di scansione e segnalano un errore oltre tale limite. Gli errori di permessi, autenticazione o connessione mostrano la causa e permettono di riprovare.

L’input può omettere la `/` iniziale, `/` e `.` ripetuti sono consentiti, mentre `..` e i percorsi assoluti fuori da `lookup.path` vengono rifiutati; una directory inesistente non restituisce candidati. L’esplorazione non sostituisce il controllo di accesso di HDFS; collegamenti simbolici e mount ViewFS seguono la configurazione del cluster.

## Applicare le policy

L’unico livello di risorsa è `path`, con accessi `read`, `write` ed `execute`. Le policy dei percorsi supportano ricorsione ed esclusioni.

Il plug-in del server gestisce e consulta le risorse. Per applicare le policy occorre anche l’agente NameNode corrispondente alla versione Hadoop; gli utenti devono soddisfare sia i permessi HDFS nativi sia le policy GrantForge. Gli agenti numerati coprono 2.7, 2.10, 3.2, 3.3, 3.4 e 3.5. La guida dell’agente riporta combinazioni verificate, copertura di autenticazione e HA e limiti del superutente.

## Guide correlate

- [Servizi di dati, policy e agenti](/it/external/data-services/)
- [Agente NameNode Apache Hadoop HDFS](/it/external/hdfs-agent/)
- [Sviluppo di plug-in e tipi di servizio](/it/develop/plugins/)
