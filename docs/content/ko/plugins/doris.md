---
title: Apache Doris
description: Apache Doris 서비스 플러그인을 설치하고 구성하며, 프런트엔드 하나 또는 여러 개에 연결하고, 비밀번호로 로그인하며, 카탈로그, 데이터베이스, 테이블, 컬럼과 워크로드 그룹을 조회하고, 누가 무엇을 읽고 적재하고 관리할 수 있는지를 컬럼과 행까지, 누가 어느 워크로드 그룹에서 쿼리를 실행할 수 있는지를 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Doris 플러그인은 카탈로그, 데이터베이스, 테이블, 컬럼과 워크로드 그룹을 조회하고, Doris 자체의 방식으로 접근 정책을 관리합니다: 컬럼 읽기, 테이블 적재·변경·삭제와 뷰 정의 보기, 데이터베이스 안에 테이블 생성과 데이터베이스 삭제, 카탈로그 안에 데이터베이스 생성, 그리고 워크로드 그룹에서 쿼리 실행이며, 테이블의 행 필터도 지원합니다. 플러그인 ID와 서비스 타입은 모두 `doris`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, 클러스터 옆에서 실행되는 [Apache Doris 에이전트](/ko/external/doris-agent/)가 Doris 자체의 역할, 권한과 행 정책으로 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 Apache Doris `2.1`, `3.1`, `4.0`, `4.1`을 각각 프런트엔드 하나와 백엔드 하나로 된 클러스터에서 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/doris`에 이 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `doris`가 활성화되어 있는지 확인하고, 플러그인 디렉터리를 갱신한 뒤에는 다시 스캔하세요. 플러그인은 자체 클래스 로더 안에서 MariaDB Connector/J로 프런트엔드의 MySQL 프로토콜과 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 Apache Doris(`doris`)를 선택합니다.
2. 프런트엔드 하나 또는 여러 개의 MySQL 프로토콜 주소를 입력합니다.
3. 조회 사용자의 이름과 비밀번호를 입력하고 연결을 테스트한 뒤 서비스를 저장합니다. 테스트는 로그인하여 클러스터를 설명합니다: Doris 버전, 로그인한 사용자, 그 사용자에게 보이는 카탈로그 수와 내부 카탈로그의 데이터베이스 수입니다. 프런트엔드에만 묻기 때문에 백엔드가 하나도 실행되지 않아도 응답합니다.

조회 사용자는 GrantForge가 리소스를 찾게 해 주며, 읽고 적재하는 사용자와는 별개입니다. 연결 테스트가 성공했다고 해서 접근이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `doris.hosts` | 필수: 프런트엔드의 MySQL 프로토콜 주소, 예: `fe1.example.com:9030`. 여러 개는 쉼표로 구분하며, 하나에 연결할 수 없으면 차례로 시도합니다. 포트를 쓰지 않으면 `9030`입니다 |
| `username` | 필수: 조회 사용자 |
| `password` | 조회 사용자의 비밀번호, 암호화되어 저장됨 |
| `ssl.mode` | `disable`(기본값), 프런트엔드를 확인하지 않고 암호화하는 `trust`, 인증서를 확인하는 `verify-ca`, 이름까지 확인하는 `verify-full`. MySQL 프로토콜에서 TLS를 제공하는 프런트엔드용입니다 |
| `ssl.root.certificate` | verify-ca와 verify-full: 프런트엔드를 검증하는 **GrantForge 서버**의 PEM CA 인증서. 비우면 JVM의 것을 사용합니다 |
| `timeout` | 연결과 문 하나에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |

거부된 로그인, 필요한 권한이 없는 조회 사용자와 연결할 수 없는 프런트엔드는 그대로 보고됩니다.

### 조회 사용자에게 보이는 것

Doris의 `SHOW` 문은 사용자가 어떤 권한이든 가진 카탈로그, 데이터베이스, 테이블과 컬럼만 나열하므로, 조회 결과는 조회 사용자에게 보이는 것입니다. 정책을 작성할 대상에 `SELECT_PRIV`를 주세요. 예: `GRANT SELECT_PRIV ON *.*.* TO 'grantforge_lookup'@'%'`. `SHOW WORKLOAD GROUPS`는 보이는 그룹을 나열합니다. Doris 자체의 데이터베이스 `information_schema`, `mysql`, `__internal_schema`는 제외됩니다: 정책이 이들을 결정하지 않습니다.

## 리소스와 접근 유형

| 수준 | 일치 방식 | 접근 유형 |
| --- | --- | --- |
| `catalog` | 와일드카드, 대소문자 구분. 내부 카탈로그 또는 외부 카탈로그 | `create_database` |
| `database` | 와일드카드, 대소문자 구분. 카탈로그 아래 | `create`, `drop_database` |
| `table` | 와일드카드, 대소문자 구분. 데이터베이스 아래의 테이블 또는 뷰 | `load`, `alter`, `drop`, `show_view` |
| `column` | 와일드카드, Doris가 컬럼 이름을 비교하는 대로 대소문자 구분 안 함. 테이블 아래 | `select` |
| `workload_group` | 와일드카드, 대소문자 구분. 그룹의 이름 | `usage` |

| 접근 유형 | Doris의 권한 |
| --- | --- |
| `select` | 컬럼, 또는 테이블·데이터베이스·카탈로그 전체에 대한 `SELECT_PRIV` |
| `load` | `LOAD_PRIV`: 적재, `INSERT`, `UPDATE`, `DELETE`와 `TRUNCATE` |
| `alter`, `drop` | 테이블에 대한 `ALTER_PRIV`, `DROP_PRIV` |
| `show_view` | `SHOW_VIEW_PRIV`: `SHOW CREATE VIEW` |
| `create` | 데이터베이스에 대한 `CREATE_PRIV`: 데이터베이스 생성과 그 안의 테이블·뷰 생성 |
| `drop_database` | 데이터베이스에 대한 `DROP_PRIV` |
| `create_database` | 카탈로그에 대한 `CREATE_PRIV` |
| `usage` | 워크로드 그룹에 대한 `USAGE_PRIV` |

읽기는 Doris가 부여하는 대로 컬럼마다 결정됩니다: `select` 정책은 컬럼을 지정하며, `*`는 모든 컬럼입니다. 그래서 정책은 테이블을 허용하면서 그 안의 컬럼 하나를 거부할 수 있고, 사용자는 나머지 모든 컬럼을 읽습니다. 정책 값에서 `*`는 임의의 문자열에, `?`는 한 글자에 일치합니다. 정책 편집기는 카탈로그, 정책이 지정한 카탈로그의 데이터베이스, 그 테이블과 컬럼, 그리고 워크로드 그룹을 제안합니다.

Doris의 데이터베이스 권한은 그 안의 모든 테이블에도, 카탈로그 권한은 그 안의 모든 데이터베이스에도 미칩니다. 그래서 에이전트는 정책이 카탈로그의 모든 데이터베이스에서 테이블 생성도 허용할 때만 `create_database`를, 데이터베이스의 모든 테이블 삭제도 허용할 때만 `drop_database`를 부여합니다. 그렇지 않으면 정책이 허용하는 것보다 더 주는 대신 권한을 보류하고 그 사실을 알립니다. `ADMIN_PRIV`, `GRANT_PRIV`, `NODE_PRIV`는 접근 유형이 아니며, 어떤 정책도 이들을 주지 않습니다.

Doris는 Apache Ranger를 통해서만 값을 마스킹하므로, 이 서비스 타입은 마스킹을 제공하지 않습니다.

### 행 필터

테이블의 행 필터 정책은 조건이 참인 행만 사용자에게 보여 줍니다. 조건은 행 정책이 받는 Doris 술어로, 예를 들어 `region = 'EU'`입니다. 에이전트는 먼저 테이블에서 그것을 실행해 보며, 거기서 실행할 수 없는 필터는 행을 하나도 보여 주지 않습니다. 다른 서비스와 마찬가지로 사용자는 자신에게 적용되는 첫 번째 정책의 필터를 받습니다.

## 정책 예시

클러스터의 내부 카탈로그에 데이터베이스 `sales`와 `hr`가 있고 워크로드 그룹 `etl`이 있다고 합시다. 같은 데이터 서비스에 다음과 같은 활성화된 정책을 만듭니다:

| 리소스 | 규칙 |
| --- | --- |
| `catalog=internal`, `database=sales`, `table=*`, `column=*` | 그룹 `analysts`에 `select` 허용 |
| `catalog=internal`, `database=sales`, `table=orders`, `column=card` | 그룹 `analysts`에 `select` 거부 |
| `catalog=internal`, `database=sales`, `table=orders` | 사용자 `ingest`에 `load` 허용 |
| `catalog=internal`, `database=team_*` | 그룹 `data-team`에 `create` 허용 |
| `catalog=internal`, `database=team_*`, `table=*` | 그룹 `data-team`에 `load`, `alter`, `drop` 허용 |
| `workload_group=etl` | 사용자 `ingest`와 그룹 `data-team`에 `usage` 허용 |
| `catalog=internal`, `database=sales`, `table=orders` (행 필터) | 사용자 `emea`에게 `region = 'EMEA'`인 행만 표시 |

에이전트가 새 스냅샷을 적용하면 `analysts`의 구성원은 `orders`의 카드 번호를 제외한 `sales`의 모든 테이블을 읽고, `ingest`는 `orders`에 적재하며 그 적재를 `etl`에서 실행할 수 있고, `data-team`의 구성원은 관리자가 만든 `team_` 데이터베이스에 테이블을 만들어 적재·변경·삭제하며, `emea`는 정책이 `orders` 읽기를 허용한다면 자기 지역의 행만 봅니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 클러스터마다 하나씩 Apache Doris 에이전트가 필요합니다. 배포와 점검은 [Apache Doris 에이전트](/ko/external/doris-agent/)에 있습니다.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [Apache Doris 에이전트](/ko/external/doris-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
