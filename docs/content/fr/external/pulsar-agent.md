---
title: Agent Apache Pulsar
description: Installer le fournisseur d’autorisation dans les brokers Pulsar pour décider avec les politiques GrantForge qui peut produire et consommer où, par quels abonnements, exécuter des functions, sources et sinks, administrer topics, namespaces, tenants et clusters, et depuis quels réseaux.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent Pulsar est un fournisseur d’autorisation des brokers : Pulsar lui demande qui peut produire, consommer, exécuter des functions, sources et sinks, et administrer topics, namespaces, tenants et clusters, et il répond à partir des politiques signées du service de données `pulsar`. La distribution le fournit sous la forme d’un JAR Java 17, `agents/pulsar/grantforge-agent-pulsar-<version>.jar`, dépendances incluses et relocalisées.

Les tests d’intégration Testcontainers du dépôt exécutent le JAR dans les brokers de Pulsar `3.0.17`, `3.3.9`, `4.0.14` et `4.2.5`. Cela ne certifie pas toutes les autres versions. Le plug-in de service est décrit dans [Apache Pulsar](/fr/plugins/pulsar/).

## Fonctionnement

- **Chaque vérification est décidée à chaque niveau du nom du topic.** Comme avec les autorisations de Pulsar, une permission sur un tenant ou un namespace couvre ce qu’il contient ; l’agent décide donc une production vers `persistent://sales/orders/payments` au tenant `sales`, au namespace `orders` et au topic `payments` : un refus à l’un d’eux rejette, une autorisation à l’un d’eux accorde.
- **La consommation est aussi décidée par l’abonnement.** À chacun de ces niveaux, l’agent interroge aussi le topic avec l’abonnement, de sorte qu’une politique peut ne laisser un rôle consommer que par certains abonnements, par exemple `billing-*`.
- **Les administrateurs du cluster sont les superutilisateurs de Pulsar.** `admin` sur le cluster du broker fait d’un rôle l’un d’eux, et Pulsar laisse ses superutilisateurs tout faire ; les politiques en dessous ne les restreignent donc pas. Les opérations de broker et de cluster de Pulsar exigent `admin` sur le cluster qu’elles nomment.
- **Les politiques peuvent ne valoir que pour certains réseaux.** Chaque vérification porte l’adresse du client, et l’agent évalue la condition **Adresses des clients** avec les plages d’adresses de Netty, qu’utilise la pile réseau du broker.
- **Accorder des droits par Pulsar est refusé.** Les autorisations de Pulsar et les rôles d’administration de ses tenants ne sont pas utilisés ; `pulsar-admin namespaces grant-permission` et consorts reçoivent donc une réponse HTTP 405, qui invite à accorder les droits dans GrantForge.
- **Les refus sont journalisés avec leur raison.** Pulsar formule le refus que reçoit le client ; le journal du broker nomme la politique GrantForge qui a refusé, ou indique qu’aucune n’autorise.
- **Les décisions sont conservées et les accès regroupés.** Les décisions de l’instantané courant sont conservées, par défaut 100 000 pendant 10 secondes, et un nouvel instantané repart de zéro. Les accès autorisés identiques dans une fenêtre, par défaut 10 secondes, sont signalés comme un seul événement d’accès indiquant leur nombre ; les refus sont signalés un par un et immédiatement. Chaque événement nomme le topic, le namespace, le tenant ou le cluster et l’adresse du client.
- **Un agent sert tout le broker.** Un broker charge son fournisseur d’autorisation pour son protocole binaire, son service web et son functions worker ; ils partagent un agent, qui s’arrête avec le broker.

| Opération | Accès requis |
| --- | --- |
| Produire, ou créer un topic en y produisant | `produce` sur le topic |
| S’abonner, consommer, se désabonner, sauter, consulter, faire expirer des messages, réinitialiser un curseur | `consume` sur le topic avec l’abonnement |
| Rechercher un topic, lire ses statistiques, métadonnées et politiques ; lister les topics d’un namespace | `produce` ou `consume` |
| Terminer, compacter, déporter, décharger ou tronquer un topic ; modifier ses politiques | `admin_topic` sur le topic |
| Créer et supprimer des topics ; modifier les politiques d’un namespace | `admin_namespace` sur le namespace |
| Exécuter des functions, sources, sinks ; utiliser des packages | `functions`, `sources`, `sinks`, `packages` sur le namespace |
| Créer, lister et supprimer les namespaces d’un tenant | `admin_tenant` sur le tenant |
| Créer, modifier et supprimer tenants et clusters ; les opérations de broker de Pulsar | `admin` sur le cluster |

## Déploiement

1. Ajoutez un service de données `pulsar` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, choisissez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; placez-le dans un fichier que seul le compte de Pulsar peut lire.
2. Copiez le JAR dans `lib` **de chaque broker**, par exemple `/pulsar/lib`.
3. Ajoutez les paramètres au `broker.conf` de chaque broker, ou au `standalone.conf`, en remplaçant l’adresse, les chemins et le nom d’instance de l’exemple :

   ```properties
   authorizationEnabled=true
   authorizationProvider=org.devlive.grantforge.pulsar.agent.GrantForgePulsarAuthorizer
   superUserRoles=broker
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/pulsar/conf/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/pulsar/data/grantforge-cache
   ```

   L’authentification doit aussi être activée, pour que les clients aient des rôles. `superUserRoles` et `proxyRoles` ne doivent nommer que le rôle propre des brokers et ceux des proxys : l’agent ne les vérifie pas, et Pulsar vérifie aussi le client d’origine d’un proxy. Donnez plutôt aux administrateurs `admin` sur le cluster. Le répertoire de cache doit être accessible en écriture au compte de Pulsar. Dans un conteneur, écrivez les paramètres `grantforge.*` dans le fichier : le shell de certaines images Pulsar abandonne les variables d’environnement dont le nom contient des points, comme `PULSAR_PREFIX_grantforge.token.file`.
4. Redémarrez les brokers l’un après l’autre. Tant que l’agent n’a pas son premier instantané, les requêtes autres que celles des brokers et des proxys sont refusées.
5. **Transférez d’abord les permissions existantes :** les autorisations de Pulsar sur les namespaces, topics et abonnements, et les rôles d’administration des tenants, ne s’appliquent plus. Créez au préalable les politiques correspondantes.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour ce broker et un répertoire inscriptible pour l’instantané signé et les événements mis en attente |
| `grantforge.signing.key.file` | Fichier facultatif qui fixe la clé de signature des instantanés ; copiez la clé publique X.509 Ed25519 encodée en Base64 depuis la page Agents. Sans lui, la clé est obtenue du serveur au premier contact |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, par défaut `5000` / `8000` millisecondes |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, par défaut `30000`, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (par défaut `500`), taille de file (par défaut `10000`), intervalle d’envoi (par défaut `5000` millisecondes) et limite du spool (par défaut 64 Mio) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle des accès autorisés identiques sont regroupés en un événement, par défaut `10000`, jusqu’à `3600000` millisecondes ; `0` les signale tous |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | Nombre maximal de décisions conservées, par défaut `100000`, jusqu’à `10000000`, `0` n’en conserve aucune ; et leur durée, par défaut `10000`, `1..3600000` millisecondes |

## Utilisateurs, groupes et rôles

Les politiques nomment comme utilisateur le rôle que l’authentification de Pulsar donne au client, par exemple le sujet d’un jeton ou le nom commun d’un certificat. Pulsar n’a pas de groupes propres : les groupes sont ceux auxquels GrantForge rattache le rôle, et les rôles ceux que GrantForge lui attribue.

## Décisions et limites

- Sans instantané utilisable, tout est refusé sauf les requêtes des brokers et des proxys. Un service non utilisé laisse Pulsar sans vérification, comme avec `authorizationEnabled` désactivé.
- Un jeton lié à un service dont le type n’est pas `pulsar` refuse tout.
- Avant de nombreuses vérifications, Pulsar demande si un rôle est superutilisateur ou administrateur d’un tenant ; seules les réponses qui laissent passer une requête sont signalées, et la vérification propre de la requête l’est dans tous les cas. Une requête que Pulsar décide par cette seule question, comme Pulsar 3.0 pour les tenants et les clusters, n’est journalisée qu’au niveau debug lorsqu’elle est refusée.
- `admin` sur un cluster ne fait d’un rôle un superutilisateur que des brokers de ce cluster.
- Les listes de permissions de Pulsar, comme `pulsar-admin namespaces permissions`, échouent : GrantForge détient les politiques.
- Les adresses des clients ne sont que des adresses et des plages ; un nom d’hôte ne correspond à aucun client.
- Les décisions sont conservées pendant `grantforge.decision.cache.ttl.ms` ; une politique commence ou cesse donc de s’appliquer jusqu’à ce délai plus tard.

## Guides associés

- [Apache Pulsar](/fr/plugins/pulsar/)
- [Services de données, politiques et agents](/fr/external/data-services/)
