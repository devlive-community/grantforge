---
title: ClickHouse 에이전트
description: 서버 옆에서 ClickHouse 에이전트를 실행하여 GrantForge 정책이 컬럼과 행 단위까지 ClickHouse 자체의 역할, 권한과 행 정책이 되게 하고, 쿼리 로그에서 접근을 보고합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

ClickHouse 에이전트는 서버 옆에서 실행되는 서비스입니다. ClickHouse 자체의 접근 제어를 `clickhouse` 데이터 서비스의 서명된 정책과 일치시킵니다: 사용자마다 정책이 그 사용자에게 주는 것만 정확히 가진 역할 하나, 행 필터마다 행 정책 하나입니다. 그러면 ClickHouse가 자신의 모든 프로토콜에서 모든 쿼리를 직접 확인하며, 에이전트는 클라이언트와 서버 사이에 끼어들지 않습니다. 배포판은 이를 Java 17 이상이 필요한 실행 가능한 JAR, `agents/clickhouse/grantforge-agent-clickhouse-<version>.jar`로 제공합니다.

저장소의 Testcontainers 통합 테스트는 에이전트와 배포되는 그대로의 JAR를 ClickHouse `24.8`, `25.3`, `25.8`, `26.3`, `26.8`에 대해 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아니며, 에이전트는 `24.8`보다 오래된 서버를 거부합니다. 서비스 플러그인은 [ClickHouse](/ko/plugins/clickhouse/)를 참조하세요.

## 동작 방식

- **사용자마다 역할 하나가 GrantForge가 주는 것을 가집니다.** 정책이 무언가를 주는 사용자마다 에이전트는 그 이름을 딴 역할, 예컨대 `alice`의 `grantforge_alice`를 유지하고 사용자에게 기본 역할로 부여합니다. `SHOW GRANTS FOR grantforge_alice`는 GrantForge가 `alice`에게 주는 모든 것을 보여 줍니다. 에이전트는 다른 역할이나 자신이 하지 않은 권한 부여를 바꾸지 않습니다.
- **아래에서 다르게 판정되지 않으면 데이터베이스 전체에 부여합니다.** 정책이 데이터베이스 전체에 허용하고, 그 안의 테이블이나 컬럼에서 이를 빼앗을 수 있는 정책이 없는 접근 유형은 데이터베이스에 부여되므로, 나중에 만든 테이블도 바로 가집니다. 그렇지 않으면 테이블별로, 읽기와 쓰기는 컬럼별로 부여합니다.
- **행 필터는 행 정책이 됩니다.** 행 필터는 테이블에 대한 사용자 역할의 허용형(permissive) 행 정책입니다. 새로 생기거나 바뀐 필터는 그것이 좁히는 권한보다 먼저 적용됩니다. ClickHouse가 거부하는 필터, 또는 그 텍스트가 행 정책을 다른 사람에게 적용되게 만드는 필터는 사용자에게 아무 행도 보여 주지 않는 필터로 바뀌고 로그에 남습니다.
- **현재 시점의 정책.** 각 동기화는 그 시점에 적용되는 정책으로 계획하므로 유효 기간이 제때 시작하고 끝나며, ClickHouse에 있는 사용자, 데이터베이스, 테이블과 컬럼으로 계획하므로 새로 생긴 것은 다음 동기화에서 다뤄집니다.
- **ClickHouse 역할은 그룹입니다.** 그룹에 대한 정책은 그 이름의 ClickHouse 역할을 직접 또는 다른 역할을 통해 가진 사용자에게, GrantForge가 넣은 그룹과 함께 적용됩니다.
- **수동 변경은 되돌립니다.** 에이전트는 각 역할과 행 정책에 준 것을 기억하며, 달라진 것은 다음 동기화에서 복원합니다. 바뀐 것이 없으면 에이전트는 아무것도 보내지 않습니다.
- **GrantForge 밖의 권한 부여를 보고합니다.** ClickHouse는 권한을 합산하므로, 에이전트는 사용자가 직접 또는 다른 역할을 통해 가진 것을 빼앗을 수 없습니다. 정책이 거부하는 것을 주는 그런 권한 부여마다 사용자, 권한, 그것이 오는 역할과 정책을 밝혀 한 번 경고합니다.
- **접근은 쿼리 로그에서 옵니다.** 에이전트는 `system.query_log`를 읽어 각 문을 그것이 건드린 테이블, 데이터베이스 또는 서버마다 하나의 이벤트로, 거부는 ClickHouse 오류가 지목한 것에 대한 하나의 거부 이벤트로 보고합니다. 각 이벤트는 GrantForge가 결정했는지, 즉 정책이 ClickHouse와 같게 판정하는지, 아니면 GrantForge 밖의 권한 부여가 허용했는지를 밝힙니다.

| 문 | 접근 이벤트 |
| --- | --- |
| `SELECT`, `INSERT … SELECT`의 읽기 부분 | 읽은 각 테이블의 `select` |
| `INSERT` | 쓴 테이블의 `insert` |
| `ALTER`, `ALTER UPDATE`, `ALTER DELETE`, `DELETE`, `UPDATE` | 테이블의 `alter` |
| `DROP TABLE`, `TRUNCATE`, `OPTIMIZE` | 테이블의 `drop`, `truncate`, `optimize` |
| `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` | 데이터베이스의 `create` |
| `DROP DATABASE` | 데이터베이스의 `drop_database` |
| `CREATE DATABASE`, `KILL QUERY`, `SYSTEM …` | 서버의 `create_database`, `kill_query`, `system` |

## 배포

1. `clickhouse` 데이터 서비스를 추가하고 정책을 설정합니다. **데이터 권한 → 에이전트**를 열고 서비스를 선택한 뒤 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되므로 에이전트 실행 계정만 읽을 수 있는 파일에 저장합니다.
2. 에이전트의 ClickHouse 사용자를 만듭니다. 이 사용자는 정책이 주는 것을 부여하므로 그 권한을 부여 옵션과 함께 가져야 하고, 역할과 행 정책을 유지하기 위한 접근 관리 권한이 필요합니다:

   ```sql
   CREATE USER grantforge IDENTIFIED BY '…';
   GRANT SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE, DROP VIEW,
       DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM ON *.* TO grantforge WITH GRANT OPTION;
   GRANT ACCESS MANAGEMENT ON *.* TO grantforge;
   ```

   에이전트가 역할을 유지하는 사용자는 바꿀 수 있는 곳, 즉 `CREATE USER`가 저장하는 `local_directory` 또는 `replicated` 사용자 디렉터리에 있어야 합니다. `users.xml`과 LDAP의 사용자는 건드리지 않고 한 번 로그에 남깁니다. 클러스터는 사용자, 역할과 행 정책을 `replicated` 사용자 디렉터리에 두는 것이 좋습니다. 그러면 에이전트 하나가 클러스터 전체를 담당합니다.
3. 설정 파일을 작성하고, 예시의 주소, 경로와 이름을 바꿉니다:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/clickhouse-agent.token
   grantforge.instance=clickhouse-agent-1
   grantforge.cache.dir=/var/lib/grantforge/clickhouse-agent
   grantforge.clickhouse.urls=https://clickhouse1.internal:8443,https://clickhouse2.internal:8443
   grantforge.clickhouse.user=grantforge
   grantforge.clickhouse.password.file=/etc/grantforge/clickhouse-agent.password
   grantforge.clickhouse.tls.ca=/etc/grantforge/clickhouse-ca.pem
   grantforge.audit.query.log.cluster=main
   ```

   이 경로들은 에이전트가 실행되는 머신에 있으며, 캐시 디렉터리는 쓰기 가능해야 합니다. 비밀번호 파일은 UTF-8로 읽고 앞뒤 공백을 제거합니다.
4. 에이전트를 시작합니다. 예를 들어 systemd 서비스로:

   ```bash
   java -jar agents/clickhouse/grantforge-agent-clickhouse-<version>.jar /etc/grantforge/clickhouse-agent.properties
   ```

   `<version>`은 배포판 버전으로 바꿉니다. `java -Dgrantforge.…=… -jar …`로 준 설정은 파일보다 우선합니다. 에이전트는 SIGTERM을 받으면 깔끔하게 멈추고 대기 중인 이벤트를 보내거나 디스크에 보관하며, ClickHouse는 받은 것을 그대로 유지합니다.
5. **기존 권한 부여를 정책으로 옮기세요:** GrantForge 밖에서 한 권한 부여는 에이전트의 역할과 함께 계속 적용됩니다. 대응하는 정책을 만든 다음 그 권한을 회수하세요. 에이전트의 경고가 정책이 거부하는 것을 주는 권한 부여를 알려 줍니다.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 에이전트의 이름, 서명된 스냅샷·대기 중인 이벤트·쿼리 로그 읽기 위치를 위한 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택적 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사합니다. 생략하면 첫 연결 때 서버에서 키를 가져옵니다 |
| `grantforge.clickhouse.urls`, `grantforge.clickhouse.user` | 필수: 서버들의 HTTP 인터페이스(하나에 연결할 수 없으면 차례로 시도)와 에이전트의 ClickHouse 사용자 |
| `grantforge.clickhouse.password.file` | 사용자의 비밀번호. 생략하면 에이전트는 비밀번호 없이 로그인합니다 |
| `grantforge.clickhouse.tls.ca` | `https://` 서버를 검증하는 PEM CA 인증서. 비우면 Java가 신뢰하는 것을 신뢰합니다 |
| `grantforge.clickhouse.tls.cert`, `grantforge.clickhouse.tls.key` | 비밀번호 대신 인증서(`IDENTIFIED WITH ssl_certificate`)로 로그인하기 위한 PEM 클라이언트 인증서와 개인 키, 함께 지정 |
| `grantforge.clickhouse.timeout.ms` | ClickHouse 요청에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |
| `grantforge.clickhouse.role.prefix` | 에이전트 역할 이름의 시작, 기본값 `grantforge_`. 문자, 숫자와 밑줄로 이루어지며 밑줄로 끝납니다. 이름이 이것으로 시작하는 모든 역할과 행 정책은 에이전트의 것입니다 |
| `grantforge.clickhouse.unmanaged.users` | 에이전트가 건드리지 않는 사용자, 쉼표로 구분, 예: 관리자. 자신의 사용자는 항상 건드리지 않습니다 |
| `grantforge.sync.interval.ms` | 에이전트가 ClickHouse를 맞추는 주기, 기본값 `10000`, 범위 `1000..3600000` 밀리초 |
| `grantforge.audit.query.log` | 기본값 `true`. `false`는 쿼리 로그를 읽지 않고 접근을 보고하지 않습니다 |
| `grantforge.audit.query.log.cluster` | `clusterAllReplicas`로 복제본의 쿼리 로그를 읽을 클러스터. 비우면 에이전트가 묻는 서버를 읽습니다 |
| `grantforge.audit.query.log.delay.ms`, `grantforge.audit.query.log.interval.ms`, `grantforge.audit.query.log.batch` | ClickHouse가 7.5초마다 로그를 쓰므로 현재보다 얼마나 뒤에서 읽는지(기본값 `10000` 밀리초), 읽는 주기(기본값 `5000` 밀리초), 한 번에 읽는 최대 항목 수(기본값 `1000`) |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결 및 읽기 타임아웃, 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 요청하는 주기, 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 간격(기본값 `5000` 밀리초), 디스크 버퍼 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 동일한 허용 접근을 하나의 이벤트로 합치는 시간, 기본값 `10000`, 최대 `3600000` 밀리초. `0`은 하나씩 보고 |

## 사용자, 그룹과 역할

정책은 ClickHouse 사용자를 지정합니다. 그룹은 사용자가 직접 또는 다른 역할을 통해 가진 ClickHouse 역할과 GrantForge가 사용자를 넣은 그룹이고, 역할은 GrantForge가 사용자에게 준 역할입니다.

## 결정과 제한 사항

- 스냅샷이 없으면 에이전트는 아무것도 바꾸지 않습니다. 사용하지 않는 서비스, 또는 타입이 `clickhouse`가 아닌 서비스에 바인딩된 토큰은 아무것도 부여하지 않습니다: 에이전트는 자신의 역할과 행 정책을 삭제하고 ClickHouse 자체의 권한이 적용됩니다.
- 에이전트가 실행되지 않는 동안 ClickHouse는 마지막으로 받은 것을 유지하며, 정책 변경은 에이전트를 기다립니다. 변경은 갱신 주기와 동기화 주기 안에 ClickHouse에 도달합니다.
- 에이전트가 테이블별로 부여하는 데이터베이스에서 만든 테이블은 다음 동기화에서 권한을 받습니다.
- ClickHouse에서 행 정책이 하나도 없는 사용자는 테이블의 모든 행을 봅니다. ClickHouse의 `users_without_row_policies_can_read_rows` 설정이 기본적으로 허용하는 동작이며, 이 설정을 끄면 필터가 없는 사용자는 필터가 걸린 테이블의 행을 하나도 보지 못합니다. 같은 사용자에 대해 GrantForge 밖에서 만든 허용형 행 정책은 그 필터가 보여 주는 범위를 넓힙니다.
- `system`은 `SYSTEM SHUTDOWN`을 포함한 모든 `SYSTEM` 문을 부여합니다.
- 쿼리 로그는 ClickHouse가 기록하는 것만 보고합니다: `log_queries`를 끈 사용자의 문은 보고되지 않습니다. 거부의 경우 사용자에게 컬럼 하나가 없을 때 ClickHouse가 테이블만 지목할 수 있으며, 그러면 이벤트도 테이블을 지목합니다.
- ClickHouse는 ClickHouse Cloud 밖에서 컬럼 마스킹을 제공하지 않으므로 정책으로 값을 마스킹할 수 없습니다.

## 관련 가이드

- [ClickHouse](/ko/plugins/clickhouse/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
