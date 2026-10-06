---
title: Applications d’exemple
description: "Deux exemples complets dans le dépôt : une boutique connectée directement depuis le navigateur, et des notes avec connexion côté serveur."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Sous `samples/` se trouvent deux applications exécutables, couvertes avec GrantForge par les tests de bout en bout ; ce sont les meilleures références pour une intégration.

| Exemple | Port | Mode d’intégration présenté |
| --- | --- | --- |
| `samples/shop` | 19081 | Le navigateur se connecte au-delà des frontières d’origine avec un client public + PKCE ; les boutons s’affichent selon les ressources ; le backend vérifie les autorisations d’API avec `@RequirePermission` ; `@GrantForgeEntity` déclare l’entité de commande et les requêtes filtrent sur les portées de données « moi » et « mon locataire » |
| `samples/notes` | 19082 | Connexion côté serveur avec Spring Security `oauth2Login` (client confidentiel + PKCE) ; `AccessTokenResolver` personnalisé qui récupère le jeton dans la session ; le bouton « écrire une note » n’est affiché qu’aux personnes autorisées |

## Exécution

Les exemples sont des builds Maven indépendants, qui dépendent du starter et du SDK JavaScript de ce dépôt :

```bash
./mvnw -N install
./mvnw -pl sdk/grantforge-spring-boot-starter install
(cd sdk/grantforge-js && pnpm install && pnpm build)
./mvnw -f samples/pom.xml package
```

La configuration des exemples vient entièrement des variables d’environnement :

| Variable | Description |
| --- | --- |
| `GRANTFORGE_URL` | Adresse de GrantForge |
| `SHOP_BROWSER_CLIENT_ID` | Client public du navigateur de la boutique |
| `SHOP_CLIENT_ID` / `SHOP_CLIENT_SECRET` | Client confidentiel utilisé par le backend de la boutique pour déclarer les entités de données (scope `catalog`) |
| `SHOP_SDK_DIRECTORY` | Répertoire des artefacts de build du SDK JavaScript (`sdk/grantforge-js/dist`) |

Dans GrantForge, il faut créer les ressources, clients, rôles et politiques de données des deux applications ; le script de préparation des tests de bout en bout `core/grantforge-web/tests/samples/setup.ts` présente ces étapes en détail et peut servir de référence directe.

## Tests de bout en bout

`script/ci/e2e_fullstack.sh` construit et démarre les deux exemples après les tests plein pile, et vérifie : la connexion PKCE inter-origines et le CORS, l’affichage et le masquage des boutons, les réponses 403 de l’API, les portées de données « moi » et « mon locataire », la suppression et la déconnexion, ainsi que la connexion côté serveur de l’application de notes. Définissez `GRANTFORGE_E2E_SKIP_SAMPLES=1` pour l’ignorer.
