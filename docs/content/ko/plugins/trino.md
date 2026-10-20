---
title: Trino
description: Trino 서비스 플러그인을 설치·구성하고, TLS 위에서 비밀번호, JSON Web Token 또는 Kerberos로 로그인하며, 카탈로그·스키마·테이블·컬럼·세션 속성을 조회하고, Trino 자체의 개념으로 접근·행 필터·마스킹 정책을 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Trino 플러그인은 카탈로그, 스키마, 테이블, 컬럼, 세션 속성을 조회하고, Trino가 검사하는 대상인 데이터, 루틴, 세션 속성, 사용자, 시스템 정보에 대한 접근·행 필터·마스킹 정책을 관리합니다. 플러그인 ID와 서비스 타입은 모두 `trino`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, coordinator에 설치한 [Trino 에이전트](/ko/external/trino-agent/)가 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 Trino `470`, `476`, `479`, `484`를 다루며, `470`과 `484`에서는 비밀번호 파일과 JSON Web Token을 사용하는 HTTPS와 Kerberos도 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/trino`에 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `trino`가 활성화되어 있는지 확인하고, 플러그인 디렉터리를 갱신한 뒤에는 다시 스캔하세요. 플러그인은 자체 클래스 로더 안의 Trino JDBC 드라이버(`trino-jdbc` 484)로 coordinator와 통신하며, 이 드라이버는 위의 모든 Trino 버전과 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 Trino(`trino`)를 선택합니다.
2. coordinator의 JDBC URL을 입력합니다. 예: `jdbc:trino://trino.example.com:8443`.
3. 조회 사용자의 로그인 방식을 고르고 입력한 뒤 연결을 테스트하고 서비스를 저장합니다. 테스트는 로그인한 뒤 coordinator의 버전과 사용자가 보는 카탈로그 수를 보여 줍니다.

설정 이름은 드라이버의 이름을 그대로 쓰므로 Trino JDBC 문서와 같이 읽힙니다. 조회 사용자는 GrantForge가 리소스를 찾는 데 쓰이며, 쿼리를 실행하는 사용자와는 별개입니다. 연결 테스트가 성공해도 쿼리가 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `jdbc.url` | 필수: `jdbc:trino://host:port`. 카탈로그와 스키마, 자격 증명 이외의 드라이버 속성(예: `?SSL=true`)을 붙일 수 있음 |
| `authentication` | `none`(기본값): 사용자 이름만; `password`; `jwt`: JSON Web Token; `kerberos`: keytab 또는 비밀번호가 있는 주체 |
| `user` | 필수 조회 사용자. Kerberos에서는 전체 주체, 예: `grantforge@EXAMPLE.COM` |
| `password` | 사용자의 비밀번호. Kerberos에서는 keytab이 없을 때 주체의 비밀번호. 암호화되어 저장됨 |
| `accessToken` | `jwt`: coordinator가 받아들이는 토큰. 암호화되어 저장됨 |
| `keytab` | Kerberos: 주체의 keytab 경로. **GrantForge 서버**에서 읽을 수 있어야 함 |
| `kerberos.kdc` | Kerberos: 주체 realm의 KDC, 쉼표로 구분한 `host[:port]`. 비우면 서버의 `krb5.conf` 사용 |
| `KerberosRemoteServiceName` | Kerberos: coordinator 주체의 첫 부분, 기본값 `trino` |
| `KerberosUseCanonicalHostname` | Kerberos: coordinator 주체가 URL 호스트의 정식 이름을 쓰는지 여부, 기본값 `true`. 주체가 그 이름을 가진 로드 밸런서나 별칭 뒤에서는 끔 |
| `SSL` | TLS 사용 여부, 기본값 `false`. Trino는 TLS로만 사용자를 로그인시키므로 `none` 이외의 모든 로그인에 필요 |
| `SSLTrustStorePath`, `SSLTrustStorePassword`, `SSLTrustStoreType` | TLS: coordinator를 검증하는 트러스트 스토어, 그 비밀번호와 유형(`JKS` 또는 `PKCS12`). 비우면 Java가 신뢰하는 것을 신뢰 |
| `SSLVerification` | TLS: `FULL`(기본값)은 인증서와 호스트 이름, `CA`는 인증서만 검사, `NONE`은 아무것도 검사하지 않으며 트러스트 스토어를 받지 않음 |
| `timeout` | 요청 시간 제한(밀리초), 기본값 `30000`, 범위 `1..600000` |

서비스는 필드가 말하는 그대로입니다. URL에는 자격 증명이나 다른 방식으로 로그인하게 하는 것을 넣을 수 없습니다. `user`, `password`, `accessToken`, `sessionUser`, `extraCredentials`, 외부 및 OAuth 2.0 인증, Kerberos 파일과 위임, 키 스토어와 트러스트 스토어 비밀번호는 거부됩니다. 비밀번호 인증에 토큰을 넣는 것처럼 다른 로그인 방식의 필드도 거부됩니다.

인증서를 검증할 수 없는 coordinator와는 통신하지 않으며, 테스트는 연결할 수 없다고 보고합니다. 거부된 로그인, 권한 부족, 연결할 수 없는 coordinator는 각각 그대로 보고됩니다.

### Kerberos

`kerberos`에서는 GrantForge가 keytab이나 비밀번호로 주체를 로그인시키고, 그 로그인의 자격 증명을 드라이버에 넘깁니다. GrantForge 서버의 다른 부분에는 Kerberos 설정이 필요 없습니다. `kerberos.kdc`에 KDC를 입력한 realm은 HDFS, Hive, Kafka, HBase 플러그인과 같은 방식으로 GrantForge 서버 전체에 선언되므로 여러 서비스가 서로 다른 realm을 나란히 쓸 수 있습니다. 세션 사용자는 Trino의 기본 주체 매핑처럼 realm 앞의 주체 이름입니다.

### 조회 사용자에게 보이는 것

[Trino 에이전트](/ko/external/trino-agent/)가 정책을 적용하면 목록에서 조회 사용자가 쓸 수 없는 것이 빠집니다. 카탈로그 `*`, 또는 정책 편집기가 리소스를 제안해야 하는 카탈로그에서 `select`를 허용하는 정책을 주세요.

## 리소스와 접근 유형

| 레벨 | 일치 방식 | 접근 유형 |
| --- | --- | --- |
| `catalog` | 와일드카드, 대소문자 구분 없음 | `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `all` |
| `schema` | 와일드카드, 대소문자 구분 없음; 카탈로그 아래 | `catalog`와 같음 |
| `table` | 와일드카드, 대소문자 구분 없음; 스키마 아래; 테이블, 뷰, 구체화된 뷰 | `catalog`와 같음 |
| `column` | 와일드카드, 대소문자 구분 없음; 테이블 아래 | `select`, `update` |
| `function` | 와일드카드, 대소문자 구분 없음; 스키마 아래 | `execute`, `create_function`, `drop_function` |
| `procedure` | 와일드카드, 대소문자 구분 없음; 스키마 아래 | `execute` |
| `session_property` | 와일드카드, 대소문자 구분 없음; 카탈로그 아래, 카탈로그 없이 이름 지정 | `set` |
| `system_session_property` | 와일드카드, 대소문자 구분 없음 | `set` |
| `user` | 와일드카드, 대소문자 구분 | `impersonate`, `view_query`, `kill_query` |
| `system_information` | 유일한 값 `*` | `read`, `write` |

`all`은 `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`를 포함합니다.

카탈로그, 스키마, 테이블의 권한은 그 안의 모든 것에 적용됩니다. 스키마 `sales`의 `select`는 그 안의 모든 테이블을 읽고, 카탈로그의 `create`는 그 안에 스키마와 테이블을 만듭니다. 검사는 그것이 가리키는 모든 범위에서 결정되며 어느 범위에서든 거부되면 거부되므로, 정책 하나로 스키마를 허용하면서 그 안 한 테이블의 한 컬럼을 거부할 수 있습니다. 함수, 프로시저, 세션 속성은 이름으로 부여하고 `*`는 스키마나 카탈로그의 전부를 뜻합니다. 함수에는 고유한 접근 유형이 있어 루틴에 대한 부여가 테이블에 대한 부여로 읽히는 일은 없습니다.

정책 값에서 `*`는 임의 개수의 문자, `?`는 한 문자와 일치합니다. 정책 편집기는 카탈로그, 정책이 지정한 카탈로그 안의 스키마, 그 스키마 안의 테이블, 그 테이블 안의 컬럼, 그리고 그 카탈로그와 시스템의 세션 속성을 제안합니다. 함수, 프로시저, 사용자는 제안하지 않습니다.

## 행 필터와 마스크

테이블의 행 필터 정책과 컬럼의 마스킹 정책은 Trino SQL이며, coordinator가 이를 읽는 모든 쿼리에 적용합니다. 행 필터는 `region = 'EU'`처럼 테이블 컬럼에 대한 조건이며, 그 안의 이름은 테이블의 카탈로그와 스키마에서 해석됩니다.

| 마스크 유형 | 표시 |
| --- | --- |
| `mask` | 문자는 `X` 또는 `x`, 숫자는 `n` |
| `mask_show_last_4`, `mask_show_first_4` | 마지막 또는 처음 네 문자만 |
| `mask_hash` | 값의 SHA-256, 16진수 |
| `mask_null` | `NULL` |
| `mask_none` | 마스킹하지 않은 값 |
| `mask_date_show_year` | 날짜나 타임스탬프의 연도만 |
| `custom` | 정책의 Trino SQL 표현식, 컬럼은 `{col}` |

에이전트는 각 마스크를 컬럼 유형에 맞춰 마스크가 컬럼을 읽는 쿼리를 깨뜨리지 않게 합니다. [Trino 에이전트](/ko/external/trino-agent/)를 참조하세요.

## 정책 예시

카탈로그 `hive`에 스키마 `sales`가 있고, 그 안에 `region`, `customer`, `card` 컬럼을 가진 테이블 `orders`와 테이블 `returns`가 있다고 합시다. 같은 데이터 서비스에 다음 정책을 활성화하여 만듭니다.

| 정책 | 리소스 | 규칙 |
| --- | --- | --- |
| 접근 | `catalog=hive`, `schema=sales` | 그룹 `analysts`에 `select` 허용 |
| 접근 | `catalog=hive`, `schema=sales`, `table=orders`, `column=card` | 그룹 `analysts`에 `select` 거부 |
| 접근 | `catalog=hive`, `schema=sales`, `table=returns` | 사용자 `alice`에 `insert` 허용 |
| 행 필터 | `catalog=hive`, `schema=sales`, `table=orders` | 사용자 `alice`에 `region = 'EU'` |
| 마스킹 | `catalog=hive`, `schema=sales`, `table=orders`, `column=customer` | 그룹 `analysts`에 `mask_hash` |
| 접근 | `catalog=system`, `schema=runtime` | 사용자 `alice`에 `select` 허용 |
| 접근 | `user=bob` | 사용자 `alice`에 `view_query` 허용 |

에이전트가 새 스냅샷을 적용하면 `analysts` 구성원은 `orders.card`를 제외한 `sales`의 모든 테이블을 읽고 `customer`는 해시로 봅니다. `alice`는 유럽 주문만 보고, `returns`에 쓰며, `system.runtime.queries`에서 자신의 쿼리와 함께 Bob의 쿼리를 봅니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 coordinator에 Trino 에이전트가 필요합니다. 배포와 검사 내용은 [Trino 에이전트](/ko/external/trino-agent/)를 참조하세요.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [Trino 에이전트](/ko/external/trino-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
