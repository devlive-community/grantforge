---
title: Agente Apache ZooKeeper
description: Distribuisci l’agente su ogni nodo ZooKeeper per applicare le policy GrantForge tramite le ACL marcatore.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Il plug-in del server `grantforge-plugin-zookeeper` fornisce in modo unificato il tipo di servizio `zookeeper`, le risorse e la configurazione di connessione. Per applicare le policy serve un secondo componente, che gira dentro ZooKeeper stesso: l’agente, che partecipa alle decisioni sulle ACL dei server come provider di autenticazione SASL. Si trova in `agents/grantforge-agent-zookeeper`; il comando `mvn -pl agents/grantforge-agent-zookeeper -am package` produce un singolo JAR shaded compatibile con Java 8, completo delle dipendenze, che la distribuzione include in `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`. L’implementazione è destinata alle API di ZooKeeper 3.6–3.9; la compatibilità con le versioni effettive deve ancora essere verificata. Non si dichiara quindi superata l’intera matrice delle versioni né concluso il collaudo per la produzione.

## Distribuzione

1. Installa il JAR nella directory `lib` di **ogni server ZooKeeper**, inclusi tutti i nodi che possono ricevere richieste dai client. Crea le **credenziali Agent** nel servizio di dati `zookeeper` corrispondente in GrantForge. Salva il token in un file separato, leggibile solo dall’account che esegue ZooKeeper; non scrivere il token stesso in `zoo.cfg`.
2. Aggiungi queste impostazioni al `zoo.cfg` di ogni server, sostituendo indirizzo, percorsi e identificativo dell’istanza dell’esempio. `grantforge.server.url` (`http[s]://GrantForge`), `grantforge.token.file`, una directory locale `grantforge.cache.dir` e `grantforge.instance` sono obbligatori. L’account che esegue ZooKeeper deve poter scrivere nella directory della cache.

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. Questo provider **sostituisce il provider SASL nativo**; non mantenere un altro provider registrato per lo stesso schema `sasl` in altre voci `authProvider.N`. `enableEagerACLCheck=false` è obbligatorio: con il controllo attivo, la registrazione del provider viene rifiutata e il marcatore non consente l’accesso. Mantieni e verifica la normale autenticazione ZooKeeper con SASL, JAAS e Kerberos; l’agente non sostituisce l’autenticazione. Riavvia i server secondo la procedura di manutenzione dell’ensemble.
4. Puoi impostare `grantforge.signing.key.file` sul file della chiave pubblica di firma fissata come attendibile. `grantforge.principal.mapping=short` è il valore predefinito e converte `kafka/host@REALM` in `kafka`; `full` mantiene il principal completo. Gli utenti delle policy devono corrispondere alle identità risultanti.

## ACL e limitazioni

- Solo l’ACL marcatore `sasl:grantforge` delega alle policy GrantForge, ad esempio `sasl:grantforge:cdrwa`, dove `c=create`, `d=delete`, `r=read`, `w=write` e `a=admin`. Le altre ACL native, comprese le altre identità SASL, continuano a funzionare normalmente.
- Il marcatore nega l’accesso se l’agente non è in esecuzione, manca uno snapshot utilizzabile delle policy, il suo `serviceType` non è `zookeeper` o nessuna policy corrisponde/consente l’accesso. **Non è un diniego globale**: altre ACL, `world:anyone` o un’identità `super` possono ancora autorizzare la richiesta.
- Le policy confrontano il percorso assoluto di destinazione sul server. Per `create`/`delete` è lo znode da creare/eliminare, non il padre che fornisce l’ACL. Con chroot, il percorso della policy deve includere il prefisso chroot effettivo: `/brokers` sotto il chroot `/kafka` corrisponde a `/kafka/brokers`. Il `create` sequenziale verifica il prefisso assoluto della richiesta sul server prima di aggiungere il numero di sequenza; i successivi `read`/`delete` verificano il nome completo del nodo generato.
- Le ACL native non vengono migrate automaticamente e le ACL non vengono ereditate ricorsivamente; i nuovi znode richiedono un’ACL marcatore esplicita. Prima di usare `setACL`, verifica accessi consentiti e negati su un ambito ristretto e conserva un canale di amministrazione indipendente. Non applicare il marcatore al nodo radice senza un’attenta verifica, per evitare di bloccare l’accesso.
- Un server senza agente tratta `sasl:grantforge` come l’identità SASL nativa `grantforge`, anziché valutare le policy. La distribuzione su tutti i nodi è quindi necessaria; una distribuzione parziale non protegge l’ensemble.
- Le scritture inoltrate da un follower/observer al leader possono non avere una connessione client in questa interfaccia del provider, quindi il contesto `clientAddress` può essere vuoto. Non sono garantite policy end-to-end nell’ensemble basate sull’IP del client.
- Questo provider non può conoscere il risultato finale della richiesta: non invia quindi eventi di audit finali `AccessEvent` e non offre un audit completo degli accessi.
