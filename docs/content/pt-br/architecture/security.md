---
title: Projeto de segurança
description: O projeto das sessões, de CSRF, das senhas e do bloqueio, da autenticação de dois fatores e da reverificação, do armazenamento criptografado, dos tokens e da auditoria.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## Sessões do console

- As sessões são guardadas no banco de dados (Spring Session JDBC) e o navegador conserva apenas o cookie `GRANTFORGE_SESSION`: HttpOnly, SameSite=Lax, e com Secure sob HTTPS (ou forçado com `grantforge.security.cookie-secure`).
- Ao iniciar sessão, ao concluir a autenticação de dois fatores e ao concluir o login federado, tanto o identificador de sessão quanto o token CSRF são trocados, para impedir a fixação de sessão.
- As requisições que modificam estado devem levar o cabeçalho `X-XSRF-TOKEN`, cujo valor vem do cookie `XSRF-TOKEN`.
- Os administradores podem listar e encerrar qualquer sessão; desabilitar, bloquear ou redefinir a senha encerra imediatamente todas as sessões dessa conta; alterar a senha encerra as sessões dos demais dispositivos.

## Senhas

- As senhas novas recebem hash com Argon2id; ainda é possível verificar os hashes de BCrypt e os importados da versão 1.x, e eles são atualizados para o algoritmo atual no próximo início de sessão.
- A política é configurável em comprimento, categorias de caracteres, histórico e validade, e a senha não pode conter o nome de usuário.
- Ao atingir o número de falhas consecutivas, a conta é bloqueada; com um nome de usuário desconhecido também é feita uma comparação de hash, de modo que o tempo de resposta não revela quais nomes de usuário existem; uma conta ou um tenant desabilitado só é indicado depois que a senha estiver correta.

## Autenticação de dois fatores e reverificação

- TOTP (RFC 6238, SHA-1, 6 dígitos, 30 segundos, com um passo de tempo de tolerância antes e depois); o código de um mesmo passo de tempo só pode ser usado uma vez, e os 10 códigos de recuperação de uso único são guardados com SHA-256.
- Em uma conta com a autenticação de dois fatores ativada, depois de acertar a senha a sessão fica no estado "pendente" por 5 minutos, e não é considerada iniciada até que o segundo passo seja concluído.
- As interfaces sensíveis são marcadas com `@RequireStepUp`: uma conta com a autenticação de dois fatores ativada precisa ter se verificado dentro de uma janela de tempo configurável; caso contrário, é retornado `GF-SECURITY-006` e o console tenta novamente após a confirmação em uma janela pop-up.

## Armazenamento criptografado

As senhas de enlace das fontes de identidade e os segredos de cliente, os segredos do verificador, a configuração sensível dos serviços de dados e a chave privada de assinatura do servidor de autorização são guardados criptografados com AES-GCM. A chave vem de `grantforge.security.encryption-key`; quando não está configurada, ela é gerada automaticamente e guardada no banco de dados (opção adequada apenas para testes). Os tokens dos agentes e os segredos de cliente de OAuth são guardados apenas como hash.

## Tokens

- O servidor de autorização guarda o hash dos tokens, e não os tokens em si.
- O token de atualização é rotacionado a cada uso e, quando o antigo é repetido, toda a autorização é revogada.
- A conta estar desabilitada, bloqueada ou precisar trocar a senha, o cliente estar desabilitado ou o tenant estar desabilitado impedem a renovação do token; a API aberta confirma em cada chamada que o token ainda é válido.
- Os instantâneos de política são assinados com Ed25519 e os agentes só os usam depois de verificar a assinatura.

## Proteção das interfaces

- Cada interface deve declarar sua forma de acesso, e uma interface sem declaração impede a inicialização; as interfaces que precisam de permissões são verificadas em cada chamada contra o instantâneo mais recente.
- As chamadas rejeitadas são registradas na auditoria (sem bloquear a requisição).
- Uma conta local que compartilha o nome com uma fonte de identidade externa não é vinculada automaticamente, para impedir a tomada de controle de contas.
- Ao exportar CSV, é adicionado um prefixo às células que começam com `=`, `+`, `-` ou `@`, para impedir a injeção de fórmulas.

## Auditoria

Todas as operações de administração, as mudanças de permissões, os eventos de início de sessão e as chamadas rejeitadas são registradas no registro de auditoria; as mudanças relacionadas a permissões e sua auditoria são confirmadas dentro da mesma transação, de modo que, se a mudança for revertida, a auditoria também não permanece.
