---
title: Agent NameNode HDFS
description: Appliquez les politiques de chemin GrantForge dans un NameNode Hadoop 3.5.0 et remontez l’audit des accès.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Côté serveur, `grantforge-plugin-hdfs` définit les ressources et la configuration de connexion ; `grantforge-agent-hdfs` s’installe dans le NameNode, vérifie les accès via l’`INodeAttributeProvider` et l’`AccessControlEnforcer` de Hadoop, et réutilise le cœur d’agent GrantForge pour télécharger les politiques signées, conserver l’instantané local et remonter l’audit par lots. L’agent actuel est construit pour **Hadoop 3.5.0 et Java 17 ou ultérieur** ; les autres versions de Hadoop demandent une adaptation et une validation spécifiques.

Dans les sources, le plug-in serveur se trouve dans `plugins/grantforge-plugin-hdfs` et l’agent NameNode dans `agents/grantforge-agent-hdfs` ; les infrastructures partagées de protocole, de cache d’instantanés et de remontée d’audit se trouvent dans `core/grantforge-agent-core`.

## Relation aux permissions natives

L’agent exécute d’abord les vérifications de permissions natives HDFS, puis les politiques GrantForge : l’utilisateur doit satisfaire les deux. Les politiques d’autorisation de GrantForge ne contournent ni les permissions POSIX, ni les ACL, ni la vérification du propriétaire, ni le sticky bit ; les politiques de refus refusent toujours. Les réglages de permissions natives continuent d’être maintenus avec les outils d’administration de Hadoop.

Par défaut, `grantforge.hdfs.native.fallback=false` : l’accès aux données est refusé quand il n’y a pas d’instantané de politiques local, pas de politique correspondante, ou que l’agent n’a pas encore démarré. Avec `true`, les accès qu’aucune politique ne décide se rabattent sur les permissions natives ; les politiques de refus explicites restent en vigueur. Tant que le serveur est momentanément injoignable, le dernier instantané local ayant passé la vérification de signature continue d’être utilisé.

Dans un même rappel d’autorisation, les ancêtres, la cible, les sous-arbres et les projections de chemin d’instantané utilisent une seule et même version de l’instantané de politiques ; les politiques rafraîchies prennent effet au rappel suivant, ce qui évite de combiner des règles d’autorisation de versions différentes. L’audit des accès consigne la version de politique réellement utilisée.

L’agent vérifie les droits `read`, `write` et `execute` dont un utilisateur ordinaire a besoin pour la cible, ainsi que les répertoires parents, les répertoires ancêtres et les sous-répertoires à valider récursivement. Les opérations comme créer, supprimer ou renommer concernent plusieurs chemins, et les politiques d’autorisation doivent toutes les couvrir. En mode strict, une politique `read` sur le seul fichier cible ne suffit pas : il faut aussi configurer à l’utilisateur des politiques `execute` sur les répertoires ancêtres — par exemple autoriser `execute` sur `/` avec la récursion cochée, puis configurer lecture et écriture sur les répertoires de données réels.

Un chemin d’instantané est vérifié à la fois tel que demandé et sous sa forme d’origine, débarrassé de `.snapshot/<nom de l’instantané>` : par exemple `/data/.snapshot/s1/secret` est aussi vérifié en `/data/secret`. Les politiques de refus sur le chemin d’origine contraignent donc aussi les instantanés ; des restrictions plus strictes peuvent en outre être posées sur les chemins d’instantanés explicites. Les consultations de métadonnées suivent la sémantique de permission de parcours de répertoire de HDFS.

Une autorisation récursive vérifie au plus `100000` inodes ; au-delà de la limite, l’opération est refusée, afin d’éviter une allocation mémoire illimitée dans le NameNode. Les chemins très longs sont jugés avec le chemin complet ; l’affichage de la ressource d’audit est limité à `1000` caractères, et la longueur d’origine ainsi qu’un condensé SHA-256 sont consignés dans le détail de la requête.

Les superutilisateurs HDFS restent gérés par Hadoop. Un rappel de superutilisateur portant un chemin passe d’abord la vérification de superutilisateur de Hadoop, puis le contrôle des politiques selon le nom d’opération fourni par Hadoop 3.5.0 : lecture de fichier et consultation de métadonnées exigent `read`, l’énumération d’un répertoire exige `read` + `execute`, et les opérations de modification connues exigent `write`. Pour les opérations inconnues, absentes ou impossibles à déduire de façon fiable (par exemple `checkAccess` et `concat`), les trois permissions sont exigées par prudence.

Les rappels de superutilisateur n’ont pas le contexte complet des inodes et des sous-arbres, et les appels d’administration du cluster sans chemin conservent les vérifications natives ; il est impossible de restreindre avec des politiques de sous-répertoires toutes les opérations récursives d’un superutilisateur. Les utilisateurs de données doivent utiliser des utilisateurs Hadoop ordinaires.

## Indicateurs

L’agent remonte ses indicateurs par le système Metrics2 de Hadoop, sur les mêmes sinks que les indicateurs dfs du NameNode lui-même ; dans le JMX du NameNode, ils apparaissent sous `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (un exportateur JMX Prometheus peut les collecter directement). Chaque indicateur porte les étiquettes `instance` (nom de l’instance du service de données) et `agentVersion`, ce qui permet de distinguer les deux NameNodes d’une paire HA. Un échec d’enregistrement des indicateurs ne coûte que les indicateurs eux-mêmes : l’agent consigne un avertissement et continue d’appliquer les autorisations sans eux.

| Indicateur | Description |
| --- | --- |
| `Callbacks` | Nombre de rappels d’autorisation exécutés par l’agent |
| `SuperuserCallbacks` | Nombre de rappels de superutilisateur exécutés par l’agent |
| `NativeDenies` | Nombre d’accès refusés par Hadoop avant même l’exécution de l’agent |
| `EvaluationFailures` | Nombre de rappels fermés par refus à cause d’une exception d’évaluation des politiques |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | Nombre de droits jugés autorisés / refusés / indécis par les politiques ; l’indécis est également refusé en mode strict |
| `MissingSnapshots` | Nombre de rappels servis alors qu’aucun instantané de politiques vérifié n’existait |
| `SnapshotVersion` | Version de l’instantané de politiques en cours d’utilisation, 0 quand il n’y en a pas |
| `QueuedEvents` / `DroppedEvents` | Nombre d’événements d’audit en attente en mémoire / d’événements abandonnés parce que la file ou le tampon disque était plein |
| `ServerReachable` | Si le dernier accès au serveur de politiques a réussi (1/0) |

## Déploiement

1. Ajoutez un service `hdfs` dans les services de données de GrantForge, enregistrez la configuration et testez la connexion ; configurez des politiques de chemin pour les noms courts d’utilisateur, groupes ou rôles Hadoop réels.
2. Dans « Autorisations sur les données → Agents », émettez un jeton pour ce service. Écrivez le jeton en clair dans un fichier local de chaque NameNode, par exemple `/etc/hadoop/grantforge/token`, lisible par l’utilisateur qui exécute le NameNode.
3. Placez sur le classpath du NameNode le `grantforge-agent-hdfs-<version>.jar` correspondant à la version publiée courante, issu du répertoire `agents/hdfs/` de la version publiée, par exemple dans `$HADOOP_HOME/share/hadoop/hdfs/lib/`. Le jar de l’agent intègre déjà son propre moteur de politiques, Jackson et la bibliothèque de signature ; les classes Hadoop sont fournies par le NameNode ; la version d’agent annoncée dans les battements de cœur provient des métadonnées de build.
4. Configurez les propriétés suivantes dans le `hdfs-site.xml` de chaque NameNode ; les deux NameNodes d’une installation HA utilisent des `instance` distincts et leurs propres répertoires de cache locaux.

```xml
<property>
  <name>dfs.namenode.inode.attributes.provider.class</name>
  <value>org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider</value>
</property>
<property>
  <name>grantforge.hdfs.server.url</name>
  <value>https://grantforge.example.com/</value>
</property>
<property>
  <name>grantforge.hdfs.token.file</name>
  <value>/etc/hadoop/grantforge/token</value>
</property>
<property>
  <name>grantforge.hdfs.instance</name>
  <value>namenode-1</value>
</property>
<property>
  <name>grantforge.hdfs.cache.dir</name>
  <value>/var/lib/hadoop/grantforge</value>
</property>
<property>
  <name>grantforge.hdfs.native.fallback</name>
  <value>false</value>
</property>
```

5. Confirmez que `dfs.permissions.enabled=true` et que `dfs.namenode.inode.attributes.provider.bypass.users` est vide ; au démarrage, l’agent refuse toute configuration qui permettrait de contourner les rappels d’autorisation. Redémarrez le NameNode, puis vérifiez le battement de cœur et la version de politique sur la page des agents de GrantForge. L’agent lit la configuration existante du NameNode ; il ne modifie pas les attributs natifs des inodes.

Pour un premier déploiement, vous pouvez commencer avec `native.fallback=true`, confirmer que l’instantané de politiques est synchronisé et que les permissions des répertoires ancêtres sont complètes, puis passer en mode strict. Le type de service auquel est lié le jeton doit être `hdfs` ; une erreur de configuration ou un lien à un autre type de service entraîne le refus des accès.

## Réglages facultatifs

| Propriété | Valeur par défaut | Usage |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | Délai d’expiration de la connexion à GrantForge |
| `grantforge.hdfs.read.timeout.ms` | `8000` | Délai d’expiration de lecture de la réponse |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | Intervalle de rafraîchissement des politiques quand le serveur est injoignable, au moins `1000` ; les battements de cœur normaux utilisent l’intervalle conseillé par le serveur |
| `grantforge.hdfs.signing.key.file` | non défini | Fichier facultatif de clé publique de signature, contenant la clé publique X.509 Base64 fournie par la console ; une fois configuré, seules les signatures de cette clé sont acceptées |
| `grantforge.hdfs.audit.batch.size` | `500` | Nombre maximal d’événements par remontée, plage `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | Capacité de la file d’audit en mémoire, plage `1..1000000`, pouvant contenir au moins un lot ; quand la file est pleine, les nouveaux événements sont comptés puis abandonnés |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | Intervalle de vidange de l’audit, entier positif, au plus `2147483647` ; le réduire diminue la latence de remontée |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | Limite du tampon disque quand le serveur est injoignable, entier non négatif ; `0` désactive le tampon disque |

En cas de pics d’accès, vous pouvez agrandir la file d’audit pour réduire les débordements ; raccourcir l’intervalle de vidange diminue la latence d’audit mais augmente la fréquence de remontée. La limite du tampon disque sert à maîtriser l’espace occupé lors d’une coupure prolongée ; désactivé ou épuisé, le tampon peut laisser perdre des événements. La remontée d’audit s’exécute en arrière-plan et n’attend pas la réponse du serveur de politiques.

Sans clé publique de signature configurée, l’agent récupère la clé publique auprès du serveur au premier contact et l’enregistre avec l’instantané. L’agent utilise le nom d’utilisateur court et les groupes transmis par Hadoop ; les rôles et groupes supplémentaires viennent de l’instantané signé ; la correspondance des noms courts des principaux Kerberos est déterminée par le `hadoop.security.auth_to_local` du cluster.

## Construire et vérifier depuis les sources

```sh
./mvnw -pl agents/grantforge-agent-hdfs -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs,core/grantforge-plugin-host -am test
```

L’artefact de l’agent se trouve dans `agents/grantforge-agent-hdfs/target/grantforge-agent-hdfs-<version>.jar`. Les tests unitaires couvrent les rappels d’autorisation du NameNode, la configuration, les métadonnées de version et les décisions de politique ; les tests WebHDFS et Kerberos démarrent des services locaux temporaires. Avant la mise en production, il faut en plus vérifier sur le cluster cible la lecture/écriture, la création, le renommage, la suppression récursive, le basculement HA et le comportement du cache après une coupure réseau.

La vérification d’intégration s’exécute avec la phase `verify` via Testcontainers (les tests unitaires ne démarrent pas de cluster ; `verify` nécessite un démon Docker disponible) :

```sh
./mvnw -pl agents/grantforge-agent-hdfs -am verify
# même point d’entrée que nightly
bash script/ci/hdfs_integration.sh
```

Les tests utilisent des images de conteneur Apache Hadoop à version figée et placent le jar d’agent réellement empaqueté sur le classpath du NameNode. Testcontainers crée un réseau isolé et gère le cycle de vie des NameNode et DataNode, en vérifiant lecture/écriture, création, ajout, renommage, suppression, refus récursifs et sur les instantanés, permissions natives et audit, rafraîchissement des politiques, redémarrage du NameNode sur le cache signé après déconnexion du serveur de politiques, ainsi que le mode strict et le repli sur les permissions natives en l’absence d’instantané.

Un démon Docker en fonctionnement est nécessaire, et le téléchargement des images de test doit être autorisé. Si Docker n’est pas disponible, les tests échouent ; ils ne sont jamais ignorés en silence. Le client de système de fichiers s’exécute à l’intérieur du conteneur Hadoop, et le service HTTP de politiques utilise la redirection de port hôte de Testcontainers ; aucun cluster Hadoop externe n’est requis. À la fin des tests, les conteneurs et le réseau de test sont nettoyés, et les journaux sont conservés dans `agents/grantforge-agent-hdfs/target/hdfs-testcontainers`.

Le test HA démarre deux NameNode, un DataNode et un JournalNode, et configure pour les deux agents des noms d’instance et répertoires de cache indépendants. Il utilise le client HDFS logique pour basculer manuellement le nœud actif, et vérifie la lecture/écriture et les politiques de refus après le basculement ; le JournalNode unique ne sert qu’aux tests : la tolérance de panne par quorum n’est pas vérifiée, et le basculement automatique via ZooKeeper n’est pas concerné.

Les sources et dépendances de test sont placées directement dans le `src/test` et le scope de test de l’`agents/grantforge-agent-hdfs` existant, sans créer de projet de test Maven séparé ; Testcontainers n’entre jamais dans la version publiée de l’agent. Le nightly exécute le même point d’entrée de test sur Java 17 et 21, et conserve les rapports et les journaux des conteneurs.

Pour les points d’entrée d’extension de Hadoop et la sémantique des permissions, voir l’[API Apache Hadoop 3.5.0](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) et le [guide des permissions HDFS](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
