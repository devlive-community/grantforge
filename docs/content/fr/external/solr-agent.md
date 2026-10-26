---
title: Agent Apache Solr
description: Installer le plug-in d’autorisation dans les nœuds Solr pour décider, avec les politiques GrantForge, qui peut interroger et mettre à jour quelles collections, par quels chemins de requête, administrer quelles collections et le cluster, et depuis quels réseaux.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent Solr est un plug-in d’autorisation des nœuds : Solr l’interroge sur chaque requête, et il répond à partir des politiques signées du service de données `solr`. La distribution le fournit sous forme d’un JAR Java 11, `agents/solr/grantforge-agent-solr-<version>.jar`, avec ses dépendances embarquées et relocalisées.

Les tests d’intégration Testcontainers du dépôt exécutent le JAR dans des nœuds SolrCloud de Solr `9.8.1`, `9.10.1`, `9.11.0` et `10.0.0`. Cela ne certifie pas toutes les autres versions. Le plug-in de service est décrit dans [Apache Solr](/fr/plugins/solr/).

## Fonctionnement

- **Les requêtes sont classées comme Solr les classe.** Chaque gestionnaire de requêtes nomme la permission dont il a besoin, comme `read` ou `collection-admin-edit`, ainsi qu’il le fait pour le plug-in à base de règles de Solr ; l’agent transforme ce nom en type d’accès, et un gestionnaire qui n’en nomme aucune est décidé selon le type de la requête.
- **Chaque requête vers une collection est aussi décidée sur son chemin de requête.** L’agent décide une interrogation de `sales` par `/select` sur la collection `sales` et sur `sales` avec le chemin `/select` : un refus sur l’un ou l’autre la rejette, de sorte qu’une politique peut refuser un gestionnaire d’une collection.
- **Les équipes administrent leurs propres collections.** Une requête de l’API Collections qui nomme des collections exige `admin_collection` sur chacune, de sorte qu’une politique peut ne laisser une équipe créer, recharger ou supprimer que les collections dont le nom commence par son préfixe.
- **Les administrateurs du cluster peuvent tout faire.** `admin` sur le cluster accorde toute requête, comme la permission `all` de Solr.
- **Les politiques peuvent ne valoir que pour certains réseaux.** Chaque vérification porte l’adresse du client ; derrière les proxys inverses auxquels l’agent fait confiance, le client est la dernière adresse de `X-Forwarded-For` qu’aucun proxy de confiance n’a ajoutée.
- **Les rôles vérifiés d’un jeton web JSON comptent comme groupes.** Avec l’authentification JWT de Solr, les rôles que la revendication du jeton donne à l’utilisateur sont des groupes de la décision, à côté de ceux dans lesquels GrantForge place l’utilisateur.
- **Les refus sont journalisés avec leur raison.** Solr répond à une requête refusée par son propre 403 ; le journal du nœud nomme la politique GrantForge qui l’a refusée, ou indique qu’aucune ne l’autorise.
- **Les décisions sont conservées et les accès regroupés.** Les décisions de l’instantané courant sont conservées, par défaut 100 000 pendant 10 secondes, et un nouvel instantané repart de zéro. Les accès autorisés identiques dans une fenêtre, par défaut 10 secondes, sont signalés comme un seul événement d’accès indiquant leur nombre ; les refus sont signalés un par un, immédiatement. Chaque événement nomme la collection et le chemin, ou le cluster, ainsi que l’adresse du client.
- **Les nœuds ne sont pas vérifiés.** Les requêtes entre les nœuds, que Solr signe au nom du nœud lui-même, passent ; une requête sans utilisateur est invitée à se connecter.

| Requête | Accès requis |
| --- | --- |
| Rechercher, obtenir, exporter, interroger par tout gestionnaire de lecture | `query` sur la collection et le chemin |
| Ajouter, mettre à jour ou supprimer des documents | `update` sur la collection et le chemin |
| Lire ou modifier le schéma d’une collection | `schema_read` ou `schema_edit` |
| Lire ou modifier la config d’une collection | `config_read` ou `config_edit` |
| Requêtes de l’API Collections qui nomment des collections : créer, recharger, sauvegarder, restaurer, supprimer, modifier, leur état dans le cluster | `admin_collection` sur chaque collection nommée |
| Contrôles de santé ; métriques | `health` ; `metrics` sur le cluster |
| Lister les collections, état des cores, lectures de ZooKeeper, du file store et des paquets | `admin_read` sur le cluster |
| Lire ou modifier l’authentification et l’autorisation | `security` sur le cluster |
| Administration des cores, modifications du file store et des paquets, et ce qu’aucun gestionnaire ne nomme | `admin` sur le cluster |

## Déploiement

1. Ajoutez un service de données `solr` et configurez ses politiques, y compris `admin` sur le cluster pour les administrateurs de Solr. Ouvrez **Autorisations sur les données → Agents**, choisissez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; placez-le dans un fichier que seul le compte de Solr peut lire.
2. Copiez le JAR dans `server/solr-webapp/webapp/WEB-INF/lib` de **chaque nœud**, par exemple `/opt/solr/server/solr-webapp/webapp/WEB-INF/lib`, et redémarrez les nœuds un par un.
3. Définissez la section d’autorisation du `security.json` du cluster, à côté d’un plug-in d’authentification, en remplaçant l’adresse, les chemins et le nom d’instance de l’exemple :

   ```json
   {
     "authentication": {
       "class": "solr.BasicAuthPlugin",
       "blockUnknown": true,
       "credentials": { "solr": "<salted SHA-256 hash> <salt>" }
     },
     "authorization": {
       "class": "org.devlive.grantforge.solr.agent.GrantForgeSolrAuthorizer",
       "grantforge.server.url": "https://grantforge.example.com",
       "grantforge.token.file": "/var/solr/data/grantforge.token",
       "grantforge.instance": "solr-cluster",
       "grantforge.cache.dir": "/var/solr/data/grantforge-cache"
     }
   }
   ```

   Téléversez-le avec `bin/solr zk cp file:security.json zk:/security.json -z <zookeeper>` ; les nœuds le prennent en compte sans redémarrage. L’authentification doit être activée, afin que les requêtes aient des utilisateurs.
4. **Sous Solr 9, tenez compte du gestionnaire de sécurité de Java**, que Solr active par défaut : placez le jeton, la clé épinglée et le répertoire de cache sous `SOLR_HOME`, par exemple `/var/solr/data`, et accordez à l’agent ce dont il a besoin au-delà de la politique de Solr, dans `server/etc/security.policy` ou dans un fichier de politique à vous que `-Djava.security.properties` ajoute comme `policy.url.3` :

   ```text
   grant {
     permission java.security.SecurityPermission "getProperty.org.devlive.grantforge.solr.agent.internal.bouncycastle.*";
   };
   ```

   Solr 10 s’exécute sans gestionnaire de sécurité.
5. **Migrez d’abord les permissions existantes :** les permissions et les rôles d’utilisateur du plug-in à base de règles de Solr ne s’appliquent plus. Créez au préalable les politiques correspondantes. Tant que l’agent n’a pas son premier instantané, les requêtes autres que celles entre les nœuds sont refusées.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour les nœuds du cluster et un répertoire accessible en écriture pour l’instantané signé et les événements mis en file |
| `grantforge.trusted.proxies` | Proxys inverses dont l’agent croit le `X-Forwarded-For`, adresses ou plages séparées par des virgules ; aucun par défaut |
| `grantforge.signing.key.file` | Fichier facultatif épinglant la clé de signature des instantanés ; copiez la clé publique X.509 Ed25519 encodée en Base64 depuis la page Agents. S’il est omis, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, `5000` / `8000` millisecondes par défaut |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, `30000` par défaut, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (`500` par défaut), taille de file (`10000` par défaut), intervalle d’envoi (`5000` millisecondes par défaut) et limite du tampon disque (64 Mio par défaut) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle les accès autorisés identiques sont regroupés en un événement, `10000` par défaut, jusqu’à `3600000` millisecondes ; `0` les signale un par un |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Le nombre maximal de décisions conservées, `100000` par défaut, jusqu’à `10000000`, `0` n’en conserve aucune ; et leur durée, `10000` par défaut, `1..3600000` millisecondes |

## Utilisateurs, groupes et rôles

Les politiques nomment l’utilisateur que l’authentification de Solr connecte, comme un utilisateur de l’authentification basic ou le principal d’un jeton web JSON. Les groupes sont les rôles qu’un jeton web JSON a vérifiés pour l’utilisateur, avec les groupes dans lesquels GrantForge place l’utilisateur ; les rôles sont ceux que GrantForge donne à l’utilisateur.

## Décisions et limites

- Sans instantané utilisable, tout est refusé sauf les requêtes entre les nœuds. Un service qui n’est pas utilisé laisse Solr sans contrôle, comme sans plug-in d’autorisation.
- Un jeton lié à un service dont le type n’est pas `solr` refuse tout.
- Une requête de l’API Collections qui ne nomme ses collections que dans un corps JSON, comme certaines requêtes de l’API v2, n’en nomme aucune que l’agent puisse voir, et exige `admin` sur le cluster.
- Solr résout les alias en leurs collections avant de demander ; les politiques nomment donc des collections, pas des alias.
- L’API `/admin/authorization` de Solr ne peut pas modifier les règles de l’agent : GrantForge détient les politiques.
- Les adresses des clients sont uniquement des adresses et des plages ; un nom d’hôte ne correspond à rien.
- Les décisions sont conservées pendant `grantforge.decision.cache.ttl.ms`, de sorte qu’une politique commence ou cesse de s’appliquer jusqu’à ce délai plus tard.

## Guides associés

- [Apache Solr](/fr/plugins/solr/)
- [Services de données, politiques et agents](/fr/external/data-services/)
