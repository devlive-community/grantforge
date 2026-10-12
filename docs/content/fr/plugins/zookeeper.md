---
title: Apache ZooKeeper
description: Installer et configurer le plug-in ZooKeeper, parcourir les znodes et gérer les politiques d’accès aux chemins de znodes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Apache ZooKeeper fournit à GrantForge la connexion aux ensembles, la recherche de chemins de znodes et la gestion des politiques. Son identifiant de plug-in et son type de service sont tous deux `zookeeper`.

## Installation

La distribution inclut le plug-in dans `plugins/zookeeper`. Vérifiez dans **Administration de la plateforme → Plug-ins** que `zookeeper` est activé ; après une mise à jour, relancez l’analyse du répertoire des plug-ins. Le plug-in utilise le client ZooKeeper 3.9 dans son propre chargeur de classes, séparé des bibliothèques du serveur.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez ZooKeeper (`zookeeper`).
2. Saisissez la chaîne de connexion : les serveurs de l’ensemble avec un chroot facultatif, par exemple `zk1:2181,zk2:2181,zk3:2181/kafka`. Le client bascule seul d’un serveur à l’autre.
3. Choisissez l’authentification, définissez le znode de recherche et testez la connexion (elle vérifie que le znode existe et que ses enfants peuvent être listés), puis enregistrez le service.

| Paramètre | Rôle |
| --- | --- |
| `zookeeper.connect` | Chaîne de connexion obligatoire : `host[:port]` séparés par des virgules, suivis éventuellement de `/chroot` ; le chroot ne peut pas se terminer par `/` |
| `authentication` | `none` (par défaut), `digest` ou `kerberos` |
| `username` | Digest : l’utilisateur digest des ACL (sans deux-points) ; Kerberos : le principal, par exemple `grantforge@EXAMPLE.COM` |
| `password` | Mot de passe de l’utilisateur digest ; stocké chiffré |
| `keytab` | Kerberos : chemin du keytab du principal sur le serveur GrantForge |
| `zookeeper.sasl.client.username` | Kerberos : première partie des principals des serveurs, `zookeeper` par défaut, comme dans `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos : principal complet des serveurs lorsqu’il n’a pas cette forme, par exemple `zookeeper/zk.example.com@EXAMPLE.COM` |
| `zookeeper.client.secure` | Se connecter au port client TLS des serveurs, `false` par défaut |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | Magasin de confiance qui vérifie les serveurs ; vide, il fait confiance à ce que le Java du serveur approuve ; type `jks`, `pkcs12` ou `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | Magasin de clés avec le certificat client, pour les serveurs qui en exigent un |
| `session.timeout.ms` | Délai de session, `15000` par défaut, plage `2000..120000` |
| `connection.timeout.ms` | Attente de la connexion initiale et de l’authentification uniquement, `10000` ms par défaut, plage `1000..60000` ; ne limite pas les requêtes synchrones suivantes |
| `request.timeout.ms` | Attente maximale pour une requête synchrone, `8000` ms par défaut, plage `1000..60000` |
| `lookup.path` | Znode de départ pour la recherche et la navigation, sous le chroot, `/` par défaut ; par exemple `/brokers`, la navigation restant limitée à ce znode |
| `lookup.max.entries` | Nombre maximal d’enfants lus d’un znode à la fois, `10000` par défaut, plage `1..100000` |

## Authentification

- **none** : aucune connexion ; seuls les znodes dont l’ACL est ouverte à `world:anyone` sont lisibles.
- **digest** : connexion en tant que `username:password`, qui correspond aux identités `digest` des ACL de ZooKeeper. Un mauvais mot de passe n’est pas refusé à la connexion : il forme une autre identité, et les znodes protégés apparaissent comme accès refusé.
- **kerberos** : connexion SASL avec un keytab ; le `krb5.conf` du serveur doit mener au KDC. Chaque principal et son keytab reçoivent une section JAAS propre à côté de la configuration du processus, si bien qu’un même GrantForge atteint plusieurs ensembles sous des principals différents. La connexion Kerberos par mot de passe seul n’est pas encore prise en charge.

La configuration est vérifiée à l’enregistrement : la forme de la chaîne de connexion, un utilisateur et un mot de passe pour digest, un principal et un keytab pour Kerberos, un keytab et un principal de serveur uniquement avec Kerberos, et les plages des délais et de la limite.

Lorsque TLS est désactivé, les chemins et mots de passe des magasins de confiance et de clés doivent rester vides. Un mot de passe de magasin exige le chemin du magasin correspondant. Les seuls types acceptés pour les deux magasins sont `jks`, `pkcs12` et `pem`.

Les espaces au début et à la fin des mots de passe des magasins TLS sont conservés. La configuration du service détermine le mode TLS et les magasins utilisés pour cette connexion et remplace les paramètres TLS et de magasins ZooKeeper déjà présents dans la JVM ; les certificats et noms d’hôte des serveurs restent vérifiés.

## Parcourir les znodes

Choisissez le service dans **Permissions sur les données → Politiques** et utilisez **Parcourir** à côté de `path` :

- Les enfants sont listés page par page, par nom ; un znode qui a lui-même des enfants peut être ouvert.
- Chaque znode affiche son ACL (`scheme:id:cdrwa`, comme l’écrivent les outils de ZooKeeper), la taille de ses données et sa dernière modification.
- Un znode dont l’utilisateur de recherche ne peut pas lire l’ACL reste listé par son nom ; l’ouvrir indique pourquoi l’accès est refusé.

Si des znodes sont supprimés pendant la pagination, le curseur avance toujours à partir du dernier nom d’enfant examiné, même si ce nœud n’existe plus. La liste des enfants n’est pas un instantané : les ajouts et suppressions simultanés suivent la sémantique de ZooKeeper et peuvent modifier les pages suivantes.

La saisie d’un chemin propose des suggestions ; un parent inexistant n’en propose aucune, et les chemins hors de `lookup.path` sont refusés. Un znode ayant plus d’enfants que `lookup.max.entries` provoque une erreur plutôt qu’un affichage partiel.

## Appliquer les politiques

Il existe un seul niveau de ressource, `path`, et les types d’accès suivent les permissions d’ACL de ZooKeeper : `read`, `write`, `create`, `delete` et `admin` ; les politiques de chemin prennent en charge la récursivité et les exclusions.

Le plug-in de service assure la gestion, la recherche et la navigation ; l’agent intégré au serveur applique les politiques aux ACL de marquage via un fournisseur d’authentification SASL. La commande `mvn -pl agents/grantforge-agent-zookeeper -am package` produit un unique JAR shaded compatible Java 8, avec ses dépendances. La distribution le fournit sous `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`. L’implémentation cible les API ZooKeeper 3.6–3.9 ; la compatibilité avec les versions réelles reste à valider. Cela ne signifie ni que toute la matrice de versions a été validée, ni que la recette pour la production est terminée.

### Déploiement

1. Installez le JAR dans le répertoire `lib` de **chaque serveur ZooKeeper**, y compris tous les nœuds pouvant recevoir des requêtes clientes. Créez des **identifiants Agent** dans le service de données `zookeeper` correspondant de GrantForge. Stockez le jeton dans un fichier séparé, lisible uniquement par le compte exécutant ZooKeeper ; n’inscrivez pas le jeton lui-même dans `zoo.cfg`.
2. Ajoutez ces paramètres au `zoo.cfg` de chaque serveur, en remplaçant l’adresse, les chemins et l’identifiant d’instance de l’exemple. `grantforge.server.url` (`http[s]://GrantForge`), `grantforge.token.file`, un `grantforge.cache.dir` local et `grantforge.instance` sont obligatoires. Le compte exécutant ZooKeeper doit pouvoir écrire dans le répertoire de cache.

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. Ce fournisseur **remplace le fournisseur SASL natif** ; ne conservez aucun autre fournisseur enregistré pour le même schéma `sasl` dans une autre entrée `authProvider.N`. `enableEagerACLCheck=false` est impératif : lorsque cette vérification est activée, l’enregistrement du fournisseur est refusé et le marqueur n’autorise aucun accès. Conservez et vérifiez l’authentification normale de ZooKeeper avec SASL, JAAS et Kerberos ; l’agent ne remplace pas l’authentification. Redémarrez les serveurs selon la procédure de maintenance de l’ensemble.
4. Le paramètre facultatif `grantforge.signing.key.file` désigne le fichier contenant la clé publique de signature épinglée comme clé de confiance. `grantforge.principal.mapping=short` est la valeur par défaut et transforme `kafka/host@REALM` en `kafka` ; `full` conserve le principal complet. Les utilisateurs des politiques doivent correspondre aux identités ainsi obtenues.

### ACL et limites

- Seule l’ACL de marquage `sasl:grantforge` délègue la décision aux politiques GrantForge, par exemple `sasl:grantforge:cdrwa`, où `c=create`, `d=delete`, `r=read`, `w=write` et `a=admin`. Les autres ACL natives, y compris les autres identités SASL, gardent leur comportement habituel.
- Le marqueur refuse l’accès si l’agent ne fonctionne pas, si aucun instantané de politiques utilisable n’est disponible, si son `serviceType` n’est pas `zookeeper` ou si aucune politique ne correspond/n’autorise l’accès. **Ce n’est pas un refus global** : d’autres ACL, `world:anyone` ou une identité `super` peuvent encore autoriser la requête.
- Les politiques comparent le chemin cible absolu côté serveur. Pour `create`/`delete`, il s’agit du znode à créer/supprimer, et non du parent dont l’ACL est vérifiée. Avec un chroot, le chemin de la politique doit inclure le préfixe chroot réel : `/brokers` sous le chroot `/kafka` correspond à `/kafka/brokers`. Un `create` séquentiel vérifie le préfixe absolu de la requête côté serveur avant l’ajout du numéro de séquence ; les `read`/`delete` ultérieurs vérifient le nom complet du nœud généré.
- Les ACL natives ne sont pas migrées automatiquement et les ACL ne sont pas héritées récursivement ; les nouveaux znodes nécessitent une ACL de marquage explicite. Avant d’utiliser `setACL`, vérifiez les autorisations et les refus sur un périmètre limité et conservez un canal d’administration indépendant. N’appliquez pas le marqueur au nœud racine sans validation soigneuse, sous peine de bloquer l’accès.
- Un serveur sans agent traite `sasl:grantforge` comme l’identité SASL native `grantforge` au lieu d’évaluer les politiques. Le déploiement sur tous les nœuds est donc obligatoire ; un déploiement partiel ne protège pas l’ensemble.
- Les écritures transmises d’un follower/observer au leader peuvent ne pas avoir de connexion cliente à cette interface du fournisseur ; le contexte `clientAddress` peut donc être vide. Les politiques de bout en bout dans l’ensemble fondées sur l’IP du client ne sont pas garanties.
- Ce fournisseur ne peut pas connaître le résultat final d’une requête. Il n’envoie donc pas d’événements d’audit finaux `AccessEvent` et ne fournit pas un audit complet des accès.

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Développement de plug-ins et types de service](/fr/develop/plugins/)
