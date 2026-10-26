---
title: PostgreSQL 에이전트
description: 서버 옆에서 PostgreSQL 에이전트를 실행하여 GrantForge 정책을 컬럼과 행 단위까지 PostgreSQL 자체의 역할, 권한, 행 보안 정책으로 만들고, 데이터베이스마다 하나의 트랜잭션으로 바꾸며, 서버 로그에서 접근을 보고합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

PostgreSQL 에이전트는 서버 옆에서 실행되는 서비스입니다. PostgreSQL 자체의 접근 제어를 `postgresql` 데이터 서비스의 서명된 정책과 일치시킵니다: 에이전트가 관리하는 모든 데이터베이스에서 정책이 사용자에게 주는 것만 정확히 가진 사용자별 역할 하나, 행 필터마다 행 보안 정책 하나입니다. 그러면 PostgreSQL이 모든 구문을 스스로 검사하며, 에이전트는 클라이언트와 서버 사이에 끼어들지 않습니다. 배포판은 이를 실행 가능한 JAR `agents/postgresql/grantforge-agent-postgresql-<version>.jar`로 제공하며, Java 17 이상이 필요합니다.

저장소의 Testcontainers 통합 테스트는 에이전트와 배포되는 그대로의 JAR를 pgaudit가 있는 PostgreSQL `15`, `16`, `17`, `18`에 대해 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아니며, 에이전트는 `15`보다 오래된 서버를 거부합니다. 서비스 플러그인은 [PostgreSQL](/ko/plugins/postgresql/)을 참조하세요.

## 동작 방식

- **사용자마다 역할 하나가 GrantForge가 주는 것을 가집니다.** 정책이 무언가를 주는 사용자마다 에이전트는 그 이름을 딴 역할, 예컨대 `alice`의 `grantforge_alice`를 유지하고, 사용자를 `SET ROLE` 없이도 역할의 권한을 갖는 구성원으로 만듭니다. 에이전트는 자신의 모든 역할에 주석을 남기며 주석이 없는 역할은 자기 것으로 여기지 않으므로, 다른 역할이나 자신이 부여하지 않은 권한은 바꾸지 않습니다.
- **데이터베이스마다 하나의 트랜잭션으로 바뀝니다.** 에이전트는 관리하는 각 데이터베이스에 로그인하여 그 안에서 하나의 트랜잭션으로 변경하므로, 사용자는 한 정책 버전이 데이터베이스에 모두 적용되었거나 전혀 적용되지 않은 상태만 봅니다.
- **아래에서 다르게 판정되지 않으면 테이블 전체에 부여합니다.** 정책이 테이블 전체에 허용하고 어떤 컬럼에서 이를 빼앗을 수 있는 정책이 없는 접근 유형은 테이블에 부여되므로, 나중에 추가된 컬럼도 바로 가집니다. 그렇지 않으면 읽기, 삽입, 수정은 컬럼별로 부여됩니다. 데이터베이스의 `CONNECT`, 스키마의 `USAGE`, 그리고 사용자가 삽입할 수 있는 serial 컬럼의 시퀀스에 대한 `USAGE`가 함께 부여됩니다.
- **행 필터는 좁히기만 합니다.** 행 필터는 테이블에 대한 사용자 역할의 `SELECT`용 제한형(restrictive) 행 보안 정책입니다. 행 보안이 꺼져 있으면 에이전트는 이를 켜면서 모든 사람에게 모든 행을 보여 주는 허용형 정책을 접두사 이름(`grantforge`)으로 함께 만들어, 다른 사람에게는 아무것도 바뀌지 않게 합니다. 테이블에 필터도 자체 정책도 남지 않으면 행 보안을 다시 끕니다. 소유자가 켠 행 보안은 소유자의 것으로 남으며, 에이전트의 정책은 그것이 보여 주는 것을 좁히기만 합니다. PostgreSQL이 거부하는 필터, 정책이 묶는 대상을 바꾸는 필터, 또는 둘 이상의 구문으로 된 텍스트는 사용자에게 아무 행도 보여 주지 않는 정책으로 바뀌고 기록됩니다.
- **현재 시점의 정책.** 각 동기화는 그 시점에 적용되는 정책에 따라 계획하므로 유효 기간이 제때 시작하고 끝나며, PostgreSQL이 가진 사용자, 스키마, 테이블, 컬럼에 따라 계획하므로 새로 생긴 것은 다음 동기화에서 다뤄집니다.
- **역할은 그룹입니다.** 그룹에 대한 정책은 직접 또는 다른 역할을 통해 그 이름의 PostgreSQL 역할의 구성원인 사용자에게, GrantForge가 넣어 둔 그룹과 함께 적용됩니다.
- **수동 변경은 되돌려집니다.** 에이전트는 동기화할 때마다 자기 역할의 권한을 PostgreSQL이 보여 주는 것과, 자기 행 보안 정책을 마지막으로 만든 것과 비교합니다. 다른 부분은 다음 동기화에서 복원됩니다. 아무것도 바뀌지 않았다면 에이전트는 아무것도 보내지 않습니다.
- **GrantForge 밖의 권한을 보고합니다.** PostgreSQL은 권한을 더하므로 에이전트는 사용자가 직접, 다른 역할을 통해, 또는 PUBLIC을 통해 가진 것과, 자신이 소유한 테이블로 할 수 있는 일을 빼앗을 수 없습니다. 정책이 거부하는 것을 주는 그런 권한마다 한 번 경고하며, 사용자, 권한, 사용자가 그것을 갖는 경로, 정책을 밝힙니다.
- **접근은 서버 로그에서 옵니다.** 에이전트는 PostgreSQL의 JSON 서버 로그를 읽습니다: PostgreSQL이 허용한 것은 pgaudit의 세션 로그에서 구문이 읽거나 쓴 테이블마다 하나의 이벤트로, 권한이 없어 PostgreSQL이 거부한 것은 오류가 지목한 대상에 대한 하나의 거부 이벤트로 보고합니다. 각 이벤트는 GrantForge가 결정했는지, 즉 정책이 PostgreSQL과 같은 결정을 내리는지, 아니면 GrantForge 밖의 권한이 허용했는지를 밝힙니다.

| 구문 | 접근 이벤트 |
| --- | --- |
| `SELECT`, `COPY … TO`, 다른 구문의 읽기 부분 | 읽은 각 테이블의 `select` |
| `INSERT`, `COPY … FROM` | 쓴 테이블의 `insert` |
| `UPDATE`, `MERGE`, `SELECT … FOR UPDATE` | 테이블의 `update` |
| `DELETE`, `TRUNCATE` | 테이블의 `delete`, `truncate` |
| `CREATE TRIGGER` | 테이블의 `trigger` |
| `CREATE TABLE`, `CREATE VIEW`와 그 밖의 객체 | 스키마의 `create` |
| `CREATE SCHEMA`, `CREATE TEMPORARY TABLE` | 데이터베이스의 `create_schema`, `temporary` |
| 거부된 연결, `USAGE`가 없어 거부된 스키마 | 데이터베이스의 `connect`, 스키마의 `usage` |

## 배포

1. `postgresql` 데이터 서비스를 추가하고 정책을 설정합니다. **데이터 권한 → 에이전트**를 열고 서비스를 선택한 뒤 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되므로 에이전트 실행 계정만 읽을 수 있는 파일에 저장합니다.
2. 에이전트의 PostgreSQL 사용자를 슈퍼유저로 만듭니다. 이 사용자는 자신이 소유하지 않은 테이블에 권한을 부여하고, 행 보안을 켜고 끄며, 역할을 유지하고 서버 로그를 읽습니다:

   ```sql
   CREATE ROLE grantforge LOGIN SUPERUSER PASSWORD '…';
   ```

   테스트는 에이전트를 슈퍼유저로 실행합니다. 슈퍼유저는 권한에도 행 보안에도 묶이지 않으므로, 에이전트는 이들을 위한 역할을 유지하지 않습니다.
3. 접근을 보고하려면 PostgreSQL이 서버 로그를 JSON으로 쓰고 pgaudit(PGDG의 `postgresql-17-pgaudit` 같은 패키지나 플랫폼의 패키지)를 로드하도록 `postgresql.conf`를 설정한 뒤 서버를 다시 시작합니다:

   ```ini
   logging_collector = on
   log_destination = 'stderr,jsonlog'
   shared_preload_libraries = 'pgaudit'
   pgaudit.log = 'read, write, ddl'
   pgaudit.log_relation = on
   pgaudit.log_catalog = off
   ```

   pgaudit가 없으면 에이전트는 PostgreSQL이 거부한 것만 보고하고 허용한 것은 보고하지 않습니다. JSON 로그가 없으면 아무것도 보고하지 않으며 이를 한 번 알립니다.
4. 예시의 주소, 경로, 이름을 바꿔 설정 파일을 작성합니다:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/postgresql-agent.token
   grantforge.instance=postgresql-agent-1
   grantforge.cache.dir=/var/lib/grantforge/postgresql-agent
   grantforge.postgresql.hosts=postgres1.internal:5432,postgres2.internal:5432
   grantforge.postgresql.user=grantforge
   grantforge.postgresql.password.file=/etc/grantforge/postgresql-agent.password
   grantforge.postgresql.ssl.mode=verify-full
   grantforge.postgresql.tls.ca=/etc/grantforge/postgresql-ca.pem
   ```

   이 경로들은 에이전트가 있는 머신의 경로이며, 캐시 디렉터리는 쓰기 가능해야 합니다. 비밀번호 파일은 UTF-8로 읽고 앞뒤 공백을 제거합니다.
5. 예를 들어 systemd 서비스로 에이전트를 시작합니다:

   ```bash
   java -jar agents/postgresql/grantforge-agent-postgresql-<version>.jar /etc/grantforge/postgresql-agent.properties
   ```

   `<version>`을 배포판 버전으로 바꿉니다. `java -Dgrantforge.…=… -jar …`로 준 설정이 파일보다 우선합니다. 에이전트는 SIGTERM을 받으면 깔끔하게 멈추고 대기 중인 이벤트를 보내거나 보관합니다. PostgreSQL은 받은 것을 유지합니다.
6. **기존 권한을 정책으로 옮기세요:** GrantForge 밖에서 부여한 권한은 에이전트의 역할과 함께 계속 적용됩니다. 대응하는 정책을 만든 뒤 그 권한을 회수하세요. 에이전트의 경고는 정책이 거부하는 것을 주는 권한을 알려 줍니다.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 에이전트의 이름, 서명된 스냅샷·보관된 이벤트·서버 로그 읽기 위치를 위한 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택적 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사합니다. 생략하면 첫 접속 때 서버에서 키를 가져옵니다 |
| `grantforge.postgresql.hosts`, `grantforge.postgresql.user` | 필수: `host:port` 형식의 서버(연결 가능한 서버가 프라이머리가 될 때까지 차례로 시도)와 에이전트의 PostgreSQL 사용자 |
| `grantforge.postgresql.database` | 역할, 데이터베이스, 서버 로그를 읽기 위해 에이전트가 먼저 로그인하는 데이터베이스, 기본값 `postgres` |
| `grantforge.postgresql.password.file` | 사용자의 비밀번호. 생략하면 에이전트는 비밀번호 없이 로그인합니다 |
| `grantforge.postgresql.ssl.mode` | libpq의 `sslmode`가 부르는 TLS 방식, 기본값 `prefer` |
| `grantforge.postgresql.tls.ca` | verify-ca와 verify-full: 서버를 검증하는 PEM CA 인증서. 비우면 Java가 신뢰하는 것을 신뢰합니다 |
| `grantforge.postgresql.tls.cert`, `grantforge.postgresql.tls.key` | 비밀번호 대신 인증서(`cert` 인증)로 로그인하기 위한 PEM 클라이언트 인증서와 그 PKCS #8 개인 키(함께 설정). `require`나 검증 방식이 필요하며, 검증 방식과 함께 쓰려면 `grantforge.postgresql.tls.ca`도 필요합니다 |
| `grantforge.postgresql.timeout.ms` | 연결과 구문에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |
| `grantforge.postgresql.databases` | 에이전트가 관리하는 데이터베이스, 쉼표로 구분. 비우면 연결을 허용하는 모든 데이터베이스를 관리합니다 |
| `grantforge.postgresql.role.prefix` | 에이전트 역할 이름의 시작, 기본값 `grantforge_`. 문자, 숫자, 밑줄로 이루어지고 밑줄로 끝나며 최대 32자이고 `pg_`로 시작하지 않아야 합니다. 이름이 이것으로 시작하고 에이전트가 표시한 역할, 그리고 그 역할들이나 마지막 밑줄을 뺀 접두사 이름의 행 보안 정책은 에이전트 소유입니다 |
| `grantforge.postgresql.unmanaged.users` | 에이전트가 건드리지 않는 사용자, 쉼표로 구분, 예: 관리자. 에이전트는 자신의 사용자와 슈퍼유저를 항상 건드리지 않습니다 |
| `grantforge.sync.interval.ms` | 에이전트가 PostgreSQL을 맞추는 주기, 기본값 `10000`, 범위 `1000..3600000` 밀리초 |
| `grantforge.audit.server.log` | 기본값 `true`. `false`면 서버 로그를 읽지 않고 접근을 보고하지 않습니다 |
| `grantforge.audit.server.log.interval.ms`, `grantforge.audit.server.log.max.bytes` | 로그를 읽는 주기, 기본값 `5000` 밀리초, 그리고 한 번에 읽는 최대 바이트, 기본값 16 MiB. 밀린 로그는 여러 번에 나누어 읽습니다 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결·읽기 시간 제한, 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 요청하는 주기, 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 주기(기본값 `5000` 밀리초), 디스크 보관 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 동일한 허용 접근을 하나의 이벤트로 합치는 시간, 기본값 `10000`, 최대 `3600000` 밀리초. `0`이면 하나씩 보냅니다 |

## 사용자, 그룹과 역할

정책은 PostgreSQL 사용자, 즉 로그인할 수 있는 역할을 지정합니다. 그룹은 사용자가 직접 또는 다른 역할을 통해 구성원인 역할과 GrantForge가 사용자를 넣은 그룹이며, 역할은 GrantForge가 사용자에게 주는 역할입니다.

## 결정과 제한 사항

- 스냅샷이 없으면 에이전트는 아무것도 바꾸지 않습니다. 사용 중이 아닌 서비스, 또는 타입이 `postgresql`이 아닌 서비스에 묶인 토큰은 아무것도 부여하지 않습니다: 에이전트는 자신의 역할과 행 보안 정책을 삭제하고, PostgreSQL 자체의 권한이 적용됩니다.
- 에이전트가 실행되지 않는 동안 PostgreSQL은 마지막으로 받은 것을 유지하며, 정책 변경은 에이전트를 기다립니다. 변경은 갱신 주기와 동기화 주기 안에 PostgreSQL에 도달합니다.
- 동기화 뒤에 만든 테이블은 다음 동기화에서 권한을 받습니다.
- 슈퍼유저와 테이블 소유자는 테이블로 무엇이든 할 수 있습니다. 슈퍼유저, `BYPASSRLS`가 있는 역할, 테이블 소유자는 소유자가 강제하지 않는 한 그 테이블의 행 보안에 묶이지 않습니다.
- 뷰는 `security_invoker`가 아니면 소유자의 권한으로 테이블을 읽습니다: 서버 로그는 뷰가 읽은 테이블을 GrantForge 밖의 권한이 허용한 것으로 보고합니다.
- 파티션을 직접 읽으면 그 파티션 테이블의 행 보안으로 걸러지지 않습니다.
- pgaudit는 PostgreSQL이 구문을 실행하기 전에 기록하므로, 시퀀스가 거부된 삽입처럼 나중에 실패하는 구문은 허용으로, 그 거부는 거부로 보고됩니다. pgaudit가 기록하지 않는 세션의 구문은 보고되지 않습니다.
- PostgreSQL의 거부는 스키마 없이 테이블 이름만 밝힙니다: 이벤트는 구문이 한정한 테이블, 그 이름의 유일한 테이블, 또는 `public`의 테이블을 지목합니다. 스키마 없이 만든 객체는 `public`으로 보고됩니다.
- 에이전트는 자신이 로그인한 서버, 즉 프라이머리의 서버 로그를 읽으며 복제본의 로그는 읽지 않습니다.
- PostgreSQL에는 컬럼 마스킹이 없으므로 정책은 값을 마스킹할 수 없습니다.

## 관련 가이드

- [PostgreSQL](/ko/plugins/postgresql/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
