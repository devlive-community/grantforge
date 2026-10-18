---
title: Apache HBase 에이전트
description: HBase master와 region server에 코프로세서를 설치해 작업과 읽기·쓰기를 GrantForge 정책으로 검사하고, 사용자가 읽을 수 있는 컬럼만 반환하며, 사용할 수 없는 것을 숨깁니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

HBase 에이전트는 master, region, region server의 코프로세서입니다. HBase는 모든 작업과 읽기·쓰기에 대해 에이전트에 묻고, 에이전트는 `hbase` 데이터 서비스의 서명된 정책으로 답합니다. 배포판은 의존성을 재배치해 포함한 Java 8 호환 JAR 하나, `agents/hbase/grantforge-agent-hbase-<version>.jar`로 제공합니다.

저장소의 Testcontainers 통합 테스트는 HBase `2.5.16`, `2.6.7`, `3.0.0`에서 JAR를 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다. 서비스 플러그인은 [Apache HBase](/ko/plugins/hbase/)를 참고하세요.

## 동작 방식

- **모든 요청을 지정한 각 범위에서 검사합니다.** HBase에서 권한은 범위 안의 모든 것을 포함하므로, 에이전트는 요청의 네임스페이스, 테이블, 컬럼 패밀리, 컬럼에서 각각 결정합니다. 어느 범위에서든 거부되면 요청을 거부하고, 어느 범위에서든 허용되면 허용합니다.
- **네임스페이스 소유자는 그 테이블을 가집니다.** 네임스페이스의 `create`와 `admin`이 있으면 테이블마다 정책을 두지 않고도 네임스페이스를 만들고, 그 안에 테이블을 만들어 관리할 수 있습니다.
- **읽기는 읽을 수 있는 것을 반환합니다.** 사용자가 테이블의 일부(예: 컬럼 패밀리 하나)만 읽을 수 있으면 get과 scan이 실패하지 않고 그 컬럼을 반환합니다. 테이블에서 아무것도 읽을 수 없는 사용자는 거부됩니다.
- **목록에서 사용할 수 없는 것을 뺍니다.** 네임스페이스와 테이블 목록에는 사용자가 사용할 수 있거나 그 안의 무언가를 사용할 수 있는 것만 보이고, 스냅숏 목록에는 사용자가 관리하는 테이블의 스냅숏만 보입니다. 사용할 수 없는 네임스페이스의 설명은 거부됩니다.
- **거부 사유를 알려 줍니다.** 클라이언트는 `Permission denied: user bob has no read on table sales:items (no GrantForge policy allows it)` 또는 거부한 정책을 받습니다.
- **결정을 보관하고 접근을 묶습니다.** 현재 스냅숏의 결정을 기본 100,000개까지 10초 동안 보관하며, 새 스냅숏은 처음부터 다시 시작합니다. 기본 10초 창 안의 동일한 허용 접근은 횟수를 담은 하나의 접근 이벤트로 보고되고, 거부는 즉시 하나씩 보고됩니다.
- **서버 자체는 검사하지 않습니다.** `hbase.superuser`에 지정한 HBase 슈퍼 사용자와 서버 자체의 작업은 검사하지도 감사하지도 않습니다. `hbase` 네임스페이스의 테이블(예: `hbase:meta`)은 클라이언트에 필요하므로 누구나 읽을 수 있습니다.

| 작업 | 필요한 접근 |
| --- | --- |
| Get, scan | 테이블 또는 반환되는 컬럼의 `read` |
| 컬럼의 put, increment, append, delete | 쓰는 각 컬럼의 `write` |
| 컬럼 패밀리 또는 행 삭제 | 각 컬럼 패밀리의 `write`. HBase는 행을 패밀리별로 삭제합니다 |
| Check-and-mutate | 비교하는 컬럼의 `read`(필터면 테이블의 `read`)와 쓰는 대상의 `write` |
| 테이블 생성 | 네임스페이스의 `create` |
| 삭제, truncate, 수정, 활성화, 비활성화, flush, split, merge, compact | 테이블의 `admin` 또는 `create` |
| 이동, 할당, 할당 해제, 오프라인 | 테이블의 `admin` |
| 스냅숏 생성, 복원, 삭제, 목록 | 테이블의 `admin`. 복제에는 대상 네임스페이스의 `create`도 필요 |
| 대량 적재 | 테이블의 `create` |
| 네임스페이스 생성, 수정, 삭제 | 네임스페이스의 `admin` |
| 네임스페이스 설명, 네임스페이스·테이블 목록 | 그 자체나 그 안의 것에 대한 아무 접근 |
| 밸런서, 종료, 할당량, 복제 피어, region server 그룹, 프로시저, 해제, region server 중지, WAL 교체 | `global`의 `admin` |

## 배포

1. `hbase` 데이터 서비스를 추가하고 정책을 구성합니다. **데이터 권한 → 에이전트**를 열어 서비스를 선택하고 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되므로 HBase 계정만 읽을 수 있는 파일에 저장하세요.
2. JAR를 **모든 master와 region server**의 `lib`(예: `/opt/hbase/lib`)에 복사합니다.
3. 각 노드의 `hbase-site.xml`에 설정을 추가하고, 예시 주소·경로·인스턴스 이름을 바꿉니다.

   ```xml
   <property><name>hbase.coprocessor.master.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.region.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>hbase.coprocessor.regionserver.classes</name>
     <value>org.devlive.grantforge.hbase.agent.GrantForgeHBaseCoprocessor</value></property>
   <property><name>grantforge.server.url</name><value>https://grantforge.example.com</value></property>
   <property><name>grantforge.token.file</name><value>/etc/hbase/grantforge.token</value></property>
   <property><name>grantforge.instance</name><value>regionserver-1</value></property>
   <property><name>grantforge.cache.dir</name><value>/var/lib/hbase/grantforge-cache</value></property>
   ```

   캐시 디렉터리는 HBase 계정이 쓸 수 있어야 합니다. 코프로세서 목록에서 HBase 자체의 `AccessController`를 대체하고, 둘을 함께 로드하지 마세요.
4. master와 region server를 하나씩 재시작합니다. 에이전트가 첫 스냅숏을 받기 전에는 슈퍼 사용자가 아닌 요청이 거부됩니다.
5. **기존 권한을 먼저 옮기세요.** HBase의 `AccessController`가 `hbase:acl`에 저장한 권한은 더 이상 적용되지 않습니다. 미리 `user_permission`으로 나열하고 대응하는 정책을 만드세요.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 master나 region server의 이름, 서명된 스냅숏과 대기 이벤트를 위한 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅숏 서명 키를 고정하는 선택적 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사하세요. 생략하면 첫 연결 시 서버에서 키를 가져옵니다 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결·읽기 시간 제한, 기본값 `5000` / `8000`밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 묻는 주기, 기본값 `30000`, 최소 `1000`밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 대기열 크기(기본값 `10000`), 전송 간격(기본값 `5000`밀리초), 디스크 대기열 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 동일한 허용 접근을 하나의 이벤트로 묶는 시간, 기본값 `10000`, 최대 `3600000`밀리초. `0`이면 하나씩 보고합니다 |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | 보관할 최대 결정 수, 기본값 `100000`, 최대 `10000000`, `0`이면 보관하지 않음. 그리고 보관 시간, 기본값 `10000`, `1..3600000`밀리초 |

HBase는 master, region server, 그리고 여는 모든 region마다 코프로세서를 하나씩 시작하며, 모두 에이전트 하나와 결정 캐시, 이벤트 묶음을 공유합니다.

## 사용자, 그룹과 역할

정책에는 HBase가 연결 사용자에게 부여한 짧은 이름을 씁니다. simple 인증의 사용자 이름, 또는 `hadoop.security.auth_to_local`에 따른 Kerberos 주체의 짧은 이름입니다. 그룹은 Hadoop 그룹 매핑이 서버에서 사용자에게 주는 그룹과 GrantForge가 사용자를 넣은 그룹이며, 역할은 GrantForge가 부여한 것입니다.

## 결정과 제한 사항

- 사용 가능한 스냅숏이 없으면 슈퍼 사용자의 요청과 `hbase` 네임스페이스 읽기를 제외한 모든 요청이 거부됩니다. 사용하지 않는 서비스는 권한 부여자가 없을 때처럼 HBase를 검사하지 않습니다.
- 토큰이 `hbase`가 아닌 유형의 서비스에 연결되어 있으면 모두 거부합니다.
- HBase의 `grant`, `revoke`, `user_permission`은 *AccessController is not loaded*로 실패합니다. 권한은 GrantForge 정책입니다.
- 코프로세서 엔드포인트 호출은 검사하지 않습니다. 신뢰하는 엔드포인트만 로드하세요.
- 여기서 HBase는 테이블 생성자에게 별도 권한을 주지 않습니다. 위와 같은 네임스페이스 정책이 소유자에게 테이블을 줍니다.
- HBase는 일부 거부를 반환하지 않고 기록만 합니다. 예를 들어 밸런서 스위치는 그대로 유지되지만 클라이언트는 알림을 받지 못합니다.
- 결정은 `grantforge.decision.cache.ttl.ms` 동안 보관되므로 정책의 유효 기간은 최대 그만큼 늦게 시작하거나 끝날 수 있습니다.
- HBase의 셀 수준 권한과 가시성 레이블은 다루지 않습니다. 컬럼은 UTF-8 텍스트로 비교합니다.

## 관련 가이드

- [Apache HBase](/ko/plugins/hbase/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
