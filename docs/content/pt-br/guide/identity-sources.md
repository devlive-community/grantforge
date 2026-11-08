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
| Claim de telefone | Lê `phone` por padrão (Casdoor); outros provedores podem usar `phone_number`. Para sincronizar o telefone, o escopo precisa incluir `phone`. Se o provedor não devolver um número no login, o número existente é mantido |
| Endereço do avatar | Sincronizado da claim padrão do OIDC `picture`; o escopo precisa incluir `profile`. Se nada for devolvido, o endereço existente é mantido |
| Parâmetros extras da requisição de autorização | Objeto JSON opcional cujos valores devem ser strings. No Casdoor, por exemplo, `{"provider_hint":"github"}` escolhe um provedor configurado. Os parâmetros aparecem no endereço do navegador, então nunca coloque segredos neles |

O GrantForge gerencia os parâmetros essenciais do protocolo (incluindo `state`, `nonce`, `redirect_uri` e os do PKCE); parâmetros extras não podem substituí-los.

Depois de habilitada, a página de login exibe o botão "Entrar com <nome>". O usuário faz login no provedor e volta ao GrantForge; contas com a autenticação de dois fatores habilitada precisam informar também um código de verificação.

## Regras das contas

- O identificador único do usuário da fonte de identidade (`entryUUID`/`objectGUID` no LDAP, `sub` no OIDC) corresponde a uma conta do GrantForge; mudar de nome não afeta essa correspondência.
- Quando já existe uma conta local com o mesmo nome, **não há associação automática**: o login é rejeitado e um administrador precisa antes renomear ou excluir a conta local. Isso evita que um usuário homônimo do diretório assuma o controle de uma conta existente.
- Uma fonte de identidade que não cria contas automaticamente só permite o login de usuários já associados.
- Depois de desabilitar uma fonte de identidade, seus usuários não conseguem fazer login; uma fonte de identidade que ainda tem contas em uso não pode ser excluída.
- Às contas de fonte de identidade podem ser atribuídos papéis e habilitada a autenticação de dois fatores normalmente.

A sincronização da estrutura organizacional é configurada em uma fonte de dados separada, sem alterar as configurações de login OIDC.

## Fontes de dados e sincronização da organização

As fontes de dados sincronizam a estrutura organizacional, e as fontes de identidade continuam responsáveis pelo login OIDC ou LDAP. As duas são configuradas separadamente: desabilitar uma fonte de dados não afeta o login OIDC.

1. Configure o login OpenID Connect existente em "Controle de acesso → Fontes de identidade".
2. Em "Controle de acesso → Fontes de dados", adicione uma fonte do DingTalk e escolha a fonte de identidade OIDC associada.
3. Informe CorpId, AppKey, AppSecret e o ID do departamento raiz (normalmente `1`). O AppSecret é criptografado separadamente, a API nunca o devolve em texto claro e, se o campo ficar vazio na edição, o valor anterior é mantido.
4. A fonte pode ser montada sob um departamento local. O intervalo de sincronização padrão é de 30 minutos e aceita de 15 a 10080 minutos; vazio significa apenas sincronização manual.
5. Depois de salvar, teste a conexão e execute a primeira sincronização. O aplicativo do DingTalk precisa de permissão de leitura dos departamentos e do diretório de funcionários, e seu escopo autorizado deve cobrir a subárvore escolhida.
6. Quando um usuário faz login por SSO, o funcionário é localizado pelo e-mail OIDC confiável, e seus departamentos e o departamento principal são preenchidos automaticamente. O e-mail deve ser mantido por um administrador, não pode permitir que um funcionário se passe por outro e deve coincidir com o e-mail do DingTalk. Sem e-mail do funcionário, usa-se o e-mail corporativo.

O login lê apenas o snapshot local do diretório e não consulta o DingTalk. Com MFA, a vinculação a departamentos é atualizada depois que a segunda etapa é concluída. Contas que fazem login antes da primeira sincronização guardam o e-mail confiável e são associadas quando a sincronização termina; o e-mail do perfil, editável localmente, nunca é usado na correspondência. E-mails ausentes, repetidos ou já vinculados a outra conta não criam novos vínculos.

O vínculo com um funcionário se baseia no ID externo dele e não passa para outro funcionário quando o e-mail muda. A sincronização cobre o pertencimento a vários departamentos; o departamento principal continua sendo o anterior enquanto for válido e, caso contrário, é escolhido pela ordem estável dos IDs de departamento. Departamentos locais, grupos de usuários, cargos e atribuições diretas de papéis são preservados. Ao atribuir um papel a um departamento sincronizado, os usuários o herdam pelas regras existentes, e uma transferência atualiza as permissões.

Assim que existirem um snapshot da organização ou contas aguardando associação, a fonte de identidade associada, a empresa, o aplicativo, o departamento raiz e o ponto de montagem ficam fixos. Departamentos e vínculos sincronizados são somente leitura e não podem ser sobrescritos manualmente na gestão de usuários ou da organização. Departamentos que desaparecem são mantidos para proteger as permissões existentes; quando um funcionário sai do diretório autorizado, os vínculos gerenciados por esta fonte são removidos, mas a conta não é desabilitada automaticamente. Uma fonte de dados que possui departamentos não pode ser excluída diretamente, mas pode ser desabilitada.

A sincronização obtém todas as páginas antes de aplicar o resultado de forma atômica. Em caso de falha de leitura, hierarquia inválida ou mudança do escopo autorizado, o último snapshot bem-sucedido é mantido; os administradores conferem o resultado pelo resumo da sincronização, pela lista de anomalias e pelo registro de auditoria. Se o escopo mudou, restaure primeiro o escopo aprovado e só então sincronize de novo.

O acesso usa o [SDK oficial de servidor do DingTalk](https://open.dingtalk.com/document/development/download-the-server-side-sdk), sem chamadas HTTP próprias. Os adaptadores de fornecedor produzem snapshots neutros de departamentos e funcionários, de modo que um futuro adaptador do Feishu poderá reutilizar a mesma lógica de sincronização, vinculação e atualização de permissões.
