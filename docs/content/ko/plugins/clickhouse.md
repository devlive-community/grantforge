---
title: ClickHouse
description: ClickHouse 서비스 플러그인을 설치·구성하고, 하나 또는 여러 서버에 연결하며, 비밀번호나 TLS 클라이언트 인증서로 로그인하고, 데이터베이스·테이블·컬럼을 조회하며, 누가 무엇을 읽고 쓰고 관리할 수 있는지 컬럼과 행 단위까지 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

ClickHouse 플러그인은 데이터베이스, 테이블과 컬럼을 조회하고 ClickHouse 자체의 방식으로 접근 정책을 관리합니다: 컬럼 읽기와 쓰기, 테이블 수정·삭제·비우기·최적화, 데이터베이스 안의 테이블 생성과 데이터베이스 삭제, 그리고 서버의 데이터베이스 생성, `KILL QUERY`와 `SYSTEM` 문이며, 테이블의 행 필터도 다룹니다. 플러그인 ID와 서비스 타입은 모두 `clickhouse`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되고, 서버 옆에서 실행되는 서비스인 [ClickHouse 에이전트](/ko/external/clickhouse-agent/)가 ClickHouse 자체의 권한과 행 정책으로 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 최근 2년의 장기 지원 릴리스인 ClickHouse `24.8`, `25.3`, `25.8`, `26.3`, `26.8`을 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/clickhouse`에 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `clickhouse`가 활성화되어 있는지 확인하고, 업데이트 후에는 플러그인 디렉터리를 다시 스캔합니다. 플러그인은 자체 클래스 로더 안에서 ClickHouse 자체의 Java 클라이언트로 HTTP를 통해 서버와 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 ClickHouse(`clickhouse`)를 선택합니다.
2. 서버 하나 또는 여러 서버의 HTTP 인터페이스 URL을 입력합니다.
3. 조회 사용자의 로그인 방식을 선택하고 사용자 이름과 자격 증명을 입력한 뒤 연결을 테스트하고 서비스를 저장합니다. 테스트는 로그인하여 서버를 설명합니다: ClickHouse 버전, 로그인한 사용자, 그 사용자가 보는 데이터베이스 수입니다.

조회 사용자는 GrantForge가 리소스를 찾게 해 주며, 읽고 쓰는 사용자와는 별개입니다. 연결 테스트가 성공했다고 해서 접근이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `clickhouse.urls` | 필수: 서버의 HTTP 인터페이스, 예: `https://clickhouse1.example.com:8443`. 여러 개는 쉼표로 구분하며, 하나에 연결할 수 없으면 차례로 시도합니다 |
| `authentication` | `password`(기본값) 또는 `certificate`: ClickHouse가 사용자에 대응시키는 TLS 클라이언트 인증서(`IDENTIFIED WITH ssl_certificate`) |
| `username` | 필수: 조회 사용자 |
| `password` | password: 조회 사용자의 비밀번호. 암호화되어 저장됩니다 |
| `ssl.root.certificate` | https: **GrantForge 서버**에서 서버들을 검증하는 PEM CA 인증서. 비우면 JVM의 것을 사용합니다 |
| `ssl.client.certificate`, `ssl.client.key` | certificate: GrantForge 서버의 PEM 클라이언트 인증서와 그 개인 키. 인증서 로그인에는 `https://` 서버가 필요합니다 |
| `timeout` | 요청에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |

한 로그인 방식의 설정은 다른 방식에서 거부되므로, 서비스는 어떻게 로그인하는지 정확히 밝힙니다. 거부된 로그인, 읽기 전용 사용자, 연결할 수 없거나 인증서를 검증할 수 없는 서버, 그리고 프록시처럼 ClickHouse가 아닌 것이 대신 응답하는 경우는 그대로 보고됩니다.

### 조회 사용자에게 보이는 것

ClickHouse는 사용자가 어떤 권한이라도 가진 데이터베이스, 테이블과 컬럼만 보여 줍니다. 정책을 작성할 대상에 대해 조회 사용자에게 `SHOW`를 주세요. 예를 들어 `GRANT SHOW ON *.* TO grantforge_lookup`이면 데이터를 읽지 않고 이름만 봅니다. ClickHouse 자체의 데이터베이스 `system`과 `information_schema`는 제외됩니다: 정책이 이들을 결정하지 않습니다.

## 리소스와 접근 유형

| 수준 | 일치 방식 | 접근 유형 |
| --- | --- | --- |
| `database` | 와일드카드, 대소문자 구분; 데이터베이스 이름 | `create`, `drop_database` |
| `table` | 와일드카드, 대소문자 구분; 데이터베이스 아래 | `alter`, `drop`, `truncate`, `optimize` |
| `column` | 와일드카드, 대소문자 구분; 테이블 아래 | `select`, `insert` |
| `server` | 유일한 값 `*` | `create_database`, `kill_query`, `system` |

| 접근 유형 | ClickHouse 권한 |
| --- | --- |
| `select`, `insert` | 컬럼에 대한 `SELECT`, `INSERT` |
| `alter` | `ALTER`: `ALTER UPDATE`와 `ALTER DELETE`를 포함한 테이블의 모든 변경 |
| `drop` | `DROP TABLE`, `DROP VIEW`, `DROP DICTIONARY` |
| `truncate`, `optimize` | `TRUNCATE`, `OPTIMIZE` |
| `create` | 데이터베이스 안의 `CREATE TABLE`, `CREATE VIEW`, `CREATE DICTIONARY` |
| `drop_database` | `DROP DATABASE` |
| `create_database`, `kill_query`, `system` | `CREATE DATABASE`, `KILL QUERY`, `SYSTEM`: 모든 `SYSTEM` 문 |

읽기와 쓰기는 ClickHouse가 권한을 주는 방식대로 컬럼 단위로 판정됩니다: `select`나 `insert` 정책은 컬럼을 지정하며 `*`는 모든 컬럼입니다. 따라서 정책은 테이블을 허용하면서 그중 한 컬럼을 거부할 수 있고, 사용자는 나머지 컬럼을 모두 읽을 수 있습니다. 정책 값에서 `*`는 임의의 문자열, `?`는 한 문자와 일치합니다. 정책 편집기는 데이터베이스, 정책이 지정한 데이터베이스의 테이블과 그 컬럼을 제안합니다.

접근 유형은 `ALL` 같은 ClickHouse 권한 그룹이 아닙니다: 각각은 쓰인 그대로를 뜻하므로 정책은 지정한 것보다 더 많이 주지 않습니다. ClickHouse는 ClickHouse Cloud 밖에서 컬럼 마스킹을 제공하지 않으므로 이 서비스 타입도 제공하지 않습니다.

### 행 필터

테이블의 행 필터 정책은 사용자에게 조건이 성립하는 행만 보여 줍니다. 조건은 행 정책이 받는 형태의 ClickHouse 식이며, 예를 들어 `region = 'EU'`나 `owner = currentUser()`입니다. 다른 서비스와 마찬가지로 사용자는 자신에게 적용되는 첫 번째 정책의 필터를 봅니다.

## 정책 예시

서버에 데이터베이스 `sales`와 `hr`가 있다고 가정합니다. 같은 데이터 서비스에 다음과 같은 활성화된 정책을 만듭니다.

| 리소스 | 규칙 |
| --- | --- |
| `database=sales`, `table=*`, `column=*` | 그룹 `analysts`에 `select` 허용 |
| `database=sales`, `table=orders`, `column=card` | 그룹 `analysts`에 `select` 거부 |
| `database=sales`, `table=orders`, `column=*` | 사용자 `ingest`에 `insert` 허용 |
| `database=team_*` | 그룹 `data-team`에 `create` 허용 |
| `database=team_*`, `table=*` | 그룹 `data-team`에 `alter`, `drop`, `truncate`, `optimize` 허용 |
| `database=team_*`, `table=*`, `column=*` | 그룹 `data-team`에 `select`와 `insert` 허용 |
| `server=*` | 그룹 `data-team`에 `create_database` 허용 |
| `database=sales`, `table=orders`(행 필터) | 사용자 `emea`에게 `region = 'EMEA'`인 행만 표시 |

에이전트가 새 스냅샷을 적용하면 `analysts`의 구성원은 `orders`의 카드 번호를 제외한 `sales`의 모든 테이블을 읽고, `ingest`는 `orders`에 쓰지만 아무것도 읽지 못합니다. `data-team`의 구성원은 데이터베이스를 만들고, 이름이 `team_`으로 시작하는 데이터베이스에 테이블을 만들어 읽고 쓰고 변경합니다. `emea`는 정책이 `orders` 읽기를 허용한다면 자기 지역의 행만 봅니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 서버 또는 클러스터마다 하나의 ClickHouse 에이전트가 필요합니다. 배포와 확인 사항은 [ClickHouse 에이전트](/ko/external/clickhouse-agent/)를 참조하세요.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [ClickHouse 에이전트](/ko/external/clickhouse-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
