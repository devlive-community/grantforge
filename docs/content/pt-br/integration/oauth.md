---
title: OAuth 2.1 e OpenID Connect
description: Os endpoints do servidor de autorização, os tipos de cliente, as regras dos tokens e a chave de assinatura.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O GrantForge traz embutido um servidor de autorização baseado no Spring Authorization Server que segue os requisitos de segurança do OAuth 2.1: aceita apenas o fluxo de código de autorização (com PKCE obrigatório), o token de atualização e as credenciais de cliente; não aceita o fluxo implícito nem o modo de senha.

![Servidor de autorização](/screenshots/oauth.png)

## Documento de descoberta e endpoints

O documento de descoberta fica em `<GrantForge>/.well-known/openid-configuration` e pode ser copiado diretamente na página **Administração da plataforma → Servidor de autorização**. O emissor é, por padrão, o endereço por onde chega a requisição; fixe-o com `grantforge.oauth.issuer` ao implantar o GrantForge atrás de um proxy reverso.

| Endpoint | Descrição |
| --- | --- |
| `/oauth2/authorize` | fluxo de código de autorização; todos os clientes devem usar PKCE (S256) |
| `/oauth2/token` | código de autorização, token de atualização e credenciais de cliente. O token de atualização é trocado a cada uso; se o token antigo aparecer de novo, toda a autorização é revogada |
| `/oauth2/revoke` | revogar tokens |
| `/oauth2/jwks` | chave pública de assinatura (RS256) |
| `/userinfo` | `sub`, `tid` e `preferred_username`; com o escopo `profile` vem `name` e, com o escopo `email`, `email` |

O token de acesso e o token de ID contêm `tid` (o ID do tenant) e `preferred_username`; o `auth_time` do token de ID é o momento em que o usuário fez login no console.

## Clientes

Em **Administração da plataforma → Catálogo de recursos**, selecione a aplicação e clique em "Cliente OAuth" para gerenciar seus clientes.

| Ajuste | Regra |
| --- | --- |
| Tipo | o **cliente público** serve a aplicações que não conseguem guardar uma chave, como as de navegador e as móveis; o **cliente confidencial** serve a aplicações de servidor e tem chave |
| Endereços de retorno | no máximo 10, endereços absolutos, sem curingas nem fragmento; devem ser https, ou http local (localhost, 127.0.0.1, [::1]), ou o protocolo personalizado de uma aplicação nativa |
| scope | `openid`, `profile`, `email`, `permissions` (consultar permissões) e `catalog` (declarar entidades de dados, apenas com credenciais de cliente) |
| Formas de autorização | código de autorização, token de atualização (exige código de autorização e só é obtido por clientes confidenciais) e credenciais de cliente (apenas clientes confidenciais) |
| Validade dos tokens | token de acesso de 1 minuto a 24 horas (15 minutos por padrão), token de atualização de 1 hora a 90 dias (30 dias por padrão) |

A chave do cliente confidencial é exibida apenas uma vez, no registro ou na rotação; o GrantForge guarda apenas o hash dela. Na rotação é possível definir um período de carência (de no máximo 7 dias) em que a chave antiga e a nova continuam válidas, para facilitar a atualização gradual.

## Regras dos tokens

- Os tokens são guardados como hash: mesmo com o vazamento do banco de dados não se obtém um token utilizável.
- Depois de qualquer um destes eventos, os tokens já emitidos deixam de ser renovados: o cliente é desabilitado ou excluído, a conta é desabilitada ou bloqueada, a conta precisa trocar a senha ou o tenant é desabilitado.
- Origem cruzada do navegador: o GrantForge permite que a origem dos endereços de retorno dos clientes habilitados faça chamadas de origem cruzada aos endpoints de token e à API aberta, sem enviar cookies.

## Chave de assinatura

A chave de assinatura é gerada com RSA 2048 e a chave privada é guardada criptografada. Por padrão, ela é rotacionada automaticamente a cada 90 dias (`grantforge.oauth.signing-key-rotation`) e a chave pública antiga continua publicada no JWKS por 2 dias (`signing-key-retention`), garantindo que os tokens emitidos antes da rotação ainda possam ser validados. Quando necessário, é possível rotacionar na hora na página do servidor de autorização (é uma operação sensível: contas com a autenticação de dois fatores habilitada precisam se verificar novamente).

## Usar o GrantForge para entrar em sistemas além do console

Qualquer sistema compatível com OpenID Connect (Grafana, GitLab, Jenkins etc.) pode usar o GrantForge como IdP: crie para ele uma aplicação e um cliente confidencial no catálogo de recursos e informe o endereço do documento de descoberta, o `client_id` e a chave na configuração OIDC do outro sistema. No sentido inverso, o GrantForge também permite entrar com outros IdP; ver [Fontes de identidade](/pt-br/guide/identity-sources/).
