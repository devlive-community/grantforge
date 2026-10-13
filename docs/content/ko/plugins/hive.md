---
title: Apache Hive
description: Hive 서비스 플러그인을 설치·구성하고, 비밀번호나 Kerberos로 로그인하며, 데이터베이스·테이블·컬럼·UDF를 조회하고 접근·마스킹·행 필터 정책을 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Hive 플러그인은 HiveServer2를 위해 데이터베이스, 테이블, 컬럼, 영구 UDF 조회와 접근·마스킹·행 필터 정책을 제공하며, Apache Ranger의 Hive 서비스를 모델로 합니다. 플러그인 ID와 서비스 타입은 모두 `hive`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, HiveServer2에 설치하는 [Hive 에이전트](/ko/external/hive-agent/)가 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 HiveServer2 `3.1.3`, `4.0.1`, `4.1.0`을 다루며 `4.0.1`에 대한 Kerberos 로그인도 포함합니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/hive`에 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `hive`가 활성화되어 있는지 확인하고, 플러그인 디렉터리를 갱신한 뒤에는 다시 스캔하세요. 플러그인은 별도의 클래스 로더에서 Hive 자체 JDBC 드라이버(`hive-jdbc` 4.0.1 standalone)로 HiveServer2와 통신하므로 HiveServer2 3.1과 4.x 모두에서 동작합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 Apache Hive(`hive`)를 선택합니다.
2. HiveServer2의 JDBC URL을 입력합니다. 예: `jdbc:hive2://hiveserver2.example.com:10000/default`. ZooKeeper 서비스 검색도 지원합니다: `jdbc:hive2://zk1:2181,zk2:2181/;serviceDiscoveryMode=zooKeeper;zooKeeperNamespace=hiveserver2`.
3. 조회 사용자의 로그인 방식을 선택하고 사용자를 입력한 뒤 연결을 테스트하고 서비스를 저장합니다. 테스트는 조회와 같은 방식으로 로그인하여 데이터베이스 목록을 가져옵니다.

조회 사용자는 GrantForge가 리소스를 찾는 데만 쓰이며, 쿼리를 실행하는 사용자와는 별개입니다. 연결 테스트가 성공해도 쿼리가 이미 정책의 적용을 받는다는 뜻은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `jdbc.url` | 필수 HiveServer2 URL `jdbc:hive2://호스트/데이터베이스;세션 변수`. TLS(`;ssl=true;sslTrustStore=…`), HTTP 전송, Kerberos에서는 서버 주체(`;principal=hive/_HOST@EXAMPLE.COM`)를 포함할 수 있습니다 |
| `authentication` | `password`(기본값): LDAP, PAM 또는 인증 없음을 위한 사용자와 비밀번호; `kerberos`: keytab 또는 비밀번호를 가진 주체 |
| `username` | 필수 조회 사용자. Kerberos에서는 `grantforge@EXAMPLE.COM` 같은 전체 주체 |
| `password` | 사용자의 비밀번호, keytab이 없으면 주체의 Kerberos 비밀번호. 암호화되어 저장됩니다 |
| `keytab` | Kerberos: 주체 keytab의 경로. **GrantForge 서버**에서 읽을 수 있어야 합니다 |
| `kerberos.kdc` | Kerberos: 주체 영역의 KDC, `host[:port]`를 쉼표로 구분. 비우면 서버의 `krb5.conf`를 사용합니다 |
| `read.timeout.ms` | 읽기 시간 제한, 기본값 `30000`, 범위 `1..600000` |

URL에는 자격 증명이나 GrantForge 서버에서 파일·토큰을 읽는 내용을 넣을 수 없습니다. `user`, `password`, `initFile`, `kerberosAuthType`, `delegationToken`, `storePasswordPath`, `jwt`, `browser…` 변수는 대소문자와 관계없이 거부됩니다. `auth`는 `noSasl`만 허용됩니다. Kerberos 서비스는 URL에 `principal`이 있어야 하고, 비밀번호 서비스에는 있으면 안 됩니다.

### Kerberos

`kerberos`를 선택하면 GrantForge가 keytab 또는 비밀번호로 주체를 직접 로그인시키고 그 주체로 연결을 엽니다. GrantForge 서버에 다른 Kerberos 설정은 필요 없습니다. `kerberos.kdc`에 KDC를 입력한 영역은 HDFS 플러그인과 같은 방식으로 GrantForge 서버 전체에 선언되므로, 여러 Hive·HDFS 서비스가 서로 다른 영역을 함께 사용할 수 있습니다.

## 리소스와 접근 유형

| 레벨 | 매칭 | 참고 |
| --- | --- | --- |
| `database` | 와일드카드, 대소문자 무시 | 정책이 여기서 끝날 수 있습니다 |
| `table` | 와일드카드, 대소문자 무시 | 데이터베이스 아래. 정책이 여기서 끝날 수 있습니다 |
| `column` | 와일드카드, 대소문자 무시 | 테이블 아래 |
| `udf` | 와일드카드, 대소문자 무시 | 데이터베이스 아래의 영구 함수 |
| `url` | 경로, 대소문자 구분, 재귀 | 문이 읽거나 쓰는 데이터 위치(예: `hdfs://nn:8020/landing`). 접근 유형 `read`, `write` |
| `hiveservice` | 와일드카드 | HiveServer2 자체. 접근 유형 `serviceadmin` |
| `global` | 와일드카드 | 임시 함수와 추가된 파일. 접근 유형 `tempudfadmin` |

접근 유형은 Hive와 같습니다: `select`, `update`, `create`, `drop`, `alter`, `index`, `lock`, `read`, `write`, `repladmin`, `serviceadmin`, `tempudfadmin`, `refresh`, `all`. Ranger와 마찬가지로 `all`은 `tempudfadmin`을 제외한 모든 유형을 포함합니다. `tempudfadmin`은 사용자가 HiveServer2 안에서 임의의 코드를 실행할 수 있게 하기 때문입니다.

정책 값에서 `*`는 임의 개수의 문자, `?`는 한 문자와 일치합니다. 정책 편집기는 접두사로 이름을 제안합니다: 데이터베이스, 정책이 지정한 데이터베이스의 테이블과 UDF, 그 테이블의 컬럼.

## 마스킹

마스킹 정책은 컬럼에 적용됩니다. 각 마스크 유형은 Hive의 마스킹 함수이며 `{col}`이 컬럼을 나타냅니다.

| 마스크 유형 | 표시 |
| --- | --- |
| `mask` | 대문자는 `X`, 소문자는 `x`, 숫자는 `n` |
| `mask_show_last_4` | 마지막 네 글자만 |
| `mask_show_first_4` | 처음 네 글자만 |
| `mask_hash` | SHA-256 해시 |
| `mask_null` | `NULL` |
| `mask_none` | 값 그대로 |
| `mask_date_show_year` | 날짜가 속한 해의 1월 1일 |
| `custom` | `{col}`을 컬럼으로 쓰는 직접 작성한 Hive 식. 예: `concat('****', substr({col}, -4))` |

예를 들어 `mask_show_last_4`는 `4111111111111111`을 `xxxxxxxxxxxx1111`로 표시합니다.

## 행 필터

행 필터 정책은 테이블에 적용됩니다. 필터는 `region = 'EU'`처럼 테이블 컬럼에 대한 Hive 조건이며, 쿼리는 조건을 만족하는 행만 봅니다.

마스크와 행 필터는 접근 권한을 부여하지 않습니다. 별도의 접근 정책이 쿼리를 허용해야 합니다.

## 정책 예시

Hive에 `id`, `region`, `card` 컬럼이 있는 `sales.orders` 테이블이 있다고 가정합니다. 같은 데이터 서비스에서 사용자 `alice`에 대해 활성화된 정책 세 개를 만듭니다.

| 정책 종류 | 리소스 | 규칙 |
| --- | --- | --- |
| 접근 | `database=sales`, `table=orders`, `column=*` | 사용자 `alice`에게 `select` 허용 |
| 마스킹 | `database=sales`, `table=orders`, `column=card` | 사용자 `alice`에게 `mask_show_last_4` 사용 |
| 행 필터 | `database=sales`, `table=orders` | 사용자 `alice`에게 `region = 'EU'` 사용 |

에이전트가 새 스냅숏을 적용하면 `alice`는 `sales.orders`의 EU 행만 보고, 각 카드는 마지막 네 글자를 제외하고 마스킹됩니다. `SHOW DATABASES`는 그녀에게 `sales`를 보여 주고, 다른 데이터베이스는 접근을 거부합니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 마스크와 행 필터를 포함해 정책을 적용하려면 모든 HiveServer2에 Hive 에이전트가 필요합니다. 배포 방법과 검사 범위는 [Hive 에이전트](/ko/external/hive-agent/)를 참고하세요.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [Hive 에이전트](/ko/external/hive-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
