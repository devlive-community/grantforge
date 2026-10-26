---
title: OpenSearch 에이전트
description: 클러스터 옆에서 OpenSearch 에이전트를 실행하여 GrantForge 정책을 필드 수준 보안, 마스킹된 필드와 문서 수준 보안을 갖춘 보안 플러그인 자체의 역할로 만들고, 인덱스 패턴을 패턴 그대로 두어 새 인덱스를 즉시 쓸 수 있게 하며, 감사 로그에서 접근을 보고합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

OpenSearch 에이전트는 클러스터 옆에서 실행되는 서비스입니다. 보안 플러그인 자체의 접근 제어를 `opensearch` 데이터 서비스의 서명된 정책과 일치시킵니다: 사용자마다 정책이 그 사용자에게 주는 것만 정확히 담은 역할 하나를 그 사용자에게만 매핑하며, 필드 수준 보안, 마스킹된 필드와 문서 수준 보안을 포함합니다. 그러면 OpenSearch가 모든 요청을 직접 검사하며, 에이전트는 클라이언트와 클러스터 사이에 끼지 않습니다. 배포판은 실행 가능한 JAR `agents/opensearch/grantforge-agent-opensearch-<version>.jar`로 제공하며, Java 17 이상이 필요합니다.

저장소의 Testcontainers 통합 테스트는 에이전트와 배포되는 JAR를 보안 플러그인이 있는 OpenSearch `2.19`, `3.3`, `3.6`, `3.9`에서 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아니며, 에이전트는 `2.19`보다 오래된 클러스터와 보안 플러그인이 없는 클러스터를 거부합니다. 서비스 플러그인은 [OpenSearch](/ko/plugins/opensearch/)를 참고하세요.

## 동작 방식

- **사용자마다 역할 하나가 GrantForge가 주는 것을 담습니다.** 정책이 무언가를 주는 각 사용자에 대해 에이전트는 그 이름을 딴 역할, 예를 들어 `alice`에게는 `grantforge_alice`를 유지하고 그 사용자에게만 매핑합니다. 역할의 설명이 에이전트의 것임을 표시하며, 에이전트는 이 표시가 없는 역할을 자신의 것으로 여기지 않으므로 다른 역할과 매핑을 바꾸지 않습니다.
- **인덱스 패턴은 패턴으로 남습니다.** `logs-*` 같은 정책의 인덱스 패턴은 모든 정책이 그 범위 전체에서 똑같이 결정하는 곳에서 패턴으로 부여됩니다: 인덱스가 패턴과 겹치는 모든 종류의 모든 정책이 그 패턴 전체를 포괄해야 합니다. 에이전트는 패턴의 유한 오토마타로 이를 정확히 판단합니다. 그러면 내일의 로그처럼 나중에 만들어지는 인덱스도 즉시 쓸 수 있으며, 첫 문서를 쓸 때 인덱스가 만들어지는 경우도 마찬가지입니다. `logs-*` 안에서 `logs-secret`을 거부하는 것처럼 정책이 패턴 안에서 다르게 결정하면, 에이전트는 클러스터에 있는 각 인덱스와 데이터 스트림을 이름으로 부여하고 새로 생긴 것은 다음 동기화 때 부여합니다.
- **아래에서 다르게 결정되는 경우에만 필드 수준 보안을 씁니다.** 사용자가 전체를 읽을 수 있고 어떤 정책도 필드를 빼앗을 수 없는 인덱스는 필드 수준 보안 없이 읽으므로, 나중에 추가된 필드도 즉시 읽힙니다. 그 밖에는 문서에 있는 필드와 정책이 지정한 필드 가운데 정책이 허용하는 필드를 읽으며, `name.keyword` 같은 멀티 필드는 원래 필드를 따릅니다.
- **마스크와 행 필터는 보안 플러그인 자체의 것입니다.** 마스크는 항목의 마스킹된 필드가 되고, 행 필터는 그 문서 수준 보안 쿼리가 됩니다. OpenSearch의 validate API가 부여 전에 각 쿼리를 검사하며, OpenSearch가 실행하지 않을 쿼리는 사용자에게 문서를 하나도 보여 주지 않고 로그에 남습니다. 보안 플러그인이 모르는 마스크는 대신 그 필드를 숨깁니다.
- **권한 집합마다 항목 하나.** 권한이 같은 인덱스와 패턴은 항목 하나를 함께 씁니다. 보안 플러그인이 겹치는 항목의 필드·문서 수준 보안을 합치기 때문에 항목끼리는 절대 겹치지 않습니다.
- **클러스터의 복합 작업은 읽기와 쓰기에 함께 따라옵니다.** 읽기에는 멀티 검색, 멀티 조회, 스크롤을 위한 `cluster_composite_ops_ro`가, 쓰기에는 bulk를 위한 `cluster_composite_ops`가 따라옵니다.
- **변경은 하나의 패치입니다.** 에이전트는 역할과 매핑을 JSON 패치로 쓰며, 보안 플러그인은 이를 클러스터 전체에 한 번에 적용합니다: 역할을 그것을 가리키는 매핑보다 먼저, 매핑을 그것이 가리키는 제거될 역할보다 먼저 처리합니다. 보안 플러그인이 거부한 패치는 변경 하나씩 적용되므로, 잘못된 역할 하나만 실패합니다.
- **현재 시점의 정책.** 각 동기화는 그 시점에 적용되는 정책으로 계획을 세우므로, 유효 기간이 제때 시작하고 끝납니다.
- **백엔드 역할이 곧 그룹입니다.** 그룹에 대한 정책은 같은 이름의 백엔드 역할을 가진 내부 사용자와, GrantForge가 사용자를 넣은 그룹에 적용됩니다.
- **직접 변경한 것은 되돌립니다.** 에이전트는 동기화할 때마다 자신의 역할과 매핑을 보안 플러그인이 보관한 것과 비교하고, 다른 부분을 다시 씁니다. 바뀐 것이 없으면 아무것도 보내지 않습니다.
- **GrantForge 밖의 역할을 보고합니다.** OpenSearch는 사용자의 역할 중 어느 하나라도 주는 것을 허용하므로, 에이전트는 사용자에게 매핑된 다른 역할이 주는 것을 빼앗을 수 없습니다. 정책이 거부하는 것을 주는 그런 역할마다 한 번씩 역할, 사용자, 접근, 인덱스와 정책을 밝혀 경고하며, 어떤 역할로도 묶이지 않는 `all_access`에 매핑된 사용자도 경고합니다.
- **접근은 감사 로그에서 옵니다.** 에이전트는 보안 플러그인이 쓰는 인덱스 `security-auditlog-*`에서 감사 로그를 읽습니다: 거부된 것은 `MISSING_PRIVILEGES`로, 허용된 것은 감사 구성이 기록할 때 `GRANTED_PRIVILEGES`로 읽습니다. 각 이벤트는 사용자, 접근 유형, 접근한 각 인덱스나 데이터 스트림 또는 클러스터를 밝히고, GrantForge가 결정했는지 GrantForge 밖의 역할이 허용했는지를 알려 줍니다.

| 액션 | 접근 이벤트 |
| --- | --- |
| `indices:data/read/*`: 검색, 조회, 카운트 | 각 인덱스 또는 데이터 스트림의 `read` |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/aliases*`, `indices:monitor/*` | `manage_aliases`, `monitor` |
| 그 밖의 `indices:admin/*` | `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*`와 `repository/*` | 클러스터의 `cluster_monitor`, `manage_snapshots` |
| 인덱스·컴포넌트 템플릿, 수집 파이프라인 | 클러스터의 `manage_templates`, `manage_pipelines` |

## 배포

1. `opensearch` 데이터 서비스를 추가하고 정책을 구성합니다. **데이터 권한 → 에이전트**를 열어 서비스를 선택하고 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되니 에이전트 계정만 읽을 수 있는 파일에 저장하세요.
2. 보안 플러그인 구성을 바꿀 수 있는 사용자를 에이전트에 줍니다: `plugins.security.restapi.roles_enabled`에 나열된 동안 `all_access`에 매핑된 사용자, 또는 `plugins.security.authcz.admin_dn`이 지정한 관리자 인증서입니다. 테스트는 데모 구성의 `admin`과 그 관리자 인증서로 에이전트를 실행합니다.
3. 접근을 보고하려면 감사 로그를 클러스터에 보관하세요(`plugins.security.audit.type: internal_opensearch`). OpenSearch가 허용한 것까지 보고하려면 감사 구성의 비활성화된 범주에서 `GRANTED_PRIVILEGES`도 빼세요. 거부는 기본으로 기록됩니다.
4. 예시 주소, 경로와 이름을 바꿔 설정 파일을 작성합니다:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/opensearch-agent.token
   grantforge.instance=opensearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/opensearch-agent
   grantforge.opensearch.hosts=https://opensearch1.internal:9200,https://opensearch2.internal:9200
   grantforge.opensearch.user=grantforge
   grantforge.opensearch.password.file=/etc/grantforge/opensearch-agent.password
   grantforge.opensearch.tls.ca=/etc/grantforge/opensearch-ca.pem
   ```

   이 경로들은 에이전트 장비에 있으며, 캐시 디렉터리는 쓸 수 있어야 합니다. 비밀번호 파일은 UTF-8로 읽고 앞뒤 공백을 제거합니다.
5. 예를 들어 systemd 서비스로 에이전트를 시작합니다:

   ```bash
   java -jar agents/opensearch/grantforge-agent-opensearch-<version>.jar /etc/grantforge/opensearch-agent.properties
   ```

   `<version>`을 배포 버전으로 바꾸세요. `java -Dgrantforge.…=… -jar …`로 준 설정이 파일보다 우선합니다. 에이전트는 SIGTERM에 깔끔하게 멈추고 대기 중인 이벤트를 보내거나 스풀링하며, OpenSearch는 받은 것을 유지합니다.
6. **기존 역할을 정책으로 옮기기:** GrantForge 밖에서 사용자에게 매핑된 역할은 에이전트의 역할과 함께 계속 적용됩니다. 대응하는 정책을 만든 뒤 그 매핑을 제거하세요. 에이전트의 경고가 정책이 거부하는 것을 주는 역할을 알려 줍니다.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 에이전트의 이름, 그리고 서명된 스냅샷, 스풀된 이벤트와 감사 로그 읽기 위치를 둘 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사합니다. 생략하면 처음 연결할 때 서버에서 가져옵니다 |
| `grantforge.opensearch.hosts` | 필수: URL 형식의 노드, 하나에 연결할 수 없으면 차례로 시도합니다. 스킴이 없으면 `https`, 포트 기본값은 `9200` |
| `grantforge.opensearch.user`, `grantforge.opensearch.password.file` | 에이전트의 사용자와 그 비밀번호 파일 |
| `grantforge.opensearch.tls.cert`, `grantforge.opensearch.tls.key` | 대신: 인증서로 로그인하기 위한 PEM 클라이언트 인증서와 그 개인 키, 함께 지정. `grantforge.opensearch.tls.key.passphrase.file`에는 암호화된 키의 암호 문구를 둡니다 |
| `grantforge.opensearch.ssl.verification.mode` | `full`(기본값)은 노드의 인증서와 이름을, `certificate`는 인증서만 확인하고, `none`은 확인하지 않습니다 |
| `grantforge.opensearch.tls.ca` | 노드를 검증하는 PEM CA 인증서. 비우면 Java가 신뢰하는 것을 신뢰합니다 |
| `grantforge.opensearch.timeout.ms` | 연결과 요청에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |
| `grantforge.opensearch.role.prefix` | 에이전트 역할 이름의 시작, 기본값 `grantforge_`. 소문자, 숫자, 하이픈과 밑줄로 이루어지고 밑줄로 끝나며 최대 32자입니다. 이름이 이것으로 시작하고 에이전트가 표시한 역할이 에이전트의 것입니다 |
| `grantforge.opensearch.unmanaged.users` | 에이전트가 건드리지 않는 사용자, 쉼표로 구분, 예: 서비스 계정. 자신의 사용자와 `all_access`에 매핑된 사용자는 항상 건드리지 않습니다 |
| `grantforge.sync.interval.ms` | 에이전트가 OpenSearch를 맞추는 주기, 기본값 `10000`, 범위 `1000..3600000` 밀리초 |
| `grantforge.audit.log` | 기본값 `true`. `false`면 감사 로그를 읽지 않고 접근을 보고하지 않습니다 |
| `grantforge.audit.log.index`, `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.documents` | 감사 로그의 인덱스, 기본값 `security-auditlog-*`; 읽는 주기, 기본값 `5000` 밀리초; 한 번에 읽는 최대 문서 수, 기본값 `1000`으로, 밀린 것은 여러 번에 걸쳐 읽습니다 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결·읽기 시간 초과, 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 요청하는 주기, 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 주기(기본값 `5000` 밀리초)와 디스크 스풀 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 같은 허용 접근을 이벤트 하나로 묶는 시간, 기본값 `10000`, 최대 `3600000` 밀리초. `0`은 하나씩 보냅니다 |

## 사용자, 그룹과 역할

정책은 보안 플러그인이 아는 OpenSearch 사용자를 지정합니다. 에이전트는 내부 사용자, LDAP나 SAML 같은 디렉터리로 로그인할 수 있는 정책에 지정된 사용자, 그리고 이전에 역할을 유지하던 사용자의 역할을 유지하며, `kibanaserver` 같은 보안 플러그인의 예약·숨김 사용자는 건드리지 않습니다. 그룹은 내부 사용자의 백엔드 역할과 GrantForge가 사용자를 넣은 그룹입니다. 디렉터리가 로그인 때 주는 백엔드 역할은 에이전트가 알 수 없으니, 그런 사용자의 그룹은 GrantForge에서 지정하세요. 역할은 GrantForge가 사용자에게 주는 역할입니다.

## 결정과 제한 사항

- 스냅샷이 없으면 에이전트는 아무것도 바꾸지 않습니다. 사용하지 않는 서비스나 유형이 `opensearch`가 아닌 서비스에 묶인 토큰은 아무것도 부여하지 않습니다: 에이전트는 자신의 역할과 매핑을 제거하고, 보안 플러그인 자체의 역할이 적용됩니다.
- 에이전트가 실행되지 않는 동안 OpenSearch는 마지막으로 받은 것을 유지하고, 정책 변경은 에이전트를 기다립니다. 변경은 갱신 주기와 동기화 주기 안에 OpenSearch에 반영됩니다.
- 패턴이 아닌 이름으로 부여되는 인덱스는 다음 동기화 때 권한을 받습니다.
- 별칭은 그 뒤의 인덱스에 닿습니다: 별칭을 검색하려면 그것이 담은 모든 인덱스에 권한이 필요하므로, 정책은 별칭이 아니라 인덱스와 데이터 스트림을 지정합니다.
- 행 필터는 사용자가 읽을 수 있는 필드만 검사할 수 있으며, 마스크는 text와 keyword 필드의 값에 적용됩니다.
- 이름이 점으로 시작하는 인덱스는 점으로 시작하는 정책 패턴으로만 닿습니다.
- 에이전트는 OpenSearch Dashboards를 위한 보안 플러그인의 테넌트를 관리하지 않습니다.

## 관련 가이드

- [OpenSearch](/ko/plugins/opensearch/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
