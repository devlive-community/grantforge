---
title: Trino 에이전트
description: Trino coordinator에 시스템 접근 제어를 설치해 GrantForge 정책으로 쿼리를 검사하고, 사용자가 쓸 수 있는 것만 나열하며, 행을 필터링하고 컬럼을 마스킹하고, 사용자 가장과 다른 사용자의 쿼리를 정책으로 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Trino 에이전트는 `grantforge`라는 이름의 시스템 접근 제어입니다. coordinator는 쿼리가 닿는 모든 카탈로그, 스키마, 테이블, 컬럼, 루틴, 세션 속성, 사용자에 대해 에이전트에 묻고, 에이전트는 `trino` 데이터 서비스의 서명된 정책으로 답합니다. Trino 470~478은 Java 23 또는 24에서, Trino 479 이상은 Java 25에서 실행되므로 배포판은 의존성을 내부에 재배치한 JAR 두 개를 제공합니다.

| Trino | JAR |
| --- | --- |
| 470~478 | `agents/trino/470/grantforge-agent-trino-470-<version>.jar` |
| 479 이상 | `agents/trino/479/grantforge-agent-trino-479-<version>.jar` |

저장소의 Testcontainers 통합 테스트는 첫 번째 JAR을 Trino `470`과 `476`에서, 두 번째 JAR을 `479`와 `484`에서 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다. 서비스 플러그인은 [Trino](/ko/plugins/trino/)를 참조하세요.

## 동작 방식

- **모든 검사는 그것이 가리키는 각 범위에서 결정됩니다.** 카탈로그, 스키마, 테이블의 권한은 그 안에 적용되므로, 에이전트는 컬럼을 그 카탈로그, 스키마, 테이블, 그리고 컬럼 자체에서 결정합니다. 어느 범위에서든 거부되면 거부되고, 어느 범위에서든 허용되면 허용됩니다.
- **목록에서는 쓸 수 없는 것이 빠집니다.** `SHOW CATALOGS`, `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW FUNCTIONS`, `information_schema`, 드라이버 메타데이터는 사용자가 쓸 수 있거나 그 안에서 무언가를 쓸 수 있는 것만 보여 줍니다.
- **데이터를 설명하는 것은 열려 있습니다.** 모든 카탈로그의 `information_schema`, JDBC 드라이버와 BI 도구가 읽는 `system` 카탈로그의 `jdbc`와 `metadata` 스키마, Trino 내장 함수에는 정책이 필요 없으며, 그 행은 목록처럼 필터링됩니다.
- **행을 필터링하고 컬럼을 마스킹합니다.** 행 필터 정책은 테이블을 읽는 모든 쿼리에 적용되며, 마스크는 컬럼 유형에 맞춰져 쿼리를 깨뜨리지 않습니다. 텍스트 마스크는 `varchar`와 `char` 컬럼에 적용되어 컬럼 유형으로 다시 변환되고, 연도 마스크는 날짜와 타임스탬프에 적용되며, 사용자 정의 표현식은 그 유형으로 변환되고, 텍스트 마스크가 가리키는 그 밖의 컬럼은 해당 유형의 `NULL`로 읽힙니다.
- **사용자 가장과 다른 사람의 쿼리는 정책을 따릅니다.** 다른 사용자로 쿼리를 실행하려면 그 사용자에 대한 `impersonate`가, 다른 사용자의 쿼리를 보거나 종료하려면 그 사용자에 대한 `view_query` 또는 `kill_query`가 필요합니다. 누구나 쿼리를 실행하고 자신의 쿼리를 보고 종료할 수 있습니다.
- **거부는 이유를 알려 줍니다.** 클라이언트는 `Access Denied: alice may not select hive.sales.orders.card: GrantForge policy 6 denies it`, 또는 허용하는 정책이 없다는 것, 아직 정책을 받지 못했다는 것을 받습니다.
- **결정을 보관하고 접근을 묶습니다.** 현재 스냅샷의 결정을 기본 100,000개, 10초 동안 보관하며, 새 스냅샷은 처음부터 시작합니다. 시간 창(기본 10초) 안의 동일한 허용 접근은 횟수를 담은 하나의 접근 이벤트로 보고되고, 거부는 즉시 하나씩 보고됩니다. 각 이벤트에는 쿼리가 기록됩니다.
- **권한은 GrantForge가 관리합니다.** 정책은 GrantForge에서 관리되므로 Trino의 `GRANT`, `REVOKE`, `DENY`와 역할 문은 거부됩니다.

| 문 | 필요한 접근 |
| --- | --- |
| `SELECT` | 읽는 각 컬럼의 `select`. `count(*)`처럼 컬럼을 읽지 않는 쿼리는 테이블의 `select` |
| `INSERT`, `DELETE`, `TRUNCATE` | `insert`; 나머지 둘은 `delete` |
| `UPDATE` | 쓰는 각 컬럼의 `update` |
| `CREATE SCHEMA`, `CREATE TABLE`, `CREATE VIEW`, `CREATE MATERIALIZED VIEW` | 만드는 대상의 `create` |
| 스키마, 테이블, 뷰, 구체화된 뷰의 `DROP` | 대상의 `drop` |
| 테이블, 뷰, 컬럼의 `ALTER`, `COMMENT`, `SET PROPERTIES`, `SET AUTHORIZATION`, `ALTER TABLE ... EXECUTE` | 대상의 `alter` |
| 스키마, 테이블, 뷰, 구체화된 뷰의 이름 변경 | 대상의 `alter`와 새 이름의 `create` |
| `REFRESH MATERIALIZED VIEW` | 대상의 `insert` |
| 소유자 권한으로 실행되는 뷰 | 소유자에게 읽는 컬럼의 `select`와 함수의 `execute`가 필요 |
| `SHOW SCHEMAS`, `SHOW TABLES`, `SHOW COLUMNS`, `SHOW FUNCTIONS`, `SHOW CREATE` | 대상 또는 그 안에 대한 임의의 접근 |
| `CREATE CATALOG`, `DROP CATALOG` | 카탈로그의 `create` 또는 `drop` |
| 함수 호출; `CREATE FUNCTION`, `DROP FUNCTION` | `execute`; 함수의 `create_function`, `drop_function` |
| `CALL` | 프로시저의 `execute` |
| `SET SESSION` | 카탈로그 또는 시스템 세션 속성의 `set` |
| 다른 사용자로 실행, 다른 사람의 쿼리 보기·종료 | 사용자에 대한 `impersonate`, `view_query`, `kill_query` |
| 시스템 정보 읽기·쓰기: Trino REST API와 웹 UI의 노드, 통계, 스레드 덤프 | `system_information`의 `read` 또는 `write` |

Trino 479부터 두 번째 JAR은 테이블 브랜치도 검사합니다. 브랜치를 나열하려면 테이블을 볼 수 있어야 하고, 브랜치를 만들거나 삭제하거나 fast-forward하려면 테이블의 `alter`가 필요합니다. 뷰를 새로 고치거나 구체화된 뷰의 소유자나 주석을 바꾸려면 대상의 `alter`가 필요합니다.

## 배포

1. `trino` 데이터 서비스를 추가하고 정책을 구성합니다. **데이터 권한 → 에이전트**를 열고 서비스를 선택해 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되므로 Trino 계정만 읽을 수 있는 파일에 보관하세요.
2. coordinator 버전에 맞는 JAR을 Trino 플러그인 디렉터리 안의 별도 디렉터리(예: `/usr/lib/trino/plugin/grantforge/`)에 복사합니다. 접근은 coordinator만 검사하며 worker에는 아무것도 필요 없습니다.
3. coordinator에 `etc/access-control.properties`를 만들고 예시의 주소, 경로, 인스턴스 이름을 바꿉니다.

   ```properties
   access-control.name=grantforge
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/trino/grantforge.token
   grantforge.instance=coordinator-1
   grantforge.cache.dir=/var/lib/trino/grantforge-cache
   ```

   캐시 디렉터리는 Trino 계정이 쓸 수 있어야 합니다. GrantForge는 Trino의 파일 기반 또는 기타 접근 제어와 나란히 실행되는 것이 아니라 이를 대체합니다.
4. coordinator를 다시 시작합니다. 에이전트가 첫 스냅샷을 받기 전에는 데이터를 설명하는 것을 제외한 쿼리가 거부됩니다.
5. **기존 규칙을 먼저 옮기세요:** Trino 파일 기반 접근 제어의 규칙은 더 이상 적용되지 않습니다. 미리 해당 정책을 만드세요.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 coordinator의 이름, 서명된 스냅샷과 보류 이벤트용 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사하세요. 생략하면 첫 연결 시 서버에서 가져옴 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결·읽기 시간 제한, 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 묻는 주기, 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 간격(기본값 `5000` 밀리초), 스풀 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 동일한 허용 접근을 한 이벤트로 묶는 시간, 기본값 `10000`, 최대 `3600000` 밀리초. `0`은 하나씩 보고 |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | 보관할 최대 결정 수, 기본값 `100000`, 최대 `10000000`, `0`은 보관 안 함; 보관 시간, 기본값 `10000`, `1..3600000` 밀리초 |

## 사용자, 그룹과 역할

정책은 Trino가 인증과 사용자 매핑을 거쳐 주는 쿼리의 세션 사용자를 가리킵니다. 그룹은 Trino 그룹 공급자가 사용자에게 주는 그룹과 GrantForge가 사용자를 넣은 그룹이며, 역할은 GrantForge가 사용자에게 준 역할입니다.

## 결정과 제한 사항

- 쓸 수 있는 스냅샷이 없으면 데이터를 설명하는 것, 쿼리 실행, 자신의 쿼리를 제외한 모든 것이 거부됩니다. 사용하지 않는 서비스는 접근 제어가 없을 때처럼 Trino를 검사하지 않지만, Trino의 권한·역할 문은 그래도 거부됩니다.
- 유형이 `trino`가 아닌 서비스에 묶인 토큰은 모든 것을 거부합니다.
- Trino는 무엇보다 먼저 사용자가 카탈로그를 쓸 수 있는지 묻습니다. 카탈로그에 아무 권한이 없는 사용자는 Trino 자체의 `Cannot access catalog`를 받습니다.
- 사용자가 읽을 수 없는 컬럼을 읽는 `SELECT`는 첫 번째 해당 컬럼을 밝히며 거부되며, 결과에서 컬럼을 빼지 않습니다.
- Trino는 사용자에게 컬럼을 마스킹하는 테이블에 쓰지 않습니다. 마스크가 적용되는 동안 그런 `INSERT`는 실패합니다.
- 마스크는 컬럼 유형으로 변환되므로 길이가 제한된 `varchar`는 마스킹된 값 중 들어가는 만큼만 보여 줍니다. 예를 들어 `varchar(3)`에서는 해시의 3문자만 보입니다.
- 스냅샷이 읽을 수 없는 행 필터나 마스크는 데이터 대신 행을 보여 주지 않거나 컬럼을 `NULL`로 보여 줍니다.
- 결정은 `grantforge.decision.cache.ttl.ms` 동안 보관되므로 정책의 유효 기간이 그만큼 늦게 시작하거나 끝날 수 있습니다.

## 관련 가이드

- [Trino](/ko/plugins/trino/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
