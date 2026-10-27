---
title: Agent PostgreSQL
description: Exécuter l’agent PostgreSQL à côté des serveurs, pour que les politiques GrantForge deviennent les rôles, les droits et les politiques de sécurité des lignes de PostgreSQL, jusqu’aux colonnes et aux lignes, base par base en une transaction chacune, et que les accès soient remontés depuis le journal du serveur.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent PostgreSQL est un service à côté des serveurs. Il tient le contrôle d’accès de PostgreSQL en accord avec les politiques signées d’un service de données `postgresql` : un rôle par utilisateur qui détient exactement ce que les politiques donnent à l’utilisateur, dans chaque base qu’il tient, et une politique de sécurité des lignes par filtre de lignes. PostgreSQL vérifie ensuite lui-même chaque instruction, et l’agent ne se place jamais entre les clients et le serveur. La distribution le livre sous forme de JAR exécutable, `agents/postgresql/grantforge-agent-postgresql-<version>.jar`, qui demande Java 17 ou plus récent.

Les tests d’intégration Testcontainers du dépôt exécutent l’agent, et le JAR tel que livré, contre PostgreSQL `15`, `16`, `17` et `18` avec pgaudit. Cela ne certifie pas toutes les autres versions ; l’agent refuse les serveurs antérieurs à `15`. Voir [PostgreSQL](/fr/plugins/postgresql/) pour le plug-in de service.

## Fonctionnement

- **Un rôle par utilisateur détient ce que GrantForge donne.** Pour chaque utilisateur à qui les politiques donnent quelque chose, l’agent tient un rôle à son nom, `grantforge_alice` pour `alice`, et fait de l’utilisateur un membre qui a les droits du rôle sans `SET ROLE`. L’agent écrit un commentaire sur chacun de ses rôles et ne prend aucun rôle sans lui pour l’un des siens, si bien qu’il ne modifie aucun autre rôle ni aucun droit qu’il n’a pas accordé.
- **Chaque base change en une transaction.** L’agent se connecte à chaque base qu’il tient et y fait ses changements en une transaction, si bien que les utilisateurs voient une version des politiques appliquée à une base tout entière ou pas du tout.
- **Des tables entières là où rien en dessous n’en décide autrement.** Un type d’accès qu’une politique autorise sur toute une table, sans politique qui pourrait le retirer à une colonne, est accordé sur la table, si bien que les colonnes ajoutées ensuite l’ont aussitôt. Sinon, la lecture, l’insertion et la modification sont accordées colonne par colonne. `CONNECT` sur la base, `USAGE` sur le schéma et sur les séquences des colonnes serial où l’utilisateur peut insérer viennent avec.
- **Les filtres de lignes ne font que restreindre.** Un filtre de lignes est une politique de sécurité des lignes restrictive du rôle de l’utilisateur sur la table, pour `SELECT`. Là où la sécurité des lignes est désactivée, l’agent l’active avec une politique permissive qui montre toutes les lignes à tout le monde, nommée d’après son préfixe (`grantforge`), si bien que rien ne change pour les autres ; il la désactive de nouveau quand la table n’a plus de filtre ni de politique à elle. La sécurité des lignes qu’un propriétaire a activée reste celle du propriétaire, et les politiques de l’agent ne font que restreindre ce qu’elle montre. Un filtre que PostgreSQL refuse, un filtre qui changerait qui la politique lie, ou un texte de plus d’une instruction est remplacé par une politique qui ne montre aucune ligne à l’utilisateur, et journalisé.
- **Les politiques du moment présent.** Chaque synchronisation planifie d’après les politiques telles qu’elles s’appliquent à ce moment, si bien que les périodes de validité commencent et finissent à l’heure, et d’après les utilisateurs, schémas, tables et colonnes de PostgreSQL, si bien que les nouveaux sont couverts à la synchronisation suivante.
- **Les rôles sont des groupes.** Une politique pour un groupe s’applique aux utilisateurs membres d’un rôle PostgreSQL de ce nom, directement ou par d’autres rôles, avec les groupes où GrantForge les place.
- **Les changements faits à la main sont annulés.** L’agent compare à chaque synchronisation les droits de ses rôles avec ce que montre PostgreSQL, et ses politiques de sécurité des lignes avec ce qu’il a créé en dernier ; ce qui diffère est rétabli à la synchronisation suivante. Quand rien n’a changé, l’agent n’envoie rien.
- **Les droits hors de GrantForge sont signalés.** PostgreSQL additionne les droits, si bien que l’agent ne peut pas retirer ce qu’un utilisateur détient lui-même, par un autre rôle ou par PUBLIC, ni ce qu’il peut faire des tables qu’il possède. Il avertit une fois de chacun de ces droits qui donne ce qu’une politique refuse, en nommant l’utilisateur, le droit, la façon dont l’utilisateur le détient et la politique.
- **Les accès viennent du journal du serveur.** L’agent lit le journal JSON du serveur PostgreSQL : le journal de session de pgaudit pour ce que PostgreSQL a autorisé, un événement par table qu’une instruction a lue ou écrite, et les refus de PostgreSQL faute d’un droit, un événement refusé pour ce que nomme l’erreur. Chaque événement dit si GrantForge l’a décidé, c’est-à-dire si les politiques décident comme PostgreSQL, ou si un droit hors de GrantForge l’a autorisé.

| Instruction | Événement d’accès |
| --- | --- |
| `SELECT`, `COPY … TO`, le côté lecture d’autres instructions | `select` sur chaque table lue |
| `INSERT`, `COPY … FROM` | `insert` sur la table écrite |
| `UPDATE`, `MERGE`, `SELECT … FOR UPDATE` | `update` sur la table |
| `DELETE`, `TRUNCATE` | `delete`, `truncate` sur la table |
| `CREATE TRIGGER` | `trigger` sur la table |
| `CREATE TABLE`, `CREATE VIEW` et d’autres objets | `create` sur le schéma |
| `CREATE SCHEMA`, `CREATE TEMPORARY TABLE` | `create_schema`, `temporary` sur la base |
| Une connexion refusée, un schéma refusé faute de `USAGE` | `connect` sur la base, `usage` sur le schéma |

## Déploiement

1. Ajoutez un service de données `postgresql` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, choisissez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; placez-le dans un fichier que seul le compte de l’agent peut lire.
2. Créez l’utilisateur PostgreSQL de l’agent comme superutilisateur. Il accorde des droits sur des tables qu’il ne possède pas, active et désactive la sécurité des lignes, tient des rôles et lit le journal du serveur :

   ```sql
   CREATE ROLE grantforge LOGIN SUPERUSER PASSWORD '…';
   ```

   Les tests exécutent l’agent comme superutilisateur. Les superutilisateurs ne sont liés ni par les droits ni par la sécurité des lignes, l’agent ne tient donc pas de rôle pour eux.
3. Pour remonter les accès, faites écrire à PostgreSQL son journal en JSON et chargez pgaudit, depuis les paquets de PGDG comme `postgresql-17-pgaudit` ou ceux de la plateforme, dans `postgresql.conf`, puis redémarrez le serveur :

   ```ini
   logging_collector = on
   log_destination = 'stderr,jsonlog'
   shared_preload_libraries = 'pgaudit'
   pgaudit.log = 'read, write, ddl'
   pgaudit.log_relation = on
   pgaudit.log_catalog = off
   ```

   Sans pgaudit, l’agent remonte ce que PostgreSQL a refusé, pas ce qu’il a autorisé ; sans le journal JSON, il ne remonte rien et le dit une fois.
4. Écrivez le fichier de paramètres en remplaçant les adresses, chemins et noms d’exemple :

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/postgresql-agent.token
   grantforge.instance=postgresql-agent-1
   grantforge.cache.dir=/var/lib/grantforge/postgresql-agent
   grantforge.postgresql.hosts=postgres1.internal:5432,postgres2.internal:5432
   grantforge.postgresql.user=grantforge
   grantforge.postgresql.password.file=/etc/grantforge/postgresql-agent.password
   grantforge.postgresql.ssl.mode=verify-full
   grantforge.postgresql.tls.ca=/etc/grantforge/postgresql-ca.pem
   ```

   Ces chemins sont sur la machine de l’agent, et le répertoire de cache doit être accessible en écriture. Les fichiers de mot de passe sont lus en UTF-8 et rognés.
5. Démarrez l’agent, par exemple comme service systemd :

   ```bash
   java -jar agents/postgresql/grantforge-agent-postgresql-<version>.jar /etc/grantforge/postgresql-agent.properties
   ```

   Remplacez `<version>` par la version de la distribution. Les paramètres donnés sous la forme `java -Dgrantforge.…=… -jar …` remplacent ceux du fichier. L’agent s’arrête proprement sur SIGTERM et envoie ou met en file les événements en attente ; PostgreSQL garde ce qui lui a été donné.
6. **Reporter les droits existants dans des politiques :** les droits accordés hors de GrantForge continuent de s’appliquer à côté des rôles de l’agent. Créez les politiques correspondantes, puis révoquez les droits ; les avertissements de l’agent nomment ceux qui donnent ce qu’une politique refuse.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour cet agent et un répertoire accessible en écriture pour l’instantané signé, les événements en file et la position de lecture du journal du serveur |
| `grantforge.signing.key.file` | Fichier facultatif qui fixe la clé de signature des instantanés ; copiez la clé publique Ed25519 X.509 encodée en Base64 depuis la page Agents. Sans lui, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.postgresql.hosts`, `grantforge.postgresql.user` | Obligatoires : les serveurs sous la forme `host:port`, essayés tour à tour jusqu’à ce qu’un serveur joignable soit le primaire, et l’utilisateur PostgreSQL de l’agent |
| `grantforge.postgresql.database` | La base à laquelle l’agent se connecte d’abord, pour lire les rôles, les bases et le journal du serveur, par défaut `postgres` |
| `grantforge.postgresql.password.file` | Le mot de passe de l’utilisateur ; omis, l’agent se connecte sans mot de passe |
| `grantforge.postgresql.ssl.mode` | TLS tel que le nomme `sslmode` de libpq, par défaut `prefer` |
| `grantforge.postgresql.tls.ca` | verify-ca et verify-full : certificat d’AC PEM qui vérifie les serveurs ; vide fait confiance à ce à quoi Java fait confiance |
| `grantforge.postgresql.tls.cert`, `grantforge.postgresql.tls.key` | Certificat client PEM et sa clé privée PKCS #8, ensemble, pour se connecter avec un certificat (authentification `cert`) plutôt qu’avec un mot de passe ; ils demandent `require` ou un mode de vérification, et un mode de vérification demande alors `grantforge.postgresql.tls.ca` |
| `grantforge.postgresql.timeout.ms` | La durée permise pour se connecter et pour une instruction, par défaut `30000`, plage `1..600000` millisecondes |
| `grantforge.postgresql.databases` | Les bases que tient l’agent, séparées par des virgules ; vide tient toutes les bases qui acceptent des connexions |
| `grantforge.postgresql.role.prefix` | Le début des noms de rôles de l’agent, par défaut `grantforge_` ; lettres, chiffres et soulignés, se terminant par un souligné, 32 caractères au plus et ne commençant pas par `pg_`. L’agent possède les rôles qu’il a marqués dont le nom commence ainsi, et les politiques de sécurité des lignes nommées d’après eux ou d’après le préfixe sans son dernier souligné |
| `grantforge.postgresql.unmanaged.users` | Utilisateurs que l’agent laisse tranquilles, séparés par des virgules, comme les administrateurs ; il laisse toujours tranquilles son propre utilisateur et les superutilisateurs |
| `grantforge.sync.interval.ms` | La fréquence à laquelle l’agent remet PostgreSQL en accord, par défaut `10000`, plage `1000..3600000` millisecondes |
| `grantforge.audit.server.log` | Par défaut `true` ; `false` ne lit aucun journal du serveur et ne remonte aucun accès |
| `grantforge.audit.server.log.interval.ms`, `grantforge.audit.server.log.max.bytes` | La fréquence de lecture du journal, par défaut `5000` millisecondes, et le nombre maximal d’octets d’une lecture, par défaut 16 Mio, si bien qu’un retard se lit en plusieurs tours |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, par défaut `5000` / `8000` millisecondes |
| `grantforge.refresh.interval.ms` | La fréquence à laquelle l’agent demande de nouvelles politiques, par défaut `30000`, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (par défaut `500`), taille de la file (par défaut `10000`), intervalle d’envoi (par défaut `5000` millisecondes) et limite de la file sur disque (par défaut 64 Mio) |
| `grantforge.audit.fold.window.ms` | La durée pendant laquelle des accès autorisés identiques sont regroupés en un événement, par défaut `10000`, jusqu’à `3600000` millisecondes ; `0` envoie chacun |

## Utilisateurs, groupes et rôles

Les politiques nomment des utilisateurs PostgreSQL, les rôles qui peuvent se connecter. Les groupes sont les rôles dont un utilisateur est membre, directement ou par d’autres rôles, avec les groupes où GrantForge place l’utilisateur ; les rôles sont ceux que GrantForge donne à l’utilisateur.

## Décisions et limites

- Sans instantané, l’agent ne change rien. Un service qui n’est pas en service, ou un jeton lié à un service dont le type n’est pas `postgresql`, n’accorde rien : l’agent supprime ses rôles et ses politiques de sécurité des lignes, et les droits propres de PostgreSQL s’appliquent.
- Tant que l’agent ne tourne pas, PostgreSQL garde ce qui lui a été donné en dernier ; les changements de politiques l’attendent. Un changement atteint PostgreSQL dans l’intervalle de rafraîchissement et l’intervalle de synchronisation.
- Les tables créées après une synchronisation reçoivent leurs droits à la synchronisation suivante.
- Les superutilisateurs et les propriétaires d’une table peuvent tout faire avec elle ; les superutilisateurs, les rôles avec `BYPASSRLS` et les propriétaires d’une table ne sont pas liés par sa sécurité des lignes, sauf si le propriétaire l’impose.
- Une vue lit ses tables avec les droits de son propriétaire sauf si elle est `security_invoker` : le journal du serveur remonte les tables qu’une vue a lues comme autorisées par un droit hors de GrantForge.
- La lecture directe d’une partition n’est pas filtrée par la sécurité des lignes de sa table partitionnée.
- pgaudit journalise une instruction avant que PostgreSQL l’exécute, si bien qu’une instruction qui échoue ensuite, comme une insertion dont la séquence est refusée, est remontée comme autorisée et son refus comme refusé. Les instructions d’une session que pgaudit ne journalise pas ne sont pas remontées.
- Les refus de PostgreSQL nomment une table sans son schéma : l’événement nomme la table que l’instruction qualifie, la seule table de ce nom, ou celle de `public`. Un objet créé sans schéma est remonté dans `public`.
- L’agent lit le journal du serveur auquel il se connecte, le primaire ; les journaux des réplicas ne sont pas lus.
- PostgreSQL n’a pas de masquage de colonnes, les politiques ne peuvent donc pas masquer de valeurs.

## Guides associés

- [PostgreSQL](/fr/plugins/postgresql/)
- [Services de données, politiques et agents](/fr/external/data-services/)
