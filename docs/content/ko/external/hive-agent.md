---
title: Apache Hive 에이전트
description: HiveServer2에 에이전트를 설치해 GrantForge 정책으로 문을 검사하고, SHOW를 필터링하며, 컬럼을 마스킹하고 행을 필터링합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Hive 에이전트는 Apache Ranger의 Hive 플러그인처럼 Hive 권한 부여 API의 권한 부여자입니다. HiveServer2는 각 문을 실행하기 전에 에이전트에 묻고, 에이전트는 `hive` 데이터 서비스의 서명된 정책으로 답합니다. 배포판은 이를 Java 8 호환 JAR 하나, `agents/hive/grantforge-agent-hive-<version>.jar`로 제공하며 의존성은 내부로 재배치되어 있습니다.

저장소의 Testcontainers 통합 테스트는 HiveServer2 `3.1.3`, `4.0.1`, `4.1.0` 안에서 이 JAR을 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다. 서비스 플러그인은 [Apache Hive](/ko/plugins/hive/)를 참고하세요.

## 동작 방식

- **문을 검사합니다.** 문이 읽거나 쓰는 각 객체에 대해 에이전트는 Ranger처럼 작업에 필요한 접근 유형을 정책에 묻습니다. 첫 거부에서 문은 `Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries]`로 실패합니다. 쿼리는 컬럼 단위로 검사합니다.
- **목록을 필터링합니다.** `SHOW DATABASES`와 `SHOW TABLES`는 사용자가 쓸 수 있는 것, 즉 데이터베이스나 테이블 또는 그 안의 무언가에 대해 어떤 접근이든 있는 것만 보여 줍니다. `USE`와 `DESCRIBE DATABASE`도 같은 조건이 필요합니다.
- **쿼리를 마스킹하고 필터링합니다.** 사용자에게 테이블의 마스킹 컬럼이나 행 필터가 있으면 HiveServer2가 마스크와 조건으로 쿼리를 다시 작성하므로, 조인·조건·함수가 모두 사용자가 볼 수 있는 것만 봅니다.
- **마스크를 우회할 수 없습니다.** 사용자에게 마스크나 행 필터가 있는 테이블에 쓰기, 변경, 내보내기를 하면 Ranger의 기본 동작처럼 거부됩니다.
- **권한과 역할은 GrantForge가 관리합니다.** Hive의 `GRANT`, `REVOKE`, `SHOW GRANT`, `CREATE ROLE`, `SET ROLE` 및 기타 역할 문은 거부되며, `SHOW CURRENT ROLES`는 GrantForge가 사용자에게 준 역할을 보여 줍니다.
- **안전하지 않은 문을 거부합니다.** HiveServer2 자신의 권한으로 파일 시스템에 접근하게 되는 `dfs` 명령은 거부됩니다. HiveServer2 세션에서는 `TRANSFORM` 스크립트가 금지되고, 사용자는 Hive SQL 표준 권한 부여가 허용하는 설정만 `SET`할 수 있습니다.
- **모든 결정을 보고합니다.** 사용자, 클라이언트 주소, 리소스, 접근 유형, 결과, 작업, 문을 GrantForge에 보냅니다.

| 작업 | 접근 유형 |
| --- | --- |
| 쿼리, `SHOW CREATE TABLE`, `SHOW PARTITIONS`, `SHOW TBLPROPERTIES`, `DESCRIBE`, `SHOW COLUMNS`, `ANALYZE` | `select` |
| `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `TRUNCATE`, `LOAD`(대상) | `update` |
| `CREATE DATABASE`, `CREATE TABLE`, `CREATE VIEW`, `CREATE FUNCTION` | 만드는 대상에 `create`, 읽는 대상에 `select` |
| `ALTER …`, `MSCK` | `alter` |
| `DROP …` | `drop` |
| `LOCK`, `UNLOCK` | `lock` |
| `SHOW DATABASES`, `SHOW TABLES`, `USE`, `DESCRIBE DATABASE` | 객체 또는 그 안의 무언가에 대한 어떤 접근이든 |
| `REPL DUMP`, `REPL LOAD`, `REPL STATUS` | `repladmin` |
| `KILL QUERY`, 리소스 계획, LLAP 관리 | `hiveservice`에 대한 `serviceadmin` |
| 임시 함수, `ADD JAR`, `COMPILE` | `global`에 대한 `tempudfadmin` |
| 문이 읽거나 쓰는 위치 | `url`에 대한 `read` 또는 `write` |

## 배포

1. `hive` 데이터 서비스를 추가하고 정책을 구성합니다. HiveServer2가 자체 작업을 수행하는 사용자(예: `hive`)에게 필요한 모든 것을 허용하는 정책도 포함해야 합니다. **데이터 권한 → 에이전트**를 열고 서비스를 선택해 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되므로 HiveServer2 계정만 읽을 수 있는 파일에 저장하세요.
2. **모든 HiveServer2**의 `lib`(예: `/opt/hive/lib`)에 JAR을 복사합니다.
3. 각 HiveServer2의 `hiveserver2-site.xml` 또는 `hive-site.xml`에 다음 설정을 추가하고, 예시 주소·경로·인스턴스 이름을 바꿉니다.

   ```xml
   <property><name>hive.security.authorization.enabled</name><value>true</value></property>
   <property><name>hive.security.authorization.manager</name>
     <value>org.devlive.grantforge.hive.agent.GrantForgeHiveAuthorizerFactory</value></property>
   <property><name>hive.security.authenticator.manager</name>
     <value>org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator</value></property>
   <property><name>hive.server2.enable.doAs</name><value>false</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hive/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>hs2-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hive/grantforge-cache</value></property>
   ```

   캐시 디렉터리는 HiveServer2 계정이 쓸 수 있어야 합니다. Ranger와 마찬가지로 `hive.server2.enable.doAs=false`는 HiveServer2가 자기 계정으로 데이터를 읽고 쓰게 하므로, 누가 무엇을 보는지는 정책이 결정합니다.
4. HiveServer2를 재시작합니다. 에이전트는 첫 세션과 함께 시작하고 서버와 함께 멈추며, 보내지 못한 이벤트를 전송하거나 대기열에 저장합니다.
5. **HiveServer2를 우회하는 경로를 막으세요.** 에이전트는 HiveServer2에서 실행되는 문만 검사합니다. Hive CLI, Spark, HDFS 클라이언트처럼 메타스토어나 웨어하우스 파일에 직접 접근하는 클라이언트는 검사하지 않으므로, 메타스토어와 HDFS 자체 권한이나 [HDFS 에이전트](/ko/external/hdfs-agent/)로 보호하세요.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 HiveServer2의 이름, 서명된 스냅숏과 대기 이벤트를 위한 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅숏 서명 키를 고정하는 선택적 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사하세요. 생략하면 첫 연결 시 서버에서 키를 가져옵니다 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결·읽기 시간 제한, 기본값 `5000` / `8000`밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 묻는 주기, 기본값 `30000`, 최소 `1000`밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 대기열 크기(기본값 `10000`), 전송 간격(기본값 `5000`밀리초), 디스크 대기열 한도(기본값 64 MiB) |

에이전트는 HiveServer2의 구성 파일과 시스템 속성(`-Dgrantforge.…`, 우선 적용)에서 설정을 읽으며, 클라이언트가 정할 수 있는 세션 설정에서는 절대 읽지 않습니다.

## 사용자, 그룹과 역할

정책은 HiveServer2가 세션에 부여한 사용자를 가리킵니다. 클라이언트가 로그인한 사용자 이름이나 Kerberos 주체의 짧은 이름(`alice@EXAMPLE.COM`이면 `alice`)입니다. 그룹은 HiveServer2에서 Hadoop 그룹 매핑이 사용자에게 주는 그룹과 GrantForge에서 사용자가 속한 그룹이며, 역할은 GrantForge가 사용자에게 준 역할입니다.

## 결정과 제한 사항

- 사용 가능한 스냅숏이 없거나 정책을 읽을 수 없으면 모든 문이 거부됩니다. 사용하지 않는 서비스는 권한 부여자가 없을 때처럼 HiveServer2를 검사하지 않습니다.
- 타입이 `hive`가 아닌 서비스에 바인딩된 토큰은 모든 것을 거부합니다.
- 문이 읽고 쓰는 위치는 `url` 정책이 결정합니다. Ranger와 달리 에이전트는 파일 시스템 자체 권한으로 돌아가지 않습니다.
- Hive가 객체를 지정하지 않는 문은 모든 데이터베이스에 대해 검사합니다. 객체가 없는 `REPL` 문은 `repladmin`, 객체가 없는 테이블·데이터베이스 소유자 변경은 `alter`가 데이터베이스 `*`에 필요합니다.
- `EXPLAIN`, `DESCRIBE FUNCTION`, `SHOW FUNCTIONS`, `SET` 및 Ranger가 검사하지 않는 다른 문도 검사하지 않으며, `getTables` 같은 JDBC 클라이언트의 메타데이터 호출은 필터링하지 않습니다.
- 에이전트는 HiveServer2에서 동작합니다. 메타스토어 내부의 권한 부여는 다루지 않습니다.

## 관련 가이드

- [Apache Hive](/ko/plugins/hive/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
