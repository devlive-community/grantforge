---
title: Docker, Compose e Helm
description: Esegui GrantForge con le immagini container, provalo con Compose insieme ai vari database e distribuiscilo su Kubernetes con Helm.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Immagini

Ogni versione pubblica un’immagine `ghcr.io/devlive-community/grantforge:<versione>` (linux/amd64 e linux/arm64) e le versioni stabili aggiornano anche `latest`:

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

L’immagine viene costruita dal pacchetto di rilascio, si basa su `eclipse-temurin:21-jre`, viene eseguita con un utente non privilegiato (UID 10001), scrive i log sulla console e non include un database. La si può anche costruire da sé dal codice sorgente:

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

Convenzioni dell’immagine:

| Percorso / variabile | Spiegazione |
| --- | --- |
| `/opt/grantforge/data` | volume: i file di dati dell’H2 incorporata |
| `/opt/grantforge/plugins` | volume: plug-in per tipi di servizio |
| `/opt/grantforge/drivers` | driver JDBC aggiuntivi (qui va il MySQL Connector/J) |
| `9999` | porta del servizio |
| `HEALTHCHECK` | interroga `/actuator/health/readiness` |

## Esempio di Compose

`deploy/compose/` contiene un esempio per ogni database: `h2`, `postgres`, `mariadb`, `mysql`, `sqlserver`, `oracle`.

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

Poi apri http://127.0.0.1:9999/. La password predefinita del database usata dagli esempi è adatta solo per le prove: cambiala con `GRANTFORGE_DB_PASSWORD` prima dell’uso in produzione. L’esempio MySQL richiede di aver prima inserito `mysql-connector-j-<versione>.jar` in `deploy/compose/drivers/`.

## Helm

`deploy/helm/grantforge` è un chart Helm: uno StatefulSet con un database esterno, in cui ogni replica ottiene il proprio numero di nodo ID in base all’ordinale del Pod (richiede Kubernetes 1.28 o superiore).

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

Parametri più usati:

| Parametro | Valore predefinito | Spiegazione |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / l’appVersion del chart | immagine |
| `replicaCount` | `1` | numero di repliche, può essere maggiore di 1 |
| `database.url` / `username` / `password` | — | connessione al database; si consiglia di mettere la password in `existingSecret` |
| `setupToken` | vuoto | token di inizializzazione preimpostato; se è vuoto viene stampato nel log |
| `encryptionKey` | vuoto | chiave Base64 a 32 byte per le chiavi conservate cifrate (password delle fonti di identità, chiavi dei verificatori, chiavi private di firma, ecc.); se è vuota viene generata automaticamente e salvata nel database |
| `cookieSecure` | `false` | impostalo su `true` quando TLS termina all’Ingress; il cookie di sessione porta sempre Secure |
| `ingress.*` | disattivato | espone la console e l’API |
| `plugins.persistence.enabled` | `false` | monta un volume persistente per la directory dei plug-in |
| `podDisruptionBudget.enabled` | `false` | si consiglia di attivarlo con più repliche |

> [!IMPORTANT]
> In produzione imposta sempre `encryptionKey`. Se non la imposti, la chiave viene salvata nel database e chiunque ottenga un backup del database può decifrare le chiavi che vi sono conservate cifrate.
