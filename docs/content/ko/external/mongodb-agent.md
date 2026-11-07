---
title: MongoDB 에이전트
description: 배포 옆에서 MongoDB 에이전트를 실행하여 GrantForge 정책을 MongoDB 자체의 역할로 만들고, 역할의 권한을 한 번에 교체하며, GrantForge 밖의 내장 및 사용자 정의 역할이 무엇을 주는지 읽고, 접근을 감사 로그나 프로파일러에서 보고합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MongoDB 에이전트는 배포 옆에서 실행되는 서비스입니다. MongoDB 자체의 접근 제어를 `mongodb` 데이터 서비스의 서명된 정책과 일치시킵니다: 사용자마다 정책이 그 사용자에게 주는 것만 정확히 담은 역할 하나를 유지합니다. 그러면 MongoDB가 모든 명령을 직접 검사하며, 에이전트는 어떤 클라이언트 앞에도 끼지 않습니다. 배포판은 실행 가능한 JAR `agents/mongodb/grantforge-agent-mongodb-<version>.jar`로 제공하며, Java 17 이상이 필요합니다.

저장소의 Testcontainers 통합 테스트는 에이전트와 배포되는 JAR를 MongoDB `6.0`, `7.0`, `8.0`, `9.0` 복제 세트와 `9.0` 샤드 클러스터에서 실행하고, Percona Server for MongoDB `7.0`, `8.0`의 감사 로그를 읽습니다. 이것이 다른 모든 버전을 인증하는 것은 아니며, 에이전트는 `6.0`보다 오래된 배포를 거부합니다. 서비스 플러그인은 [MongoDB](/ko/plugins/mongodb/)를 참고하세요.

## 동작 방식

- **사용자마다 역할 하나가 GrantForge가 주는 것을 담습니다.** 정책이 무언가를 주는 각 사용자에 대해 에이전트는 `admin`에 그 이름을 딴 역할, 예를 들어 `alice`에게는 `grantforge_alice`를 유지하고, `alice@admin`과 `alice@sales`처럼 그 이름의 모든 MongoDB 사용자에게 부여합니다. `rolesInfo`는 GrantForge가 `alice`에게 주는 것을 보여 줍니다. 에이전트는 다른 역할을 바꾸지 않습니다.
- **변경은 한 번에 이루어집니다.** `updateRole`은 역할의 권한을 한꺼번에 교체하므로, 사용자가 이전과 새 권한을 함께 갖거나 둘 다 잃는 일이 없습니다.
- **아래에서 다르게 결정되지 않으면 데이터베이스 전체에 부여합니다.** 정책이 데이터베이스 전체에서 허용하고 아래에서 어떤 정책도 빼앗을 수 없는 접근 유형은 데이터베이스에 부여되므로, 나중에 만든 컬렉션도 즉시 갖게 됩니다. 그 밖에는 컬렉션마다 부여되며, 새 컬렉션은 다음 동기화 때 반영됩니다.
- **정책이 허용하는 것 이상은 주지 않습니다.** MongoDB의 데이터베이스 삭제 권한은 그 안의 모든 컬렉션을 삭제하는 권한입니다. 에이전트는 정책이 그것도 줄 때만 부여합니다. 그렇지 않으면 보류하고, 어떤 사용자가 무엇을 왜 할 수 없는지 한 번 로그에 남깁니다.
- **현재 시점의 정책.** 각 동기화는 그 시점에 적용되는 정책과 MongoDB에 있는 사용자, 데이터베이스와 컬렉션으로 계획을 세우므로, 유효 기간이 제때 시작하고 끝나며 새로 생긴 것은 다음 동기화 때 반영됩니다.
- **MongoDB 역할이 곧 그룹입니다.** 그룹에 대한 정책은 같은 이름의 MongoDB 역할을 가진 사용자와, GrantForge가 사용자를 넣은 그룹에 적용됩니다.
- **수동 변경은 되돌려집니다.** 에이전트는 동기화할 때마다 자기 역할을 계획과 비교하여 수동으로 바뀐 권한, 상속하게 만든 역할, 다른 사람에게 부여된 역할, 아무도 필요로 하지 않는 역할을 바로잡습니다. 아무것도 바뀌지 않았으면 아무것도 보내지 않습니다.
- **GrantForge 밖의 역할이 무엇을 주는지 읽습니다.** MongoDB는 사용자의 모든 역할이 주는 것을 더하므로, 에이전트는 사용자가 다른 역할로 가진 것을 빼앗을 수 없습니다. MongoDB는 내장 및 상속 역할까지 각 역할이 무엇을 주는지 알려 주므로, 에이전트는 `readWrite@hr`처럼 정책이 거부하는 것을 주는 역할을 사용자, 접근과 정책을 밝혀 한 번씩 경고합니다. 사용자를 관리할 수 있어 자신에게 무엇이든 줄 수 있는 사용자, `root` 또는 모든 리소스의 모든 동작을 가진 사용자, 그리고 권한 부여를 전혀 검사하지 않는 서버도 경고합니다.
- **접근은 감사 로그나 프로파일러에서 옵니다.** 아래 접근 보고를 참고하세요. 각 이벤트는 GrantForge가 결정했는지, 즉 정책이 MongoDB와 같이 결정하는지, 밖의 역할이 명령을 허용했는지 알려 줍니다. 집계는 `$lookup`, `$graphLookup`, `$unionWith` 단계가 읽고 `$out`, `$merge` 단계가 쓰는 각 컬렉션마다 보고됩니다. 이벤트는 명령과 네임스페이스만 밝히며, 문서와 비밀번호가 들어 있을 수 있는 인수는 절대 밝히지 않습니다.

## 배포

1. `mongodb` 데이터 서비스를 추가하고 정책을 구성합니다. **데이터 권한 → 에이전트**를 열어 서비스를 선택하고 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되니 에이전트 계정만 읽을 수 있는 파일에 저장하세요.
2. 에이전트의 MongoDB 사용자를 만듭니다. `userAdminAnyDatabase`로 역할을 만들고 바꾸며 사용자와 역할을 읽고, 그것만 담은 역할로 컬렉션을 나열하며, `clusterMonitor`로 서버 옵션과 프로파일러를 읽습니다. `userAdminAnyDatabase`는 자신에게 무엇이든 줄 수 있으므로 이 사용자를 관리자처럼 보호하세요. 나열 역할의 이름은 에이전트의 역할 접두사로 시작하면 안 됩니다.

   ```javascript
   const admin = db.getSiblingDB("admin");
   admin.createRole({ role: "agent_inventory", roles: [],
     privileges: [{ resource: { db: "", collection: "" }, actions: ["listCollections"] }] });
   admin.createUser({ user: "grantforge", pwd: passwordPrompt(),
     roles: ["userAdminAnyDatabase", "clusterMonitor", "agent_inventory"] });
   ```

3. 접근을 보고하려면 아래 접근 보고의 소스 중 하나를 설정하세요.
4. 예시 주소, 경로와 이름을 바꿔 설정 파일을 씁니다:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/mongodb-agent.token
   grantforge.instance=mongodb-agent-1
   grantforge.cache.dir=/var/lib/grantforge/mongodb-agent
   grantforge.mongodb.uri=mongodb://db1.internal:27017,db2.internal:27017/?replicaSet=rs0
   grantforge.mongodb.user=grantforge
   grantforge.mongodb.password.file=/etc/grantforge/mongodb-agent.password
   ```

   이 경로는 에이전트 머신에 있으며, 캐시 디렉터리는 쓰기 가능해야 합니다. 비밀번호 파일은 UTF-8로 읽고 앞뒤 공백을 지웁니다.
5. 예를 들어 systemd 서비스로 에이전트를 시작합니다:

   ```bash
   java -jar agents/mongodb/grantforge-agent-mongodb-<version>.jar /etc/grantforge/mongodb-agent.properties
   ```

   `<version>`을 배포 버전으로 바꾸세요. `java -Dgrantforge.…=… -jar …`로 준 설정이 파일보다 우선합니다. 에이전트는 SIGTERM에 깔끔하게 멈추고 대기 중인 이벤트를 보내거나 스풀링하며, MongoDB는 받은 것을 유지합니다.
6. **기존 권한을 정책으로 옮기기:** GrantForge 밖에서 부여된 역할은 에이전트의 역할과 함께 계속 적용됩니다. 대응하는 정책을 만든 뒤 그 역할을 회수하세요. 에이전트의 경고가 정책이 거부하는 것을 주는 역할을 알려 줍니다.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 에이전트의 이름, 그리고 서명된 스냅샷, 스풀된 이벤트와 감사 로그 읽기 위치를 둘 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사합니다. 생략하면 처음 연결할 때 서버에서 가져옵니다 |
| `grantforge.mongodb.uri` | 필수: 자격 증명 없는 연결 문자열 |
| `grantforge.mongodb.auth.mechanism` | `SCRAM-SHA-256`(기본값), `SCRAM-SHA-1`, `MONGODB-X509`, `PLAIN` 또는 `GSSAPI` |
| `grantforge.mongodb.user`, `grantforge.mongodb.password.file`, `grantforge.mongodb.auth.source` | 에이전트의 사용자, `GSSAPI`에서는 주체이고 `MONGODB-X509`에서는 선택 사항; 그 비밀번호; SCRAM에서 사용자가 정의된 데이터베이스, 기본값 `admin` |
| `grantforge.mongodb.tls.ca.file`, `grantforge.mongodb.tls.certificate.key.file`, `grantforge.mongodb.tls.certificate.key.password.file` | 연결 문자열에 `tls=true`가 있을 때: 서버를 검증하는 PEM CA 인증서, 비우면 Java가 신뢰하는 것; 클라이언트 인증서와 키가 함께 든 PEM 파일; 암호화된 키의 비밀번호 |
| `grantforge.mongodb.kerberos.keytab`, `grantforge.mongodb.kerberos.service.name` | `GSSAPI`: 주체의 keytab, 없으면 비밀번호를 사용하고, 서버 주체의 서비스 이름, 기본값 `mongodb`. 영역은 머신의 `krb5.conf`에서 옵니다 |
| `grantforge.mongodb.timeout.ms` | 서버 찾기, 연결과 명령 하나에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |
| `grantforge.mongodb.role.prefix` | 에이전트 역할 이름의 시작, 기본값 `grantforge_`. 영문자, 숫자와 밑줄로 이루어지고 밑줄로 끝나며 최대 32자입니다. `admin`에서 이름이 이것으로 시작하는 모든 역할이 에이전트의 것입니다 |
| `grantforge.mongodb.unmanaged.users` | 에이전트가 건드리지 않는 사용자, 쉼표로 구분, 예: 서비스 계정. 자신의 사용자와 `root`, `__system` 또는 모든 리소스의 모든 동작을 가진 사용자는 항상 건드리지 않습니다 |
| `grantforge.mongodb.databases` | 에이전트가 컬렉션별로 부여하는 데이터베이스, 쉼표로 구분, 비우면 전부. 나머지는 전체에 부여할 수 있는 것만 받으므로, 컬렉션이 아주 많은 데이터베이스를 동기화할 때마다 나열하지 않아도 됩니다 |
| `grantforge.mongodb.audit.files` | 읽을 감사 로그 파일, 쉼표로 구분. 비우면 프로파일러를 읽습니다 |
| `grantforge.sync.interval.ms` | 에이전트가 MongoDB를 맞추는 주기, 기본값 `10000`, 범위 `1000..3600000` 밀리초 |
| `grantforge.audit.log` | 기본값 `true`. `false`면 접근을 읽지 않고 보고하지 않습니다 |
| `grantforge.audit.log.delay.ms`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.batch` | 프로파일러를 현재보다 얼마나 늦게 읽을지(기본값 `2000`), 접근을 읽는 주기(기본값 `5000` 밀리초), 파일이나 데이터베이스에서 한 번에 읽는 최대 항목 수(기본값 `1000`) |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결·읽기 시간 초과, 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 요청하는 주기, 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 주기(기본값 `5000` 밀리초)와 디스크 스풀 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 같은 허용 접근을 이벤트 하나로 묶는 시간, 기본값 `10000`, 최대 `3600000` 밀리초. `0`은 하나씩 보냅니다 |

## 접근 보고

| 소스 | 어디서 | 알려 주는 것 | 켜는 방법 |
| --- | --- | --- | --- |
| 감사 로그 | MongoDB Enterprise와 Percona Server for MongoDB | 에이전트가 파일을 읽을 수 있는 각 멤버에서 무엇이 허용되고 거부되었는지 | `--auditDestination file --auditFormat JSON --auditPath <path>`와 `--setParameter auditAuthorizationSuccess=true`로 `mongod`를 시작하고, `grantforge.mongodb.audit.files`에 파일을 지정합니다 |
| 프로파일러 | 모든 에디션 | 에이전트가 통신하는 멤버, 즉 복제 세트의 프라이머리에서 무엇이 실행되었는지; 거부는 없음 | 보고할 각 데이터베이스에서 `db.setProfilingLevel(2)`. 서버에 부담이 되므로 에이전트는 절대 직접 켜지 않으며, `system.profile`은 작은 고정 크기 컬렉션입니다 |

감사 로그는 파일에서 읽으므로 에이전트를 노드에서 실행하거나 파일을 모아 둔 디렉터리를 주세요. 테스트는 MongoDB Enterprise와 같은 형식인 Percona Server의 감사 로그를 읽습니다. `mongos`에는 프로파일러가 없으므로 샤드 클러스터는 감사 로그로만 접근을 보고합니다.

| 명령 | 접근 이벤트 |
| --- | --- |
| `find`, `aggregate`, `count`, `distinct`, `listIndexes` | 읽은 각 컬렉션의 `read` |
| `$changeStream`으로 시작하는 `aggregate` | `change_stream` |
| `insert`, `update`, `delete`, `findAndModify`, `bulkWrite` | 쓴 각 컬렉션의 `insert`, `update` 또는 `remove`; 요청하면 `bypass_validation`도 |
| `createIndexes`, `dropIndexes` | `create_index`, `drop_index` |
| `collMod`, `validate`, `convertToCapped` | `alter` |
| `drop`, `create`, `dropDatabase` | `drop`, `create`, `drop_database` |
| `listDatabases`; `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `currentOp` | `list_databases`; `monitor` |

MongoDB는 명령 전체를 거부하며 어느 컬렉션이 원인인지 밝히지 않으므로, 거부는 정책이 거부하는 컬렉션에 대해, 거부하는 것이 없으면 첫 번째 컬렉션에 대해 보고됩니다.

## 사용자, 그룹과 역할

정책은 MongoDB 사용자를 사용자 이름으로 지정합니다. 여러 데이터베이스에 정의된 이름은 한 사용자이며, 에이전트의 역할은 그 모두에게 부여됩니다. 그룹은 사용자가 가진 MongoDB 역할과 GrantForge가 사용자를 넣은 그룹입니다. 역할은 GrantForge가 사용자에게 주는 역할입니다.

## 결정과 제한 사항

- 스냅샷이 없으면 에이전트는 아무것도 바꾸지 않습니다. 사용하지 않는 서비스나 유형이 `mongodb`가 아닌 서비스에 묶인 토큰은 아무것도 부여하지 않습니다: 에이전트는 자신의 역할을 삭제하고, MongoDB 자체의 역할은 남습니다.
- 에이전트가 실행되지 않는 동안 MongoDB는 마지막으로 받은 것을 유지하고, 정책 변경은 에이전트를 기다립니다. 변경은 갱신 주기와 동기화 주기 안에 MongoDB에 반영되며, `mongos`는 사용자 캐시가 만료되면, 기본적으로 30초 이내에 적용합니다(`userCacheInvalidationIntervalSecs`).
- MongoDB의 권한은 데이터베이스와 컬렉션을 정확히 지정하므로, 정책의 와일드카드는 존재하는 것에 대해 부여됩니다. 데이터베이스는 컬렉션이 있어야 존재합니다: 그 전에는 정책이 지정한 데이터베이스의 `create`를 부여할 대상이 없습니다.
- LDAP 권한 부여가 직접 역할에 매핑하는 사용자처럼 MongoDB 어디에도 정의되지 않은 사용자는 보이지 않으며, 에이전트는 이들의 역할을 유지하지 않습니다.
- 데이터베이스 전체에 대한 권한은 그 컬렉션을 포함하지만 `system.` 컬렉션은 포함하지 않으며, MongoDB 자체의 데이터베이스는 MongoDB에 맡깁니다.
- 컬렉션 이름 변경과 MongoDB의 다른 관리 동작은 접근 유형이 아닙니다: GrantForge 밖의 역할에 남습니다.
- MongoDB는 역할별로 행을 필터링하거나 필드를 마스킹하지 않습니다. [뷰](/ko/plugins/mongodb/)가 제한된 읽기를 제공합니다.

## 관련 가이드

- [MongoDB](/ko/plugins/mongodb/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
