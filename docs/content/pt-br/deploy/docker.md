---
title: Docker, Compose e Helm
description: Execute o GrantForge com imagens de contêiner, experimente-o com o Compose ao lado de diferentes bancos de dados e implante-o no Kubernetes com o Helm.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Imagens

Cada versão publica uma imagem `ghcr.io/devlive-community/grantforge:<versão>` (linux/amd64 e linux/arm64), e as versões estáveis atualizam também `latest`:

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

A imagem é construída a partir do pacote de lançamento, baseia-se em `eclipse-temurin:21-jre`, roda com um usuário sem privilégios (UID 10001), escreve os registros no console e não traz banco de dados integrado. Também é possível construí-la você mesmo a partir do código-fonte:

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

Convenções da imagem:

| Caminho / variável | Explicação |
| --- | --- |
| `/opt/grantforge/data` | volume: os arquivos de dados da H2 integrada |
| `/opt/grantforge/plugins` | volume: plug-ins de tipo serviço |
| `/opt/grantforge/drivers` | drivers JDBC adicionais (o MySQL Connector/J vai aqui) |
| `9999` | porta do serviço |
| `HEALTHCHECK` | consulta `/actuator/health/readiness` |

## Exemplo de Compose

`deploy/compose/` traz um exemplo para cada banco de dados: `h2`, `postgres`, `mariadb`, `mysql`, `sqlserver`, `oracle`.

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

Depois, abra http://127.0.0.1:9999/. A senha padrão do banco de dados usada pelos exemplos serve apenas para experimentar; altere-a com `GRANTFORGE_DB_PASSWORD` antes de entrar em produção. O exemplo de MySQL precisa que antes você coloque `mysql-connector-j-<versão>.jar` em `deploy/compose/drivers/`.

## Helm

`deploy/helm/grantforge` é um chart do Helm: um StatefulSet mais um banco de dados externo, e cada réplica obtém seu próprio número de nó de ID conforme o ordinal do Pod (precisa do Kubernetes 1.28 ou superior).

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

Parâmetros mais usados:

| Parâmetro | Valor padrão | Explicação |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / o appVersion do chart | imagem |
| `replicaCount` | `1` | número de réplicas, pode ser maior que 1 |
| `database.url` / `username` / `password` | — | conexão com o banco de dados; recomenda-se deixar a senha em `existingSecret` |
| `setupToken` | vazio | token de inicialização definido previamente; quando vazio, é impresso no log |
| `encryptionKey` | vazio | chave Base64 de 32 bytes com a qual são cifradas as chaves guardadas (senhas de fontes de identidade, chaves de verificadores, chaves privadas de assinatura, etc.); quando vazia, é gerada automaticamente e guardada no banco de dados |
| `cookieSecure` | `false` | defina como `true` quando o TLS termina no Ingress; o cookie de sessão leva sempre Secure |
| `ingress.*` | desativado | expõe o console e a API |
| `plugins.persistence.enabled` | `false` | monta um volume persistente para o diretório de plug-ins |
| `podDisruptionBudget.enabled` | `false` | recomenda-se ativar com várias réplicas |

> [!IMPORTANT]
> Em produção, defina sempre `encryptionKey`. Sem ela, a chave fica guardada no banco de dados, e quem obtiver uma cópia de segurança do banco de dados poderá decifrar as chaves que estão cifradas dentro dele.
