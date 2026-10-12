---
title: MySQL 프록시 에이전트
description: MySQL 네트워크 프록시를 배포하고 TLS와 정책 동기화를 설정하여 열 권한, 마스킹, 행 필터 및 거부 오류를 확인합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MySQL 에이전트는 독립적인 네트워크 프록시입니다. 클라이언트는 서버 대신 프록시에 연결하고, 프록시는 지원하는 문이 MySQL에 도달하기 전에 `mysql` 데이터 서비스의 서명된 정책으로 확인합니다. MySQL 서버 내부에 설치할 필요가 없습니다. 배포판에는 실행 가능한 JAR `agents/mysql/grantforge-agent-mysql-<version>.jar`가 포함되며 Java 17 이상이 필요합니다.

저장소의 Testcontainers 통합 테스트는 `mysql:8.0`과 `mysql:8.4`를 대상으로 MariaDB Connector/J 및 MySQL Connector/J 클라이언트를 사용합니다. 다른 서버 버전이나 모든 MySQL 프로토콜 기능이 검증되었다는 의미는 아닙니다. 서비스 플러그인의 버전 및 연결 요구 사항은 [MySQL](/ko/plugins/mysql/)을 참고하세요.

## 동작 방식

- **로그인은 그대로 통과합니다.** 서버의 인사 패킷은 클라이언트로, 클라이언트의 응답은 서버로 전달되므로 비밀번호는 MySQL이 직접 확인합니다(`caching_sha2_password`와 `mysql_native_password`). 프록시는 MySQL이 받아들인 사용자를 알게 됩니다. 정책의 사용자는 호스트 부분 없이 이 사용자 이름입니다.
- **SQL 리소스를 확인합니다.** 프록시는 COM_QUERY와 서버 측 준비된 문을 분석하고, 해당 데이터베이스·테이블·열에 필요한 접근 유형을 확인합니다. 거부된 문은 MySQL에 도달하지 않으며, 프록시가 1142나 1143처럼 MySQL과 호환되는 오류 코드를 생성합니다.
- **읽기는 마스킹되고 걸러집니다.** 문이 읽는 테이블에 해당 사용자에 대한 마스킹 열이나 행 필터가 있으면, 프록시는 같은 이름의 파생 테이블로 바꿉니다. 파생 테이블은 마스킹된 값과 걸러진 행만 보여 주므로 조인, 조건, 함수 모두 사용자가 볼 수 있는 내용만 다룹니다.
- **리소스 권한 확인 이벤트**를 사용자, 클라이언트 주소, 리소스, 접근 유형, 결정, 문과 함께 GrantForge에 전송합니다. 감사 범위는 아래에서 설명합니다.
- MySQL의 실행 결과를 클라이언트에 중계하며, 프록시가 데이터베이스에 저장된 원래 값을 바꾸지는 않습니다.

## 배포

1. `mysql` 데이터 서비스를 추가하고 정책을 설정합니다. **데이터 권한 → 에이전트**에서 서비스를 선택하고 에이전트 토큰을 발급하세요. 토큰은 한 번만 표시되므로 프록시 실행 계정만 읽을 수 있는 파일에 저장합니다.
2. 프록시용 메타데이터 사용자를 만듭니다. 이 사용자는 `information_schema`에서 테이블의 열과 저장 함수를 읽으며 데이터는 읽지 않습니다. `REFERENCES`로 모든 열이, `SHOW_ROUTINE`으로 모든 저장 함수가 보입니다. 역할을 통하지 말고 사용자에게 직접 부여하세요. `SHOW_ROUTINE`이 없으면 프록시가 시작되지 않습니다.

   ```sql
   CREATE USER 'grantforge_meta'@'proxy-host' IDENTIFIED BY '…';
   GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'proxy-host';
   ```

3. 설정 파일을 작성하고 예시의 주소, 경로, 이름을 바꿉니다.

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mysql-proxy.token
   grantforge.instance=mysql-proxy-1
   grantforge.cache.dir=/var/lib/grantforge/mysql-proxy
   grantforge.proxy.host=0.0.0.0
   grantforge.proxy.port=3307
   grantforge.proxy.tls.cert=/etc/grantforge/mysql-proxy.crt
   grantforge.proxy.tls.key=/etc/grantforge/mysql-proxy.key
   grantforge.proxy.tls.required=true
   grantforge.mysql.host=mysql.internal
   grantforge.mysql.port=3306
   grantforge.mysql.tls=verify-identity
   grantforge.mysql.tls.ca=/etc/grantforge/mysql-ca.pem
   grantforge.mysql.metadata.user=grantforge_meta
   grantforge.mysql.metadata.password.file=/etc/grantforge/mysql-meta.password
   ```

   이 예시는 클라이언트와 프록시 사이 및 프록시와 MySQL 사이의 TLS를 모두 활성화합니다. `mysql-proxy.crt`는 클라이언트가 사용하는 프록시 호스트 이름과 일치해야 하며, MySQL 인증서는 `mysql.internal`과 일치해야 합니다. 경로는 프록시가 실행되는 컴퓨터 기준이며 캐시 디렉터리에 쓸 수 있어야 합니다. 비밀번호 파일은 UTF-8로 읽은 뒤 앞뒤 공백을 제거하므로, 앞뒤 공백이 필요한 비밀번호는 이 방식으로 저장할 수 없습니다.

4. 예를 들어 systemd 서비스로 프록시를 시작합니다.

   ```bash
   java -jar agents/mysql/grantforge-agent-mysql-<version>.jar /etc/grantforge/mysql-proxy.properties
   ```

   `<version>`을 배포판 버전으로 바꾸세요. `java -Dgrantforge.…=… -jar …`로 지정한 설정은 파일보다 우선합니다. SIGTERM을 받으면 프록시는 정상 종료하고 대기 중인 이벤트를 전송하거나 임시 저장합니다.
5. 클라이언트가 프록시의 주소와 포트로 연결하도록 합니다. 이제 MySQL은 프록시의 주소를 보므로 계정의 호스트 부분이 이를 허용해야 합니다.
6. **프록시를 우회하는 길을 막으세요.** MySQL에 직접 연결할 수 있는 클라이언트는 확인되지 않습니다. 서버 포트에는 프록시와 관리자만 접근하게 하세요.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 프록시 이름, 서명된 스냅샷과 임시 이벤트를 저장할 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 선택: 스냅샷 서명 키를 고정하는 파일. 에이전트 페이지에서 Base64로 인코딩한 X.509 Ed25519 공개 키를 복사. 생략하면 최초 연결 시 서버에서 키를 가져옴 |
| `grantforge.proxy.host`, `grantforge.proxy.port` | 수신 주소. 기본값은 모든 주소와 포트 `3307`. 포트 범위 `0..65535`; `0`이면 자동 할당 |
| `grantforge.proxy.tls.cert`, `grantforge.proxy.tls.key` | 클라이언트에 TLS를 제공하는 PEM 인증서와 개인 키. 둘을 함께 설정해야 함 |
| `grantforge.proxy.tls.required` | 기본값 `false`; `true`이면 TLS 없는 클라이언트를 거부하며(오류 3159) 인증서와 개인 키가 필요함 |
| `grantforge.mysql.host`, `grantforge.mysql.port` | MySQL 호스트 필수. 포트 기본값 `3306`, 범위 `1..65535` |
| `grantforge.mysql.tls` | MySQL로의 TLS: `disabled`(기본값), `required`(암호화하지만 검증하지 않음), `verify-ca`, `verify-identity` |
| `grantforge.mysql.tls.ca` | MySQL을 검증할 PEM CA. 비어 있으면 Java가 신뢰하는 인증서를 신뢰함 |
| `grantforge.mysql.metadata.user`, `grantforge.mysql.metadata.password.file` | 메타데이터 사용자 필수. 비밀번호 파일은 선택이며 생략하면 드라이버에 비밀번호를 전달하지 않음 |
| `grantforge.mysql.metadata.cache.seconds` | 열과 저장 함수의 캐시 유지 시간. 기본값 `30`초, 범위 `0..86400`; `0`이면 캐시를 재사용하지 않음 |
| `grantforge.mysql.connect.timeout.ms` | MySQL 연결 제한 시간. 기본값 `10000`밀리초, 범위 `1..600000`; 메타데이터 읽기 제한 시간은 `30000`밀리초로 고정 |
| `grantforge.proxy.check.threads` | 문을 확인하는 스레드 수. 기본값 `8`, 범위 `1..256` |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결 및 읽기 제한 시간. 기본값 `5000` / `8000`밀리초, 범위 `1..2147483647` |
| `grantforge.refresh.interval.ms` | 최초 또는 서버에 연결할 수 없을 때의 대체 갱신 간격. 기본값 `30000`밀리초, 최소 `1000`; 정상 하트비트에는 서버가 반환한 간격을 사용 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity` | 기본 배치 `500`, 큐 `10000`개 이벤트. 배치 범위 `1..1000`, 큐는 배치 이상이고 최대 `1000000` |
| `grantforge.audit.flush.interval.ms` | 감사 전송 간격. 기본값 `5000`밀리초, 범위 `1..2147483647` |
| `grantforge.audit.spool.limit.bytes` | 감사 디스크 임시 저장 한도. 기본값 `67108864`(64 MiB), 최소 `0` |

양쪽의 TLS는 서로 독립적입니다. 한쪽만 TLS를 쓰고 MySQL이 전체 비밀번호를 요구할 때(캐시에 없는 `caching_sha2_password`) 프록시가 비밀번호를 전달합니다. 평문 클라이언트가 서버의 키를 요청하면 자체 RSA 키로 응답하고, TLS 클라이언트의 비밀번호는 평문 서버를 위해 MySQL의 키로 암호화합니다. 프록시를 거쳐 연결하는 클라이언트에는 MySQL 공개 키 파일을 설정하지 마세요.

## 문에 필요한 접근

| 문 | 접근 |
| --- | --- |
| `SELECT`, 서브쿼리, `WITH`, `EXPLAIN`, `SET @x = (SELECT …)` | 사용하는 모든 열의 `select`. `COUNT(*)`나 `EXISTS`처럼 열을 읽지 않는 테이블 읽기에는 테이블 전체의 `select`가 필요합니다 |
| `INSERT`, `LOAD DATA` | 쓰는 열의 `insert`(열을 나열하지 않으면 모든 열). `ON DUPLICATE KEY UPDATE`에는 `update`가 추가로 필요합니다 |
| `REPLACE` | 열의 `insert`와 테이블의 `delete` |
| `UPDATE` | 설정하는 열의 `update`와 읽는 열의 `select` |
| `DELETE` | 테이블의 `delete`와 읽는 열의 `select` |
| `CREATE TABLE`, `CREATE VIEW` / `DROP TABLE`, `DROP VIEW`, `TRUNCATE` / `ALTER TABLE`, `ALTER VIEW`, `OPTIMIZE` | 테이블이나 뷰의 `create` / `drop` / `alter` |
| `RENAME TABLE` | 이전 이름의 `alter`와 `drop`, 새 이름의 `create`와 `insert` |
| `CREATE INDEX`, `DROP INDEX` | 테이블의 `index` |
| `CREATE DATABASE`, `DROP DATABASE` | 데이터베이스의 `create`, `drop` |
| `LOCK TABLES`, `ANALYZE`, `CHECK`, `CHECKSUM TABLE` | 테이블의 `select` |

데이터를 쓰지 않는 문은 MySQL 자체 권한에 맡깁니다: 트랜잭션, `SET`, `USE`, `SHOW`, `DESCRIBE`, `KILL`, 그리고 `GRANT`, `CREATE USER` 같은 계정 문. `information_schema`와 `performance_schema`도 다른 데이터베이스처럼 정책이 필요합니다.

프록시는 완전히 확인할 수 없는 문을 거부합니다. 실행 가능한 주석(`/*! … */`), 저장 함수와 `CALL`, `CREATE PROCEDURE`·`FUNCTION`·`TRIGGER`·`EVENT`, SQL 수준의 `PREPARE`/`EXECUTE`, `HANDLER`, 복제와 클론 명령, 한 배치에서 다른 문과 함께 보낸 `USE`나 `SET NAMES`, 분석할 수 없는 문이 해당합니다.

## 마스킹, 행 필터와 그 제한

- 마스킹과 필터는 문이 그 테이블을 읽는 모든 곳에 적용됩니다. 서브쿼리, 조인, `INSERT … SELECT`, `CREATE TABLE … SELECT`(복사본에는 마스킹된 값이 들어갑니다)도 포함합니다.
- 단일 테이블 `UPDATE`나 `DELETE`는 필터로 보이는 행만 변경합니다. 필터가 있는 테이블의 다중 테이블 `UPDATE`나 `DELETE`는 거부되며, 변경하는 테이블의 마스킹 열을 사용하는 쓰기도 숨겨진 값을 복사하거나 탐색할 수 있으므로 거부됩니다.
- 마스킹이나 행 필터가 있는 테이블에 대한 뷰는 만들거나 변경할 수 없습니다. MySQL이 나중에 프록시를 거치지 않고 뷰를 실행하기 때문입니다.
- 정책 버전이 같으면 준비된 문은 기존 확인과 재작성을 재사용합니다. 버전이 바뀌면 다시 확인하여 접근이 여전히 허용되고 재작성한 텍스트가 같을 때 실행을 계속합니다. 그렇지 않으면 오류 1615가 반환되므로 클라이언트가 다시 준비해야 합니다.
- 테이블 이름 없이 쓴 열은 보이는 범위에서 같은 이름의 열을 가진 모든 테이블에 대해 계산됩니다. 같은 이름의 제한된 열 때문에 거부되지 않도록 열을 테이블로 한정하세요.
- `*`로 읽는 열은 메타데이터 사용자가 보는 `information_schema`를 따르며 `grantforge.mysql.metadata.cache.seconds` 동안 캐시됩니다. 프록시를 거쳐 일반 SQL 명령으로 실행한 DDL이 성공하면 캐시가 무효화되어 다음 조회 때 다시 읽습니다. 외부 또는 준비된 DDL은 실행 시 캐시를 무효화한다고 보장하지 않으므로 캐시가 만료되기를 기다리거나 프록시를 다시 시작하세요.

## 적용 범위

프록시는 클라이언트 SQL에서 분석할 수 있는 리소스를 확인하며, 기존 뷰·트리거 또는 서버 내부에서 실행되는 다른 작업을 재귀적으로 펼쳐 확인하지 않습니다. 기본 테이블을 보호하려면 MySQL 자체 권한과 기존 뷰 및 트리거의 사용도 제한해야 합니다. 업무 사용자에게 관리자 권한을 부여한 뒤 프록시가 관리 문을 막아 줄 것이라고 기대하지 마세요.

행 필터는 일반적인 쓰기 제약 조건이 아닙니다. `INSERT`, `REPLACE`, `LOAD DATA`는 쓰기 권한을 확인하지만 새 행이 필터 식을 만족하도록 자동으로 강제하지 않습니다. `INSERT … ON DUPLICATE KEY UPDATE`의 기존 행 업데이트 분기에도 행 필터 조건을 추가하지 않습니다. 프로토콜 압축, ZSTD, 쿼리 속성, 선택적 결과 메타데이터는 핸드셰이크에서 비활성화됩니다.

## 정책과 장애

- 사용할 수 있는 스냅샷이 없거나 토큰이 `mysql`이 아닌 서비스에 연결되어 있으면 데이터에 접근하는 모든 문을 거부합니다.
- GrantForge에 일시적으로 연결할 수 없거나 새 스냅샷의 서명이 잘못되면 이미 적용한 스냅샷을 유지합니다. 최초 시작 시 사용할 수 있는 스냅샷이 전혀 없으면 리소스 접근을 거부합니다.
- 서비스를 비활성화하면 GrantForge 리소스 권한 확인, 마스킹, 행 필터를 건너뜁니다. SQL 분석과 프로토콜 제한은 계속 적용됩니다. 서비스 비활성화는 데이터 접근을 차단하지 않습니다.
- 프록시의 확인 뒤에도 MySQL 권한은 적용됩니다. 프록시는 권한을 좁힐 수만 있습니다.
- `COM_CHANGE_USER`를 따르지만, 클라이언트 쪽과 서버 쪽의 TLS가 다르면 거부합니다. 다른 사용자로 다시 연결하세요.

## 확인 예시

별도의 테스트 데이터베이스에서 데이터베이스 관리자가 다음 데이터와 계정을 만들도록 합니다. `proxy-host`는 MySQL이 실제로 보는 프록시의 출발지 주소로 바꾸세요.

```sql
CREATE DATABASE grantforge_demo;
CREATE TABLE grantforge_demo.orders (id INT PRIMARY KEY, region VARCHAR(8), card VARCHAR(32));
INSERT INTO grantforge_demo.orders VALUES (1, 'EU', '4111111111111111'), (2, 'US', '5555555555554444');
CREATE USER 'alice'@'proxy-host' IDENTIFIED BY 'replace-with-a-password';
GRANT SELECT, UPDATE ON grantforge_demo.* TO 'alice'@'proxy-host';
```

`alice`에 대해 GrantForge 정책 세 개를 만듭니다. `database=grantforge_demo`, `table=orders`, `column=*`에 `select`를 허용하고, `mask_show_last_4`로 `card`를 마스킹하며, `region = 'EU'`로 테이블을 필터링합니다. 서비스와 세 정책을 모두 활성화하고 **데이터 권한 → 에이전트**에 최신 정책 버전이 적용되었다고 표시될 때까지 기다리세요.

MySQL 명령줄 클라이언트로 프록시에 연결합니다. `-p`는 비밀번호 입력을 요청하며, CA 파일은 프록시 인증서를 신뢰해야 합니다.

```bash
mysql --host=mysql-proxy.example.com --port=3307 --user=alice -p \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/path/to/proxy-ca.pem grantforge_demo
```

```sql
SELECT id, region, card FROM orders ORDER BY id;
-- 한 행만 반환: 1 | EU | xxxxxxxxxxxx1111

UPDATE orders SET region = 'US' WHERE id = 1;
-- 1143 / 42000: GrantForge가 update를 허용하지 않음
```

이 예시에서는 MySQL 자체 권한이 이미 `UPDATE`를 허용하므로, 거부 결과로 프록시의 정책 적용을 확인할 수 있습니다. 다음으로 행 필터를 `region = 'US'`로 바꾸고 프록시가 새 버전을 적용한 뒤 다시 조회하세요. 카드가 `xxxxxxxxxxxx4444`인 2번 행만 나타나야 합니다.

## 문제 해결과 감사

| 오류 코드 / SQLSTATE | 의미와 확인 사항 |
| --- | --- |
| `1044 / 42000`, `1142 / 42000`, `1143 / 42000` | 데이터베이스·테이블·열 접근 거부. 메시지에 `by GrantForge`가 있으면 정책 사용자, 리소스, 접근 유형 및 스냅샷 버전을 확인. 다른 거부는 MySQL 자체 권한도 확인 |
| `1046 / 3D000` | 데이터베이스 미선택. `USE`, 연결의 기본 데이터베이스 또는 전체 테이블 이름 사용 |
| `1227 / 42000` | 금지되었거나 안전하게 확인할 수 없는 작업. 메시지에 따라 문 제한과 메타데이터 권한 확인 |
| `1064 / 42000` | SQL을 분석할 수 없음 |
| `1615 / HY000` | 정책 변경 후 문을 다시 준비 |
| `3159 / HY000` | 프록시가 TLS를 요구하지만 클라이언트가 TLS 없이 연결함 |
| `1043 / 08S01` | 백엔드 프로토콜 또는 TLS 핸드셰이크 실패. TLS 모드, 인증서, 호스트 이름 및 서버 기능 확인 |
| `2003 / HY000` | 프록시가 MySQL에 연결할 수 없음. 주소, 네트워크 및 백엔드 상태 확인 |

**데이터 권한 → 접근 감사**에서 `alice`와 해당 리소스의 허용 및 거부 이벤트를 찾으세요. 한 SQL 문이 여러 리소스 권한 확인 이벤트를 만들 수 있습니다. 이벤트는 백엔드 실행 전에 기록되므로 `ALLOWED`는 MySQL 실행 성공을 의미하지 않습니다. 분석 실패, 리소스에 접근하지 않는 문, 비활성화된 서비스는 리소스 이벤트를 만들지 않을 수 있습니다. 같은 정책 버전에서 준비된 문을 반복 실행해도 권한 확인 이벤트는 반복되지 않습니다. 이는 리소스 권한 감사이며 전체 SQL 실행 로그는 아닙니다.

## 관련 가이드

- [MySQL](/ko/plugins/mysql/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
