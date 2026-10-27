---
title: Apache Doris 에이전트
description: 클러스터 옆에서 Apache Doris 에이전트를 실행하여 GrantForge 정책을 Doris 자체의 역할, 컬럼 권한, 워크로드 그룹 권한과 행 정책으로 만들고, 사용자의 모든 ID에 주며, Doris가 더 주지 않는 곳에서만 데이터베이스와 카탈로그 전체에 부여하고, 접근을 Doris의 감사 로그 테이블에서 보고합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Doris 에이전트는 클러스터 옆에서 실행되는 서비스입니다. Doris 자체의 접근 제어를 `doris` 데이터 서비스의 서명된 정책과 일치시킵니다: 사용자마다 정책이 그 사용자에게 주는 것만 정확히 담은 역할 하나를 컬럼과 워크로드 그룹까지 유지하고, 행 필터마다 행 정책 하나를 유지합니다. 그러면 Doris가 모든 문을 직접 검사하며, 에이전트는 클라이언트와 프런트엔드 사이에 끼지 않습니다. 배포판은 실행 가능한 JAR `agents/doris/grantforge-agent-doris-<version>.jar`로 제공하며, Java 17 이상이 필요합니다.

저장소의 Testcontainers 통합 테스트는 에이전트와 배포되는 JAR를 Apache Doris `2.1`, `3.1`, `4.0`, `4.1`에서 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아니며, 에이전트는 `2.1`보다 오래된 클러스터를 거부합니다. 서비스 플러그인은 [Apache Doris](/ko/plugins/doris/)를 참고하세요.

## 동작 방식

- **사용자마다 역할 하나가 GrantForge가 주는 것을 담습니다.** 정책이 무언가를 주는 각 사용자에 대해 에이전트는 그 이름을 딴 역할, 예를 들어 `alice`에게는 `grantforge_alice`를 사용자를 밝히는 주석과 함께 유지하고, `'alice'@'%'`와 `'alice'@'10.%'`처럼 사용자의 모든 ID에 부여합니다. `SHOW ROLES`는 GrantForge가 `alice`에게 주는 것을 보여 줍니다. 에이전트는 다른 역할도, 자신이 부여하지 않은 권한도 바꾸지 않습니다.
- **아래에서 다르게 결정되지 않으면 데이터베이스와 카탈로그 전체에 부여합니다.** 정책이 데이터베이스나 카탈로그 전체에서 허용하고, 아래에서 어떤 정책도 빼앗을 수 없는 읽기, 적재, 뷰 보기는 데이터베이스나 카탈로그에 부여되므로, 나중에 만든 테이블도 즉시 갖게 됩니다. 그 밖에는 테이블마다, 읽기는 컬럼마다 부여됩니다. 변경은 테이블마다 부여됩니다. 데이터베이스에 대한 Doris의 이 권한은 데이터베이스 이름 변경도 허용하기 때문입니다.
- **정책이 허용하는 것 이상은 주지 않습니다.** 카탈로그에 데이터베이스를 만드는 Doris의 권한은 그 안의 모든 데이터베이스에 테이블을 만드는 권한이고, 데이터베이스를 삭제하는 권한은 그 모든 테이블을 삭제하는 권한입니다. 에이전트는 정책이 더 넓은 권한도 줄 때만 각각을 부여합니다. 그렇지 않으면 보류하고, 어떤 사용자가 무엇을 왜 할 수 없는지 한 번 로그에 남깁니다.
- **워크로드 그룹은 이름으로 부여됩니다.** 워크로드 그룹에 대한 정책은 그것이 허용하는 각 그룹에 `USAGE_PRIV`를 주어, 사용자가 거기서 쿼리를 실행할 수 있게 합니다. 나중에 만든 그룹은 다음 동기화 때 부여됩니다.
- **행 필터는 행 정책이 됩니다.** 행 필터는 테이블에 대한 사용자 역할의 허용적(permissive) 행 정책입니다. 새 필터는 그것이 좁히는 권한보다 먼저 자리를 잡습니다. 바뀌는 필터는 행을 하나도 보여 주지 않는 제한적(restrictive) 정책 뒤에서 교체되므로, 사용자는 두 필터 중 어느 것보다 많이 보지 않습니다. 각 필터는 먼저 테이블에서 실행해 보며, Doris가 거기서 실행할 수 없거나 거부하는 필터, 예를 들어 테이블에 없는 컬럼을 가리키는 필터는 사용자에게 행을 하나도 보여 주지 않는 필터로 교체되고 로그에 남습니다.
- **현재 시점의 정책.** 각 동기화는 그 시점에 적용되는 정책과 Doris에 있는 사용자, 카탈로그, 데이터베이스, 테이블과 컬럼으로 계획을 세우므로, 유효 기간이 제때 시작하고 끝나며 새로 생긴 것은 다음 동기화 때 반영됩니다.
- **Doris 역할이 곧 그룹입니다.** 그룹에 대한 정책은 같은 이름의 Doris 역할을 가진 사용자와, GrantForge가 사용자를 넣은 그룹에 적용됩니다.
- **수동 변경은 되돌려집니다.** 에이전트는 동기화할 때마다 `SHOW ROLES`가 보여 주는 자기 역할의 권한과 자기 행 정책을 계획과 비교하여 다른 부분을 되돌립니다. Doris는 컬럼 권한을 사용자별로, 그 모든 권한을 합친 형태로만 보여 줍니다. 에이전트는 계획이 지정한 컬럼을 사용자가 모두 읽을 수 없으면 그 컬럼을 부여하고, 나머지 컬럼을 자기 역할에서 한 번 회수합니다. 그 뒤에도 읽히는 컬럼은 GrantForge 밖의 권한에서 온 것입니다. 아무것도 바뀌지 않았으면 아무것도 보내지 않습니다.
- **GrantForge 밖의 권한을 보고합니다.** Doris는 권한을 더하고 허용적 행 정책을 OR로 묶으므로, 에이전트는 사용자가 직접 또는 다른 역할로 가진 것을 빼앗을 수 없습니다. 정책이 거부하는 것을 주는 그런 권한, 컬럼 권한이나 행 정책마다 한 번, 사용자, 권한과 그 출처를 밝혀 경고하며, 어떤 역할로도 제한되지 않는 `ADMIN_PRIV` 또는 `NODE_PRIV`를 가진 사용자도 경고합니다.
- **접근은 감사 로그에서 옵니다.** 에이전트는 모든 프런트엔드가 쓰는 `__internal_schema.audit_log`를 읽고, 각 문을 그것이 닿은 테이블, 데이터베이스나 카탈로그마다 이벤트 하나로 보고합니다. 로그는 테이블을 밝히지 않으므로, 닿은 테이블은 Druid의 Doris 파서로 문 자체에서 읽습니다. 거부는 Doris 오류가 밝힌 권한과 객체에 대한 거부 이벤트 하나입니다. 각 이벤트는 GrantForge가 결정했는지, 즉 정책이 Doris와 같이 결정하는지, GrantForge 밖의 권한이 허용했는지 알려 줍니다. 로그는 프런트엔드의 배치 간격 `audit_plugin_max_batch_interval_sec`에 5초를 더한 만큼 현재보다 늦게 읽습니다.

| 문 | 접근 이벤트 |
| --- | --- |
| `SELECT`, `INSERT … SELECT`의 읽기 부분 | 읽은 각 테이블의 `select` |
| `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` | 쓴 테이블의 `load` |
| `ALTER TABLE`, `DROP TABLE`, `SHOW CREATE VIEW` | 테이블의 `alter`, `drop`, `show_view` |
| `CREATE TABLE`, `CREATE VIEW` | 데이터베이스의 `create` |
| `DROP DATABASE` | 데이터베이스의 `drop_database` |
| `CREATE DATABASE` | 카탈로그의 `create_database` |

## 배포

1. `doris` 데이터 서비스를 추가하고 정책을 구성합니다. **데이터 권한 → 에이전트**를 열어 서비스를 선택하고 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되니 에이전트 계정만 읽을 수 있는 파일에 저장하세요.
2. 에이전트의 Doris 사용자를 만듭니다. 역할과 행 정책을 만들고, 정책이 주는 것을 부여하며, 모든 사용자의 권한과 감사 로그를 읽습니다. `ADMIN_PRIV`가 이를 허용합니다:

   ```sql
   CREATE USER 'grantforge'@'%' IDENTIFIED BY '…';
   GRANT ADMIN_PRIV ON *.*.* TO 'grantforge'@'%';
   ```

3. 접근을 보고하려면 Doris 2.1처럼 감사 로그가 꺼져 있는 곳에서 켜세요: `SET GLOBAL enable_audit_plugin = true`. `audit_plugin_max_batch_interval_sec`를 기본값 60초보다 짧게 하면 접근이 더 빨리 보고됩니다.
4. 예시 주소, 경로와 이름을 바꿔 설정 파일을 씁니다:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/doris-agent.token
   grantforge.instance=doris-agent-1
   grantforge.cache.dir=/var/lib/grantforge/doris-agent
   grantforge.doris.hosts=fe1.internal:9030,fe2.internal:9030
   grantforge.doris.user=grantforge
   grantforge.doris.password.file=/etc/grantforge/doris-agent.password
   ```

   이 경로는 에이전트 머신에 있으며, 캐시 디렉터리는 쓰기 가능해야 합니다. 비밀번호 파일은 UTF-8로 읽고 앞뒤 공백을 지웁니다.
5. 예를 들어 systemd 서비스로 에이전트를 시작합니다:

   ```bash
   java -jar agents/doris/grantforge-agent-doris-<version>.jar /etc/grantforge/doris-agent.properties
   ```

   `<version>`을 배포 버전으로 바꾸세요. `java -Dgrantforge.…=… -jar …`로 준 설정이 파일보다 우선합니다. 에이전트는 SIGTERM에 깔끔하게 멈추고 대기 중인 이벤트를 보내거나 스풀링하며, Doris는 받은 것을 유지합니다.
6. **기존 권한을 정책으로 옮기기:** GrantForge 밖에서 부여된 권한은 에이전트의 역할과 함께 계속 적용됩니다. 대응하는 정책을 만든 뒤 그 권한을 회수하세요. 에이전트의 경고가 정책이 거부하는 것을 주는 권한을 알려 줍니다.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 에이전트의 이름, 그리고 서명된 스냅샷, 스풀된 이벤트와 감사 로그 읽기 위치를 둘 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사합니다. 생략하면 처음 연결할 때 서버에서 가져옵니다 |
| `grantforge.doris.hosts`, `grantforge.doris.user` | 필수: 프런트엔드의 MySQL 프로토콜 주소(하나에 연결할 수 없으면 차례로 시도하며, 포트를 쓰지 않으면 `9030`)와 에이전트의 Doris 사용자 |
| `grantforge.doris.password.file` | 사용자의 비밀번호. 생략하면 비밀번호 없이 로그인합니다 |
| `grantforge.doris.ssl.mode`, `grantforge.doris.tls.ca` | TLS를 제공하는 프런트엔드용 `disable`(기본값), `trust`, `verify-ca`, `verify-full`과, 이를 검증하는 PEM CA 인증서. 비우면 Java가 신뢰하는 것을 신뢰합니다 |
| `grantforge.doris.timeout.ms` | 연결과 문 하나에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |
| `grantforge.doris.role.prefix` | 에이전트 역할 이름의 시작, 기본값 `grantforge_`. 영문자, 숫자와 밑줄로 이루어지고 밑줄로 끝나며 최대 32자입니다. 이름이 이것으로 시작하는 모든 역할과 행 정책이 에이전트의 것입니다 |
| `grantforge.doris.unmanaged.users` | 에이전트가 건드리지 않는 사용자, 쉼표로 구분, 예: 서비스 계정. 자신의 사용자, `root`, `admin`과 `ADMIN_PRIV` 또는 `NODE_PRIV`를 가진 사용자는 항상 건드리지 않습니다 |
| `grantforge.doris.catalogs` | 에이전트가 데이터베이스, 테이블과 컬럼별로 부여하는 카탈로그, 쉼표로 구분, 비우면 전부. 나머지는 전체에 부여할 수 있는 것만 받으므로, 테이블이 많은 외부 카탈로그를 동기화할 때마다 나열하지 않아도 됩니다 |
| `grantforge.sync.interval.ms` | 에이전트가 Doris를 맞추는 주기, 기본값 `10000`, 범위 `1000..3600000` 밀리초 |
| `grantforge.audit.log` | 기본값 `true`. `false`면 감사 로그를 읽지 않고 접근을 보고하지 않습니다 |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | 로그를 현재보다 얼마나 늦게 읽을지(기본값은 클러스터의 배치 간격에 5초를 더한 값), 읽는 주기(기본값 `5000` 밀리초), 한 번에 읽는 최대 항목 수(기본값 `1000`) |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결·읽기 시간 초과, 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 요청하는 주기, 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 주기(기본값 `5000` 밀리초)와 디스크 스풀 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 같은 허용 접근을 이벤트 하나로 묶는 시간, 기본값 `10000`, 최대 `3600000` 밀리초. `0`은 하나씩 보냅니다 |

## 사용자, 그룹과 역할

정책은 Doris 사용자를 사용자 이름으로 지정합니다. 여러 호스트를 가진 사용자 이름은 한 사용자이며, 에이전트의 역할은 그 모든 ID에 부여됩니다. 그룹은 사용자가 가진 Doris 역할과 GrantForge가 사용자를 넣은 그룹입니다. 역할은 GrantForge가 사용자에게 주는 역할입니다.

## 결정과 제한 사항

- 스냅샷이 없으면 에이전트는 아무것도 바꾸지 않습니다. 사용하지 않는 서비스나 유형이 `doris`가 아닌 서비스에 묶인 토큰은 아무것도 부여하지 않습니다: 에이전트는 자신의 역할과 행 정책을 삭제하고, Doris 자체의 권한이 적용됩니다.
- 에이전트가 실행되지 않는 동안 Doris는 마지막으로 받은 것을 유지하고, 정책 변경은 에이전트를 기다립니다. 변경은 갱신 주기와 동기화 주기 안에 Doris에 반영됩니다.
- 에이전트가 테이블마다 부여하는 데이터베이스에서 만든 테이블과, 데이터베이스마다 부여하는 카탈로그에서 만든 데이터베이스는 다음 동기화 때 권한을 받습니다.
- Doris의 행 정책은 `root`와 `admin`을 묶을 수 없습니다. Doris가 행 정책을 하나도 보여 주지 않는 사용자는 테이블의 모든 행을 봅니다. GrantForge 밖에서 같은 사용자에게 만든 허용적 행 정책은 그 필터가 보여 주는 범위를 넓힙니다.
- Doris는 쿼리가 백엔드에서 실행될 때 워크로드 그룹의 `USAGE_PRIV`를 검사합니다. 상수나 캐시된 결과처럼 프런트엔드가 직접 답하는 쿼리는 이 검사를 거치지 않습니다. Doris가 모든 사용자에게 `normal` 그룹을 주므로 누구나 그것을 쓸 수 있습니다.
- 에이전트가 역할을 유지하는 사용자는 Doris에 존재해야 합니다. Doris에 자기 사용자가 없이 LDAP로 로그인하는 사용자는 역할을 받지 않습니다.
- Doris는 Apache Ranger를 통해서만 값을 마스킹하므로, 정책으로 값을 마스킹할 수 없습니다.

## 관련 가이드

- [Apache Doris](/ko/plugins/doris/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
