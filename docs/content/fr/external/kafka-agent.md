---
title: Agent Apache Kafka
description: Installer l’agent dans les brokers et contrôleurs Kafka pour autoriser chaque requête avec les politiques GrantForge, afficher les politiques comme des ACL en lecture seule et regrouper les accès répétés en un seul événement d’audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent Kafka est un autoriseur de l’API `Authorizer` de Kafka, nommé dans `authorizer.class.name` : brokers et contrôleurs l’interrogent sur chaque requête, et il répond à partir des politiques signées du service de données `kafka`. La distribution le fournit sous forme d’un JAR compatible Java 8, `agents/kafka/grantforge-agent-kafka-<version>.jar`, avec ses dépendances relocalisées à l’intérieur.

Les tests d’intégration Testcontainers du dépôt exécutent le JAR dans Kafka `3.7.2`, `3.9.2`, `4.1.2` et `4.3.1`, dans des nœuds KRaft à la fois broker et contrôleur. Cela ne certifie pas toutes les autres versions. Voir [Apache Kafka](/fr/plugins/kafka/) pour le plug-in de service.

## Fonctionnement

- **Chaque requête est vérifiée.** Pour chaque ressource qu’une requête touche, Kafka demande une opération ACL ; l’agent répond à partir des politiques, avec les types de ressources de Kafka comme niveaux de ressource et ses opérations comme types d’accès. Un refus parvient au client sous la forme de l’erreur propre à Kafka, comme `TOPIC_AUTHORIZATION_FAILED`.
- **Un producteur idempotent n’a besoin que d’un topic.** Kafka laisse démarrer un producteur sans `idempotent_write` sur le cluster s’il peut écrire dans un topic, et le demande à l’autoriseur. L’agent répond à partir des politiques elles-mêmes : un `write` autorisé sur n’importe quel topic compte, et un refus ne compte que dans une politique qui couvre tous les topics.
- **Les politiques apparaissent comme des ACL.** `kafka-acls --list` et `describeAcls` de l’API Admin affichent les politiques qui s’appliquent maintenant : une valeur sans caractère générique comme ACL littérale, une valeur comme `orders*` comme ACL préfixée et `*` comme caractère générique de Kafka ; les utilisateurs comme principaux `User:`, les groupes comme `Group:`, les rôles comme `Role:` et le groupe `public` comme `User:*`. Ce que les ACL ne peuvent pas exprimer est omis : les autres caractères génériques, les valeurs exclues, les éléments avec conditions et les éléments d’une politique qui comporte des exceptions. Créer ou supprimer des ACL est refusé : *the ACLs of this cluster are GrantForge policies; change them in GrantForge*.
- **Les décisions sont conservées.** Un broker pose les mêmes questions à chaque production et chaque lecture ; l’agent conserve les décisions de l’instantané courant, par défaut 100 000 pendant 10 secondes. Un nouvel instantané repart de zéro, si bien qu’un changement de politique s’applique dès que l’agent le reçoit.
- **Les accès répétés sont regroupés.** Les accès autorisés identiques au sein d’une fenêtre, de 10 secondes par défaut, sont signalés comme un seul événement d’accès qui indique leur nombre et le moment du dernier ; la console affiche *N accès identiques*. Des accès sont identiques s’ils partagent l’utilisateur, l’adresse du client, la ressource, le type d’accès, le type de requête et la politique décisive. Les refus sont signalés un par un, immédiatement. Kafka choisit ce qui mérite d’être signalé : il ne demande pas, par exemple, de signaler les topics qu’il omet d’une liste.
- **Les super-utilisateurs ne sont pas vérifiés.** Les principaux de `super.users` peuvent tout faire, comme avec l’autoriseur de Kafka, et ne sont pas audités.

## Déploiement

1. Ajoutez un service de données `kafka` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, sélectionnez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; stockez-le dans un fichier que seul le compte de Kafka peut lire.
2. Copiez le JAR dans `libs` de **chaque broker et contrôleur**, par exemple `/opt/kafka/libs`.
3. Ajoutez les paramètres au `server.properties` de chacun, en remplaçant les principaux, l’adresse, les chemins et le nom d’instance d’exemple :

   ```properties
   authorizer.class.name=org.devlive.grantforge.kafka.agent.GrantForgeKafkaAuthorizer
   super.users=User:kafka
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/kafka/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/var/lib/kafka/grantforge-cache
   ```

   `super.users` doit nommer les principaux que brokers et contrôleurs utilisent entre eux, comme celui du listener inter-brokers, ou des politiques doivent leur autoriser ce qu’ils font, comme `cluster_action` sur le cluster. Les connexions à un listener `PLAINTEXT` sont `User:ANONYMOUS` ; ne le nommez que si aucun client ne peut atteindre un tel listener. Le répertoire de cache doit être accessible en écriture au compte de Kafka.
4. Redémarrez les brokers et contrôleurs un par un. Les listeners autres que ceux des contrôleurs s’ouvrent dès que l’agent a son premier instantané, au plus tard après `grantforge.start.timeout.ms` ; jusque-là, et chaque fois qu’il n’y a pas d’instantané, les requêtes sont refusées.
5. **Migrez d’abord les ACL existantes :** les ACL stockées par l’autoriseur de Kafka ne s’appliquent plus une fois l’agent en place. Listez-les au préalable avec `kafka-acls --list` et créez les politiques correspondantes.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour ce broker ou contrôleur et un répertoire accessible en écriture pour l’instantané signé et les événements en file |
| `grantforge.signing.key.file` | Fichier facultatif qui épingle la clé de signature des instantanés ; copiez la clé publique Ed25519 X.509 encodée en Base64 depuis la page Agents. Sans lui, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, `5000` / `8000` millisecondes par défaut |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, `30000` par défaut, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille des lots (`500` par défaut), taille de la file (`10000` par défaut), intervalle d’envoi (`5000` millisecondes par défaut) et limite de mise en file (64 Mio par défaut) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle les accès autorisés identiques sont regroupés en un événement, `10000` par défaut, au plus `3600000` millisecondes ; `0` signale chacun |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Nombre maximal de décisions conservées, `100000` par défaut, au plus `10000000`, `0` n’en conserve aucune ; et leur durée, `10000` par défaut, `1..3600000` millisecondes |
| `grantforge.start.timeout.ms` | Durée pendant laquelle les listeners attendent le premier instantané, `30000` par défaut, `0..600000` millisecondes |

L’agent lit ses paramètres dans la configuration que Kafka transmet à son autoriseur. Un nœud à la fois broker et contrôleur crée un autoriseur par rôle ; les deux partagent un seul agent.

## Utilisateurs, groupes et rôles

Les politiques nomment le principal que Kafka attribue à la connexion : le nom d’utilisateur SASL, le nom court d’un principal Kerberos selon `sasl.kerberos.principal.to.local.rules`, ou le nom d’un certificat TLS selon `ssl.principal.mapping.rules`. Kafka n’a ni groupes ni rôles ; ceux des politiques sont ceux que GrantForge attribue à l’utilisateur.

## Décisions et limites

- Sans instantané utilisable, tout est refusé hormis les requêtes des super-utilisateurs. Un service non utilisé laisse Kafka sans contrôle, comme sans autoriseur.
- Un jeton lié à un service dont le type n’est pas `kafka` refuse tout.
- Refuser un type d’accès refuse aussi ce qu’il implique : refuser `write` sur un topic refuse `describe`, si bien que l’utilisateur ne le voit pas. Les ACL propres à Kafka diffèrent sur ce point.
- La question de Kafka de savoir si un utilisateur peut agir sur une ressource quelconque d’un type n’est pas un accès et n’est pas signalée.
- Les décisions sont conservées pendant `grantforge.decision.cache.ttl.ms` : la période de validité d’une politique commence ou se termine donc jusqu’à autant plus tard.
- L’agent fonctionne dans les brokers et les contrôleurs. L’API REST de Kafka Connect, les registres de schémas et les autres services autour de Kafka ne sont pas couverts.

## Guides associés

- [Apache Kafka](/fr/plugins/kafka/)
- [Services de données, politiques et agents](/fr/external/data-services/)
