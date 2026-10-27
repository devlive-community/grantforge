---
title: Agent Elasticsearch
description: Exécuter l’agent Elasticsearch à côté du cluster, pour que les politiques GrantForge deviennent les rôles propres d’Elasticsearch, donnés aux utilisateurs natifs et associés aux utilisateurs des autres realms, avec sécurité au niveau des champs et des documents là où la licence les offre, que les motifs d’index restent des motifs afin que les nouveaux index soient aussitôt accessibles, et que les accès soient signalés depuis les fichiers du journal d’audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent Elasticsearch est un service à côté du cluster. Il maintient le contrôle d’accès propre d’Elasticsearch en phase avec les politiques signées d’un service de données `elasticsearch` : un rôle par utilisateur qui contient exactement ce que les politiques lui donnent, avec sécurité au niveau des champs et des documents là où la licence du cluster les offre. Un utilisateur natif porte le rôle parmi ses propres rôles ; un utilisateur d’un autre realm, comme LDAP, SAML ou OpenID Connect, le reçoit par une association de rôles à son nom. Elasticsearch vérifie alors lui-même chaque requête, et l’agent ne se place jamais entre les clients et le cluster. La distribution le fournit sous forme de JAR exécutable, `agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar`, qui requiert Java 17 ou plus récent.

Les tests d’intégration Testcontainers du dépôt exécutent l’agent, et le JAR tel que livré, contre Elasticsearch `8.19`, `9.3`, `9.4` et `9.5` avec la sécurité activée. Cela ne certifie pas toutes les autres versions ; l’agent refuse les clusters antérieurs à `8.19`, les clusters sans sécurité et les clusters OpenSearch, que tient l’[agent OpenSearch](/fr/external/opensearch-agent/). Pour le plug-in de service, voir [Elasticsearch](/fr/plugins/elasticsearch/).

## Fonctionnement

- **Un rôle par utilisateur contient ce que GrantForge donne.** Pour chaque utilisateur à qui les politiques donnent quelque chose, l’agent tient un rôle à son nom, `grantforge_alice` pour `alice`. Ses métadonnées nomment l’utilisateur et le marquent comme celui de l’agent, et l’agent ne prend aucun rôle sans cette marque pour l’un des siens, de sorte qu’il ne modifie aucun autre rôle.
- **Les utilisateurs natifs portent le rôle ; les autres realms le reçoivent par leur nom.** L’agent ajoute le rôle aux rôles d’un utilisateur natif et garde tous ses autres rôles. Les associations de rôles s’appliquent à tous les realms sauf les realms natif et fichier ; l’agent tient donc aussi une association de rôles du même nom qui donne le rôle au nom de l’utilisateur, pour les utilisateurs qui se connectent par LDAP, Active Directory, SAML, OpenID Connect, JWT, Kerberos ou PKI. Le realm fichier ne peut pas être modifié par l’API d’Elasticsearch ; ses utilisateurs ne reçoivent aucun rôle.
- **Les motifs d’index restent des motifs.** Un motif d’index d’une politique, comme `logs-*`, est accordé comme motif partout où toutes les politiques décident de même sur toute son étendue : toute politique de tout type dont les index recoupent le motif le couvre entièrement. L’agent le décide exactement, avec des automates finis des motifs. Les index créés ensuite, comme les journaux du lendemain, sont alors aussitôt accessibles, y compris lorsque le premier document écrit les crée. Là où une politique décide autrement à l’intérieur d’un motif, comme un refus de `logs-secret` dans `logs-*`, l’agent accorde chaque index et flux de données du cluster par son nom, et les nouveaux à la synchronisation suivante.
- **La sécurité au niveau des champs seulement là où quelque chose en dessous se décide autrement.** Un index que l’utilisateur peut lire en entier, sans politique qui pourrait lui retirer un champ, est lu sans sécurité au niveau des champs, de sorte que les champs ajoutés plus tard sont lus aussitôt. Sinon l’utilisateur lit les champs qu’une politique autorise, parmi ceux que ses documents ont et ceux que nomment les politiques, chacun avec ses multi-champs, comme `name.keyword` avec `name`.
- **Les filtres de lignes sont la sécurité au niveau des documents.** Un filtre de lignes devient la requête de l’entrée. L’API validate d’Elasticsearch vérifie chaque requête avant qu’elle soit accordée ; une requête qu’Elasticsearch n’exécuterait pas ne montre aucun document à l’utilisateur, et elle est journalisée.
- **Jamais plus que ce que permettent les politiques sous Platinum.** Elasticsearch offre la sécurité au niveau des documents et des champs à partir d’une licence Platinum, et désactive en dessous tout rôle qui s’en sert. L’agent lit la licence à chaque synchronisation. Sous Platinum, une lecture qui a besoin de l’une ou de l’autre, parce qu’une politique restreint des champs ou des documents de l’index, n’est pas accordée du tout, et l’agent journalise quel utilisateur ne peut pas lire quel index et pourquoi ; la lecture d’index entiers et tout le reste sont accordés comme d’habitude.
- **Une entrée par ensemble de privilèges.** Les index et motifs aux mêmes privilèges partagent une entrée ; les entrées ne se recoupent jamais, car Elasticsearch combine la sécurité au niveau des champs et des documents des entrées qui se recoupent.
- **Les politiques du moment présent.** Chaque synchronisation planifie selon les politiques telles qu’elles s’appliquent à ce moment, de sorte que les périodes de validité commencent et finissent à l’heure.
- **Les rôles des utilisateurs natifs sont des groupes.** Une politique pour un groupe s’applique aux utilisateurs natifs qui ont un rôle de ce nom, ainsi qu’aux groupes dans lesquels GrantForge place les utilisateurs.
- **Les changements faits à la main sont annulés.** L’agent compare à chaque synchronisation ses rôles, ses associations de rôles et les rôles des utilisateurs natifs avec ce que détient Elasticsearch ; ce qui diffère est réécrit, et le rôle d’un utilisateur porté par un autre lui est retiré. Les rôles sont écrits avant d’être donnés, en une requête, et retirés avant d’être supprimés. Quand rien n’a changé, l’agent n’envoie rien ; un changement qu’Elasticsearch refuse est journalisé et n’arrête pas les autres.
- **Les rôles hors de GrantForge sont signalés.** Elasticsearch accorde ce que donne n’importe lequel des rôles d’un utilisateur, l’agent ne peut donc pas retirer ce qu’un autre rôle donne. Il avertit une fois de chaque rôle de ce genre, porté par un utilisateur natif ou donné par une association de rôles à son nom, qui donne ce qu’une politique refuse, en nommant le rôle, l’utilisateur, l’accès, l’index et la politique, ainsi que des utilisateurs ayant le rôle `superuser`, qu’aucun rôle ne limite.
- **Les accès viennent des fichiers du journal d’audit.** Elasticsearch écrit son journal d’audit uniquement dans un fichier sur chaque nœud. L’agent lit les fichiers qu’on lui donne, là où la dernière lecture s’est arrêtée, les suit quand Elasticsearch les fait tourner, et signale les événements `access_granted` et `access_denied`. Chacun nomme l’utilisateur, le type d’accès, chaque index ou flux de données atteint, ou le cluster, et indique si GrantForge l’a décidé ou si un rôle hors de GrantForge l’a permis.

| Action | Événement d’accès |
| --- | --- |
| `indices:data/read/*` : recherche, lecture, comptage | `read` sur chaque index ou flux de données |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/delete`, `data_stream/delete` | `delete_index` |
| `indices:admin/get`, `mappings/get`, `settings/get`, `resolve/*` | `view_index_metadata` |
| `indices:monitor/*` ; autres `indices:admin/*` | `monitor` ; `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` et `repository/*`, `slm/*` | `cluster_monitor`, `create_snapshot`, `manage_slm` sur le cluster |
| Modèles d’index et de composants, pipelines d’ingestion, `cluster:admin/ilm/*` | `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` sur le cluster |

## Déploiement

1. Ajoutez un service de données `elasticsearch` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, sélectionnez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; conservez-le dans un fichier que seul le compte de l’agent peut lire.
2. Donnez à l’agent un utilisateur avec le privilège de cluster `manage_security`, qui gère les rôles, les associations de rôles et les utilisateurs, et le droit de lire les mappings de chaque index et de valider des requêtes, comme un utilisateur ayant le rôle `superuser`. Il peut se connecter avec un mot de passe, une clé d’API créée par cet utilisateur ou un certificat client qu’un realm PKI lui associe.
3. Pour signaler les accès, activez le journal d’audit (`xpack.security.audit.enabled: true`), qui exige une licence Gold ou supérieure, et laissez l’agent lire les fichiers d’audit des nœuds, `<nom du cluster>_audit.json` dans le répertoire des journaux de chaque nœud : exécutez l’agent sur un nœud, ou rassemblez les fichiers dans un répertoire qu’il peut lire. `xpack.security.audit.logfile.events.include: access_granted, access_denied` limite le journal à ce que l’agent signale.
4. Écrivez le fichier de paramètres en remplaçant les adresses, chemins et noms d’exemple :

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/elasticsearch-agent.token
   grantforge.instance=elasticsearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/elasticsearch-agent
   grantforge.elasticsearch.hosts=https://es1.internal:9200,https://es2.internal:9200
   grantforge.elasticsearch.user=grantforge
   grantforge.elasticsearch.password.file=/etc/grantforge/elasticsearch-agent.password
   grantforge.elasticsearch.tls.ca=/etc/grantforge/http_ca.crt
   grantforge.audit.log.files=/var/log/elasticsearch/production_audit.json
   ```

   Ces chemins sont sur la machine de l’agent, et le répertoire de cache doit être accessible en écriture. Les fichiers de mot de passe et de clé sont lus en UTF-8 et rognés.
5. Démarrez l’agent, par exemple comme service systemd :

   ```bash
   java -jar agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar /etc/grantforge/elasticsearch-agent.properties
   ```

   Remplacez `<version>` par la version de la distribution. Les paramètres fournis sous la forme `java -Dgrantforge.…=… -jar …` remplacent ceux du fichier. L’agent s’arrête proprement sur SIGTERM et envoie ou met en file les événements en attente ; Elasticsearch garde ce qu’il a reçu.
6. **Transférer les rôles existants dans des politiques :** les rôles que les utilisateurs ont hors de GrantForge continuent de s’appliquer à côté de ceux de l’agent. Créez les politiques correspondantes, puis retirez ces rôles aux utilisateurs ; les avertissements de l’agent nomment les rôles qui donnent ce qu’une politique refuse.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour cet agent et un répertoire accessible en écriture pour l’instantané signé, les événements en file et les positions de lecture du journal d’audit |
| `grantforge.signing.key.file` | Fichier facultatif qui fixe la clé de signature des instantanés ; copiez la clé publique X.509 Ed25519 encodée en Base64 depuis la page Agents. S’il est omis, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.elasticsearch.hosts` | Obligatoire : les nœuds sous forme d’URL, essayés tour à tour lorsqu’un nœud est injoignable ; sans schéma, un nœud est `https`, et le port par défaut est `9200` |
| `grantforge.elasticsearch.user`, `grantforge.elasticsearch.password.file` | L’utilisateur de l’agent et le fichier de son mot de passe |
| `grantforge.elasticsearch.api.key.file` | À la place : le fichier d’une clé d’API encodée, telle qu’Elasticsearch la renvoie à sa création |
| `grantforge.elasticsearch.tls.cert`, `grantforge.elasticsearch.tls.key` | À la place : certificat client PEM et sa clé privée, ensemble, pour se connecter avec un certificat ; `grantforge.elasticsearch.tls.key.passphrase.file` contient la phrase secrète d’une clé chiffrée |
| `grantforge.elasticsearch.ssl.verification.mode` | `full` (par défaut) vérifie les certificats et les noms des nœuds, `certificate` leurs certificats seulement, `none` rien |
| `grantforge.elasticsearch.tls.ca` | Certificats d’AC PEM qui vérifient les nœuds, comme le `http_ca.crt` d’Elasticsearch ; vide fait confiance à ce à quoi Java fait confiance |
| `grantforge.elasticsearch.timeout.ms` | Durée permise pour la connexion et une requête, par défaut `30000`, plage `1..600000` millisecondes |
| `grantforge.elasticsearch.role.prefix` | Le début des noms des rôles de l’agent, par défaut `grantforge_` ; lettres minuscules, chiffres, traits d’union et tirets bas, terminé par un tiret bas, au plus 32 caractères. L’agent possède les rôles qu’il a marqués dont le nom commence par lui |
| `grantforge.elasticsearch.unmanaged.users` | Utilisateurs que l’agent laisse tranquilles, séparés par des virgules, comme des comptes de service ; il laisse toujours tranquilles son propre utilisateur, les utilisateurs réservés d’Elasticsearch et les utilisateurs ayant le rôle `superuser` |
| `grantforge.sync.interval.ms` | Fréquence à laquelle l’agent met Elasticsearch en phase, par défaut `10000`, plage `1000..3600000` millisecondes |
| `grantforge.audit.log.files` | Les fichiers du journal d’audit à lire, séparés par des virgules ; sans eux, l’agent ne lit pas de journal d’audit et ne signale aucun accès |
| `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.lines` | La fréquence de lecture des fichiers, par défaut `5000` millisecondes, et le nombre maximal de lignes qu’une lecture prend de chacun, par défaut `5000`, de sorte qu’un arriéré soit lu en plusieurs fois |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, par défaut `5000` / `8000` millisecondes |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, par défaut `30000`, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (par défaut `500`), taille de file (par défaut `10000`), intervalle d’envoi (par défaut `5000` millisecondes) et limite de file sur disque (par défaut 64 Mio) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle des accès autorisés identiques sont regroupés en un événement, par défaut `10000`, jusqu’à `3600000` millisecondes ; `0` envoie chacun |

## Utilisateurs, groupes et rôles

Les politiques nomment les utilisateurs d’Elasticsearch par leur nom d’utilisateur. L’agent tient des rôles pour les utilisateurs natifs, pour les utilisateurs que nomment les politiques, qui peuvent se connecter par un autre realm, et pour les utilisateurs dont il tenait déjà des rôles ; les utilisateurs réservés d’Elasticsearch, comme `kibana_system`, sont laissés tranquilles. Les groupes sont les autres rôles d’un utilisateur natif ainsi que les groupes dans lesquels GrantForge place l’utilisateur ; l’agent ne connaît pas les groupes qu’un annuaire donne à la connexion, donnez donc à ces utilisateurs leurs groupes dans GrantForge. Les rôles sont ceux que GrantForge donne à l’utilisateur.

## Décisions et limites

- Sans instantané, l’agent ne change rien. Un service inutilisé, ou un jeton lié à un service dont le type n’est pas `elasticsearch`, n’accorde rien : l’agent retire ses rôles et les supprime avec leurs associations de rôles, et les rôles propres d’Elasticsearch s’appliquent.
- Tant que l’agent ne tourne pas, Elasticsearch garde ce qu’il a reçu en dernier ; les changements de politiques l’attendent. Un changement atteint Elasticsearch dans l’intervalle de rafraîchissement et l’intervalle de synchronisation.
- Les index accordés par leur nom, et non par un motif, reçoivent leurs privilèges à la synchronisation suivante.
- Sous Platinum, un utilisateur dont une politique restreint la lecture d’un index n’en lit rien ; sous Gold, l’agent ne signale aucun accès.
- Une association de rôles au nom d’un utilisateur s’applique à tout realm qui a ce nom d’utilisateur ; les politiques nomment des utilisateurs, pas des realms.
- Les alias atteignent les index qui les composent, donc les politiques nomment des index et des flux de données, pas des alias. Les index dont le nom commence par un point ne sont atteints que par des motifs de politique qui commencent par un point.
- L’agent ne gère pas les espaces ni les privilèges d’application de Kibana.

## Guides associés

- [Elasticsearch](/fr/plugins/elasticsearch/)
- [Services de données, politiques et agents](/fr/external/data-services/)
