---
title: Plug-ins et types de service
description: "Étendre la gestion des politiques de GrantForge aux systèmes de données externes grâce aux plug-ins de type de service : contrat, empaquetage, isolation et distribution."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Un plug-in de type de service décrit un système externe : quelles hiérarchies de ressources il comporte, quels types d’accès il connaît, s’il permet le caviardage et le filtrage par lignes, et quelle configuration une connexion exige. GrantForge fournit ainsi à ces systèmes des services de données, un éditeur de politiques générique, des instantanés de politiques et l’audit des accès (voir [Services de données et politiques](/fr/external/data-services/)).

## Dépendances

Un plug-in ne dépend que de `grantforge-plugin-api` (qui lui-même ne dépend que du JDK et de JSpecify), introduit avec la portée `provided` :

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## Implémenter ServiceTypeProvider

```java
public final class ExampleProvider implements ServiceTypeProvider
{
    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder("example").label("Example warehouse")
                .resources(ResourceDefinition.builder("database").label("Database").lookupSupported(true).validLeaf(true)
                                .excludesSupported(false).build(),
                        ResourceDefinition.builder("table").label("Table").parent("database").lookupSupported(true).validLeaf(true).build(),
                        ResourceDefinition.builder("column").label("Column").parent("table").accessTypes("select").build(),
                        ResourceDefinition.builder("path").label("Path").matcher(MatcherType.PATH).recursiveSupported(true).build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("all", "All", "select", "update"))
                .dataMask(new DataMaskDefinition(Set.of("column"), List.of(new MaskTypeDefinition("redact", "Redact", "redact({col})"))))
                .rowFilter(new RowFilterDefinition(Set.of("table")))
                .conditions(ConditionDefinition.of("ip-range", "Client addresses", "ip-range"))
                .configFields(ConfigField.builder("url").label("Address").type(ConfigFieldType.STRING).mandatory().pattern("example://.+").build(),
                        ConfigField.builder("timeout").label("Timeout (seconds)").type(ConfigFieldType.INTEGER).defaultValue("30").build(),
                        ConfigField.builder("password").label("Password").type(ConfigFieldType.SECRET).mandatory().build())
                .build();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        return "example".equals(config.get("password")) ? ConnectionResult.succeeded()
                : ConnectionResult.failed("the example warehouse refused the password");
    }

    @Override
    public List<String> lookup(LookupRequest request)
    {
        // Renvoie au plus request.limit() valeurs candidates sous le niveau request.resource() qui commencent par request.userInput()
        return List.of();
    }
}
```

L’extrait ci-dessus est tiré du plug-in d’exemple `plugins/grantforge-plugin-example` et peut être copié directement comme modèle.

La définition est validée en une seule passe lors de la construction et signale tous les problèmes d’un coup : niveau parent inconnu ou cyclique, noms en doublon, référence à un type d’accès ou à une ressource non déclarés, etc. Les noms doivent correspondre à `[a-z][a-z0-9_-]{0,63}`.

| Élément | Description |
| --- | --- |
| Ressources | hiérarchie, mode de correspondance (exact, joker, chemin, expression régulière), sensibilité à la casse, caractère obligatoire, prise en charge des exclusions et de la récursivité (chemin uniquement), possibilité de recherche, possibilité de servir de feuille |
| Types d’accès | nom, nom d’affichage, autres types d’accès impliqués (`all` implique par exemple `select`), éventuellement limités à certaines ressources |
| Caviardage, filtrage par lignes | déclaration des ressources prises en charge et des modes de caviardage ; l’application a lieu dans le système cible |
| Conditions | conditions qu’une politique peut attacher (comme une plage d’adresses IP), évaluées par le SPI de conditions du moteur de politiques |
| Champs de configuration | chaîne, texte long, entier, booléen, secret, énumération ; les champs de type secret sont conservés chiffrés et ne peuvent pas avoir de valeur par défaut |

Le fournisseur doit exposer un constructeur public sans argument et être sûr en présence de threads. `validateConfig`, `testConnection` et `lookup` disposent tous d’implémentations par défaut, à surcharger selon les besoins.

## Descripteur et empaquetage

Placez `grantforge-plugin.yaml` à la racine du plug-in :

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.0"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

Un plug-in peut être :

- un jar, avec le descripteur à la racine du jar ;
- un répertoire ou un zip : `grantforge-plugin.yaml`, `classes/` et `lib/*.jar`.

Déposez-le dans `grantforge.plugins.directory` (par défaut `plugins`), puis cliquez sur « Relancer l’analyse » dans la page « Plug-ins » de la console : aucun redémarrage n’est nécessaire.

Lorsqu’un plug-in embarque des dépendances, empaquetez-le comme `plugins/grantforge-plugin-hdfs`, avec assembly, en un zip de la classification `plugin` (descripteur au premier niveau, `classes/`, `lib/`), et copiez les dépendances d’exécution vers `target/plugin-lib` lors de la phase `generate-resources` :

```xml
<plugin>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions>
    <execution>
      <id>plugin-lib</id>
      <phase>generate-resources</phase>
      <goals><goal>copy-dependencies</goal></goals>
      <configuration>
        <includeScope>runtime</includeScope>
        <outputDirectory>${project.build.directory}/plugin-lib</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## Au démarrage depuis les sources

Lorsque `org.devlive.grantforge.server.GrantForge` est lancé directement dans l’IDE, les classes du serveur proviennent des `target/classes` de chaque module. Dans ce cas, si `grantforge.plugins.directory` n’est pas configuré et qu’aucun répertoire `plugins` n’existe dans le répertoire de travail, le répertoire `plugins/` du dépôt est utilisé : les modules de plug-ins qui y ont été construits (un descripteur dans `target/classes` et une construction ayant produit `target/plugin-lib`) sont chargés directement comme plug-ins, avec les classes issues de `target/classes` et les dépendances issues de `target/plugin-lib` ; les modules qui ne produisent pas de `plugin-lib` (comme le plug-in d’exemple utilisé pour les tests) ne sont pas chargés. Après une modification du code d’un plug-in, laissez l’IDE le recompiler, puis relancez l’analyse dans la page « Plug-ins » de la console pour la prendre en compte. Construisez une fois un module de plug-in avec Maven avant sa première utilisation, afin d’en copier les dépendances :

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## Compatibilité

`apiVersion` déclare la version du contrat dont le plug-in a besoin. L’hôte fournit actuellement la version `1.0.0` ; un plug-in n’est chargé que si sa version majeure est identique et si la version fournie n’est pas inférieure à la version requise, sinon il est marqué « incompatible ». Chaque changement du contrat élève la version, et l’intégration continue la compare à la version publiée précédente avec japicmp (`script/ci/check_plugin_api_compat.py`) ; les changements incompatibles doivent élever la version majeure.

## Isolation

- Chaque plug-in dispose de son propre chargeur de classes, dont le parent est le chargeur de classes de la plateforme ; seuls `org.devlive.grantforge.plugin.api.` et `org.jspecify.annotations.` sont délégués à l’hôte. Un plug-in ne voit ni les classes de Spring ni celles du serveur, et peut embarquer des dépendances dans n’importe quelle version.
- Un échec de lecture, une version incompatible, un doublon, une exception du constructeur ou un dépassement de délai ne font que marquer ce plug-in comme échoué et en consigner la raison ; le service continue de fonctionner normalement.
- Chaque appel vers un plug-in est soumis à un délai d’attente (`grantforge.plugins.call-timeout`, 10 secondes par défaut).

## Agents et instantanés

Le code source des plug-ins de type de service se trouve dans `plugins/` et est chargé par le serveur GrantForge ; le code source des agents concrets se trouve dans `agents/`, et il est déployé, après empaquetage, dans les systèmes cibles : `agents/grantforge-agent-hdfs` est par exemple déployé sur le NameNode HDFS. L’infrastructure d’agents partagée se trouve dans `core/grantforge-agent-core`.

Les agents des systèmes cibles accèdent à `/api/v1/agent/**` au moyen d’un jeton d’agent :

| Interface | Rôle |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | Signale l’état de l’agent et la version actuelle de l’instantané |
| `GET /api/v1/agent/policies` | Télécharge l’instantané de politiques ; renvoie 304 en l’absence de changement ; la réponse porte une signature Ed25519 |
| `GET /api/v1/agent/signing-key` | La clé publique servant à vérifier la signature |
| `POST /api/v1/agent/access-events` | Remonte les événements d’accès par lots, qui alimentent l’audit des accès |

Les agents évaluent localement avec `grantforge-policy-engine` (une API Java 8, intégrable dans des systèmes plus anciens) et n’ont pas besoin d’appeler GrantForge à chaque accès.

Les agents n’ont pas à implémenter eux-mêmes ces protocoles : `core/grantforge-agent-core` (Java 8) les encapsule déjà :

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // évaluateurs de conditions, par nom

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- Envoie un battement de cœur à l’intervalle exigé par le serveur ; lorsque la version des politiques change, télécharge l’instantané (304 si l’ETag n’a pas changé), en vérifie la signature avec la clé publique Ed25519 du serveur (la clé peut être figée dans les réglages, sinon elle est récupérée auprès du serveur au premier contact puis conservée), ne remplace la copie locale qu’après une vérification réussie, et l’enregistre dans le répertoire de cache ; si le serveur est injoignable au démarrage, le dernier instantané continue d’être utilisé.
- L’instantané déploie les rôles et les groupes jusqu’aux utilisateurs, et `decide` ajoute à la requête les rôles et les groupes que l’utilisateur y possède. Lorsque le service est désactivé ou qu’aucun instantané n’existe encore, le résultat est `NOT_DETERMINED`, et l’agent décide de retomber sur les vérifications propres du système ou de refuser.
- Les événements d’accès entrent dans une file bornée (pleine : ils sont ignorés et comptés, sans jamais bloquer le système) et sont envoyés par lots ; si le serveur est injoignable, ils sont écrits dans `audit-spool/` du répertoire de cache, puis réexpédiés après le rétablissement, les plus anciens étant ignorés au-delà de la limite.
- Dépend de Jackson 2 et de Bouncy Castle (Ed25519 n’existe pas avant le JDK 15) ; les systèmes cibles fournissent d’autres versions de ces bibliothèques, aussi l’agent doit-il les relocaliser avec shade lors de son empaquetage.

## Exemple

`plugins/grantforge-plugin-example` est un plug-in complet : le type `example` (database → table → column et path), les types d’accès select, update et all, le caviardage des colonnes, le filtrage par lignes sur les tables, une condition de plage d’adresses IP, ainsi que la configuration url, timeout et password (le test de connexion réussit lorsque le mot de passe est `example`) ; il permet en outre de rechercher des bases et des tables d’exemple. Le test de bout en bout full-stack l’utilise pour parcourir tout le cycle : « ajouter un service → écrire une politique → émettre un jeton → l’agent récupère → audit des accès ».
