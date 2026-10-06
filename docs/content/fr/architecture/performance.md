---
title: Performances et benchmarks
description: Objectifs de performance à l’échelle du million de comptes, données et méthode des benchmarks, et façon de les exécuter en local.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Les objectifs de performance de GrantForge visent une grande organisation : **un million de comptes, dix mille rôles, cent mille ressources**. Les benchmarks système s’exécutent chaque nuit sur PostgreSQL 17 ; tout indicateur qui dépasse son plafond fait échouer le build.

## Objectifs

| Indicateur | Signification | Plafond |
| --- | --- | --- |
| `authz.snapshot.hit` | Instantané d’autorisations déjà en cache (utilisé à chaque appel d’interface) | p99 1 ms |
| `authz.snapshot.build` | Premier calcul de l’instantané de console d’un compte | p95 50 ms |
| `authz.snapshot.app` | Calcul de l’instantané dans une application dotée d’un grand arbre de ressources | p95 50 ms |
| `api.users.page` | Une page de la liste d’utilisateurs (dans les 500 premières pages) | p95 200 ms |
| `api.users.search` | Recherche d’utilisateurs par nom | p95 200 ms |
| `api.groups.page` | Une page de la liste des groupes | p95 200 ms |
| `api.roles.list` | Liste des rôles (sans pagination) | p95 200 ms |
| `api.me.authorization` | Autorisations du compte courant | p95 200 ms |
| `api.member.groups` | Accès d’un compte disposant d’une seule autorisation de page à une liste protégée | p95 200 ms |
| `jmh.derivation.*` | Préparer la dérivation pour un grand arbre de ressources, dériver vingt autorisations (JMH) | moyenne 50 / 5 ms |

Les plafonds ne peuvent que être durcis ; tout assouplissement exige une décision documentée.

## Données

Les benchmarks démarrent le vrai service, vont au bout de l’initialisation, puis écrivent via les entités propres de l’application :

- 100 services, un groupe pour mille comptes, 100 postes ;
- chaque compte appartient à un service et à un groupe, un compte sur dix a un poste, un compte sur vingt a des rôles affectés directement ;
- un rôle sur dix hérite de l’un des cent premiers rôles ; chaque groupe a trois rôles, chaque service deux, chaque poste un ; chaque rôle autorise cinq pages de la console ;
- une application : cent pages réparties sur plusieurs modules, neuf opérations par page ; un rôle sur dix reçoit l’autorisation sur vingt de ces pages et opérations.

Chaque indicateur est mesuré appel par appel après la montée en température.

## Exécution en local

```bash
bash script/ci/perf_benchmark.sh full            # échelle cible, postgres:17 (nécessite Docker), vérifie les plafonds
bash script/ci/perf_benchmark.sh smoke           # petite échelle sur H2, vérifie seulement que les benchmarks s’exécutent
bash script/ci/perf_benchmark.sh full mysql:8.4  # autres bases de données
```

Le rapport est écrit dans `perf/target/perf-report.json`, et un tableau est également affiché dans le journal. Des paramètres supplémentaires peuvent être transmis via `PERF_OPTS` :

| Paramètre | Valeur par défaut | Signification |
| --- | --- | --- |
| `perf.database` | `postgres:17` | `h2` ou `<moteur>:<version>` |
| `perf.users` / `perf.roles` / `perf.resources` | 1000000 / 10000 / 100000 | Échelle des données |
| `perf.samples` / `perf.warmup` | 1000 / 200 | Mesures et montées en température par indicateur |
| `perf.jmh` | `true` | Exécuter également les benchmarks JMH |
| `perf.enforce` | `true` | Sortir avec le code 1 en cas de dépassement d’un plafond |

## Pourquoi c’est rapide

- Les instantanés d’autorisations sont mis en cache par compte et invalidés en bloc via les numéros de version du catalogue et du locataire ; en cas de succès, il ne reste qu’une lecture en mémoire.
- La dérivation se fait en mémoire : l’arbre de ressources, les relations d’héritage et les affectations sont préchargés dans des structures compactes, ce qui évite une requête par élément.
- Les interfaces de liste sont toutes paginées, et le tri comme le filtrage portent sur des colonnes indexées ; les jointures différées chargent par lots de 64 et les écritures sont validées par lots de 50 (les ID étant générés par l’application, l’insertion par lots est possible).
