---
title: Atualizar e migrar de versões antigas
description: A atualização entre versões 2.x e a migração de contas, papéis e menus a partir da 1.x (AuthX / GrantForge 1.x).
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Atualizar entre versões 2.x

1. Faça uma cópia de segurança do banco de dados (e de `plugins/` e `configure/`).
2. Pare o serviço: `bin/shutdown.sh`.
3. Substitua o `lib/` e o `bin/` antigos pelos do pacote de lançamento da nova versão.
4. Inicie: `bin/startup.sh`. O Liquibase executa automaticamente as migrações de banco de dados da nova versão, e a sonda de prontidão só devolve 200 depois que elas terminam.

Ao atualizar um cluster, pare primeiro todas as instâncias e só depois inicie a nova versão, para evitar que a versão antiga e a nova leiam e escrevam ao mesmo tempo. As migrações já publicadas nunca são modificadas, e cada versão foi verificada em todos os bancos de dados suportados com o caminho de atualização a partir da versão anterior.

## Migrar a partir da 1.x

A 1.x guarda os dados em outro conjunto de tabelas e a 2.x não as lê. A forma de migrar é: instale a 2.x sobre um **banco de dados novo** e conclua a inicialização, pare o serviço e depois importe as contas, os papéis e os menus do banco de dados antigo para algum tenant:

```bash
# Primeiro um ensaio: apenas gera o relatório, não escreve
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default
# Importa de verdade depois de confirmado o relatório
bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default --apply
```

Os dois comandos escrevem `logs/legacy-import-report.json`, que indica o que foi criado (ou será criado), o que foi omitido e por qual motivo, e o novo ID correspondente a cada objeto antigo. O driver JDBC do banco de dados antigo é colocado em `drivers/`, e a senha é fornecida por `GRANTFORGE_LEGACY_SOURCE_PASSWORD` (quando não é, o script a pergunta). Executar a importação novamente apenas acrescenta o que falta.

Regras de importação:

- As **contas** conservam sua senha, que é trocada automaticamente pelo novo algoritmo de hash no primeiro início de sessão. São omitidas as contas que não cumprem as regras de nome de usuário da 2.x (de 3 a 64 letras, números ou `._@-`), as que não têm senha e as cujo nome de usuário já está ocupado em outro tenant.
- Os **papéis** conservam o nome, e seu código passa a minúsculas (`GLY` → `gly`).
- Os **menus** passam a ser recursos da aplicação `legacy`: um menu com endereço `#` se torna um grupo, outros endereços se tornam páginas, e os menus que ficam abaixo de uma página se tornam botões. O endereço do menu e seu método HTTP se tornam o recurso de API `api:<método>:<caminho>`; um endereço que termina em `*` se torna `<caminho>/**`, que é comparado por segmentos de caminho e não por prefixo de caracteres, e o relatório os enumera um a um para conferência.
- São migradas apenas as **permissões explícitas**: a 1.x permitia que qualquer pessoa acessasse os endereços registrados como menus; a 2.x não faz isso.

> [!WARNING]
> Antes de importar, confira no relatório as contas omitidas e os endereços com curinga, e só então execute `--apply`.
