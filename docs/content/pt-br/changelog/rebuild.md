---
title: "2026.0.0 (reconstrução)"
description: "GrantForge reescrito do zero: identidade multi-tenant, permissões sobre recursos e papéis, permissões de dados e de campo, integração por protocolos padrão, governança corporativa e permissões sobre sistemas externos por plug-ins."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

A versão 2026.0.0 é uma reescrita completa, que substitui a 1.x (AuthX). Ela deixa de ser um modelo de painel administrativo e passa a ser uma plataforma de identidade e permissões de implantação independente. As contas, os papéis e os menus da 1.x podem ser importados; ver [Atualização e migração de versões antigas](/pt-br/deploy/upgrade/).

## Plataforma

- Spring Boot 4, runtime Java 17; um pacote de lançamento traz o servidor e o console, além de imagens Docker, Compose e Helm Chart.
- Suporte a H2, PostgreSQL, MySQL, MariaDB, Oracle e SQL Server; as migrações são geridas pelo Liquibase e cada commit é testado em nove versões de banco de dados.
- Assistente de inicialização no primeiro start, que cria o administrador da plataforma com um token de uso único exibido no log do serviço.
- Implantação em cluster: as sessões ficam no banco de dados e os IDs são TSIDs ordenados por tempo.
- Console totalmente novo: Vue 3, Tailwind CSS, temas claro e escuro, chinês e inglês.

## Identidade e organização

- Multi-tenant: as contas, a organização e as permissões de cada tenant são totalmente isoladas.
- Usuários, árvore de departamentos, grupos de usuários, cargos e importação e exportação em massa por CSV.
- Senhas Argon2id, política de senhas e bloqueio configuráveis, gerenciamento de sessões, autenticação de dois fatores com TOTP e códigos de recuperação, e verificação adicional em operações sensíveis.
- Fontes de identidade LDAP / Active Directory e OIDC, com sincronização e login federado.

## Permissões

- Catálogo de recursos: módulos, menus, páginas, etiquetas, botões, API, entidades de dados e campos, além das dependências entre eles. As páginas, os botões e as API do próprio console também estão no catálogo e recebem as mesmas permissões.
- Papéis e permissões: permitir e negar, herança, atribuição por usuário / grupo de usuários / departamento / cargo e prazo de validade.
- Permissões de dados: limitar as linhas visíveis por escopo da organização ou por condição estruturada.
- Permissões de campo: ocultar, mascarar ou deixar campos em somente leitura conforme o papel.
- Explicação das permissões, simulação por usuário, registros de auditoria completos e verificação de configurações inválidas.

## Governança

- Separação de funções: papéis mutualmente excludentes são rejeitados na atribuição, na herança e na solicitação, e conflitos já existentes podem ser detectados.
- Solicitações de acesso: os usuários pedem os papéis que é permitido solicitar; depois que o aprovador aprova, eles valem por tempo limitado e são retirados automaticamente ao expirar.
- Revisão periódica de permissões.

## Integração de aplicações

- Servidor de autorização OAuth 2.1 / OpenID Connect embutido: código de autorização + PKCE, credenciais de cliente, rotação do token de atualização e rotação da chave de assinatura.
- API aberta de consulta de permissões, com versão e ETag.
- Spring Boot Starter e SDK JavaScript, com aplicações de exemplo prontas para rodar.

## Permissões sobre sistemas externos

- Tipos de serviço plugáveis: o plug-in define a hierarquia de recursos, os tipos de acesso, a máscara e o filtro de linhas; cada plug-in é carregado de forma independente.
- Editor de políticas genérico, snapshots de política assinados com Ed25519, heartbeat dos agentes e auditoria de acessos.
- Plug-in de exemplo, tipo de serviço HDFS e agente NameNode para Hadoop 3.5.0; o plug-in de Hive e os agentes para outras versões do Hadoop estão em desenvolvimento.

## Qualidade

- Benchmarks de desempenho em escala de um milhão de contas rodam toda noite e falham se os limites forem superados.
- Análise estática (NullAway, Error Prone, Checkstyle, PMD, SpotBugs, ArchUnit), limites de cobertura e testes de navegador de ponta a ponta.
- O site de documentação foi reconstruído com Next.js e Tailwind CSS.
