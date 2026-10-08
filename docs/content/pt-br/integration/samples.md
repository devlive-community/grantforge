---
title: Aplicações de exemplo
description: "Os dois exemplos completos do repositório: uma loja a que o navegador se conecta diretamente e um bloco de notas com início de sessão no servidor."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Em `samples/` há duas aplicações executáveis que, junto com o GrantForge, são cobertas por testes de ponta a ponta e são a melhor referência na hora de integrar.

| Exemplo | Porta | Forma de integração que demonstra |
| --- | --- | --- |
| `samples/shop` | 19081 | o navegador inicia sessão de forma cruzada com um cliente público + PKCE; os botões são exibidos conforme os recursos; o backend valida a API com `@RequirePermission`; `@GrantForgeEntity` declara a entidade de pedido e as consultas usam os escopos de dados "somente eu" e "todo o tenant atual" |
| `samples/notes` | 19082 | o servidor inicia sessão com o `oauth2Login` do Spring Security (cliente confidencial + PKCE); um `AccessTokenResolver` personalizado obtém o token da sessão; "Escrever nota" só é exibido para quem tem a permissão |

## Executar

Os exemplos são builds Maven independentes que dependem do starter e do SDK JavaScript deste repositório:

```bash
./mvnw -N install
./mvnw -pl sdk/grantforge-spring-boot-starter install
(cd sdk/grantforge-js && pnpm install && pnpm build)
./mvnw -f samples/pom.xml package
```

Toda a configuração dos exemplos vem de variáveis de ambiente:

| Variável | Explicação |
| --- | --- |
| `GRANTFORGE_URL` | o endereço do GrantForge |
| `SHOP_BROWSER_CLIENT_ID` | o cliente público do navegador da loja |
| `SHOP_CLIENT_ID` / `SHOP_CLIENT_SECRET` | o cliente confidencial que o backend da loja usa para declarar as entidades de dados (o scope `catalog`) |
| `SHOP_SDK_DIRECTORY` | o diretório com o resultado do build do SDK JavaScript (`sdk/grantforge-js/dist`) |

No GrantForge, é preciso criar os recursos, os clientes, os papéis e as políticas de dados das duas aplicações; o script de preparação dos testes de ponta a ponta `core/grantforge-web/tests/samples/setup.ts` demonstra todos esses passos e serve de referência direta.

## Testes de ponta a ponta

`script/ci/e2e_fullstack.sh` compila e inicia os dois exemplos depois dos testes full-stack e verifica: o início de sessão PKCE de origem cruzada e o CORS, a exibição e a ocultação dos botões, o 403 das interfaces, os escopos de dados "somente eu" e "todo o tenant atual", a exclusão e o encerramento de sessão, e o início de sessão no servidor da aplicação de notas. Se você definir `GRANTFORGE_E2E_SKIP_SAMPLES=1`, eles são pulados.
