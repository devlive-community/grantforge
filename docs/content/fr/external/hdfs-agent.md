---
title: Agent NameNode Apache Hadoop HDFS
description: Appliquez les politiques GrantForge avec l’agent NameNode adapté à la version Hadoop et remontez l’audit des accès.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Le plug-in serveur `grantforge-plugin-hdfs` fournit de façon unifiée le type de service `hdfs`, ses ressources et ses réglages de connexion. Les agents NameNode sont construits séparément pour chaque version de Hadoop, contrôlent les accès via les `INodeAttributeProvider` et `AccessControlEnforcer` de leur version et partagent la logique des politiques signées, du cache, de l’évaluation des politiques et de l’audit.

Dans les sources, le plug-in serveur se trouve dans `plugins/grantforge-plugin-hdfs`, les agents de version dans `agents/grantforge-agent-hdfs-<line>` et la logique de production commune sans dépendance à Hadoop dans `agents/grantforge-agent-hdfs-common`. L’adaptation native partagée se trouve dans le module de production Maven `agents/grantforge-agent-hdfs-native`, avec Java 8 / Hadoop 2.7.7 comme base ; chaque module numéroté le référence comme dépendance binaire Maven, conserve ses points d’entrée et rappels propres à sa version et ne recompile plus les sources de production partagées. Le protocole, le cache des instantanés et l’infrastructure d’audit restent dans `core/grantforge-agent-core`.

Le plug-in serveur conserve un seul type `hdfs`, et la version du client est indépendante de celle de l’agent. La connexion et la recherche de chemins sous Hadoop 2.x utilisent `webhdfs://namenode:50070` (l’adresse `swebhdfs://` correspondante avec HTTPS) ; Hadoop 3.x accepte le RPC `hdfs://` ou WebHDFS. La matrice de conteneurs vérifie WebHDFS en 2.x, ainsi que RPC et WebHDFS en 3.x ; le RPC 2.x n’est pas certifié et le protocole de connexion n’est jamais changé automatiquement.

Avec l’extension des attributs d’inode activée, Apache Hadoop 2.7.7 déclenche dans son code natif une erreur de pointeur nul lorsqu’un utilisateur ordinaire interroge directement la racine `/`, avant même le rappel de l’agent. Avec cette version, utilisez des répertoires de données réels comme `/data` pour les opérations sur les données et le `lookup.path` du service ; les tests conservent une assertion distincte de l’échec sur la racine.

## Choisir la version

| Suffixe du module d’agent | Apache Hadoop vérifié | JVM du conteneur | Interface d’autorisation | Rappel des chemins superutilisateur |
| --- | --- | --- | --- | --- |
| `2.7` | 2.7.7 | Java 8 | par paramètres | non |
| `2.10` | 2.10.2 | Java 8 | par paramètres | non |
| `3.2` | 3.2.4 | Java 8 | par paramètres | non |
| `3.3` | 3.3.6 | Java 8 | par contexte | non |
| `3.4` | 3.4.3 | Java 11 | par contexte, rappels superutilisateur et de refus | oui |
| `3.5` | 3.5.0 | Java 17 | par contexte, rappels superutilisateur et de refus | oui |

Pour Hadoop 2.10.2, par exemple, installez `agents/hdfs/2.10/grantforge-agent-hdfs-2.10-<GrantForge-version>.jar`. Installez un seul agent de version par NameNode et retirez l’ancien jar lors d’une mise à niveau ; le nom de classe du provider dans la configuration ne change pas. Au démarrage, l’agent vérifie les versions majeure et mineure de Hadoop ainsi que les rappels requis, et refuse de démarrer si la version ne correspond pas ; les autres correctifs de la même version et les distributions des éditeurs doivent toujours être vérifiés séparément.

Le code principal des agents `common` et `2.7` à `3.4` utilise le bytecode Java 8, `3.5` Java 17. Le tableau indique les JVM réellement utilisées par les tests d’intégration ; il ne signifie pas que 3.4.3 a été vérifié sous Java 8. L’image de test officielle de 3.3.6 n’existe que pour amd64 : sur un hôte ARM, elle s’exécute explicitement en émulation amd64.

## Relation avec les autorisations natives

L’agent exécute d’abord les contrôles d’autorisation natifs de HDFS, puis les politiques GrantForge : l’utilisateur doit satisfaire à la fois les autorisations natives et les politiques. Les politiques d’autorisation de GrantForge ne contournent ni les droits POSIX, ni les ACL, ni les contrôles de propriétaire, ni le sticky bit ; les politiques de refus refusent toujours. Les autorisations natives se gèrent toujours avec les outils d’administration de Hadoop.

Par défaut, `grantforge.hdfs.native.fallback=false` : sans instantané local des politiques, sans politique correspondante ou tant que l’agent n’a pas démarré, l’accès aux données est refusé. Avec `true`, les accès qu’aucune politique ne tranche relèvent des autorisations natives ; les refus explicites restent en vigueur. Si le serveur est temporairement injoignable, le dernier instantané local à la signature vérifiée continue d’être utilisé.

Les projections des ancêtres, de la cible, des sous-arbres et des chemins d’instantané au sein d’un même rappel d’autorisation utilisent la même version de l’instantané des politiques ; les politiques actualisées prennent effet au rappel suivant, ce qui évite de mélanger des règles d’autorisation de versions différentes. L’audit d’accès enregistre la version des politiques réellement utilisée.

L’agent vérifie les droits `read`, `write` et `execute` dont un utilisateur ordinaire a besoin sur la cible, ainsi que sur le répertoire parent, les répertoires ancêtres et les sous-répertoires à vérifier récursivement. Créer, supprimer, renommer et d’autres opérations touchent plusieurs chemins, et les politiques d’autorisation doivent tous les couvrir. En mode strict, une politique `read` sur le fichier cible ne suffit pas : l’utilisateur a aussi besoin de politiques `execute` sur les répertoires ancêtres, par exemple `execute` sur `/` avec la récursion cochée, puis de droits de lecture et d’écriture sur les répertoires de données réels.

Les chemins d’instantané sont vérifiés à la fois tels qu’ils sont demandés et sous leur forme d’origine sans `.snapshot/<nom de l’instantané>` : `/data/.snapshot/s1/secret` vérifie aussi `/data/secret`. Les politiques de refus du chemin d’origine s’appliquent donc aussi aux instantanés ; on peut en plus poser des restrictions plus strictes sur les chemins d’instantané explicites. Les requêtes de métadonnées suivent la sémantique de parcours des répertoires de HDFS.

Une autorisation récursive examine au plus `100000` inodes ; au-delà, l’opération est refusée pour éviter que le NameNode n’alloue de la mémoire sans limite. Les chemins très longs sont évalués sur le chemin complet ; l’affichage de la ressource dans l’audit est limité à `1000` caractères, et le détail de la requête enregistre la longueur d’origine et un condensat SHA-256.

Hadoop 2.7, 2.10, 3.2 et 3.3 laissent passer les superutilisateurs avant d’appeler l’agent, qui ne peut donc ni contrôler ni auditer ces accès. Avec Hadoop 3.4 et 3.5, les rappels superutilisateur avec chemin passent d’abord le contrôle natif, puis les politiques sont vérifiées selon le nom de l’opération : la lecture de fichiers et les requêtes de métadonnées exigent `read`, le listage de répertoires `read` + `execute`, et les opérations de modification connues `write`. Les opérations inconnues, absentes ou impossibles à déduire avec certitude (comme `checkAccess` et `concat`) exigent par prudence les trois droits.

Les rappels superutilisateur n’ont pas le contexte complet des inodes et des sous-arbres ; les appels d’administration du cluster sans chemin restent soumis au seul contrôle natif, et les politiques de sous-répertoires ne peuvent pas restreindre toutes les opérations récursives d’un superutilisateur. Les consommateurs de données doivent utiliser des utilisateurs Hadoop ordinaires.

## Métriques

L’agent publie ses métriques via le système Metrics2 de Hadoop, avec les mêmes sinks que les métriques dfs du NameNode ; dans le JMX du NameNode, elles apparaissent sous `Hadoop:service=NameNode,name=GrantForgeHdfsAgent` (l’exportateur JMX de Prometheus peut les collecter directement). Chaque métrique porte les étiquettes `instance` (nom de l’instance du service de données) et `agentVersion`, ce qui permet de suivre séparément les deux NameNode d’une paire HA. Un échec d’enregistrement ne fait perdre que les métriques : l’agent journalise un avertissement et continue d’autoriser sans métriques.

| Métrique | Description |
| --- | --- |
| `Callbacks` | Rappels d’autorisation exécutés par l’agent |
| `SuperuserCallbacks` | Rappels superutilisateur exécutés par l’agent |
| `NativeDenies` | Accès refusés par Hadoop avant l’agent |
| `EvaluationFailures` | Rappels fermés en échec à cause d’une exception d’évaluation des politiques |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | Droits que les politiques ont autorisés / refusés / laissés indéterminés ; les indéterminés sont aussi refusés en mode strict |
| `MissingSnapshots` | Rappels servis sans instantané des politiques vérifié |
| `SnapshotVersion` | Version de l’instantané des politiques en cours, 0 s’il n’y en a pas |
| `QueuedEvents` / `DroppedEvents` | Événements d’audit en attente d’envoi en mémoire / abandonnés parce que la file ou le tampon disque était plein |
| `ServerReachable` | Si le dernier accès au serveur de politiques a réussi (1/0) |

## Déploiement

1. Ajoutez un service `hdfs` dans les services de données de GrantForge, enregistrez la configuration et testez la connexion ; configurez des politiques de chemin pour les noms courts réels des utilisateurs Hadoop, leurs groupes ou leurs rôles.
2. Dans « Autorisations sur les données → Agents », émettez un jeton pour ce service. Écrivez le texte du jeton dans un fichier local sur chaque NameNode, par exemple `/etc/hadoop/grantforge/token`, lisible par l’utilisateur qui exécute le NameNode.
3. Selon le tableau, choisissez le jar d’agent correspondant dans `agents/hdfs/<ligne Hadoop>/` du paquet de distribution et placez-le dans le classpath du NameNode, par exemple `$HADOOP_HOME/share/hadoop/hdfs/lib/`. Le jar d’agent embarque son propre moteur de politiques, Jackson et la bibliothèque de signature ; les classes Hadoop sont fournies par le NameNode. La version d’agent envoyée dans le heartbeat comprend la version du produit et la version de Hadoop utilisée pour la compilation.
4. Configurez les propriétés suivantes dans le `hdfs-site.xml` de chaque NameNode ; les deux NameNode d’une paire HA utilisent des `instance` différentes et chacun son propre répertoire de cache local.

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

5. Vérifiez que `dfs.permissions.enabled=true` et que `dfs.namenode.inode.attributes.provider.bypass.users` est vide ; l’agent refuse de démarrer avec une configuration qui permettrait de contourner le rappel d’autorisation. Redémarrez le NameNode, puis vérifiez le heartbeat et la version des politiques sur la page des agents de GrantForge. L’agent lit la configuration existante du NameNode et ne modifie pas les attributs natifs des inodes.

Pour un premier déploiement, vous pouvez commencer avec `native.fallback=true`, vérifier que l’instantané des politiques est synchronisé et compléter les droits sur les répertoires ancêtres, puis passer en mode strict. Le jeton doit être lié au type de service `hdfs` ; une configuration erronée ou un lien vers un autre type de service entraîne un refus.

## Réglages facultatifs

| Propriété | Valeur par défaut | Rôle |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | Délai de connexion à GrantForge |
| `grantforge.hdfs.read.timeout.ms` | `8000` | Délai de lecture de la réponse |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | Intervalle d’actualisation des politiques tant que le serveur est injoignable, au moins `1000` ; le heartbeat normal suit l’intervalle conseillé par le serveur |
| `grantforge.hdfs.signing.key.file` | non défini | Fichier facultatif de clé publique de signature, contenant la clé publique X.509 en Base64 fournie par la console ; une fois défini, seules les signatures de cette clé sont acceptées |
| `grantforge.hdfs.audit.batch.size` | `500` | Nombre maximal d’événements par envoi, plage `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | Capacité de la file d’audit en mémoire, plage `1..1000000`, au moins un lot ; quand elle est pleine, les nouveaux événements sont comptés et abandonnés |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | Intervalle de vidage de l’audit, entier positif d’au plus `2147483647` ; une valeur plus petite réduit le délai d’envoi |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | Limite du tampon disque tant que le serveur est injoignable, entier positif ou nul ; `0` désactive le tampon disque |

En cas de pics d’accès, augmentez la file d’audit pour limiter les débordements ; raccourcir l’intervalle de vidage réduit le délai d’audit mais augmente la fréquence des envois. La limite du tampon disque borne l’espace disque utilisé lors de longues coupures ; tampon désactivé ou épuisé, des événements peuvent être perdus. L’audit est envoyé en arrière-plan sans attendre la réponse du serveur de politiques.

Sans clé publique de signature configurée, l’agent la récupère auprès du serveur au premier accès et l’enregistre avec l’instantané. L’agent utilise le nom d’utilisateur court et les groupes transmis par Hadoop ; les rôles et les groupes supplémentaires viennent de l’instantané signé. La correspondance entre principals Kerberos et noms courts est déterminée par le `hadoop.security.auth_to_local` du cluster.

## Construire depuis les sources et vérifier

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

L’artefact de l’agent se trouve dans `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar`. Les tests unitaires couvrent les rappels d’autorisation du NameNode, la configuration, les métadonnées de version et les décisions des politiques ; les tests WebHDFS et Kerberos démarrent des services locaux temporaires. Avant la mise en production, vérifiez aussi sur le cluster cible la lecture et l’écriture, la création, le renommage, la suppression récursive, la bascule HA et le comportement du cache après une coupure.

La vérification d’intégration s’exécute avec Testcontainers pendant la phase `verify` (les tests unitaires ne démarrent pas de cluster ; `verify` exige un démon Docker disponible) :

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# même point d’entrée que nightly
bash script/ci/hdfs_integration.sh
# ne vérifier que la ligne de versions indiquée
bash script/ci/hdfs_integration.sh 2.10
```

Les tests utilisent des images Apache Hadoop de version fixe ; les images anciennes sont construites à partir d’une image de base Java 8 au condensat fixé et d’une distribution Apache vérifiée par SHA-512. Le véritable jar d’agent de la version correspondante est placé dans le classpath du NameNode, et les versions réelles de Hadoop et de la JVM sont vérifiées. Testcontainers crée un réseau isolé et gère le cycle de vie du NameNode et du DataNode ; les tests vérifient la lecture et l’écriture, la création, l’ajout, le renommage, la suppression, les refus récursifs et sur instantané, les autorisations natives et l’audit, l’actualisation des politiques, le redémarrage du NameNode avec le cache signé après coupure du serveur de politiques, ainsi que le mode strict et le repli sur les autorisations natives en l’absence d’instantané.

Un démon Docker en marche est nécessaire, ainsi que l’autorisation de télécharger les images de test. Sans Docker, les tests échouent au lieu d’être ignorés en silence. Le client du système de fichiers s’exécute dans le conteneur Hadoop, et le service HTTP de politiques passe par la redirection de ports de l’hôte de Testcontainers ; aucun cluster Hadoop externe n’est nécessaire. Après les tests, les conteneurs et le réseau de test sont supprimés, et les journaux sont conservés dans `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`.

Le test HA démarre deux NameNode, un DataNode et un JournalNode, et donne aux deux agents des noms d’instance et des répertoires de cache distincts. Il bascule le nœud actif à la main avec un client HDFS logique, puis vérifie la lecture, l’écriture et les politiques de refus après la bascule ; un seul JournalNode ne sert qu’au test, si bien que ni la tolérance aux pannes par majorité ni le basculement automatique via ZooKeeper ne sont vérifiés.

Le test de HA automatique (Hadoop 2.10 à 3.5, Java 17 et 21) démarre ZooKeeper, trois JournalNode, deux NameNode dotés chacun d’un ZKFC et un DataNode. Il vérifie qu’après l’arrêt direct du NameNode actif, ZooKeeper élit un autre nœud, que le nouveau nœud actif continue d’appliquer les politiques et que les événements de refus sont rattachés à son instance ; que les nouvelles politiques publiées pendant l’absence d’un nœud s’appliquent après son retour en Standby, et qu’après la bascule inverse la nouvelle version reste appliquée au lieu de revenir à l’ancienne ; que, tant que le service de politiques est indisponible, le nœud qui a pris le relais continue avec l’instantané déjà chargé et renvoie l’audit une fois le service rétabli ; qu’avec un JournalNode sur trois arrêté les écritures réussissent, alors qu’avec deux elles échouent et le NameNode actif s’arrête au lieu de continuer sans majorité. Il existe aussi une variante Kerberos (Hadoop 3.5.0 uniquement) : les JournalNode se connectent aussi par keytab et n’ouvrent que HTTPS, les ZKFC s’authentifient auprès de ZooKeeper par SASL, et seul le principal du NameNode peut accéder au nœud d’élection (un client ZooKeeper non authentifié ne peut même pas lire) ; le test vérifie qu’après l’arrêt du NameNode actif un autre nœud prend le relais et continue d’appliquer les politiques aux utilisateurs Kerberos, que les clients sans ticket sont toujours refusés et qu’on peut rebasculer une fois le nœud d’origine revenu. Les JournalNode tournent sous un principal de même nom court que celui du NameNode, car un JournalNode ne livre les edits qu’au principal complet du NameNode ou à un demandeur portant son propre nom court, et l’authentification HTTP ne fournit que le nom court. Le HA automatique de Hadoop 2.7 n’est pas vérifié : son client ne bascule que si tous les noms d’hôte des NameNode se résolvent ; le nom du conteneur arrêté pendant le test ne se résout plus, si bien que le client signale aussitôt une UnknownHostException sans essayer l’autre nœud.

Le test Kerberos s’exécute sur les six lignes de versions (Java 17 et 21) : le KDC tourne dans la JVM de test, le NameNode et le DataNode démarrent en mode sécurisé avec chacun son keytab, le transfert de données du DataNode exige SASL et n’ouvre que HTTPS, et WebHDFS utilise SPNEGO. Le test vérifie qu’une fois le principal ramené au nom court, les politiques GrantForge s’appliquent à la lecture et à l’écriture, que les utilisateurs sans politique sont refusés, que les refus sont audités sous le nom court, que les clients sans ticket sont refusés sans repli sur l’authentification simple, et que le NameNode se reconnecte après un redémarrage et continue d’appliquer les politiques.

Les sources des tests se trouvent dans le `src/test` des véritables modules de production. Les tests unitaires communs des politiques s’exécutent dans common et les tests unitaires natifs partagés dans `agents/grantforge-agent-hdfs-native/src/test` ; ils ne sont plus compilés dans les six agents de version. Les tests de rappel de chaque version restent dans leur module numéroté, et les sources de tests de conteneur partagées `agents/grantforge-agent-hdfs-common/src/test/shared` sont toujours compilées dans chaque module numéroté ; il n’y a pas de projet Maven de test séparé. Testcontainers n’est présent qu’avec la portée test. Nightly vérifie les six versions de Hadoop sur des hôtes Java 17 et 21, et les conteneurs Hadoop utilisent les JVM du tableau. Les conteneurs couvrent aujourd’hui l’authentification Simple, la HA manuelle, Kerberos et HTTPS sur les six lignes de versions, la HA automatique de 2.10 à 3.5 et la HA automatique sous Kerberos sur Hadoop 3.5.0 ; la HA automatique de 2.7, la HA automatique sous Kerberos sur les autres versions et les correctifs des éditeurs restent à vérifier dans l’environnement cible.

Les points d’extension et la sémantique des autorisations de Hadoop sont décrits dans l’[API Apache Hadoop 3.5.0](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html) et le [guide des autorisations HDFS](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html).
