---
title: Agent Apache Doris
description: Exécuter l’agent Apache Doris à côté du cluster, pour que les politiques GrantForge deviennent les rôles, droits de colonnes, droits de groupes de charge de travail et politiques de lignes propres à Doris, donnés à chaque identité d’un utilisateur, accordés sur des bases de données et catalogues entiers seulement là où Doris ne donnerait pas plus, et que les accès soient signalés depuis la table du journal d’audit de Doris.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent Apache Doris est un service à côté du cluster. Il maintient le contrôle d’accès propre à Doris en phase avec les politiques signées d’un service de données `doris` : un rôle par utilisateur qui contient exactement ce que les politiques lui donnent, jusqu’aux colonnes et aux groupes de charge de travail, et une politique de lignes par filtre de lignes. Doris vérifie alors lui-même chaque instruction, et l’agent ne se place jamais entre les clients et les frontends. La distribution le fournit sous forme de JAR exécutable, `agents/doris/grantforge-agent-doris-<version>.jar`, qui requiert Java 17 ou plus récent.

Les tests d’intégration Testcontainers du dépôt exécutent l’agent, et le JAR tel que livré, contre Apache Doris `2.1`, `3.1`, `4.0` et `4.1`. Cela ne certifie pas toutes les autres versions ; l’agent refuse les clusters antérieurs à `2.1`. Pour le plug-in de service, voir [Apache Doris](/fr/plugins/doris/).

## Fonctionnement

- **Un rôle par utilisateur contient ce que GrantForge donne.** Pour chaque utilisateur à qui les politiques donnent quelque chose, l’agent tient un rôle à son nom, `grantforge_alice` pour `alice`, avec un commentaire qui nomme l’utilisateur, et l’accorde à chaque identité de l’utilisateur, comme `'alice'@'%'` et `'alice'@'10.%'`. `SHOW ROLES` montre ce que GrantForge donne à `alice` ; l’agent ne modifie aucun autre rôle ni aucun droit qu’il n’a pas accordé.
- **Bases de données et catalogues entiers là où rien en dessous ne se décide autrement.** Lire, charger et afficher des vues, qu’une politique autorise sur toute une base de données ou tout un catalogue sans politique qui pourrait les retirer en dessous, sont accordés sur la base de données ou le catalogue, de sorte que les tables créées ensuite les ont aussitôt. Sinon ils sont accordés table par table, et la lecture colonne par colonne. Modifier est accordé table par table, car le droit de Doris sur une base de données la renommerait aussi.
- **Jamais plus que ce que permettent les politiques.** Le droit de Doris de créer des bases de données dans un catalogue est celui de créer des tables dans chacune d’elles, et celui de supprimer une base de données celui de supprimer toutes ses tables. L’agent n’accorde chacun que là où les politiques donnent aussi le droit plus large ; sinon il le retient et journalise une fois quel utilisateur ne peut pas faire quoi et pourquoi.
- **Les groupes de charge de travail sont accordés par nom.** Une politique sur les groupes de charge de travail donne `USAGE_PRIV` sur chaque groupe qu’elle autorise, pour qu’un utilisateur puisse y exécuter des requêtes ; les groupes créés ensuite sont couverts à la synchronisation suivante.
- **Les filtres de lignes deviennent des politiques de lignes.** Un filtre de lignes est une politique de lignes permissive du rôle de l’utilisateur sur la table. Un nouveau filtre est en place avant les droits qu’il restreint ; un filtre qui change est remplacé derrière une politique restrictive qui ne montre aucune ligne, de sorte que l’utilisateur ne voit jamais plus que ce que montre l’un ou l’autre. Chaque filtre est d’abord exécuté sur sa table ; un filtre que Doris ne peut pas y exécuter ou refuse, comme un filtre qui nomme une colonne que la table n’a pas, est remplacé par un filtre qui ne montre aucune ligne à l’utilisateur, et journalisé.
- **Les politiques du moment présent.** Chaque synchronisation planifie selon les politiques telles qu’elles s’appliquent à ce moment, de sorte que les périodes de validité commencent et finissent à l’heure, et selon les utilisateurs, catalogues, bases de données, tables et colonnes de Doris, de sorte que les nouveaux sont couverts à la synchronisation suivante.
- **Les rôles Doris sont des groupes.** Une politique pour un groupe s’applique aux utilisateurs qui ont un rôle Doris de ce nom, ainsi qu’aux groupes dans lesquels GrantForge les place.
- **Les changements faits à la main sont annulés.** L’agent compare à chaque synchronisation les droits de ses rôles, tels que `SHOW ROLES` les montre, et ses politiques de lignes avec les plans, et rétablit ce qui diffère. Doris ne montre les droits de colonnes que par utilisateur, réunis sur tous ses droits : l’agent accorde les colonnes qu’un plan nomme là où un utilisateur ne peut pas toutes les lire, et retire une fois les autres de son rôle ; une colonne qui reste lisible vient d’un droit hors de GrantForge. Quand rien n’a changé, l’agent n’envoie rien.
- **Les droits hors de GrantForge sont signalés.** Doris additionne les droits et réunit les politiques de lignes permissives par OR, l’agent ne peut donc pas retirer ce qu’un utilisateur a directement ou par un autre rôle. Il avertit une fois de chaque droit, droit de colonne ou politique de lignes de ce genre qui donne ce qu’une politique refuse, en nommant l’utilisateur, le droit et sa provenance, ainsi que des utilisateurs ayant `ADMIN_PRIV` ou `NODE_PRIV`, qu’aucun rôle ne limite.
- **Les accès viennent du journal d’audit.** L’agent lit `__internal_schema.audit_log`, que chaque frontend écrit, et signale chaque instruction comme un événement par table, base de données ou catalogue qu’elle a touché ; quelles tables, il le lit dans l’instruction elle-même avec l’analyseur Doris de Druid, car le journal n’en nomme aucune. Un refus est un événement refusé pour le droit et l’objet que nomme l’erreur de Doris. Chaque événement indique si GrantForge l’a décidé, c’est-à-dire si les politiques décident comme Doris, ou si un droit hors de GrantForge l’a permis. Le journal est lu en retard sur le présent de l’intervalle de lots des frontends, `audit_plugin_max_batch_interval_sec`, plus cinq secondes.

| Instruction | Événement d’accès |
| --- | --- |
| `SELECT`, la partie lecture de `INSERT … SELECT` | `select` sur chaque table lue |
| `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` | `load` sur la table écrite |
| `ALTER TABLE`, `DROP TABLE`, `SHOW CREATE VIEW` | `alter`, `drop`, `show_view` sur la table |
| `CREATE TABLE`, `CREATE VIEW` | `create` sur la base de données |
| `DROP DATABASE` | `drop_database` sur la base de données |
| `CREATE DATABASE` | `create_database` sur le catalogue |

## Déploiement

1. Ajoutez un service de données `doris` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, sélectionnez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; conservez-le dans un fichier que seul le compte de l’agent peut lire.
2. Créez l’utilisateur Doris de l’agent. Il crée des rôles et des politiques de lignes, accorde ce que les politiques donnent et lit les droits de chaque utilisateur et le journal d’audit, ce que permet `ADMIN_PRIV` :

   ```sql
   CREATE USER 'grantforge'@'%' IDENTIFIED BY '…';
   GRANT ADMIN_PRIV ON *.*.* TO 'grantforge'@'%';
   ```

3. Pour signaler les accès, activez le journal d’audit de Doris là où il est désactivé, comme dans Doris 2.1 : `SET GLOBAL enable_audit_plugin = true`. Un `audit_plugin_max_batch_interval_sec` plus court que ses 60 secondes par défaut signale les accès plus tôt.
4. Écrivez le fichier de paramètres en remplaçant les adresses, chemins et noms d’exemple :

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/doris-agent.token
   grantforge.instance=doris-agent-1
   grantforge.cache.dir=/var/lib/grantforge/doris-agent
   grantforge.doris.hosts=fe1.internal:9030,fe2.internal:9030
   grantforge.doris.user=grantforge
   grantforge.doris.password.file=/etc/grantforge/doris-agent.password
   ```

   Ces chemins sont sur la machine de l’agent, et le répertoire de cache doit être accessible en écriture. Les fichiers de mot de passe sont lus en UTF-8 et rognés.
5. Démarrez l’agent, par exemple comme service systemd :

   ```bash
   java -jar agents/doris/grantforge-agent-doris-<version>.jar /etc/grantforge/doris-agent.properties
   ```

   Remplacez `<version>` par la version de la distribution. Les paramètres fournis sous la forme `java -Dgrantforge.…=… -jar …` remplacent ceux du fichier. L’agent s’arrête proprement sur SIGTERM et envoie ou met en file les événements en attente ; Doris garde ce qu’il a reçu.
6. **Transférer les droits existants dans des politiques :** les droits accordés hors de GrantForge continuent de s’appliquer à côté des rôles de l’agent. Créez les politiques correspondantes, puis retirez ces droits ; les avertissements de l’agent nomment ceux qui donnent ce qu’une politique refuse.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour cet agent et un répertoire accessible en écriture pour l’instantané signé, les événements en file et la position de lecture du journal d’audit |
| `grantforge.signing.key.file` | Fichier facultatif qui fixe la clé de signature des instantanés ; copiez la clé publique X.509 Ed25519 encodée en Base64 depuis la page Agents. S’il est omis, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.doris.hosts`, `grantforge.doris.user` | Obligatoires : les adresses du protocole MySQL des frontends, essayées tour à tour lorsqu’une est injoignable, le port `9030` s’il n’est pas indiqué, et l’utilisateur Doris de l’agent |
| `grantforge.doris.password.file` | Le mot de passe de l’utilisateur ; omis, l’agent se connecte sans |
| `grantforge.doris.ssl.mode`, `grantforge.doris.tls.ca` | `disable` (par défaut), `trust`, `verify-ca` ou `verify-full`, pour les frontends qui offrent TLS ; et le certificat d’AC PEM qui les vérifie, vide pour ce à quoi Java fait confiance |
| `grantforge.doris.timeout.ms` | Durée permise pour la connexion et une instruction, par défaut `30000`, plage `1..600000` millisecondes |
| `grantforge.doris.role.prefix` | Le début des noms des rôles de l’agent, par défaut `grantforge_` ; lettres, chiffres et tirets bas, terminé par un tiret bas, au plus 32 caractères. L’agent possède chaque rôle et politique de lignes dont le nom commence par lui |
| `grantforge.doris.unmanaged.users` | Utilisateurs que l’agent laisse tranquilles, séparés par des virgules, comme des comptes de service ; il laisse toujours tranquilles son propre utilisateur, `root`, `admin` et les utilisateurs ayant `ADMIN_PRIV` ou `NODE_PRIV` |
| `grantforge.doris.catalogs` | Les catalogues où l’agent accorde par base de données, table et colonne, séparés par des virgules ; vide pour tous. Les autres ne reçoivent que ce qui peut leur être accordé en entier, de sorte qu’un catalogue externe aux nombreuses tables n’a pas à être listé à chaque synchronisation |
| `grantforge.sync.interval.ms` | Fréquence à laquelle l’agent met Doris en phase, par défaut `10000`, plage `1000..3600000` millisecondes |
| `grantforge.audit.log` | Par défaut `true` ; `false` ne lit pas de journal d’audit et ne signale aucun accès |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | Le retard de lecture du journal sur le présent, par défaut l’intervalle de lots du cluster plus cinq secondes ; la fréquence de lecture, par défaut `5000` millisecondes ; et le nombre maximal d’entrées par lecture, par défaut `1000` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, par défaut `5000` / `8000` millisecondes |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, par défaut `30000`, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (par défaut `500`), taille de file (par défaut `10000`), intervalle d’envoi (par défaut `5000` millisecondes) et limite de file sur disque (par défaut 64 Mio) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle des accès autorisés identiques sont regroupés en un événement, par défaut `10000`, jusqu’à `3600000` millisecondes ; `0` envoie chacun |

## Utilisateurs, groupes et rôles

Les politiques nomment les utilisateurs Doris par leur nom d’utilisateur ; un nom d’utilisateur avec plusieurs hôtes est un seul utilisateur, et le rôle de l’agent va à chacune de ses identités. Les groupes sont les rôles Doris d’un utilisateur ainsi que les groupes dans lesquels GrantForge le place ; les rôles sont ceux que GrantForge donne à l’utilisateur.

## Décisions et limites

- Sans instantané, l’agent ne change rien. Un service inutilisé, ou un jeton lié à un service dont le type n’est pas `doris`, n’accorde rien : l’agent supprime ses rôles et politiques de lignes, et les droits propres à Doris s’appliquent.
- Tant que l’agent ne tourne pas, Doris garde ce qu’il a reçu en dernier ; les changements de politiques l’attendent. Un changement atteint Doris dans l’intervalle de rafraîchissement et l’intervalle de synchronisation.
- Les tables créées dans une base de données où l’agent accorde table par table, et les bases de données d’un catalogue où il accorde base par base, reçoivent leurs droits à la synchronisation suivante.
- Les politiques de lignes de Doris ne peuvent pas lier `root` et `admin`. Un utilisateur pour qui Doris ne montre aucune politique de lignes voit toutes les lignes d’une table ; les politiques de lignes permissives créées hors de GrantForge pour le même utilisateur élargissent ce que montre son filtre.
- Doris vérifie `USAGE_PRIV` sur un groupe de charge de travail quand une requête s’exécute sur les backends ; une requête à laquelle le frontend répond lui-même, comme une constante ou un résultat en cache, s’exécute sans la vérification. Chaque utilisateur peut utiliser le groupe `normal`, car Doris le donne à tous.
- Les utilisateurs dont l’agent tient les rôles doivent exister dans Doris ; les utilisateurs qui se connectent par LDAP sans utilisateur Doris propre ne reçoivent aucun rôle.
- Doris ne masque les valeurs que par Apache Ranger, les politiques ne peuvent donc pas masquer de valeurs.

## Guides associés

- [Apache Doris](/fr/plugins/doris/)
- [Services de données, politiques et agents](/fr/external/data-services/)
