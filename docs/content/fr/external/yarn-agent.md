---
title: Agent Apache Hadoop YARN
description: Installer le fournisseur d’autorisation dans les ResourceManagers YARN pour décider avec les politiques GrantForge qui peut soumettre quelles applications à quelle file d’attente, administrer les files et le cluster, et depuis quels réseaux.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent YARN est un fournisseur d’autorisation du ResourceManager : YARN lui demande qui peut soumettre à une file d’attente, administrer une file et administrer le cluster, et il répond à partir des politiques signées du service de données `yarn`. La distribution le fournit sous la forme d’un JAR compatible Java 8, `agents/yarn/grantforge-agent-yarn-<version>.jar`, dépendances incluses et relocalisées.

Les tests d’intégration Testcontainers du dépôt exécutent le JAR dans les ResourceManagers de Hadoop `3.2.4`, `3.3.6`, `3.4.3` et `3.5.0`. Cela ne certifie pas toutes les autres versions. Le plug-in de service est décrit dans [Apache Hadoop YARN](/fr/plugins/yarn/).

## Fonctionnement

- **Chaque vérification est décidée dans chaque file du chemin.** Comme dans YARN, une permission sur une file d’attente couvre celles en dessous ; l’agent décide donc une soumission à `root.sales.etl` dans `root`, `root.sales` et `root.sales.etl` : un refus dans l’une d’elles rejette, une autorisation dans l’une d’elles accorde.
- **Les soumissions sont aussi décidées par le nom de l’application.** Dans chacune de ces files, l’agent interroge aussi la file avec le nom de l’application, de sorte qu’une politique peut n’accepter que certaines applications, par exemple `payroll-*`, dans une file et celles en dessous.
- **Les politiques peuvent ne valoir que pour certains réseaux.** Chaque vérification porte l’adresse du client, derrière un proxy la première transmise, et l’agent évalue la condition **Adresses des clients** avec les listes d’hôtes de Hadoop.
- **L’administration du cluster est soumise aux politiques.** Les opérations d’administration de YARN, qu’accorde sinon `yarn.admin.acl`, exigent `admin` sur le cluster.
- **Les refus sont journalisés avec leur raison.** YARN formule le refus que reçoit le client ; le journal du ResourceManager nomme la politique GrantForge qui a refusé, ou indique qu’aucune n’autorise.
- **Les décisions sont conservées et les accès regroupés.** Les décisions de l’instantané courant sont conservées, par défaut 100 000 pendant 10 secondes, et un nouvel instantané repart de zéro. Les accès autorisés identiques dans une fenêtre, par défaut 10 secondes, sont signalés comme un seul événement d’accès indiquant leur nombre ; les refus sont signalés un par un et immédiatement. Chaque événement nomme l’application, la file d’attente et l’adresse du client.
- **Le ResourceManager n’est pas vérifié.** L’utilisateur propre du ResourceManager, par exemple `yarn`, n’est ni vérifié ni audité.

| Opération | Accès requis |
| --- | --- |
| Soumettre une application | `submit_app` sur sa file d’attente, ou sur sa file avec le nom de l’application ; `admin_queue` sur la file suffit aussi |
| Arrêter, déplacer, changer la priorité ou voir tous les détails de l’application d’un autre utilisateur | `admin_queue` sur sa file d’attente, et pour un déplacement aussi `submit_app` sur la nouvelle ; le propriétaire de l’application le peut toujours, tout comme ceux qu’autorisent ses propres ACL |
| `yarn rmadmin` : recharger les files, les nœuds, les groupes d’utilisateurs et les ACL, modifier les étiquettes et attributs de nœud, et les autres opérations d’administration | `admin` sur le cluster |

## Déploiement

1. Ajoutez un service de données `yarn` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, choisissez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; placez-le dans un fichier que seul le compte de YARN peut lire.
2. Copiez le JAR dans `share/hadoop/yarn/lib` **de chaque ResourceManager**, par exemple `/opt/hadoop/share/hadoop/yarn/lib`.
3. Ajoutez les paramètres au `yarn-site.xml` de chaque ResourceManager, en remplaçant l’adresse, les chemins, l’utilisateur et le nom d’instance de l’exemple :

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
   </configuration>
   ```

   `yarn.admin.acl` ne doit nommer que l’utilisateur propre du ResourceManager : YARN laisse ses administrateurs tout faire sur toute application avant de demander à l’agent pour la file d’attente. Le répertoire de cache doit être accessible en écriture au compte de YARN.
4. Redémarrez les ResourceManagers l’un après l’autre. Tant que l’agent n’a pas son premier instantané, les requêtes autres que celles du ResourceManager sont refusées.
5. **Transférez d’abord les permissions existantes :** les ACL de file de la configuration de l’ordonnanceur, par exemple `acl_submit_applications` et `acl_administer_queue`, ne s’appliquent plus. Créez au préalable les politiques correspondantes.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour ce ResourceManager et un répertoire inscriptible pour l’instantané signé et les événements mis en attente |
| `grantforge.signing.key.file` | Fichier facultatif qui fixe la clé de signature des instantanés ; copiez la clé publique X.509 Ed25519 encodée en Base64 depuis la page Agents. Sans lui, la clé est obtenue du serveur au premier contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, par défaut `5000` / `8000` millisecondes |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, par défaut `30000`, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (par défaut `500`), taille de file (par défaut `10000`), intervalle d’envoi (par défaut `5000` millisecondes) et limite du spool (par défaut 64 Mio) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle des accès autorisés identiques sont regroupés en un événement, par défaut `10000`, jusqu’à `3600000` millisecondes ; `0` les signale tous |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Nombre maximal de décisions conservées, par défaut `100000`, jusqu’à `10000000`, `0` n’en conserve aucune ; et leur durée, par défaut `10000`, `1..3600000` millisecondes |

## Utilisateurs, groupes et rôles

Les politiques nomment le nom court que YARN donne à l’appelant : le nom d’utilisateur de l’authentification simple, ou le nom court d’un principal Kerberos selon `hadoop.security.auth_to_local`. Les groupes sont ceux que le mappage de groupes de Hadoop donne à l’utilisateur sur le ResourceManager, plus les groupes auxquels GrantForge rattache l’utilisateur ; les rôles sont ceux que GrantForge lui attribue.

## Décisions et limites

- Sans instantané utilisable, tout est refusé sauf les requêtes propres du ResourceManager. Un service non utilisé laisse YARN sans vérification, comme avec `yarn.acl.enable` désactivé.
- Un jeton lié à un service dont le type n’est pas `yarn` refuse tout.
- YARN pose les questions d’administration sans l’adresse du client, une condition Adresses des clients ne tient donc jamais sur `cluster`.
- Le propriétaire d’une application et les utilisateurs que nomment ses propres ACL la gèrent quelles que soient les politiques, comme dans YARN.
- Les vérifications d’autres types d’accès, par exemple les ACL de priorité d’application, sont refusées.
- Un nom d’hôte dans une condition Adresses des clients coûte une résolution de nom du client chaque fois qu’une décision est prise plutôt que conservée.
- Les décisions sont conservées pendant `grantforge.decision.cache.ttl.ms` ; une politique commence ou cesse donc de s’appliquer jusqu’à ce délai plus tard.

## Guides associés

- [Apache Hadoop YARN](/fr/plugins/yarn/)
- [Services de données, politiques et agents](/fr/external/data-services/)
