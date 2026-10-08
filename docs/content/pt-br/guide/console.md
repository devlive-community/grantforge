---
title: Tour pelo console
description: O layout do console, os grupos de menus e por que os menus variam de uma pessoa para outra.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

![Visão geral do espaço de trabalho](/screenshots/dashboard.png)

## Layout

- O **menu da esquerda** está organizado em grupos: espaço de trabalho, controle de acesso, permissões de dados e administração da plataforma.
- A **barra superior** reúne o acesso rápido (⌘K ou Ctrl+K, para buscar páginas pelo nome), a alternância entre chinês e inglês, o tema claro/escuro e o menu pessoal.
- A página **Visão geral** mostra a quantidade de contas, departamentos, grupos de usuários e cargos do tenant atual, além dos membros do espaço de trabalho e dos primeiros passos.

## Grupos de menus

| Grupo | Menus | Descrição |
| --- | --- | --- |
| Espaço de trabalho | Visão geral, Minhas solicitações | Visível para todo usuário autenticado |
| Controle de acesso | Gestão de usuários, Estrutura da organização, Grupos de usuários, Cargos, Importação e exportação, Gestão de papéis, Separação de funções, Aprovação de permissões, Revisão de permissões, Sessões on-line, Fontes de identidade, Registros de auditoria | A identidade e as permissões deste tenant |
| Permissões de dados | Serviços de dados, Políticas, Agentes, Auditoria de acesso | As permissões dos sistemas de dados externos (HDFS, Hive etc.); ver [Serviços de dados, políticas e agentes](/pt-br/external/data-services/) |
| Administração da plataforma | Gestão de tenants, Catálogo de recursos, Catálogo de API, Servidor de autorização, Revisão do catálogo, Plug-ins | Disponível apenas no tenant plataforma |

## Por que meu menu é diferente do dos outros

O console é, ele próprio, uma aplicação gerenciada pelo GrantForge: cada página e cada botão é um recurso do catálogo de recursos, e quais menus e quais botões você vê depende inteiramente dos seus papéis.

- Quem tem o papel de sistema **administrador do tenant** vê todos os menus de "Controle de acesso" e "Permissões de dados" deste tenant.
- Quem tem o papel de sistema **administrador da plataforma** vê também "Administração da plataforma".
- As demais pessoas veem apenas as páginas autorizadas pelos seus papéis; quando um grupo não tem nenhuma página, o grupo inteiro não é exibido.

> [!NOTE]
> Ocultar menus é apenas uma comodidade. O servidor volta a verificar as permissões em cada chamada à API: mesmo que se digite o endereço diretamente, uma operação sem permissão é rejeitada.

Depois de uma mudança nas permissões não é necessário fazer login novamente: cada resposta traz o número da versão das permissões vigentes e, quando a versão muda, o console recarrega o menu automaticamente.
