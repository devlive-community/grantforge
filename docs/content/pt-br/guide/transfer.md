---
title: Importação e exportação em massa
description: Importe ou exporte usuários e a estrutura da organização em massa com arquivos CSV, com verificação prévia antes da escrita.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

**Controle de acesso → Importação e exportação** permite importar ou exportar usuários e a estrutura da organização em massa por meio de arquivos CSV.

![Importação e exportação](/screenshots/transfer.png)

## Importação

1. Clique em **Baixar modelo** e preencha-o de acordo com a "Descrição das colunas". O cabeçalho não distingue maiúsculas de minúsculas e as colunas extras são ignoradas; o departamento e o cargo dos usuários são informados pelo código e, quando há mais de um, são separados por ponto e vírgula.
2. Envie o arquivo e clique em **Verificação prévia**: o GrantForge o analisa linha por linha e aponta cada problema (número da linha, coluna e motivo).
3. Depois que tudo passar, clique em **Confirmar importação de N linhas**. Se qualquer linha tiver problema, nada é escrito, para evitar importações pela metade.

Regras:

- A codificação do arquivo pode ser UTF-8 ou GBK (os CSV em chinês salvos pelo Excel são GBK por padrão) e é detectada automaticamente.
- Um arquivo aceita no máximo 1000 usuários ou 5000 departamentos e não pode passar de 2 MB (configurável).
- Os departamentos são importados depois de ordenados automaticamente pela relação de subordinação: no arquivo, um departamento superior pode aparecer escrito depois de um subordinado; se dentro do arquivo se formar um ciclo, isso é apontado.
- A senha inicial dos usuários precisa atender à política de senhas e os usuários importados devem trocá-la no primeiro login.
- Só é possível importar para os departamentos e cargos que as suas permissões de dados permitem ver.

## Exportação

Exporte os usuários que atendem aos filtros atuais ou todos os departamentos; o nome do arquivo traz a data, por exemplo `users-2026-10-05.csv`. A exportação também respeita as permissões de dados e as permissões de campo: linhas que você não pode ver não são exportadas e os campos restritos ficam ocultos ou mascarados conforme suas regras. As células que começam com `=`, `+`, `-` ou `@` recebem um prefixo, para evitar que o programa de planilhas as execute como fórmulas.
