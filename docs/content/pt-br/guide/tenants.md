---
title: Tenants
description: Criar, editar, desativar e reativar tenants.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Gestão da plataforma → Gestão de tenants**: cada tenant é uma organização isolada das demais, com seus próprios usuários e suas próprias permissões. O tenant de plataforma hospeda os administradores da plataforma e não pode ser desativado.

![Gestão de tenants](/screenshots/tenants.png)

## Criar um tenant

Informe o código e o nome do tenant, além do nome de usuário, do nome de exibição e da senha do seu primeiro administrador. O primeiro administrador possui o papel de sistema "Administrador do tenant" e precisa alterar a senha no primeiro acesso. O nome de usuário é único em toda a plataforma.

## Desativar e reativar

Ao desativar um tenant, todas as suas contas são desconectadas imediatamente e não conseguem mais se conectar, e os tokens de aplicação já emitidos deixam de ser renovados. Os dados não são excluídos: ao reativar o tenant, tudo é recuperado.

## Tenant de plataforma

O tenant de plataforma é o primeiro criado na inicialização; seus administradores podem gerenciar todos os tenants, o [Catálogo de recursos e de API](/pt-br/guide/catalog/), o servidor de autorização e os plug-ins. As aplicações de negócio e seus recursos são compartilhados por toda a plataforma, e cada tenant autoriza esses recursos em seus próprios papéis.
