---
title: Redis 에이전트
description: Redis 또는 Valkey 배포 옆에서 Redis 에이전트를 실행하여 모든 노드에서 각 사용자의 ACL 셀렉터를 GrantForge 정책과 일치시키고 한 명령으로 교체하며, GrantForge 밖의 권한이 무엇을 허용하는지 ACL DRYRUN으로 Redis에 묻고, 노드의 ACL 로그에서 거부를 보고합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Redis 에이전트는 배포 옆에서 실행되는 서비스입니다. Redis는 노드마다 ACL을 따로 두므로, 에이전트는 복제본과 클러스터의 모든 노드까지 각 노드에서 Redis 자체의 ACL을 `redis` 데이터 서비스의 서명된 정책과 일치시킵니다. 그러면 Redis가 모든 명령을 직접 검사하며, 에이전트는 어떤 클라이언트 앞에도 끼지 않습니다. 배포판은 실행 가능한 JAR `agents/redis/grantforge-agent-redis-<version>.jar`로 제공하며, Java 17 이상이 필요합니다.

저장소의 Testcontainers 통합 테스트는 에이전트와 배포되는 JAR를 Redis `7.2`, `7.4`, `8.2`, `8.10`과 Valkey `8.1`, `9.1`의 복제본 하나를 둔 프라이머리, 그리고 Redis `8.10` 클러스터에서 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아니며, 에이전트는 ACL 로그 항목에 번호가 없는 `7.2`보다 오래된 Redis를 거부합니다. 서비스 플러그인은 [Redis](/ko/plugins/redis/)를 참고하세요.

## 동작 방식

- **셀렉터는 GrantForge의 것, 루트 권한은 운영자의 것입니다.** 사용자의 루트 권한이나 셀렉터 중 하나가 허용하면 Redis는 명령을 허용합니다. 에이전트는 자신이 관리하는 사용자의 모든 셀렉터를 유지하며 루트 권한과 비밀번호는 절대 건드리지 않습니다: 셀렉터 하나는 사용자가 읽거나 쓰거나 둘 다 할 수 있는 키를, 하나는 구독할 수 있는 채널을, 하나는 발행할 수 있는 채널을 담고, 모니터링할 수 있는 노드에는 하나가 더 있습니다.
- **변경은 한 명령으로 이루어집니다.** `ACL SETUSER user clearselectors (…)`는 한 노드에서 사용자의 모든 셀렉터를 한꺼번에 교체하므로, 사용자가 이전과 새 권한을 함께 갖거나 둘 다 잃는 일이 없습니다. 수동으로 바뀐 셀렉터도 다음 동기화 때 같은 방식으로 바로잡습니다.
- **패턴은 성립하는 곳에만 부여합니다.** 정책의 키나 채널 패턴은 정책이 그 패턴 전체에서 사용자를 똑같이 결정할 때만 Redis 자체의 패턴으로 부여됩니다. 에이전트는 겹치는 모든 정책을 따라 패턴을 잘라 각 부분을 결정하므로, 다른 사용자의 정책이 이를 막지 않습니다. 정책이 그 안에서 다르게 결정하는 패턴은 보류되고 한 번 로그에 남습니다.
- **위험한 것은 주지 않고, 남의 키를 나열하지 않습니다.** 셀렉터는 Redis가 위험하다고 보는 것을 절대 주지 않으며, `SCAN`과 `RANDOMKEY`는 모든 키를 읽을 수 있는 사용자에게만 줍니다.
- **모든 노드에, 가능하면 저장합니다.** 에이전트는 주어진 노드에서 복제본과 클러스터 노드를 찾아 각각을 일치시킵니다. ACL을 파일에 두는 노드는 `ACL SAVE`로 저장하고, 그렇지 않은 노드는 재시작하면 셀렉터를 잃었다가 다음 동기화 때 다시 받습니다.
- **무엇을 허용하는지 Redis에 묻습니다.** 루트 권한은 에이전트의 셀렉터와 나란히 결정합니다. 에이전트는 그 규칙을 읽는 대신 정책이 거부하는 키, 채널이나 노드에 사용자가 명령을 실행할 수 있는지 `ACL DRYRUN`으로 Redis에 묻고, 사용자, 접근과 정책을 밝혀 한 번씩 경고합니다. 어떤 셀렉터도 묶지 않는, 모든 키에 모든 명령을 가진 사용자와, 누구나 비밀번호 없이 로그인할 수 있는 사용자도 경고합니다.
- **거부는 각 노드의 ACL 로그에서 옵니다.** 아래 거부 보고를 참고하세요.
- **현재 시점의 정책.** 각 동기화는 그 시점에 적용되는 정책과 Redis에 있는 사용자와 노드로 계획을 세우므로, 유효 기간이 제때 시작하고 끝납니다.

## 배포

1. `redis` 데이터 서비스를 추가하고 정책을 구성합니다. **데이터 권한 → 에이전트**를 열어 서비스를 선택하고 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되니 에이전트 계정만 읽을 수 있는 파일에 저장하세요.
2. 모든 노드에 에이전트의 ACL 사용자를 만듭니다. 이 사용자는 사용자를 읽고, 셀렉터를 설정하고, ACL 파일을 저장하고, ACL 로그를 읽고, 드라이런을 실행하고, 클러스터 노드를 찾습니다. `+acl|setuser`는 자신에게 무엇이든 줄 수 있으므로 이 사용자를 관리자처럼 보호하세요.

   ```text
   ACL SETUSER grantforge on >… -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun +acl|save +config|get +info +cluster|nodes +command|info
   ```

3. 에이전트가 관리하는 사용자의 루트 권한은 로그인처럼 모든 사용자에게 필요한 것으로 제한하세요. 나머지는 에이전트가 줍니다. 그보다 많이 주는 루트 권한은 발견되어 경고됩니다.

   ```text
   ACL SETUSER alice on >… resetkeys resetchannels -@all +@connection
   ```

4. 예시 주소, 경로와 이름을 바꿔 설정 파일을 씁니다:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/redis-agent.token
   grantforge.instance=redis-agent-1
   grantforge.cache.dir=/var/lib/grantforge/redis-agent
   grantforge.redis.nodes=redis1.internal:6379,redis2.internal:6379
   grantforge.redis.user=grantforge
   grantforge.redis.password.file=/etc/grantforge/redis-agent.password
   ```

   이 경로는 에이전트 머신에 있으며, 캐시 디렉터리는 쓰기 가능해야 합니다. 비밀번호 파일은 UTF-8로 읽고 앞뒤 공백을 지웁니다.
5. 예를 들어 systemd 서비스로 에이전트를 시작합니다:

   ```bash
   java -jar agents/redis/grantforge-agent-redis-<version>.jar /etc/grantforge/redis-agent.properties
   ```

   `<version>`을 배포 버전으로 바꾸세요. `java -Dgrantforge.…=… -jar …`로 준 설정이 파일보다 우선합니다. 에이전트는 SIGTERM에 깔끔하게 멈추고 대기 중인 이벤트를 보내거나 스풀링하며, Redis는 받은 것을 유지합니다.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 에이전트의 이름, 그리고 서명된 스냅샷, 스풀된 이벤트와 감사 로그 읽기 위치를 둘 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사합니다. 생략하면 처음 연결할 때 서버에서 가져옵니다 |
| `grantforge.redis.nodes` | 필수: 노드 주소 `host:port`, 포트를 쓰지 않으면 `6379` |
| `grantforge.redis.discover` | 기본값 `true`: 주어진 노드에서 클러스터의 나머지 노드와 프라이머리의 복제본을 찾습니다. 노드가 알리는 주소에 에이전트가 닿을 수 없으면 `false`로 주어진 노드만 사용합니다 |
| `grantforge.redis.user`, `grantforge.redis.password.file` | 에이전트의 ACL 사용자(비우면 기본 사용자)와 그 비밀번호 |
| `grantforge.redis.tls.mode`, `grantforge.redis.tls.ca.file`, `grantforge.redis.tls.certificate.file`, `grantforge.redis.tls.key.file`, `grantforge.redis.tls.key.password.file` | `disable`(기본값), `verify-ca` 또는 `verify-full`; 서버를 검증하는 PEM CA 인증서, 비우면 Java가 신뢰하는 것; PEM 클라이언트 인증서, 별도 파일일 때의 키, 키의 비밀번호 |
| `grantforge.redis.timeout.ms` | 연결과 명령 하나에 허용되는 시간, 기본값 `10000`, 범위 `1..600000` 밀리초 |
| `grantforge.redis.unmanaged.users` | 에이전트가 건드리지 않는 사용자, 쉼표로 구분, 예: 서비스 계정. 자신의 사용자와 모든 키에 모든 명령을 가진 사용자는 항상 건드리지 않습니다 |
| `grantforge.sync.interval.ms` | 에이전트가 Redis를 맞추는 주기, 기본값 `10000`, 범위 `1000..3600000` 밀리초 |
| `grantforge.audit.log`, `grantforge.audit.log.interval.ms` | 기본값 `true`; `false`면 ACL 로그를 읽지 않습니다. ACL 로그를 읽는 주기, 기본값 `5000` 밀리초 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결·읽기 시간 초과, 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 요청하는 주기, 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 주기(기본값 `5000` 밀리초)와 디스크 스풀 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 같은 허용 접근을 이벤트 하나로 묶는 시간, 기본값 `10000`, 최대 `3600000` 밀리초. `0`은 하나씩 보냅니다 |

## 거부 보고

Redis는 무엇을 허용했는지 기록하지 않으므로, 에이전트는 각 노드의 ACL 로그에서 Redis가 거부한 것을 보고합니다: 누가 무엇을 언제 어디서 거부당했는지, 그리고 정책이 Redis와 같이 결정하는지입니다. Redis는 1분 안에 반복된 거부를 이전 항목에 합치며, 에이전트는 항목이 갱신될 때마다 다시 보고합니다.

| 로그의 이유 | 접근 이벤트 |
| --- | --- |
| `key` | Redis가 명령에 붙인 표시에 따라 키의 `read` 또는 `write` |
| `channel` | 채널의 `subscribe` 또는 `publish` |
| `command` | 모니터링 명령은 노드의 `monitor`; 다른 명령은 키 없이 기록되므로 보고하지 않습니다 |
| `auth` | 보고하지 않음: 거부된 로그인은 어떤 접근도 가리키지 않습니다 |

## 사용자, 그룹과 역할

정책은 Redis의 ACL 사용자를 이름으로 지정합니다. Redis에는 자체 역할이나 그룹이 없으므로, 그룹은 GrantForge가 사용자를 넣은 그룹입니다.

## 결정과 제한 사항

- 스냅샷이 없으면 에이전트는 아무것도 바꾸지 않습니다. 사용하지 않는 서비스나 유형이 `redis`가 아닌 서비스에 묶인 토큰은 아무것도 부여하지 않습니다: 에이전트는 관리하는 사용자의 셀렉터를 비웁니다.
- 에이전트가 실행되지 않는 동안 Redis는 마지막으로 받은 것을 유지하고, 정책 변경은 에이전트를 기다립니다. 변경은 갱신 주기와 동기화 주기 안에 Redis에 반영됩니다.
- 에이전트는 관리하는 사용자의 모든 셀렉터를 유지합니다: 운영자가 수동으로 추가한 셀렉터는 다음 동기화 때 제거됩니다. 추가 권한은 루트 권한에 주거나 그 사용자를 운영자에게 맡기세요.
- Redis의 ACL은 데이터베이스가 아니라 키와 채널을 지정하므로, 패턴은 노드의 모든 데이터베이스에서 해당 키를 포함합니다.
- 사용자가 아예 실행할 수 없는 명령이 거부되면 Redis는 키 없이 기록하므로 보고하지 않습니다.
- Redis는 값 안을 필터링하지도 마스킹하지도 않습니다.

## 관련 가이드

- [Redis](/ko/plugins/redis/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
