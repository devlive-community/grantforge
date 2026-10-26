---
title: PostgreSQL
description: PostgreSQL 서비스 플러그인을 설치·구성하고, 하나 또는 여러 서버에 연결하며, 비밀번호나 TLS 클라이언트 인증서로 로그인하고, 데이터베이스·스키마·테이블·컬럼을 조회하며, 컬럼과 행 단위까지 누가 무엇을 읽고 쓰고 만들 수 있는지 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

PostgreSQL 플러그인은 데이터베이스, 스키마, 테이블, 컬럼을 조회하고, PostgreSQL 자체의 방식으로 접근 정책을 관리합니다: 컬럼 읽기·삽입·수정, 테이블의 행 삭제·비우기와 트리거 생성, 스키마 안의 객체 생성, 데이터베이스 안의 스키마와 임시 테이블 생성이며, 테이블의 행 필터도 지원합니다. 플러그인 ID와 서비스 타입은 모두 `postgresql`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, 서버 옆에서 실행되는 [PostgreSQL 에이전트](/ko/external/postgresql-agent/)가 PostgreSQL 자체의 권한과 행 보안 정책으로 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 PostgreSQL이 지원하는 버전인 PostgreSQL `15`, `16`, `17`, `18`을 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/postgresql`에 이 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `postgresql`이 활성화되어 있는지 확인하고, 플러그인 디렉터리를 갱신한 뒤에는 다시 스캔하세요. 플러그인은 자체 클래스 로더 안에서 PostgreSQL의 JDBC 드라이버로 서버와 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 PostgreSQL(`postgresql`)을 선택합니다.
2. 서버 하나 또는 여러 서버를 `host:port`로 입력합니다.
3. 조회 사용자의 로그인 방식을 고르고 사용자 이름과 자격 증명을 입력한 뒤, 연결을 테스트하고 서비스를 저장합니다. 테스트는 로그인하여 서버를 설명합니다: PostgreSQL 버전, 로그인한 사용자, 서버에 있는 데이터베이스 수입니다.

조회 사용자는 GrantForge가 리소스를 찾게 해 주며, 읽고 쓰는 사용자와는 별개입니다. 연결 테스트가 성공했다고 해서 접근이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `postgresql.hosts` | 필수: `host:port` 형식의 서버, 예: `postgres1.example.com:5432`. 여러 개는 쉼표로 구분하며, 하나에 연결할 수 없으면 차례로 시도합니다. 포트 기본값은 `5432`입니다 |
| `postgresql.database` | 데이터베이스 목록을 얻기 위해 조회 사용자가 먼저 로그인하는 데이터베이스, 기본값 `postgres` |
| `authentication` | `password`(기본값) 또는 `certificate`: PostgreSQL의 `cert` 인증이 사용자에 대응시키는 TLS 클라이언트 인증서 |
| `username` | 필수: 조회 사용자 |
| `password` | password: 조회 사용자의 비밀번호, 암호화되어 저장됨 |
| `ssl.mode` | libpq의 `sslmode`가 부르는 TLS 방식: `disable`, `allow`, `prefer`(기본값), `require`, `verify-ca`, 또는 서버 이름까지 확인하는 `verify-full` |
| `ssl.root.certificate` | verify-ca와 verify-full: **GrantForge 서버**에 있는, 서버를 검증하는 PEM CA 인증서. 비우면 JVM의 것을 사용합니다 |
| `ssl.client.certificate`, `ssl.client.key` | certificate: GrantForge 서버에 있는 PEM 클라이언트 인증서와 그 PKCS #8 개인 키(PEM 또는 DER). 인증서 로그인에는 `require`나 검증 방식이 필요하고, 검증 방식과 함께 쓰려면 `ssl.root.certificate`도 필요합니다 |
| `timeout` | 연결과 쿼리에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |

한 로그인 방식의 설정은 다른 방식에서 거부되므로, 서비스는 어떻게 로그인하는지 정확히 밝힙니다. PostgreSQL 드라이버는 소유자 외에 다른 사람이 읽을 수 있는 개인 키 파일을 거부합니다. 거부된 로그인, 존재하지 않는 데이터베이스, 연결할 수 없거나 인증서를 검증할 수 없는 서버는 그대로 보고됩니다.

### 조회 사용자에게 보이는 것

PostgreSQL의 카탈로그는 모든 사용자에게 데이터베이스 이름을 보여 주고, 연결할 수 있는 각 데이터베이스에서는 데이터를 읽지 않고도 스키마, 테이블, 컬럼의 이름을 보여 줍니다. 조회 사용자에게는 정책을 작성할 데이터베이스에 대한 `CONNECT`만 있으면 되며, 회수되지 않았다면 PUBLIC이 이를 가지고 있습니다. 스키마, 테이블, 컬럼 조회는 정책이 지정한 각 데이터베이스(최대 20개)에 로그인하고, 조회 사용자가 연결할 수 없는 데이터베이스는 건너뜁니다. PostgreSQL 자체의 스키마, 즉 `pg_`로 시작하는 스키마와 `information_schema`는 제외됩니다: 정책은 이를 결정하지 않습니다.

## 리소스와 접근 유형

| 레벨 | 매칭 | 접근 유형 |
| --- | --- | --- |
| `database` | 와일드카드, 대소문자 구분; 데이터베이스 이름 | `create_schema`, `temporary` |
| `schema` | 와일드카드, 대소문자 구분; 데이터베이스 아래 | `create` |
| `table` | 와일드카드, 대소문자 구분; 스키마 아래의 테이블, 파티션 테이블, 뷰, 구체화된 뷰, 외부 테이블 | `delete`, `truncate`, `trigger` |
| `column` | 와일드카드, 대소문자 구분; 테이블 아래 | `select`, `insert`, `update` |

| 접근 유형 | PostgreSQL의 권한 |
| --- | --- |
| `select`, `insert`, `update` | 컬럼 또는 테이블 전체에 대한 `SELECT`, `INSERT`, `UPDATE` |
| `delete`, `truncate`, `trigger` | 테이블에 대한 `DELETE`, `TRUNCATE`, `TRIGGER` |
| `create` | 스키마에 대한 `CREATE`: 그 안의 테이블, 뷰, 시퀀스, 함수와 그 밖의 객체 |
| `create_schema` | 데이터베이스에 대한 `CREATE`: 그 안의 스키마 |
| `temporary` | 데이터베이스에 대한 `TEMPORARY` |

읽기, 삽입, 수정은 PostgreSQL이 부여하는 방식대로 컬럼별로 결정됩니다: `select`, `insert`, `update` 정책은 컬럼을 지정하며 `*`는 모든 컬럼입니다. 따라서 정책은 테이블을 허용하면서 그중 한 컬럼을 거부할 수 있고, 사용자는 나머지 모든 컬럼을 읽을 수 있습니다. 정책 값에서 `*`는 임의의 문자열에, `?`는 한 문자에 일치합니다. 정책 편집기는 데이터베이스, 정책이 지정한 데이터베이스의 스키마, 그 테이블과 컬럼을 제안합니다.

정책이 허용하는 것에 닿으려면 PostgreSQL에서 더 필요합니다: 데이터베이스에 대한 `CONNECT`, 스키마에 대한 `USAGE`, 그리고 serial 컬럼에 삽입하려면 그 시퀀스에 대한 `USAGE`입니다. 이것들은 별도의 접근 유형이 아니며, 에이전트가 이를 필요로 하는 권한과 함께 부여합니다. PostgreSQL에는 컬럼 마스킹이 없으므로 이 서비스 타입은 마스킹을 제공하지 않습니다.

### 행 필터

테이블의 행 필터 정책은 조건이 성립하는 행만 사용자에게 보여 줍니다. 조건은 행 보안 정책의 `USING`이 받는 형태의 PostgreSQL 식으로, 예를 들어 `region = 'EU'`나 `owner = current_user`입니다. 다른 서비스와 마찬가지로 사용자는 자신에게 적용되는 첫 번째 정책의 필터를 봅니다. PostgreSQL은 테이블과 파티션 테이블의 행만 걸러 냅니다: 정책이 어떤 사용자에 대해 행을 거르는 뷰 등은 그 사용자가 전혀 읽을 수 없습니다.

## 정책 예시

서버에 데이터베이스 `sales`와 `hr`가 있고 `sales`에 스키마 `crm`이 있다고 합시다. 같은 데이터 서비스에 다음과 같은 활성화된 정책을 만듭니다:

| 리소스 | 규칙 |
| --- | --- |
| `database=sales`, `schema=crm`, `table=*`, `column=*` | 그룹 `analysts`에 `select` 허용 |
| `database=sales`, `schema=crm`, `table=orders`, `column=card` | 그룹 `analysts`에 `select` 거부 |
| `database=sales`, `schema=crm`, `table=orders`, `column=*` | 사용자 `ingest`에 `insert` 허용 |
| `database=sales`, `schema=team_*` | 그룹 `data-team`에 `create` 허용 |
| `database=sales`, `schema=team_*`, `table=*` | 그룹 `data-team`에 `delete`와 `truncate` 허용 |
| `database=sales`, `schema=team_*`, `table=*`, `column=*` | 그룹 `data-team`에 `select`, `insert`, `update` 허용 |
| `database=sales` | 그룹 `data-team`에 `create_schema` 허용 |
| `database=sales`, `schema=crm`, `table=orders` (행 필터) | 사용자 `emea`에게 `region = 'EMEA'`인 행만 표시 |

에이전트가 새 스냅샷을 적용하면 `analysts`의 구성원은 `orders`의 카드 번호를 제외한 `crm`의 모든 테이블을 읽고, `ingest`는 serial id를 포함해 `orders`에 삽입하되 아무것도 읽지 못하며, `data-team`의 구성원은 스키마를 만들고 이름이 `team_`으로 시작하는 스키마에서 테이블을 만들고 읽고 바꾸고 비울 수 있으며, `emea`는 `orders`를 읽게 하는 정책이 있다면 자기 지역의 행만 봅니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 서버 또는 프라이머리마다 하나씩 PostgreSQL 에이전트가 필요합니다. 배포와 점검은 [PostgreSQL 에이전트](/ko/external/postgresql-agent/)에 있습니다.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [PostgreSQL 에이전트](/ko/external/postgresql-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
