---
title: Fontes de identidade (LDAP e OIDC)
description: Permita que os usuários entrem com o diretório da empresa (LDAP/AD) ou com um provedor de OpenID Connect, com criação automática de contas e sincronização.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controle de acesso → Fontes de identidade** configura os diretórios ou provedores de identidade com que os usuários podem fazer login. A senha desse tipo de conta fica na fonte de identidade: o GrantForge não a guarda nem pode alterá-la.

![Fontes de identidade](/screenshots/identity-sources.png)

## LDAP / Active Directory

Clique em "Adicionar fonte de identidade" e escolha o tipo "Diretório LDAP":

| Ajuste | Descrição |
| --- | --- |
| Endereço do diretório | `ldap://` ou `ldaps://`; vários endereços separados por espaços permitem a tolerância a falhas |
| Base DN dos usuários | Por exemplo `ou=people,dc=example,dc=com` |
| Conta de consulta | A conta (Bind DN) e sua senha usadas para localizar usuários; se ficar em branco, a consulta é anônima |
| Filtro de usuários | Por padrão `(&(objectClass=person)(uid={0}))`; `{0}` é o nome digitado pelo usuário |
| Atributos | Os atributos de nome de usuário, nome de exibição, e-mail e identificador único; por padrão atendem ao OpenLDAP (`uid`, `cn`, `mail`, `entryUUID`); para Active Directory informe `sAMAccountName` e `objectGUID` |
| Criar contas automaticamente para novos usuários | Quando habilitada, a conta é criada no primeiro login de um usuário do diretório |
| Intervalo de sincronização | Se ficar em branco, só há sincronização manual; o mínimo é 15 minutos |
| Desabilitar contas de usuários que saíram do diretório | Na sincronização, desabilita os usuários que já não existem no diretório |

Depois de salvar, clique em **Testar conexão** para confirmar que a configuração está correta.

No login, o GrantForge primeiro localiza o usuário com a conta de consulta e, em seguida, valida a senha fazendo bind no diretório com a identidade desse usuário e a senha digitada.

A **sincronização** cria contas para os novos usuários do diretório, atualiza o nome e o e-mail das contas existentes e, conforme a configuração, desabilita os usuários que saíram (encerrando também as sessões deles). O resultado da sincronização aparece no cartão da fonte de identidade.

## OpenID Connect

Escolha o tipo "OpenID Connect" para integrar o Keycloak, o Azure AD, o Okta, outra instância do GrantForge etc.:

| Ajuste | Descrição |
| --- | --- |
| Endereço do emissor | O endereço do emissor do provedor; o GrantForge obtém os endpoints e as chaves pelo documento de descoberta |
| ID / segredo do cliente | O cliente registrado no provedor; quando não há segredo, ele atua como cliente público, com PKCE |
| Endereço de retorno | O `<GrantForge>/api/v1/auth/federated/callback/<codificação>` mostrado na página precisa ser registrado no cliente do provedor |
| Escopos e claims | Por padrão `openid profile email`; nome de usuário, nome de exibição e e-mail são lidos das claims `preferred_username`, `name` e `email` |

Depois de habilitada, a página de login exibe o botão "Entrar com <nome>". O usuário faz login no provedor e volta ao GrantForge; contas com a autenticação de dois fatores habilitada precisam informar também um código de verificação.

## Regras das contas

- O identificador único do usuário da fonte de identidade (`entryUUID`/`objectGUID` no LDAP, `sub` no OIDC) corresponde a uma conta do GrantForge; mudar de nome não afeta essa correspondência.
- Quando já existe uma conta local com o mesmo nome, **não há associação automática**: o login é rejeitado e um administrador precisa antes renomear ou excluir a conta local. Isso evita que um usuário homônimo do diretório assuma o controle de uma conta existente.
- Uma fonte de identidade que não cria contas automaticamente só permite o login de usuários já associados.
- Depois de desabilitar uma fonte de identidade, seus usuários não conseguem fazer login; uma fonte de identidade que ainda tem contas em uso não pode ser excluída.
- Às contas de fonte de identidade podem ser atribuídos papéis e habilitada a autenticação de dois fatores normalmente.
