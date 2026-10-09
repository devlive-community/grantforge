---
title: Services de données, politiques et agents
description: "Gérez avec des plug-ins les autorisations de systèmes externes comme HDFS et Hive : services de données, politiques d’accès, agents et audit des accès."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le groupe « autorisations sur les données » gère les autorisations des systèmes de données extérieurs à GrantForge. L’architecture ressemble à celle d’Apache Ranger : les plug-ins définissent les types de service, les administrateurs rédigent les politiques dans la console, et les agents déployés dans les systèmes cibles téléchargent les politiques et décident des accès localement.

> [!NOTE]
> La version actuelle fournit le cadre de plug-ins, l’éditeur de politiques générique, la distribution des politiques et l’audit des accès, le type de service HDFS avec un agent NameNode pour Hadoop 3.5.0, ainsi qu’un plug-in d’exemple (`example`). Le plug-in Hive et les agents pour les autres versions de Hadoop sont encore en développement.

```mermaid
flowchart LR
  C[Console : services de données et politiques] --> S[Serveur GrantForge]
  S -->|instantané de politiques signé| A[Agent (dans HDFS / Hive)]
  A -->|battement de cœur et audit des accès| S
  U[L’utilisateur accède aux données] --> A
```

## Plug-ins

**Administration de la plateforme → Plug-ins** liste les plug-ins de types de service chargés. Les plug-ins intégrés sont fournis avec le serveur ; pour les autres, il suffit de les déposer dans le répertoire `plugins` puis de cliquer sur « relancer l’analyse ». Chaque plug-in est chargé indépendamment : en cas d’erreur, seul ce plug-in est désactivé. Pour le développement de plug-ins, voir [Plug-ins et types de service](/fr/develop/plugins/).

![Plug-ins](/screenshots/plugins.png)

## HDFS

La version publiée est livrée avec le plug-in HDFS (`plugins/hdfs`), type de service `hdfs`, aligné sur le service HDFS d’Apache Ranger :

- Les ressources n’ont qu’un seul niveau, `path`, apparié par chemin : `/data/sales` correspond à lui-même et, si « récursif » est coché, à tous les fichiers et répertoires qu’il contient ; les exclusions sont prises en charge.
- Les types d’accès `read`, `write` et `execute` correspondent aux bits de permission HDFS.
- Le plug-in se connecte au cluster avec le propre client de Hadoop. Le test de connexion vérifie que le répertoire de consultation existe et que son contenu peut être listé ; lors de la rédaction d’une politique, la saisie d’un chemin liste les sous-répertoires et fichiers du répertoire correspondant, les répertoires d’abord.
- Le plug-in serveur assure la gestion et les consultations ; pour que les politiques contraignent réellement les accès HDFS, il faut aussi déployer l’[agent NameNode](/fr/external/hdfs-agent/).

| Configuration | Description |
| --- | --- |
| `username` | Utilisateur servant aux consultations de répertoire ; principal sous Kerberos, par exemple `grantforge@EXAMPLE.COM` |
| `password` / `keytab` | Sous Kerberos, l’un des deux : le mot de passe du principal, ou le chemin du fichier keytab sur le serveur GrantForge |
| `fs.default.name` | `hdfs://namenode:8020`, `hdfs://nameservice1` en haute disponibilité, ou `webhdfs://namenode:9870` |
| `hadoop.security.authentication` | `simple` ou `kerberos` |
| `hadoop.security.authorization`, `hadoop.security.auth_to_local` | Alignés sur le core-site.xml du cluster |
| `dfs.namenode.kerberos.principal` etc. | Principals de NameNode, DataNode et Secondary NameNode, par exemple `nn/_HOST@EXAMPLE.COM` |
| `hadoop.rpc.protection` | `authentication`, `integrity` ou `privacy`, aligné sur le cluster |
| Configuration Hadoop additionnelle | Une paire `key=value` par ligne, pour la haute disponibilité et d’autres réglages, par exemple `dfs.nameservices=nameservice1`, `dfs.ha.namenodes.nameservice1=nn1,nn2`, `dfs.namenode.rpc-address.nameservice1.nn1=nn1:8020`, `dfs.client.failover.proxy.provider.nameservice1=org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider` |
| `lookup.path` | Répertoire de consultation, `/` par défaut ; réglé sur `/data` par exemple, une saisie vide liste le contenu de `/data` et les saisies relatives sont complétées à partir de là. Utile pour les clusters où l’utilisateur de consultation n’a pas le droit de lister le répertoire racine |
| `lookup.max.entries` | Nombre maximal d’entrées analysées par consultation de répertoire, `10000` par défaut, plage `1..100000` ; au-delà de la limite une erreur est renvoyée, afin d’éviter des candidats manqués en silence |

La configuration Hadoop additionnelle prend le pas sur les réglages de connexion de même nom, et la vérification de configuration comme la connexion utilisent les valeurs recouvertes. `fs.defaultFS` et `fs.default.name` sont des alias : un seul des deux peut être défini dans la configuration additionnelle. Les clés en doublon, les adresses hors cluster et les configurations Kerberos sans identifiants sont refusées à l’enregistrement. L’adresse du cluster ne contient que l’URI du cluster ; les sous-répertoires à consulter se mettent dans `lookup.path`.

`lookup.path` limite le parcours des candidats de chemin ; il ne remplace pas le contrôle d’accès propre de HDFS : liens symboliques et montages ViewFS suivent toujours la configuration du cluster. Dans la saisie, le `/` initial peut être omis, les `/` et `.` répétés sont autorisés, `..` et les chemins absolus hors de la plage sont refusés. Un répertoire inexistant renvoie une liste de candidats vide ; des droits insuffisants ou un échec de connexion affichent une erreur.

Avec Kerberos, le serveur GrantForge doit pouvoir trouver le KDC : configurez `/etc/krb5.conf`, ou indiquez-le avec `-Djava.security.krb5.conf=`.

## Services de données

**Autorisations sur les données → Services de données** : un service est une instance d’un système externe dont GrantForge gère les autorisations, par exemple un cluster HDFS. À l’ajout d’un service, choisissez le type de service et renseignez les informations de connexion selon les points de configuration définis par le plug-in ; vous pouvez d’abord **tester la connexion**. Les configurations sensibles comme les mots de passe sont enregistrées chiffrées et ne sont plus affichées après l’enregistrement.

![Services de données](/screenshots/services.png)

## Politiques

**Autorisations sur les données → Politiques** décident qui peut faire quoi sur quelles ressources d’un service de données :

- **les politiques d’accès** autorisent ou refusent un accès ;
- **les politiques de caviardage** masquent des champs ;
- **les politiques de filtrage de lignes** ne laissent passer qu’une partie des lignes.

La hiérarchie des ressources (bases, tables et colonnes dans Hive, par exemple), les types d’accès (select, update, par exemple) et les conditions viennent tous du plug-in du type de service ; lors de la saisie d’une ressource, vous pouvez rechercher les ressources qui existent réellement dans le système cible. Les politiques s’appliquent aux utilisateurs, groupes ou rôles.

Les niveaux que l’on peut parcourir, comme les chemins HDFS, ont un bouton **Parcourir** : ouvrez les répertoires niveau par niveau, consultez propriétaire, groupe et permissions, et choisissez plusieurs fichiers ou répertoires à la fois. Si une recherche ou un parcours échoue, le motif s’affiche (pas de permission, injoignable ou répertoire trop grand) et vous pouvez réessayer.

![Politiques](/screenshots/policies.png)

## Agents

**Autorisations sur les données → Agents** : les agents sont déployés à l’intérieur du système cible ; munis d’un jeton, ils envoient régulièrement un battement de cœur et téléchargent un instantané de politiques signé, puis décident des accès localement. C’est ici que l’on émet les jetons d’agent (affichés une seule fois) et que l’on voit si chaque agent utilise déjà les dernières politiques.

![Agents](/screenshots/agents.png)

## Audit des accès

**Autorisations sur les données → Audit des accès** : chaque décision d’accès rapportée par un agent — qui a fait quoi, quand, depuis où, sur quelle ressource, si l’accès a été autorisé ou refusé, et quelle politique l’a décidé. Les enregistrements sont conservés 90 jours par défaut.

![Audit des accès](/screenshots/access-audit.png)
