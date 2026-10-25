---
title: Agent ClickHouse
description: Exécuter l’agent ClickHouse à côté des serveurs, pour que les politiques GrantForge deviennent les rôles, droits et politiques de lignes propres à ClickHouse, jusqu’aux colonnes et aux lignes, et que les accès soient remontés depuis le journal des requêtes.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent ClickHouse est un service à côté des serveurs. Il maintient le contrôle d’accès propre à ClickHouse en accord avec les politiques signées d’un service de données `clickhouse` : un rôle par utilisateur qui détient exactement ce que les politiques donnent à l’utilisateur, et une politique de lignes par filtre de lignes. ClickHouse vérifie alors lui-même chaque requête, sur chacun de ses protocoles, et l’agent ne se place jamais entre les clients et le serveur. La distribution le fournit sous forme de JAR exécutable, `agents/clickhouse/grantforge-agent-clickhouse-<version>.jar`, qui requiert Java 17 ou plus récent.

Les tests d’intégration Testcontainers du dépôt exécutent l’agent, et le JAR tel que livré, contre ClickHouse `24.8`, `25.3`, `25.8`, `26.3` et `26.8`. Cela ne certifie pas toutes les autres versions ; l’agent refuse les serveurs antérieurs à `24.8`. Le plug-in de service est décrit dans [ClickHouse](/fr/plugins/clickhouse/).

## Fonctionnement

- **Un rôle par utilisateur détient ce que GrantForge donne.** Pour chaque utilisateur à qui les politiques donnent quelque chose, l’agent tient un rôle à son nom, `grantforge_alice` pour `alice`, et le lui accorde comme rôle par défaut. `SHOW GRANTS FOR grantforge_alice` montre tout ce que GrantForge donne à `alice` ; l’agent ne modifie aucun autre rôle ni aucun droit qu’il n’a pas accordé.
- **Des bases entières là où rien en dessous n’en décide autrement.** Un type d’accès qu’une politique autorise sur toute une base, sans politique qui pourrait le retirer à une table ou une colonne de celle-ci, est accordé sur la base, si bien que les tables créées ensuite l’ont aussitôt. Sinon, il est accordé table par table, et la lecture et l’écriture colonne par colonne.
- **Les filtres de lignes deviennent des politiques de lignes.** Un filtre de lignes est une politique de lignes permissive du rôle de l’utilisateur sur la table. Un filtre nouveau ou modifié est en place avant les droits qu’il restreint. Un filtre que ClickHouse refuse, ou dont le texte ferait s’appliquer la politique de lignes à d’autres, est remplacé par un filtre qui ne montre aucune ligne à l’utilisateur, et journalisé.
- **Les politiques du moment présent.** Chaque synchronisation planifie d’après les politiques telles qu’elles s’appliquent à ce moment, si bien que les périodes de validité commencent et finissent à l’heure, et d’après les utilisateurs, bases, tables et colonnes de ClickHouse, si bien que les nouveaux sont couverts à la synchronisation suivante.
- **Les rôles ClickHouse sont des groupes.** Une politique pour un groupe s’applique aux utilisateurs qui détiennent un rôle ClickHouse de ce nom, directement ou par d’autres rôles, avec les groupes où GrantForge les place.
- **Les modifications à la main sont annulées.** L’agent retient ce qu’il a donné à chaque rôle et politique de lignes ; celui qui diffère est rétabli à la synchronisation suivante. Quand rien n’a changé, l’agent n’envoie rien.
- **Les droits hors de GrantForge sont signalés.** ClickHouse additionne les droits, l’agent ne peut donc pas retirer ce qu’un utilisateur détient directement ou par un autre rôle. Il avertit une fois de chaque droit de ce genre qui donne ce qu’une politique refuse, en nommant l’utilisateur, le droit, le rôle par lequel il vient et la politique.
- **Les accès viennent du journal des requêtes.** L’agent lit `system.query_log` et remonte chaque instruction comme un événement par table, base ou serveur qu’elle a touché, un refus comme un événement refusé pour ce que nomme l’erreur de ClickHouse. Chaque événement dit si GrantForge l’a décidé, c’est-à-dire si les politiques décident comme ClickHouse, ou si un droit hors de GrantForge l’a autorisé.

| Instruction | Événement d’accès |
| --- | --- |
| `SELECT`, la partie lecture d’`INSERT … SELECT` | `select` sur chaque table lue |
| `INSERT` | `insert` sur la table écrite |
| `ALTER`, `ALTER UPDATE`, `ALTER DELETE`, `DELETE`, `UPDATE` | `alter` sur la table |
| `DROP TABLE`, `TRUNCATE`, `OPTIMIZE` | `drop`, `truncate`, `optimize` sur la table |
| `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` | `create` sur la base |
| `DROP DATABASE` | `drop_database` sur la base |
| `CREATE DATABASE`, `KILL QUERY`, `SYSTEM …` | `create_database`, `kill_query`, `system` sur le serveur |

## Déploiement

1. Ajoutez un service de données `clickhouse` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, choisissez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; placez-le dans un fichier que seul le compte de l’agent peut lire.
2. Créez l’utilisateur ClickHouse de l’agent. Il accorde ce que donnent les politiques, il lui faut donc ces droits avec l’option d’octroi, et la gestion des accès pour tenir rôles et politiques de lignes :

   ```sql
   CREATE USER grantforge IDENTIFIED BY '…';
   GRANT SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE, DROP VIEW,
       DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM ON *.* TO grantforge WITH GRANT OPTION;
   GRANT ACCESS MANAGEMENT ON *.* TO grantforge;
   ```

   Les utilisateurs pour lesquels l’agent tient des rôles doivent être stockés là où ils peuvent être modifiés, dans le répertoire d’utilisateurs `local_directory` ou `replicated`, là où `CREATE USER` les stocke. Les utilisateurs de `users.xml` et de LDAP sont laissés tels quels et journalisés une fois. Un cluster devrait garder ses utilisateurs, rôles et politiques de lignes dans un répertoire d’utilisateurs `replicated` : un seul agent sert alors tout le cluster.
3. Écrivez le fichier de paramètres, en remplaçant les adresses, chemins et noms de l’exemple :

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/clickhouse-agent.token
   grantforge.instance=clickhouse-agent-1
   grantforge.cache.dir=/var/lib/grantforge/clickhouse-agent
   grantforge.clickhouse.urls=https://clickhouse1.internal:8443,https://clickhouse2.internal:8443
   grantforge.clickhouse.user=grantforge
   grantforge.clickhouse.password.file=/etc/grantforge/clickhouse-agent.password
   grantforge.clickhouse.tls.ca=/etc/grantforge/clickhouse-ca.pem
   grantforge.audit.query.log.cluster=main
   ```

   Ces chemins sont sur la machine de l’agent, et le répertoire de cache doit être accessible en écriture. Les fichiers de mot de passe sont lus en UTF-8 et rognés.
4. Démarrez l’agent, par exemple comme service systemd :

   ```bash
   java -jar agents/clickhouse/grantforge-agent-clickhouse-<version>.jar /etc/grantforge/clickhouse-agent.properties
   ```

   Remplacez `<version>` par la version de la distribution. Les paramètres fournis sous la forme `java -Dgrantforge.…=… -jar …` remplacent ceux du fichier. L’agent s’arrête proprement sur SIGTERM et envoie ou met en file les événements en attente ; ClickHouse garde ce qui lui a été donné.
5. **Faites passer les droits existants dans des politiques :** les droits accordés hors de GrantForge continuent de s’appliquer à côté des rôles de l’agent. Créez les politiques correspondantes, puis révoquez ces droits ; les avertissements de l’agent nomment ceux qui donnent ce qu’une politique refuse.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour cet agent et un répertoire accessible en écriture pour l’instantané signé, les événements en file et la position de lecture du journal des requêtes |
| `grantforge.signing.key.file` | Fichier facultatif qui épingle la clé de signature des instantanés ; copiez la clé publique X.509 Ed25519 encodée en Base64 depuis la page Agents. S’il est omis, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.clickhouse.urls`, `grantforge.clickhouse.user` | Obligatoires : les interfaces HTTP des serveurs, essayées tour à tour quand l’une est injoignable, et l’utilisateur ClickHouse de l’agent |
| `grantforge.clickhouse.password.file` | Le mot de passe de l’utilisateur ; omis, l’agent se connecte sans mot de passe |
| `grantforge.clickhouse.tls.ca` | Certificat d’AC PEM qui vérifie les serveurs `https://` ; vide fait confiance à ce à quoi Java fait confiance |
| `grantforge.clickhouse.tls.cert`, `grantforge.clickhouse.tls.key` | Certificat client PEM et clé privée, ensemble, pour se connecter avec un certificat (`IDENTIFIED WITH ssl_certificate`) au lieu d’un mot de passe |
| `grantforge.clickhouse.timeout.ms` | Durée qu’une requête à ClickHouse peut prendre, par défaut `30000`, plage `1..600000` millisecondes |
| `grantforge.clickhouse.role.prefix` | Le début des noms de rôles de l’agent, par défaut `grantforge_` ; lettres, chiffres et tirets bas, terminé par un tiret bas. L’agent possède tout rôle et toute politique de lignes dont le nom commence ainsi |
| `grantforge.clickhouse.unmanaged.users` | Utilisateurs que l’agent laisse tranquilles, séparés par des virgules, comme les administrateurs ; il laisse toujours tranquille son propre utilisateur |
| `grantforge.sync.interval.ms` | Fréquence à laquelle l’agent met ClickHouse en accord, par défaut `10000`, plage `1000..3600000` millisecondes |
| `grantforge.audit.query.log` | Par défaut `true` ; `false` ne lit aucun journal des requêtes et ne remonte aucun accès |
| `grantforge.audit.query.log.cluster` | Un cluster dont les journaux des répliques sont lus via `clusterAllReplicas` ; vide lit le serveur que l’agent interroge |
| `grantforge.audit.query.log.delay.ms`, `grantforge.audit.query.log.interval.ms`, `grantforge.audit.query.log.batch` | Retard sur le présent avec lequel le journal est lu, par défaut `10000` millisecondes, puisque ClickHouse l’écrit toutes les 7,5 secondes ; la fréquence, par défaut `5000` millisecondes ; et le nombre maximal d’entrées par lecture, par défaut `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, par défaut `5000` / `8000` millisecondes |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, par défaut `30000`, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (par défaut `500`), taille de file (par défaut `10000`), intervalle d’envoi (par défaut `5000` millisecondes) et limite de la file sur disque (par défaut 64 Mio) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle des accès autorisés identiques sont regroupés en un événement, par défaut `10000`, jusqu’à `3600000` millisecondes ; `0` envoie chacun |

## Utilisateurs, groupes et rôles

Les politiques nomment des utilisateurs ClickHouse. Les groupes sont les rôles ClickHouse qu’un utilisateur détient, directement ou par d’autres rôles, avec les groupes où GrantForge place l’utilisateur ; les rôles sont ceux que GrantForge donne à l’utilisateur.

## Décisions et limites

- Sans instantané, l’agent ne change rien. Un service qui n’est pas utilisé, ou un jeton lié à un service dont le type n’est pas `clickhouse`, n’accorde rien : l’agent supprime ses rôles et politiques de lignes, et les droits propres à ClickHouse s’appliquent.
- Tant que l’agent ne tourne pas, ClickHouse garde ce qui lui a été donné en dernier ; les changements de politiques l’attendent. Un changement atteint ClickHouse dans l’intervalle de rafraîchissement et l’intervalle de synchronisation.
- Les tables créées dans une base que l’agent accorde table par table reçoivent leurs droits à la synchronisation suivante.
- Un utilisateur pour qui ClickHouse ne montre aucune politique de lignes voit toutes les lignes d’une table, comme le permet par défaut le paramètre `users_without_row_policies_can_read_rows` de ClickHouse ; s’il est désactivé, les utilisateurs sans filtre ne voient aucune ligne des tables filtrées. Des politiques de lignes permissives créées hors de GrantForge pour le même utilisateur élargissent ce que montre son filtre.
- `system` accorde toute instruction `SYSTEM`, `SYSTEM SHUTDOWN` comprise.
- Le journal des requêtes remonte ce que ClickHouse journalise : les instructions des utilisateurs dont `log_queries` est désactivé ne sont pas remontées. Pour un refus, ClickHouse peut nommer la table quand il manque une colonne à l’utilisateur ; l’événement nomme alors la table.
- ClickHouse n’a pas de masquage de colonnes en dehors de ClickHouse Cloud ; les politiques ne peuvent donc pas masquer de valeurs.

## Guides associés

- [ClickHouse](/fr/plugins/clickhouse/)
- [Services de données, politiques et agents](/fr/external/data-services/)
