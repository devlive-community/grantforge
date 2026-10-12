---
title: Agente proxy do MySQL
description: Implante o proxy de rede MySQL, configure TLS e sincronização de políticas e verifique permissões de colunas, mascaramento, filtros de linhas e erros de recusa.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

O agente MySQL é um proxy de rede independente: os clientes se conectam a ele no lugar do servidor, e ele verifica os comandos compatíveis com as políticas assinadas do serviço de dados `mysql` antes que cheguem ao MySQL. Não é necessária instalação dentro do MySQL. A distribuição inclui o JAR executável `agents/mysql/grantforge-agent-mysql-<version>.jar`, que requer Java 17 ou posterior.

Os testes de integração com Testcontainers do repositório usam `mysql:8.0` e `mysql:8.4` com clientes MariaDB Connector/J e MySQL Connector/J. Isso não certifica outras versões do servidor nem todos os recursos do protocolo MySQL. Consulte [MySQL](/pt-br/plugins/mysql/) para os requisitos de versão e conexão do plug-in de serviço.

## Como funciona

- **O login passa direto.** A saudação do servidor chega ao cliente e as respostas do cliente chegam ao servidor, então o próprio MySQL verifica as senhas (`caching_sha2_password` e `mysql_native_password`); o proxy fica sabendo o usuário que o MySQL aceitou. As políticas nomeiam esse usuário, sem o host.
- **Os recursos SQL são verificados.** O proxy analisa COM_QUERY e os comandos preparados no servidor e verifica os tipos de acesso exigidos nos bancos de dados, tabelas e colunas. Um comando recusado não chega ao MySQL; o proxy gera um erro com código compatível com MySQL, como 1142 ou 1143.
- **As leituras são mascaradas e filtradas.** Quando um comando lê uma tabela com colunas mascaradas ou um filtro de linhas para o usuário, o proxy a substitui por uma tabela derivada de mesmo nome que mostra os valores mascarados e apenas as linhas filtradas, de modo que junções, condições e funções veem o que o usuário pode ver.
- Os **eventos de autorização de recursos** são enviados ao GrantForge com usuário, endereço do cliente, recurso, tipo de acesso, decisão e comando. O escopo da auditoria é descrito adiante.
- Os resultados da execução no MySQL são retransmitidos ao cliente; o proxy não altera os valores originais armazenados no banco de dados.

## Implantação

1. Adicione um serviço de dados `mysql` e configure suas políticas. Abra **Permissões de dados → Agentes**, selecione o serviço e emita um token de agente. Ele é exibido apenas uma vez; guarde-o em um arquivo que apenas a conta do proxy possa ler.
2. Crie um usuário de metadados para o proxy. Ele lê as colunas das tabelas e as funções armazenadas de `information_schema` e não precisa de dados: `REFERENCES` mostra todas as colunas e `SHOW_ROUTINE` todas as funções armazenadas. Conceda-os diretamente ao usuário, não por um papel; sem `SHOW_ROUTINE` o proxy não inicia.

   ```sql
   CREATE USER 'grantforge_meta'@'proxy-host' IDENTIFIED BY '…';
   GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'proxy-host';
   ```

3. Escreva o arquivo de configuração, substituindo os endereços, caminhos e nomes de exemplo:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mysql-proxy.token
   grantforge.instance=mysql-proxy-1
   grantforge.cache.dir=/var/lib/grantforge/mysql-proxy
   grantforge.proxy.host=0.0.0.0
   grantforge.proxy.port=3307
   grantforge.proxy.tls.cert=/etc/grantforge/mysql-proxy.crt
   grantforge.proxy.tls.key=/etc/grantforge/mysql-proxy.key
   grantforge.proxy.tls.required=true
   grantforge.mysql.host=mysql.internal
   grantforge.mysql.port=3306
   grantforge.mysql.tls=verify-identity
   grantforge.mysql.tls.ca=/etc/grantforge/mysql-ca.pem
   grantforge.mysql.metadata.user=grantforge_meta
   grantforge.mysql.metadata.password.file=/etc/grantforge/mysql-meta.password
   ```

   Este exemplo ativa TLS dos clientes ao proxy e do proxy ao MySQL. `mysql-proxy.crt` deve corresponder ao nome do host do proxy usado pelos clientes; o certificado do MySQL deve corresponder a `mysql.internal`. Esses caminhos ficam na máquina do proxy, e o diretório de cache deve permitir gravação. Os arquivos de senha são lidos como UTF-8 e têm os espaços em branco iniciais e finais removidos; senhas que precisam desses espaços não podem ser armazenadas assim.

4. Inicie o proxy, por exemplo como serviço do systemd:

   ```bash
   java -jar agents/mysql/grantforge-agent-mysql-<version>.jar /etc/grantforge/mysql-proxy.properties
   ```

   Substitua `<version>` pela versão da distribuição. Configurações passadas como `java -Dgrantforge.…=… -jar …` têm precedência sobre o arquivo. O proxy para de forma limpa com SIGTERM e envia ou mantém em disco os eventos pendentes.
5. Aponte os clientes para o endereço e a porta do proxy. Agora o MySQL vê o endereço do proxy, então a parte de host das contas precisa permiti-lo.
6. **Feche o caminho que contorna o proxy:** clientes que alcançam o MySQL diretamente não são verificados. Deixe apenas o proxy e os administradores alcançarem a porta do servidor.

| Configuração | Finalidade |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | Obrigatórias: servidor GrantForge, arquivo do token do agente, nome deste proxy e um diretório gravável para o snapshot assinado e os eventos mantidos em disco |
| `grantforge.signing.key.file` | Arquivo opcional que fixa a chave de assinatura dos snapshots; copie a chave pública Ed25519 X.509 codificada em Base64 da página de agentes. Se omitido, a chave é obtida do servidor no primeiro contato |
| `grantforge.proxy.host`, `grantforge.proxy.port` | Endereço de escuta; todos os endereços e porta `3307` por padrão. Faixa `0..65535`; `0` atribui uma porta automaticamente |
| `grantforge.proxy.tls.cert`, `grantforge.proxy.tls.key` | Certificado e chave privada PEM; ambos são exigidos juntos para oferecer TLS aos clientes |
| `grantforge.proxy.tls.required` | `false` por padrão; `true` recusa clientes sem TLS (erro 3159) e exige certificado e chave privada |
| `grantforge.mysql.host`, `grantforge.mysql.port` | Host MySQL obrigatório; porta `3306` por padrão, faixa `1..65535` |
| `grantforge.mysql.tls` | TLS para MySQL: `disabled` (padrão), `required` (criptografado, sem verificação), `verify-ca` ou `verify-identity` |
| `grantforge.mysql.tls.ca` | AC em PEM para verificar o MySQL; vazio usa as autoridades confiáveis do Java |
| `grantforge.mysql.metadata.user`, `grantforge.mysql.metadata.password.file` | Usuário de metadados obrigatório; o arquivo de senha é opcional e, se omitido, nenhuma senha é passada ao driver |
| `grantforge.mysql.metadata.cache.seconds` | Duração do cache de colunas e funções armazenadas: padrão `30`, faixa `0..86400` segundos; `0` desativa sua reutilização |
| `grantforge.mysql.connect.timeout.ms` | Tempo limite de conexão ao MySQL: padrão `10000`, faixa `1..600000` milissegundos; leituras de metadados têm tempo limite fixo de `30000` milissegundos |
| `grantforge.proxy.check.threads` | Threads de verificação de comandos: padrão `8`, faixa `1..256` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | Tempos limite de conexão e leitura do GrantForge: padrão `5000` / `8000` milissegundos, faixa `1..2147483647` |
| `grantforge.refresh.interval.ms` | Intervalo inicial ou de contingência quando o servidor está inacessível: padrão `30000` milissegundos, mínimo `1000`; heartbeats normais usam o intervalo retornado pelo servidor |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity` | Lote padrão de `500` e fila de `10000` eventos; faixa do lote `1..1000`, fila pelo menos do tamanho do lote e no máximo `1000000` |
| `grantforge.audit.flush.interval.ms` | Intervalo de envio da auditoria: padrão `5000` milissegundos, faixa `1..2147483647` |
| `grantforge.audit.spool.limit.bytes` | Limite do armazenamento de auditoria em disco: padrão `67108864` (64 MiB), mínimo `0` |

O TLS é independente nos dois lados. Quando só um lado o usa e o MySQL pede a senha inteira (`caching_sha2_password` sem acerto no cache), o proxy a transporta: a um cliente sem TLS que pede a chave do servidor, ele responde com uma chave RSA própria, e criptografa a senha de um cliente com TLS com a chave do MySQL para um servidor sem TLS. Os clientes não devem ser configurados com o arquivo de chave pública do MySQL através do proxy.

## O que os comandos precisam

| Comando | Acesso |
| --- | --- |
| `SELECT`, subconsultas, `WITH`, `EXPLAIN`, `SET @x = (SELECT …)` | `select` em cada coluna usada; uma tabela lida sem coluna, como em `COUNT(*)` ou `EXISTS`, precisa de `select` na tabela inteira |
| `INSERT`, `LOAD DATA` | `insert` nas colunas gravadas (todas, se nenhuma for listada); `ON DUPLICATE KEY UPDATE` acrescenta `update` |
| `REPLACE` | `insert` nas colunas e `delete` na tabela |
| `UPDATE` | `update` nas colunas atribuídas e `select` nas lidas |
| `DELETE` | `delete` na tabela e `select` nas colunas lidas |
| `CREATE TABLE`, `CREATE VIEW` / `DROP TABLE`, `DROP VIEW`, `TRUNCATE` / `ALTER TABLE`, `ALTER VIEW`, `OPTIMIZE` | `create` / `drop` / `alter` na tabela ou view |
| `RENAME TABLE` | `alter` e `drop` no nome antigo, `create` e `insert` no novo |
| `CREATE INDEX`, `DROP INDEX` | `index` na tabela |
| `CREATE DATABASE`, `DROP DATABASE` | `create`, `drop` no banco de dados |
| `LOCK TABLES`, `ANALYZE`, `CHECK`, `CHECKSUM TABLE` | `select` na tabela |

Comandos que não usam dados ficam a cargo dos privilégios do MySQL: transações, `SET`, `USE`, `SHOW`, `DESCRIBE`, `KILL` e comandos de contas como `GRANT` e `CREATE USER`. `information_schema` e `performance_schema` são bancos de dados como os outros e precisam de políticas.

O proxy recusa comandos que não consegue verificar completamente: comentários executáveis (`/*! … */`), funções armazenadas e `CALL`, `CREATE PROCEDURE`, `FUNCTION`, `TRIGGER` e `EVENT`, `PREPARE`/`EXECUTE` no nível SQL, `HANDLER`, comandos de replicação e clonagem, `USE` ou `SET NAMES` junto com outros comandos em um mesmo lote e comandos que não consegue analisar.

## Máscaras, filtros de linhas e seus limites

- Máscaras e filtros se aplicam onde quer que um comando leia a tabela, incluindo subconsultas, junções, `INSERT … SELECT` e `CREATE TABLE … SELECT` (cuja cópia contém os valores mascarados).
- Um `UPDATE` ou `DELETE` de uma única tabela altera apenas as linhas que o filtro mostra. Um `UPDATE` ou `DELETE` de várias tabelas sobre uma tabela filtrada é recusado, assim como uma gravação que usa uma coluna mascarada da tabela que altera, pois poderia copiar ou sondar os valores ocultos.
- Não é possível criar nem alterar uma view sobre uma tabela mascarada ou filtrada: o MySQL executa a view depois, sem o proxy.
- Os comandos preparados reutilizam sua verificação e reescrita enquanto a versão das políticas não muda. Uma mudança de versão provoca nova verificação: a execução continua se o acesso permanecer permitido e o texto reescrito não mudar; caso contrário, o erro 1615 exige que o cliente prepare o comando novamente.
- Uma coluna nomeada sem a tabela conta para cada tabela visível que tenha uma coluna assim; qualifique as colunas para evitar recusas causadas por uma coluna restrita de mesmo nome.
- As colunas lidas por `*` vêm do que o usuário de metadados vê em `information_schema` e ficam em cache por `grantforge.mysql.metadata.cache.seconds`. DDL bem-sucedido enviado pelo proxy como comando SQL comum invalida o cache; a próxima consulta lê os metadados novamente. DDL externo ou preparado não garante invalidação durante a execução; aguarde a expiração do cache ou reinicie o proxy.

## Escopo da aplicação das políticas

O proxy verifica os recursos que consegue analisar no SQL do cliente. Ele não expande recursivamente views existentes, gatilhos ou outras operações executadas internamente pelo servidor. Para proteger as tabelas subjacentes, restrinja também os privilégios nativos do MySQL e o uso de views e gatilhos existentes. Não conceda privilégios de administrador a usuários de negócio esperando que o proxy intercepte comandos administrativos.

Um filtro de linhas não é uma restrição geral de gravação: `INSERT`, `REPLACE` e `LOAD DATA` verificam permissões de gravação, mas não exigem automaticamente que novas linhas atendam à expressão do filtro. O ramo de `INSERT … ON DUPLICATE KEY UPDATE` que atualiza uma linha existente também não recebe uma condição de filtro de linhas. Compressão do protocolo, ZSTD, atributos de consulta e metadados opcionais de resultados são desativados durante a negociação.

## Políticas e falhas

- Sem um snapshot utilizável, ou com um token vinculado a um serviço que não é `mysql`, todo comando que usa dados é recusado.
- Se o GrantForge estiver temporariamente inacessível ou a assinatura de um novo snapshot for inválida, o proxy mantém o snapshot já aplicado. Uma inicialização sem nenhum snapshot utilizável recusa o acesso a recursos.
- Desativar o serviço ignora a autorização de recursos, o mascaramento e a filtragem de linhas do GrantForge; a análise SQL e as restrições do protocolo continuam valendo. Desativar o serviço não bloqueia o acesso aos dados.
- Os privilégios do MySQL continuam valendo após as verificações do proxy; o proxy só pode restringi-los.
- `COM_CHANGE_USER` é acompanhado e recusado quando os lados do cliente e do servidor diferem em TLS; em vez disso, conecte-se novamente como o outro usuário.

## Exemplo de verificação

Em um banco de dados de teste separado, peça a um administrador para criar os dados e a conta a seguir. Substitua `proxy-host` pelo endereço de origem do proxy que o MySQL realmente vê:

```sql
CREATE DATABASE grantforge_demo;
CREATE TABLE grantforge_demo.orders (id INT PRIMARY KEY, region VARCHAR(8), card VARCHAR(32));
INSERT INTO grantforge_demo.orders VALUES (1, 'EU', '4111111111111111'), (2, 'US', '5555555555554444');
CREATE USER 'alice'@'proxy-host' IDENTIFIED BY 'replace-with-a-password';
GRANT SELECT, UPDATE ON grantforge_demo.* TO 'alice'@'proxy-host';
```

Crie três políticas do GrantForge para `alice`: permita `select` em `database=grantforge_demo`, `table=orders`, `column=*`; masque `card` com `mask_show_last_4`; filtre a tabela com `region = 'EU'`. Mantenha o serviço e as três políticas ativados. Aguarde até **Permissões de dados → Agentes** mostrar que a versão mais recente das políticas foi aplicada.

Conecte-se ao proxy com o cliente MySQL de linha de comando. `-p` solicita a senha; o arquivo da AC deve confiar no certificado do proxy:

```bash
mysql --host=mysql-proxy.example.com --port=3307 --user=alice -p \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/path/to/proxy-ca.pem grantforge_demo
```

```sql
SELECT id, region, card FROM orders ORDER BY id;
-- One row only: 1 | EU | xxxxxxxxxxxx1111

UPDATE orders SET region = 'US' WHERE id = 1;
-- 1143 / 42000: GrantForge does not grant update
```

Os privilégios nativos do MySQL já permitem `UPDATE` neste exemplo, então sua recusa confirma que o proxy aplica a política. Em seguida, altere o filtro de linhas para `region = 'US'`, aguarde o proxy aplicar a nova versão e consulte novamente. Apenas a linha 2 deve aparecer, com o cartão `xxxxxxxxxxxx4444`.

## Solução de problemas e auditoria

| Código de erro / SQLSTATE | Significado e verificações |
| --- | --- |
| `1044 / 42000`, `1142 / 42000`, `1143 / 42000` | Acesso a banco de dados, tabela ou coluna negado. Em mensagens contendo `by GrantForge`, verifique o usuário da política, os recursos, o tipo de acesso e a versão do snapshot; verifique também os privilégios nativos do MySQL para outras recusas |
| `1046 / 3D000` | Nenhum banco de dados selecionado; use `USE`, um banco de dados padrão na conexão ou um nome de tabela qualificado |
| `1227 / 42000` | Uma operação é proibida ou não pode ser verificada com segurança; use a mensagem para revisar restrições de comandos e privilégios de metadados |
| `1064 / 42000` | O SQL não pode ser analisado |
| `1615 / HY000` | Prepare o comando novamente após uma mudança de políticas |
| `3159 / HY000` | O proxy exige TLS, mas o cliente se conectou sem ele |
| `1043 / 08S01` | Falha de protocolo ou negociação TLS com o servidor de destino; verifique modo TLS, certificados, nome do host e recursos do servidor |
| `2003 / HY000` | O proxy não consegue conectar ao MySQL; verifique endereço, rede e estado do servidor de destino |

Em **Permissões de dados → Auditoria de acessos**, localize os eventos permitidos e negados de `alice` e os recursos correspondentes. Um comando SQL pode produzir vários eventos de autorização de recursos. Eles são registrados antes da execução no servidor, portanto `ALLOWED` não significa que o MySQL executou com sucesso. Falhas de análise, comandos sem acesso a recursos e serviços desativados podem não produzir eventos de recursos; execuções repetidas de um comando preparado na mesma versão das políticas não repetem eventos de autorização. Esta é uma auditoria de autorização de recursos, não um registro completo de execução SQL.

## Guias relacionados

- [MySQL](/pt-br/plugins/mysql/)
- [Serviços de dados, políticas e agentes](/pt-br/external/data-services/)
