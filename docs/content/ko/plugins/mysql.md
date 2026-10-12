---
title: MySQL
description: MySQL 서비스 플러그인을 설치·구성하고, 데이터베이스·테이블·열을 조회하며 접근·마스킹·행 필터 정책을 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MySQL 플러그인은 GrantForge에 데이터베이스·테이블·열 조회와 접근·마스킹·행 필터 정책을 제공합니다. 플러그인 ID와 서비스 타입은 모두 `mysql`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, 실제 업무 SQL은 별도의 [MySQL 프록시 에이전트](/ko/external/mysql-agent/)가 검사합니다.

연결 검사는 MySQL 8.0 이상을 허용합니다. 저장소의 Testcontainers 통합 테스트는 `mysql:8.0`과 `mysql:8.4`를 대상으로 하며, 다른 모든 버전이 검증되었다는 뜻은 아닙니다. MariaDB 서버와 MySQL 5.7은 지원 범위에 포함되지 않습니다. `jdbc:mariadb://`는 드라이버 URL 형식일 뿐, MariaDB 서버 지원을 의미하지 않습니다.

## 설치

배포판의 `plugins/mysql`에 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `mysql`이 활성화되어 있는지 확인하고, 업데이트한 뒤에는 플러그인 디렉터리를 다시 스캔합니다. 플러그인은 MariaDB Connector/J(LGPL)로 MySQL과 통신합니다. 이 드라이버는 `caching_sha2_password`와 TLS를 포함한 MySQL 프로토콜을 지원하며 플러그인 자체 클래스 로더에서 동작합니다. MySQL 공식 Connector/J는 GPL이므로 배포하지 않습니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 MySQL(`mysql`)을 선택합니다.
2. 서버의 JDBC URL을 입력합니다. 예: `jdbc:mysql://mysql.example.com:3306`. `jdbc:mariadb://`도 같은 방식으로 쓸 수 있고, `jdbc:mysql:replication://primary:3306,replica:3306` 같은 드라이버의 장애 조치 형식도 쓸 수 있습니다.
3. 조회 사용자와 비밀번호를 입력하고 연결을 테스트한 뒤 저장합니다. 테스트는 로그인하고 서버 버전을 확인하며 조회와 같은 방식으로 `information_schema`를 읽습니다.

조회 사용자는 GrantForge의 리소스 검색에만 쓰이며, 업무 클라이언트가 MySQL에 로그인하는 사용자와는 별개입니다. 연결 테스트가 성공해도 업무 SQL에 정책이 적용되고 있다는 뜻은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `jdbc.url` | 필수 JDBC URL: `jdbc:mysql://host[:port]` 또는 `jdbc:mariadb://…`. 데이터베이스와 `?options`을 붙일 수 있으며 자격 증명은 URL에 넣지 않습니다 |
| `username` | 필수 조회 사용자 |
| `password` | 조회 사용자의 비밀번호. 암호화되어 저장됩니다 |
| `jdbc.properties` | 드라이버 옵션. 한 줄에 하나의 `key=value`. 예: `sslMode=verify-full`, `serverSslCert=/etc/grantforge/mysql-ca.pem` |
| `connect.timeout.ms` | 연결 시간 제한. 기본값 `10000`, 범위 `1..600000` |
| `read.timeout.ms` | 조회 한 번의 읽기 시간 제한. 기본값 `30000`, 범위 `1..600000` |

서버가 로컬 파일을 읽게 하거나, 연결 시 문을 실행하거나, 여러 문을 한 번에 실행하거나, 자격 증명을 담거나, 클래스를 로드하는 옵션은 URL과 `jdbc.properties` 모두에서 대소문자와 상관없이 거부됩니다: `user`, `password`, `allowLocalInfile`, `allowMultiQueries`, `initSql`, `createDatabaseIfNotExist`, `socketFactory`, `credentialType` 및 풀 옵션. 조회 연결은 읽기 전용입니다.

서버의 신원을 검증하는 TLS 연결을 사용하려면 `jdbc.properties`에 다음을 입력합니다.

```properties
sslMode=verify-full
serverSslCert=/etc/grantforge/mysql-ca.pem
```

인증서 파일은 **GrantForge 서버**에서 읽을 수 있어야 하며 URL의 호스트 이름이 인증서와 일치해야 합니다. 플러그인은 기본적으로 TLS를 필수로 요구하지 않으므로 명시적으로 설정하세요. 옵션 이름은 MariaDB Connector/J의 형식을 따릅니다.

`information_schema`는 사용자가 권한을 가진 객체만 보여 줍니다. 예를 들어 `sales` 데이터베이스의 리소스만 조회하려면 데이터베이스 관리자가 다음 권한을 부여할 수 있습니다. `grantforge-host`는 GrantForge 서버의 접속 출발지 주소로 바꾸세요.

```sql
CREATE USER 'grantforge_lookup'@'grantforge-host' IDENTIFIED BY 'replace-with-a-password';
GRANT REFERENCES ON sales.* TO 'grantforge_lookup'@'grantforge-host';
```

모든 데이터베이스를 검색해야 할 때만 `REFERENCES ON *.*`를 사용하세요. 프록시의 메타데이터 계정에는 `SHOW_ROUTINE`도 필요합니다. 자세한 내용은 에이전트 가이드를 참고하세요.

## 리소스와 접근 유형

| 수준 | 일치 | 비고 |
| --- | --- | --- |
| `database` | 와일드카드, 대소문자 구분 | 정책이 여기서 끝날 수 있습니다 |
| `table` | 와일드카드, 대소문자 구분 | 데이터베이스 아래. 정책이 여기서 끝날 수 있습니다 |
| `column` | 와일드카드, 대소문자 구분 안 함 | 테이블 아래. 접근 유형은 `select`, `insert`, `update` |

데이터베이스와 테이블은 MySQL이 Linux에서 저장하는 방식대로 대소문자를 구분해 비교합니다. `lower_case_table_names`가 1 또는 2인 서버에서는 에이전트가 확인하는 이름을 소문자로 바꾸므로, 이런 정책은 소문자로 작성하세요. 열 이름은 MySQL과 마찬가지로 항상 대소문자를 구분하지 않습니다.

접근 유형은 MySQL의 권한을 따릅니다: `select`, `insert`, `update`, `delete`, `create`, `drop`, `alter`, `index`, 그리고 이 여덟 가지를 포함하는 `all`. `all`은 MySQL의 `ALL PRIVILEGES`와 다르며 프록시의 SQL 문 제한을 해제하지 않습니다.

정책 값의 `*`는 임의 길이의 문자열, `?`는 한 문자와 일치합니다. 정책 편집기는 이름의 접두사를 기준으로 데이터베이스, 정책이 가리키는 데이터베이스의 테이블, 그 테이블의 열을 제안합니다. 제안 입력의 `%`와 `_`는 일반 문자입니다.

## 마스킹

마스킹 정책은 열에 적용됩니다. 각 마스크 유형은 MySQL 8 표현식이며 `{col}`이 열을 나타냅니다.

| 마스크 유형 | 표시 |
| --- | --- |
| `mask` | ASCII 영문자는 `X`/`x`, 숫자는 `n`으로 표시하며 다른 문자는 그대로 유지 |
| `mask_show_last_4` | 마지막 네 글자만 |
| `mask_show_first_4` | 처음 네 글자만 |
| `mask_hash` | 솔트를 사용하지 않는 SHA-256 해시 |
| `mask_null` | `NULL` |
| `mask_none` | 값 그대로 |
| `mask_date_show_year` | 해당 날짜 연도의 1월 1일 |
| `custom` | 직접 작성한 MySQL 표현식. 열은 `{col}`로 나타냅니다. 예: `CONCAT('****', RIGHT({col}, 4))` |

처음 네 글자 또는 마지막 네 글자를 표시하는 마스크는 길이가 네 글자 이하이면 값 전체를 유지합니다. 예를 들어 `mask_show_last_4`는 `4111111111111111`을 `xxxxxxxxxxxx1111`로 표시합니다.

## 행 필터

행 필터 정책은 테이블에 적용됩니다. 필터는 테이블 열에 대한 MySQL 조건으로, 예를 들어 `region = 'EU'`입니다. 사용자는 조건을 만족하는 행만 보며, 단일 테이블 `UPDATE`와 `DELETE`도 그 행만 변경합니다. 대상 테이블에 행 필터가 필요한 다중 테이블 쓰기는 거부됩니다. 다른 제한은 에이전트 가이드를 참고하세요.

마스킹과 행 필터는 접근 권한을 부여하지 않습니다. 별도의 접근 정책에서 해당 작업을 허용해야 합니다.

## 정책 예시

MySQL에 `id`, `region`, `card` 열을 가진 `sales.orders` 테이블이 있다고 가정합니다. 같은 데이터 서비스에서 MySQL 로그인 사용자 `alice`에 대해 다음 세 정책을 활성화합니다.

| 정책 종류 | 리소스 | 규칙 |
| --- | --- | --- |
| 접근 | `database=sales`, `table=orders`, `column=*` | 사용자 `alice`의 `select` 허용 |
| 마스킹 | `database=sales`, `table=orders`, `column=card` | 사용자 `alice`에 `mask_show_last_4` 적용 |
| 행 필터 | `database=sales`, `table=orders` | 사용자 `alice`에 `region = 'EU'` 적용 |

정책 사용자에는 `alice@host`가 아닌 `alice`를 입력합니다. MySQL 자체에서도 이 계정의 테이블 읽기를 허용해야 합니다. 프록시가 새 스냅샷을 적용하면 `alice`는 프록시를 통한 조회에서 EU 행만 볼 수 있으며 카드 번호는 마지막 네 글자만 표시됩니다. 조회 조건과 조인 표현식도 마스킹된 값을 사용합니다. 배포와 검증 절차는 [MySQL 프록시 에이전트](/ko/external/mysql-agent/)를 참고하세요.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 마스킹과 행 필터를 포함한 정책을 적용하려면, 클라이언트가 서버 대신 연결하는 MySQL 프록시 에이전트가 필요합니다. 배포 방법과 확인 내용은 에이전트 가이드를 참고하세요.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [MySQL 프록시 에이전트](/ko/external/mysql-agent/)
- [플러그인과 서비스 타입 개발](/ko/develop/plugins/)
