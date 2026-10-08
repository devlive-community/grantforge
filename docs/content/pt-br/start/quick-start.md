---
title: Configuração em cinco minutos
description: Inicie o GrantForge com o pacote de lançamento ou com o Docker, conclua a inicialização, crie um usuário e conceda o primeiro papel.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Este guia inicia o GrantForge na própria máquina com o banco de dados H2 embutido, que é o padrão. Para produção, consulte [instalar o pacote de lançamento](/pt-br/deploy/installation/) e [bancos de dados](/pt-br/deploy/databases/).

## 1. Iniciar o serviço

É necessário ter o Java 17 ou uma versão posterior. Baixe o pacote de lançamento em [GitHub Releases](https://github.com/devlive-community/grantforge/releases) ou compile você mesmo executando `./mvnw -DskipTests package` no diretório do código-fonte (o artefato fica em `dist/grantforge-release.tar.gz`); em seguida, descompacte e inicie:

```bash
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh
```

Você também pode usar o Docker: primeiro construa a imagem a partir do pacote de lançamento e depois inicie com um exemplo de Compose:

```bash
docker build -f deploy/docker/Dockerfile -t grantforge dist
docker compose -f deploy/compose/h2.yml up -d
```

O serviço escuta por padrão na porta `9999`. No primeiro início, as tabelas do banco de dados são criadas e o **token de inicialização** de uso único é impresso uma única vez no registro:

```text
WARN  ... First-run setup is pending. Open the console and enter this setup token: 7rV2...
```

Os registros do pacote de lançamento ficam em `logs/grantforge.log`; com o Docker, use `docker compose logs` para consultá-los.

## 2. Concluir a inicialização

Abra http://127.0.0.1:9999/ no navegador e o console entrará automaticamente na página de inicialização. Informe o token que está no registro, o nome da organização e o usuário e a senha do primeiro administrador (com pelo menos 12 caracteres).

![Página de inicialização e de login](/screenshots/login.png)

> [!TIP]
> Em instalações automatizadas, é possível definir o token antecipadamente com a variável de ambiente `GRANTFORGE_SETUP_TOKEN`; consulte a [referência de configuração](/pt-br/reference/configuration/).

Depois de concluída a inicialização, esse administrador detém ao mesmo tempo os papéis de sistema **administrador do tenant** e **administrador da plataforma** e pode usar todas as funções do console. A página de inicialização fica permanentemente fechada a partir desse momento.

## 3. Criar usuários

Entre em **Controle de acesso → Gerenciamento de usuários**, clique em "Criar usuário" e preencha o nome de usuário, a senha inicial e o departamento principal. No primeiro login, os usuários novos precisam alterar a senha.

## 4. Criar um papel e conceder permissões

1. Entre em **Controle de acesso → Gerenciamento de papéis**, clique em "Novo papel" e dê o nome, por exemplo, de "Auditor somente leitura".
2. Na linha do papel, clique em "Conceder permissões" e marque na matriz de permissões a página "Registro de auditoria". A matriz traz automaticamente as API de que essa página precisa.
3. Clique em "Atribuir" para atribuir o papel ao usuário que você acabou de criar.

![Gerenciamento de papéis](/screenshots/roles.png)

## 5. Verificar o resultado

Faça login com o usuário novo: no menu lateral aparece apenas "Registro de auditoria". Volte à conta de administrador, clique no ícone "Ver permissões efetivas" na linha do usuário e veja a origem de cada uma das permissões dele.

## Próximos passos

- Conheça os [conceitos essenciais](/pt-br/start/concepts/).
- Familiarize-se com cada menu pelo [guia de uso](/pt-br/guide/console/).
- Faça sua aplicação [se integrar ao GrantForge](/pt-br/integration/overview/).
