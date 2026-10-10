---
title: Apache Hadoop HDFS
description: Installer et configurer le plug-in HDFS, parcourir les répertoires et gérer les politiques d’accès aux chemins.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in Apache Hadoop HDFS fournit les connexions aux clusters, la recherche de chemins et la gestion des politiques dans GrantForge. Son identifiant et son type de service sont tous deux `hdfs`.

## Installation

La distribution inclut le plug-in dans `plugins/hdfs`. Vérifiez que `hdfs` est activé dans **Administration de la plateforme → Plug-ins** ; relancez l’analyse du répertoire après une mise à jour.

## Ajouter un service de données

1. Ouvrez **Permissions sur les données → Services de données**, ajoutez un service et choisissez HDFS (`hdfs`).
2. Saisissez l’URI du cluster et l’utilisateur de consultation. Hadoop 2.x utilise `webhdfs://namenode:50070` ; 3.x peut utiliser `hdfs://namenode:8020` ou `webhdfs://namenode:9870`. Pour HTTPS, utilisez `swebhdfs://` avec le port réel du cluster.
3. Définissez le répertoire de consultation et testez la connexion : le répertoire doit exister et pouvoir être listé. Enregistrez ensuite le service.

| Paramètre | Usage |
| --- | --- |
| `fs.default.name` | URI obligatoire du cluster, sans sous-répertoire, identifiants ni paramètres ; HA peut utiliser `hdfs://nameservice1` avec les propriétés supplémentaires adaptées |
| `username` | Utilisateur de consultation obligatoire ; avec Kerberos, un principal tel que `grantforge@EXAMPLE.COM` |
| `hadoop.security.authentication` | Par défaut `simple` ; choisissez `kerberos` pour un cluster Kerberos |
| `hadoop.security.authorization` | Si Hadoop vérifie les autorisations, par défaut `false` ; conforme au core-site.xml du cluster |
| `hadoop.security.auth_to_local` | Règles de correspondance entre les principals Kerberos et les noms d’utilisateur, conformes au core-site.xml du cluster |
| `password` / `keytab` | Mot de passe Kerberos ou chemin d’un fichier keytab sur le serveur GrantForge |
| `dfs.namenode.kerberos.principal`, `dfs.datanode.kerberos.principal`, `dfs.secondary.namenode.kerberos.principal` | Principals des composants du cluster sous Kerberos, tels que `nn/_HOST@EXAMPLE.COM`, conformes à la configuration du cluster |
| `lookup.path` | Répertoire initial de consultation et navigation, par défaut `/`, par exemple `/data` ; la navigation reste dans ce répertoire |
| `lookup.max.entries` | Limite des scans complets, par défaut `10000`, plage `1..100000` |
| `hadoop.config` | Un `key=value` par ligne pour HA et les autres propriétés Hadoop ; remplace les paramètres de connexion de même nom |
| `hadoop.rpc.protection` | `authentication`, `integrity` ou `privacy`, selon le cluster |
| `ssl.client.truststore.location` | Chemin sur le serveur GrantForge du magasin de confiance qui vérifie les certificats des NameNode `swebhdfs://` ; vide, on se fie à ce que Java approuve sur le serveur |
| `ssl.client.truststore.password` | Le mot de passe du magasin, s’il est protégé ; stocké chiffré |
| `ssl.client.truststore.type` | `jks` (par défaut) ou `pkcs12` |

Avec l’extension d’attributs NameNode activée dans Hadoop 2.7.7, les utilisateurs ordinaires qui interrogent le chemin racine `/` déclenchent une `NullPointerException` amont confirmée ; définissez `lookup.path` sur un répertoire existant tel que `/data` (voir le [guide de l’agent](/fr/external/hdfs-agent/)).

Kerberos nécessite aussi un KDC accessible, le `krb5.conf` du serveur ainsi que des règles `hadoop.security.auth_to_local` et des principals de service adaptés. Le compte de consultation récupère les métadonnées des répertoires.

Avec Kerberos, GrantForge réutilise une connexion d’une recherche à l’autre au lieu d’interroger le KDC à chaque fois : Hadoop renouvelle une connexion par keytab quand son ticket arrive à échéance, et une connexion par mot de passe est refaite lorsqu’il reste moins d’un cinquième de la durée du ticket (et au moins une minute) ; un mot de passe changé ou un keytab mis à jour se reconnecte. Chaque service utilise son propre magasin de confiance, qu’un `ssl-client.xml` dans le classpath du serveur ne remplace pas.

La configuration est validée à l’enregistrement : dans `hadoop.config`, `fs.defaultFS` et `fs.default.name` sont des alias, configurez-en un seul ; l’URI du cluster ne doit contenir ni identifiants, ni chemin, ni requête, ni fragment ; `kerberos` exige un `password` ou un `keytab` ; `lookup.path` doit être un chemin absolu sans `..` ; `lookup.max.entries` doit être compris entre `1` et `100000`. Les propriétés supplémentaires remplacent les paramètres de connexion de même nom, et la validation comme la connexion utilisent les valeurs remplacées.

## Parcourir les chemins

Choisissez le service dans **Permissions sur les données → Politiques** et utilisez **Parcourir** à côté de `path`.

- Ouvrez les répertoires, utilisez le chemin ou revenez au parent et chargez les pages suivantes au besoin.
- Consultez les indicateurs fichier/répertoire, propriétaire, groupe, permissions, taille et date de modification.
- Sélectionnez plusieurs fichiers ou répertoires, ou le répertoire courant, pour les ajouter à la politique ; la saisie de chemins propose toujours des suggestions.

RPC et les endpoints WebHDFS avec listage par lots utilisent la pagination native. Les anciens endpoints lisent les répertoires dans la limite du scan et signalent une erreur au-delà. Les erreurs de permissions, d’authentification ou de connexion indiquent leur cause et permettent de réessayer.

La saisie peut omettre le `/` initial, les `/` et `.` répétés sont autorisés, tandis que `..` et les chemins absolus hors de `lookup.path` sont refusés ; un répertoire inexistant ne renvoie aucun candidat. La navigation ne remplace pas le contrôle d’accès propre à HDFS ; les liens symboliques et montages ViewFS suivent la configuration du cluster.

## Appliquer les politiques

L’unique niveau de ressource est `path`, avec les accès `read`, `write` et `execute`. Les politiques de chemins acceptent la récursivité et les exclusions.

Le plug-in serveur assure la gestion et les consultations. L’application des politiques exige aussi un agent NameNode adapté à la version Hadoop ; les utilisateurs doivent satisfaire les permissions natives HDFS et les politiques GrantForge. Les agents numérotés couvrent 2.7, 2.10, 3.2, 3.3, 3.4 et 3.5. Le guide de l’agent précise les combinaisons vérifiées, la couverture d’authentification et HA et les limites du superutilisateur.

## Guides associés

- [Services de données, politiques et agents](/fr/external/data-services/)
- [Agent NameNode Apache Hadoop HDFS](/fr/external/hdfs-agent/)
- [Développement de plug-ins et types de service](/fr/develop/plugins/)
