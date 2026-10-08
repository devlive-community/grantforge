---
title: Login, contas e autenticação de dois fatores
description: Login e sessões, central pessoal, troca de senha, autenticação de dois fatores e códigos de recuperação, além da verificação adicional exigida em operações sensíveis.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Login

![Página de login](/screenshots/login.png)

O nome de usuário não distingue maiúsculas de minúsculas. Errar a senha 5 vezes seguidas (configurável) bloqueia a conta por 15 minutos; uma conta bloqueada por um administrador precisa ser desbloqueada por um administrador. Quando há fontes de identidade habilitadas, a página de login exibe também o botão "Entrar com X"; ver [Fontes de identidade](/pt-br/guide/identity-sources/).

As sessões ficam no servidor e o navegador guarda apenas um cookie de sessão HttpOnly. Depois de 30 minutos de inatividade (configurável), a sessão expira e é preciso fazer login novamente.

## Central pessoal

Clique no avatar do canto superior direito para entrar na **central pessoal**:

![Central pessoal](/screenshots/account.png)

- **Dados básicos**: altere o nome de exibição e o e-mail. O nome de usuário e a organização a que você pertence são mantidos por um administrador.
- **Trocar senha**: é preciso informar a senha atual. Depois da troca, todas as suas sessões em outros dispositivos são encerradas. Contas que entram por uma fonte de identidade não veem aqui a opção de trocar senha, pois a senha é gerenciada pela fonte de identidade.
- **Autenticação de dois fatores**: ver abaixo.
- **Meus dispositivos de login**: a lista dos navegadores com sessão aberta, onde você pode encerrar sessões que não reconheça.
- **Registro de logins recentes**: os últimos 10 logins, saídas e tentativas frustradas, incluindo tentativas de outras pessoas de entrar com o seu nome de usuário.

Depois que um administrador redefine a senha ou que a senha expira, o próximo login leva primeiro a "Trocar senha" e as demais funções ficam indisponíveis até que ela seja trocada.

## Autenticação de dois fatores

Depois de habilitada, o login exige, além da senha, o código de 6 dígitos de um aplicativo autenticador (Google Authenticator, Microsoft Authenticator, 1Password etc.).

1. Na central pessoal, clique em **Configurar autenticador** em "Autenticação de dois fatores".
2. Adicione a conta no aplicativo autenticador: digite a chave mostrada na página ou abra o link otpauth em um dispositivo com o aplicativo instalado.
3. Informe o código exibido pelo aplicativo e clique em **Habilitar**.
4. A página mostra **10 códigos de recuperação**, apenas uma vez. Guarde-os com cuidado: se perder o autenticador, cada código de recuperação serve para entrar uma vez no lugar do código de verificação.

Depois de habilitada, é possível gerar novos códigos de recuperação (os antigos expiram imediatamente) ou desativar a autenticação de dois fatores; as duas operações exigem informar um código de verificação. Se você perdeu o autenticador e não tem os códigos de recuperação, peça a um administrador para redefinir a autenticação de dois fatores da conta em **Gestão de usuários**.

> [!TIP]
> Cada código de verificação só pode ser usado uma vez. Erros consecutivos no código de verificação contam para o bloqueio da mesma forma que os erros de senha.

## Verificação adicional em operações sensíveis

Para contas com a autenticação de dois fatores habilitada, as operações abaixo exigem uma verificação feita nos últimos 10 minutos (configurável): rotacionar a chave de assinatura, criar um cliente ou rotacionar sua chave, criar ou desabilitar um tenant, redefinir a senha ou a autenticação de dois fatores de outra pessoa, atribuir papéis, alterar permissões, adicionar ou modificar fontes de identidade e aprovar solicitações de acesso.

Se essas operações forem executadas depois desse prazo, o console abre a caixa de diálogo "Confirmar identidade" e, após informar o código de verificação, continua automaticamente a operação iniciada. Definir `grantforge.security.mfa.required-for-sensitive=true` exige que as contas que executam essas operações tenham a autenticação de dois fatores habilitada.

## Sessões on-line

Em **Controle de acesso → Sessões on-line**, os administradores veem todos os navegadores com sessão aberta neste tenant (conta, IP, navegador, horário do login, atividade recente) e podem encerrar sessões suspeitas. Desabilitar a conta, bloqueá-la ou redefinir a senha encerra imediatamente todas as sessões dessa conta.

![Sessões on-line](/screenshots/sessions.png)
