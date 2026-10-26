---
title: Agent OpenSearch
description: Exécuter l’agent OpenSearch à côté du cluster, pour que les politiques GrantForge deviennent les rôles propres du plug-in de sécurité, avec sécurité au niveau des champs, champs masqués et sécurité au niveau des documents, que les motifs d’index restent des motifs afin que les nouveaux index soient aussitôt accessibles, et que les accès soient signalés depuis le journal d’audit.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

L’agent OpenSearch est un service à côté du cluster. Il maintient le contrôle d’accès propre au plug-in de sécurité en phase avec les politiques signées d’un service de données `opensearch` : un rôle par utilisateur qui contient exactement ce que les politiques lui donnent, associé à ce seul utilisateur, avec sécurité au niveau des champs, champs masqués et sécurité au niveau des documents. OpenSearch vérifie alors lui-même chaque requête, et l’agent ne se place jamais entre les clients et le cluster. La distribution le fournit sous forme de JAR exécutable, `agents/opensearch/grantforge-agent-opensearch-<version>.jar`, qui requiert Java 17 ou plus récent.

Les tests d’intégration Testcontainers du dépôt exécutent l’agent, et le JAR tel que livré, contre OpenSearch `2.19`, `3.3`, `3.6` et `3.9` avec le plug-in de sécurité. Cela ne certifie pas toutes les autres versions ; l’agent refuse les clusters antérieurs à `2.19` et les clusters sans plug-in de sécurité. Pour le plug-in de service, voir [OpenSearch](/fr/plugins/opensearch/).

## Fonctionnement

- **Un rôle par utilisateur contient ce que GrantForge donne.** Pour chaque utilisateur à qui les politiques donnent quelque chose, l’agent tient un rôle à son nom, `grantforge_alice` pour `alice`, associé à ce seul utilisateur. Sa description le marque comme celui de l’agent, et l’agent ne prend aucun rôle sans cette marque pour l’un des siens, de sorte qu’il ne modifie aucun autre rôle ni association.
- **Les motifs d’index restent des motifs.** Un motif d’index d’une politique, comme `logs-*`, est accordé comme motif partout où toutes les politiques décident de même sur toute son étendue : toute politique de tout type dont les index recoupent le motif le couvre entièrement. L’agent le décide exactement, avec des automates finis des motifs. Les index créés ensuite, comme les journaux de demain, sont alors aussitôt accessibles, y compris quand le premier document écrit les crée. Là où une politique décide autrement à l’intérieur d’un motif, comme un refus de `logs-secret` dans `logs-*`, l’agent accorde chaque index et flux de données du cluster par son nom, et les nouveaux à la synchronisation suivante.
- **La sécurité au niveau des champs seulement là où quelque chose en dessous se décide autrement.** Un index que l’utilisateur peut lire en entier, sans politique qui pourrait lui retirer un champ, est lu sans sécurité au niveau des champs, de sorte que les champs ajoutés plus tard sont lus aussitôt. Sinon l’utilisateur lit les champs qu’une politique autorise, parmi ceux que ses documents ont et ceux que les politiques nomment ; les multi-champs comme `name.keyword` suivent leur champ.
- **Masques et filtres de lignes sont ceux du plug-in de sécurité.** Un masque devient un champ masqué de l’entrée, et un filtre de lignes sa requête de sécurité au niveau des documents. L’API validate d’OpenSearch vérifie chaque requête avant qu’elle soit accordée ; une requête qu’OpenSearch n’exécuterait pas ne montre aucun document à l’utilisateur, et elle est journalisée. Un masque que le plug-in de sécurité ne connaît pas cache le champ à la place.
- **Une entrée par ensemble de permissions.** Les index et motifs aux mêmes permissions partagent une entrée ; les entrées ne se recoupent jamais, car le plug-in de sécurité combine la sécurité au niveau des champs et des documents des entrées qui se recoupent.
- **Les opérations composites du cluster viennent avec la lecture et l’écriture.** La lecture apporte `cluster_composite_ops_ro`, pour multi-search, multi-get et le défilement, et l’écriture `cluster_composite_ops`, pour bulk.
- **Les changements sont un correctif.** L’agent écrit ses rôles et associations comme correctifs JSON, que le plug-in de sécurité applique d’un coup dans tout le cluster : les rôles avant les associations qui les nomment, les associations avant les rôles qu’elles nomment et qui sont retirés. Un correctif que le plug-in de sécurité refuse est appliqué changement par changement, de sorte qu’un rôle défectueux échoue seul.
- **Les politiques du moment présent.** Chaque synchronisation planifie selon les politiques telles qu’elles s’appliquent à ce moment, de sorte que les périodes de validité commencent et finissent à l’heure.
- **Les rôles backend sont des groupes.** Une politique pour un groupe s’applique aux utilisateurs internes qui ont un rôle backend de ce nom, ainsi qu’aux groupes dans lesquels GrantForge place les utilisateurs.
- **Les changements faits à la main sont annulés.** L’agent compare à chaque synchronisation ses rôles et associations avec ce que détient le plug-in de sécurité ; ce qui diffère est réécrit. Quand rien n’a changé, l’agent n’envoie rien.
- **Les rôles hors de GrantForge sont signalés.** OpenSearch accorde ce que donne n’importe lequel des rôles d’un utilisateur, l’agent ne peut donc pas retirer ce qu’un autre rôle associé à l’utilisateur donne. Il avertit une fois de chaque rôle de ce genre qui donne ce qu’une politique refuse, en nommant le rôle, l’utilisateur, l’accès, l’index et la politique, ainsi que des utilisateurs associés à `all_access`, qu’aucun rôle ne lie.
- **Les accès viennent du journal d’audit.** L’agent lit le journal d’audit du plug-in de sécurité dans les index où il l’écrit, `security-auditlog-*` : ce qu’il a refusé, comme `MISSING_PRIVILEGES`, et ce qu’il a autorisé, comme `GRANTED_PRIVILEGES` lorsque la configuration d’audit les journalise. Chaque événement nomme l’utilisateur, le type d’accès, chaque index ou flux de données atteint, ou le cluster, et dit si GrantForge l’a décidé ou si un rôle hors de GrantForge l’a autorisé.

| Action | Événement d’accès |
| --- | --- |
| `indices:data/read/*` : recherche, lecture, comptage | `read` sur chaque index ou flux de données |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/aliases*`, `indices:monitor/*` | `manage_aliases`, `monitor` |
| Autres `indices:admin/*` | `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*` et `repository/*` | `cluster_monitor`, `manage_snapshots` sur le cluster |
| Modèles d’index et de composants, pipelines d’ingestion | `manage_templates`, `manage_pipelines` sur le cluster |

## Déploiement

1. Ajoutez un service de données `opensearch` et configurez ses politiques. Ouvrez **Autorisations sur les données → Agents**, sélectionnez le service et émettez un jeton d’agent. Il n’est affiché qu’une fois ; conservez-le dans un fichier que seul le compte de l’agent peut lire.
2. Donnez à l’agent un utilisateur qui peut modifier la configuration du plug-in de sécurité : un utilisateur associé à `all_access` tant que `plugins.security.restapi.roles_enabled` le liste, ou le certificat d’administration que nomme `plugins.security.authcz.admin_dn`. Les tests exécutent l’agent avec l’`admin` de la configuration de démonstration, et avec son certificat d’administration.
3. Pour signaler les accès, gardez le journal d’audit dans le cluster (`plugins.security.audit.type: internal_opensearch`). Pour ce qu’OpenSearch a autorisé, retirez aussi `GRANTED_PRIVILEGES` des catégories désactivées de la configuration d’audit ; les refus sont journalisés par défaut.
4. Écrivez le fichier de paramètres en remplaçant les adresses, chemins et noms d’exemple :

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/opensearch-agent.token
   grantforge.instance=opensearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/opensearch-agent
   grantforge.opensearch.hosts=https://opensearch1.internal:9200,https://opensearch2.internal:9200
   grantforge.opensearch.user=grantforge
   grantforge.opensearch.password.file=/etc/grantforge/opensearch-agent.password
   grantforge.opensearch.tls.ca=/etc/grantforge/opensearch-ca.pem
   ```

   Ces chemins sont sur la machine de l’agent, et le répertoire de cache doit être accessible en écriture. Les fichiers de mot de passe sont lus en UTF-8 et rognés.
5. Démarrez l’agent, par exemple comme service systemd :

   ```bash
   java -jar agents/opensearch/grantforge-agent-opensearch-<version>.jar /etc/grantforge/opensearch-agent.properties
   ```

   Remplacez `<version>` par la version de la distribution. Les paramètres fournis sous la forme `java -Dgrantforge.…=… -jar …` remplacent ceux du fichier. L’agent s’arrête proprement sur SIGTERM et envoie ou met en file les événements en attente ; OpenSearch garde ce qu’il a reçu.
6. **Transférer les rôles existants dans des politiques :** les rôles associés aux utilisateurs hors de GrantForge continuent de s’appliquer à côté de ceux de l’agent. Créez les politiques correspondantes, puis supprimez ces associations ; les avertissements de l’agent nomment les rôles qui donnent ce qu’une politique refuse.

| Paramètre | Rôle |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obligatoires : le serveur GrantForge, le fichier du jeton d’agent, un nom pour cet agent et un répertoire accessible en écriture pour l’instantané signé, les événements en file et la position de lecture du journal d’audit |
| `grantforge.signing.key.file` | Fichier facultatif qui fixe la clé de signature des instantanés ; copiez la clé publique X.509 Ed25519 encodée en Base64 depuis la page Agents. S’il est omis, la clé est récupérée auprès du serveur au premier contact |
| `grantforge.opensearch.hosts` | Obligatoire : les nœuds sous forme d’URL, essayés tour à tour lorsqu’un nœud est injoignable ; sans schéma, un nœud est `https`, et le port par défaut est `9200` |
| `grantforge.opensearch.user`, `grantforge.opensearch.password.file` | L’utilisateur de l’agent et le fichier de son mot de passe |
| `grantforge.opensearch.tls.cert`, `grantforge.opensearch.tls.key` | À la place : certificat client PEM et sa clé privée, ensemble, pour se connecter avec un certificat ; `grantforge.opensearch.tls.key.passphrase.file` contient la phrase secrète d’une clé chiffrée |
| `grantforge.opensearch.ssl.verification.mode` | `full` (par défaut) vérifie les certificats et les noms des nœuds, `certificate` leurs certificats seulement, `none` rien |
| `grantforge.opensearch.tls.ca` | Certificats d’AC PEM qui vérifient les nœuds ; vide fait confiance à ce à quoi Java fait confiance |
| `grantforge.opensearch.timeout.ms` | Durée permise pour la connexion et une requête, par défaut `30000`, plage `1..600000` millisecondes |
| `grantforge.opensearch.role.prefix` | Le début des noms des rôles de l’agent, par défaut `grantforge_` ; lettres minuscules, chiffres, traits d’union et tirets bas, terminé par un tiret bas, au plus 32 caractères. L’agent possède les rôles qu’il a marqués dont le nom commence par lui |
| `grantforge.opensearch.unmanaged.users` | Utilisateurs que l’agent laisse tranquilles, séparés par des virgules, comme des comptes de service ; il laisse toujours tranquilles son propre utilisateur et les utilisateurs associés à `all_access` |
| `grantforge.sync.interval.ms` | Fréquence à laquelle l’agent met OpenSearch en phase, par défaut `10000`, plage `1000..3600000` millisecondes |
| `grantforge.audit.log` | Par défaut `true` ; `false` ne lit pas de journal d’audit et ne signale aucun accès |
| `grantforge.audit.log.index`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.documents` | Les index du journal d’audit, par défaut `security-auditlog-*` ; la fréquence de lecture, par défaut `5000` millisecondes ; et le nombre maximal de documents par lecture, par défaut `1000`, de sorte qu’un arriéré soit lu en plusieurs fois |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Délais de connexion et de lecture vers GrantForge, par défaut `5000` / `8000` millisecondes |
| `grantforge.refresh.interval.ms` | Fréquence à laquelle l’agent demande de nouvelles politiques, par défaut `30000`, au moins `1000` millisecondes |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | Événements d’accès : taille de lot (par défaut `500`), taille de file (par défaut `10000`), intervalle d’envoi (par défaut `5000` millisecondes) et limite de file sur disque (par défaut 64 Mio) |
| `grantforge.audit.fold.window.ms` | Durée pendant laquelle des accès autorisés identiques sont regroupés en un événement, par défaut `10000`, jusqu’à `3600000` millisecondes ; `0` envoie chacun |

## Utilisateurs, groupes et rôles

Les politiques nomment les utilisateurs d’OpenSearch tels que le plug-in de sécurité les connaît. L’agent tient des rôles pour les utilisateurs internes, pour les utilisateurs que nomment les politiques, qui peuvent se connecter par un annuaire comme LDAP ou SAML, et pour les utilisateurs dont il tenait déjà des rôles ; les utilisateurs réservés et cachés du plug-in de sécurité, comme `kibanaserver`, sont laissés tranquilles. Les groupes sont les rôles backend d’un utilisateur interne ainsi que les groupes dans lesquels GrantForge place l’utilisateur ; l’agent ne connaît pas les rôles backend qu’un annuaire donne à la connexion, donnez donc à ces utilisateurs leurs groupes dans GrantForge. Les rôles sont ceux que GrantForge donne à l’utilisateur.

## Décisions et limites

- Sans instantané, l’agent ne change rien. Un service inutilisé, ou un jeton lié à un service dont le type n’est pas `opensearch`, n’accorde rien : l’agent retire ses rôles et associations, et les rôles propres du plug-in de sécurité s’appliquent.
- Tant que l’agent ne tourne pas, OpenSearch garde ce qu’il a reçu en dernier ; les changements de politiques l’attendent. Un changement atteint OpenSearch dans l’intervalle de rafraîchissement et l’intervalle de synchronisation.
- Les index accordés par leur nom, et non par un motif, reçoivent leurs permissions à la synchronisation suivante.
- Les alias atteignent les index qui les composent : rechercher dans un alias demande des permissions sur chacun de ses index, donc les politiques nomment des index et des flux de données, pas des alias.
- Un filtre de lignes ne peut tester que les champs que l’utilisateur peut lire, et un masque s’applique aux valeurs des champs text et keyword.
- Les index dont le nom commence par un point ne sont atteints que par des motifs de politique qui commencent par un point.
- L’agent ne gère pas les tenants du plug-in de sécurité pour OpenSearch Dashboards.

## Guides associés

- [OpenSearch](/fr/plugins/opensearch/)
- [Services de données, politiques et agents](/fr/external/data-services/)
