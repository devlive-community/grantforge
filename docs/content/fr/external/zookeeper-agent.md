---
title: Agent Apache ZooKeeper
description: Déployez l’agent sur chaque nœud ZooKeeper pour appliquer les politiques GrantForge via les ACL de marquage.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in serveur `grantforge-plugin-zookeeper` définit le type de service `zookeeper`, ses ressources et la configuration de connexion. L’application des politiques demande un second élément, qui tourne dans ZooKeeper lui-même : l’agent, qui participe aux décisions d’ACL des serveurs comme fournisseur d’authentification SASL. Il se trouve dans `agents/grantforge-agent-zookeeper` ; la commande `mvn -pl agents/grantforge-agent-zookeeper -am package` produit un unique JAR shaded compatible Java 8, avec ses dépendances, que la distribution fournit sous `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`. L’implémentation cible les API ZooKeeper 3.6–3.9 ; la compatibilité avec les versions réelles reste à valider. Cela ne signifie ni que toute la matrice de versions a été validée, ni que la recette pour la production est terminée.

## Déploiement

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

## ACL et limites

- Seule l’ACL de marquage `sasl:grantforge` délègue la décision aux politiques GrantForge, par exemple `sasl:grantforge:cdrwa`, où `c=create`, `d=delete`, `r=read`, `w=write` et `a=admin`. Les autres ACL natives, y compris les autres identités SASL, gardent leur comportement habituel.
- Le marqueur refuse l’accès si l’agent ne fonctionne pas, si aucun instantané de politiques utilisable n’est disponible, si son `serviceType` n’est pas `zookeeper` ou si aucune politique ne correspond/n’autorise l’accès. **Ce n’est pas un refus global** : d’autres ACL, `world:anyone` ou une identité `super` peuvent encore autoriser la requête.
- Les politiques comparent le chemin cible absolu côté serveur. Pour `create`/`delete`, il s’agit du znode à créer/supprimer, et non du parent dont l’ACL est vérifiée. Avec un chroot, le chemin de la politique doit inclure le préfixe chroot réel : `/brokers` sous le chroot `/kafka` correspond à `/kafka/brokers`. Un `create` séquentiel vérifie le préfixe absolu de la requête côté serveur avant l’ajout du numéro de séquence ; les `read`/`delete` ultérieurs vérifient le nom complet du nœud généré.
- Les ACL natives ne sont pas migrées automatiquement et les ACL ne sont pas héritées récursivement ; les nouveaux znodes nécessitent une ACL de marquage explicite. Avant d’utiliser `setACL`, vérifiez les autorisations et les refus sur un périmètre limité et conservez un canal d’administration indépendant. N’appliquez pas le marqueur au nœud racine sans validation soigneuse, sous peine de bloquer l’accès.
- Un serveur sans agent traite `sasl:grantforge` comme l’identité SASL native `grantforge` au lieu d’évaluer les politiques. Le déploiement sur tous les nœuds est donc obligatoire ; un déploiement partiel ne protège pas l’ensemble.
- Les écritures transmises d’un follower/observer au leader peuvent ne pas avoir de connexion cliente à cette interface du fournisseur ; le contexte `clientAddress` peut donc être vide. Les politiques de bout en bout dans l’ensemble fondées sur l’IP du client ne sont pas garanties.
- Ce fournisseur ne peut pas connaître le résultat final d’une requête. Il n’envoie donc pas d’événements d’audit finaux `AccessEvent` et ne fournit pas un audit complet des accès.
