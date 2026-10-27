---
title: Elasticsearch 에이전트
description: 클러스터 옆에서 Elasticsearch 에이전트를 실행하여 GrantForge 정책을 Elasticsearch 자체의 역할로 만들고, 네이티브 사용자에게 주며 다른 렐름의 사용자에게는 매핑하고, 라이선스가 갖춘 곳에서는 필드·문서 수준 보안을 쓰며, 인덱스 패턴을 패턴으로 남겨 새 인덱스에 즉시 닿게 하고, 접근을 감사 로그 파일에서 보고합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Elasticsearch 에이전트는 클러스터 옆에서 실행되는 서비스입니다. Elasticsearch 자체의 접근 제어를 `elasticsearch` 데이터 서비스의 서명된 정책과 일치시킵니다: 사용자마다 정책이 그 사용자에게 주는 것만 정확히 담은 역할 하나를 유지하며, 클러스터의 라이선스가 갖춘 곳에서는 필드·문서 수준 보안을 포함합니다. 네이티브 사용자는 그 역할을 자신의 역할 중 하나로 가지며, LDAP, SAML, OpenID Connect 같은 다른 렐름의 사용자는 이름에 대한 역할 매핑으로 그것을 받습니다. 그러면 Elasticsearch가 모든 요청을 직접 검사하며, 에이전트는 클라이언트와 클러스터 사이에 끼지 않습니다. 배포판은 실행 가능한 JAR `agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar`로 제공하며, Java 17 이상이 필요합니다.

저장소의 Testcontainers 통합 테스트는 에이전트와 배포되는 JAR를 보안을 켠 Elasticsearch `8.19`, `9.3`, `9.4`, `9.5`에서 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아니며, 에이전트는 `8.19`보다 오래된 클러스터, 보안이 없는 클러스터, 그리고 [OpenSearch 에이전트](/ko/external/opensearch-agent/)가 맡는 OpenSearch 클러스터를 거부합니다. 서비스 플러그인은 [Elasticsearch](/ko/plugins/elasticsearch/)를 참고하세요.

## 동작 방식

- **사용자마다 역할 하나가 GrantForge가 주는 것을 담습니다.** 정책이 무언가를 주는 각 사용자에 대해 에이전트는 그 이름을 딴 역할, 예를 들어 `alice`에게는 `grantforge_alice`를 유지합니다. 역할의 메타데이터가 사용자를 가리키고 에이전트의 것임을 표시하며, 에이전트는 이 표시가 없는 역할을 자신의 것으로 여기지 않으므로 다른 역할을 바꾸지 않습니다.
- **네이티브 사용자는 역할을 가지고, 다른 렐름은 이름으로 받습니다.** 에이전트는 네이티브 사용자의 역할에 그 역할을 더하고 사용자의 다른 역할은 모두 유지합니다. 역할 매핑은 네이티브 렐름과 파일 렐름을 뺀 모든 렐름에 적용되므로, 에이전트는 사용자의 이름에 역할을 주는 같은 이름의 역할 매핑도 유지합니다. 이는 LDAP, Active Directory, SAML, OpenID Connect, JWT, Kerberos 또는 PKI로 로그인하는 사용자를 위한 것입니다. 파일 렐름은 Elasticsearch의 API로 바꿀 수 없으므로, 그 사용자는 역할을 받지 않습니다.
- **인덱스 패턴은 패턴으로 남습니다.** `logs-*` 같은 정책의 인덱스 패턴은 모든 정책이 그 범위 전체에서 똑같이 결정하는 곳에서 패턴으로 부여됩니다: 인덱스가 패턴과 겹치는 모든 종류의 모든 정책이 그 패턴 전체를 포괄해야 합니다. 에이전트는 패턴의 유한 오토마타로 이를 정확히 판단합니다. 그러면 내일의 로그처럼 나중에 만들어지는 인덱스도 즉시 닿을 수 있으며, 첫 문서를 쓸 때 인덱스가 만들어지는 경우도 마찬가지입니다. `logs-*` 안에서 `logs-secret`을 거부하는 것처럼 정책이 패턴 안에서 다르게 결정하면, 에이전트는 클러스터에 있는 각 인덱스와 데이터 스트림을 이름으로 부여하고 새로 생긴 것은 다음 동기화 때 부여합니다.
- **아래에서 다르게 결정되는 경우에만 필드 수준 보안을 씁니다.** 사용자가 전체를 읽을 수 있고 어떤 정책도 필드를 빼앗을 수 없는 인덱스는 필드 수준 보안 없이 읽으므로, 나중에 추가된 필드도 즉시 읽힙니다. 그 밖에는 문서에 있는 필드와 정책이 지정한 필드 가운데 정책이 허용하는 필드를 읽으며, `name`과 함께 `name.keyword`처럼 각 필드는 그 멀티 필드와 함께 부여됩니다.
- **행 필터는 문서 수준 보안입니다.** 행 필터는 항목의 쿼리가 됩니다. Elasticsearch의 validate API가 부여 전에 각 쿼리를 검사하며, Elasticsearch가 실행하지 않을 쿼리는 사용자에게 문서를 하나도 보여 주지 않고 로그에 남습니다.
- **Platinum 미만에서도 정책이 허용하는 것 이상은 주지 않습니다.** Elasticsearch의 문서·필드 수준 보안은 Platinum 라이선스부터 쓸 수 있고, 그보다 낮으면 이를 쓰는 모든 역할을 끕니다. 에이전트는 동기화할 때마다 라이선스를 읽습니다. Platinum 미만에서는 정책이 인덱스의 필드나 문서를 제한해서 둘 중 하나가 필요한 읽기를 아예 부여하지 않으며, 어떤 사용자가 어떤 인덱스를 왜 읽을 수 없는지 로그에 남깁니다. 인덱스 전체 읽기와 그 밖의 모든 것은 평소처럼 부여됩니다.
- **권한 집합마다 항목 하나.** 권한이 같은 인덱스와 패턴은 항목 하나를 함께 씁니다. Elasticsearch가 겹치는 항목의 필드·문서 수준 보안을 합치기 때문에 항목끼리는 절대 겹치지 않습니다.
- **현재 시점의 정책.** 각 동기화는 그 시점에 적용되는 정책으로 계획을 세우므로, 유효 기간이 제때 시작하고 끝납니다.
- **네이티브 사용자의 역할이 곧 그룹입니다.** 그룹에 대한 정책은 같은 이름의 역할을 가진 네이티브 사용자와, GrantForge가 사용자를 넣은 그룹에 적용됩니다.
- **수동 변경은 되돌려집니다.** 에이전트는 동기화할 때마다 자신의 역할, 역할 매핑과 네이티브 사용자의 역할을 Elasticsearch가 가진 것과 비교하여 다른 부분을 다시 쓰고, 한 사용자의 역할을 다른 사용자가 가지고 있으면 회수합니다. 역할은 주기 전에 한 번의 요청으로 쓰고, 삭제하기 전에 회수합니다. 아무것도 바뀌지 않았으면 아무것도 보내지 않으며, Elasticsearch가 거부한 변경은 로그에 남고 다른 변경을 멈추지 않습니다.
- **GrantForge 밖의 역할을 보고합니다.** Elasticsearch는 사용자의 어떤 역할이든 주는 것을 허용하므로, 에이전트는 다른 역할이 주는 것을 빼앗을 수 없습니다. 네이티브 사용자가 가졌거나 사용자 이름에 대한 역할 매핑이 주는 역할 가운데 정책이 거부하는 것을 주는 역할마다 한 번, 역할, 사용자, 접근, 인덱스와 정책을 밝혀 경고하며, 어떤 역할로도 제한되지 않는 `superuser` 역할을 가진 사용자도 경고합니다.
- **접근은 감사 로그 파일에서 옵니다.** Elasticsearch는 감사 로그를 각 노드의 파일에만 씁니다. 에이전트는 지정된 파일을 지난번 읽기가 끝난 곳부터 읽고, Elasticsearch가 파일을 순환시켜도 따라가며, `access_granted`와 `access_denied` 이벤트를 보고합니다. 각 이벤트는 사용자, 접근 유형, 닿은 각 인덱스나 데이터 스트림 또는 클러스터를 밝히고, GrantForge가 결정했는지 GrantForge 밖의 역할이 허용했는지 알려 줍니다.

| 동작 | 접근 이벤트 |
| --- | --- |
| `indices:data/read/*`: 검색, 조회, 개수 세기 | 각 인덱스 또는 데이터 스트림의 `read` |
| `indices:data/write/index`, `update`, `bulk` | `index` |
| `indices:data/write/delete` | `delete` |
| `indices:admin/create`, `auto_create`, `data_stream/create` | `create_index` |
| `indices:admin/delete`, `data_stream/delete` | `delete_index` |
| `indices:admin/get`, `mappings/get`, `settings/get`, `resolve/*` | `view_index_metadata` |
| `indices:monitor/*`; 그 밖의 `indices:admin/*` | `monitor`; `manage` |
| `cluster:monitor/*`, `cluster:admin/snapshot/*`와 `repository/*`, `slm/*` | 클러스터의 `cluster_monitor`, `create_snapshot`, `manage_slm` |
| 인덱스·컴포넌트 템플릿, 수집 파이프라인, `cluster:admin/ilm/*` | 클러스터의 `manage_index_templates`, `manage_ingest_pipelines`, `manage_ilm` |

## 배포

1. `elasticsearch` 데이터 서비스를 추가하고 정책을 구성합니다. **데이터 권한 → 에이전트**를 열어 서비스를 선택하고 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되니 에이전트 계정만 읽을 수 있는 파일에 저장하세요.
2. 역할, 역할 매핑과 사용자를 관리하는 클러스터 권한 `manage_security`와, 모든 인덱스의 매핑을 읽고 쿼리를 검증할 권리를 가진 사용자를 에이전트에 줍니다. 예를 들어 `superuser` 역할을 가진 사용자입니다. 비밀번호, 그 사용자가 만든 API 키, 또는 PKI 렐름이 그 사용자에 대응시키는 클라이언트 인증서로 로그인할 수 있습니다.
3. 접근을 보고하려면 Gold 이상의 라이선스가 필요한 감사 로그를 켜고(`xpack.security.audit.enabled: true`), 에이전트가 노드의 감사 로그 파일, 즉 각 노드의 로그 디렉터리에 있는 `<클러스터 이름>_audit.json`을 읽을 수 있게 하세요. 노드에서 에이전트를 실행하거나, 에이전트가 읽을 수 있는 디렉터리로 파일을 모으면 됩니다. `xpack.security.audit.logfile.events.include: access_granted, access_denied`로 로그를 에이전트가 보고하는 것으로 줄일 수 있습니다.
4. 예시 주소, 경로와 이름을 바꿔 설정 파일을 씁니다:

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/elasticsearch-agent.token
   grantforge.instance=elasticsearch-agent-1
   grantforge.cache.dir=/var/lib/grantforge/elasticsearch-agent
   grantforge.elasticsearch.hosts=https://es1.internal:9200,https://es2.internal:9200
   grantforge.elasticsearch.user=grantforge
   grantforge.elasticsearch.password.file=/etc/grantforge/elasticsearch-agent.password
   grantforge.elasticsearch.tls.ca=/etc/grantforge/http_ca.crt
   grantforge.audit.log.files=/var/log/elasticsearch/production_audit.json
   ```

   이 경로는 에이전트 머신에 있으며, 캐시 디렉터리는 쓰기 가능해야 합니다. 비밀번호와 키 파일은 UTF-8로 읽고 앞뒤 공백을 지웁니다.
5. 예를 들어 systemd 서비스로 에이전트를 시작합니다:

   ```bash
   java -jar agents/elasticsearch/grantforge-agent-elasticsearch-<version>.jar /etc/grantforge/elasticsearch-agent.properties
   ```

   `<version>`을 배포 버전으로 바꾸세요. `java -Dgrantforge.…=… -jar …`로 준 설정이 파일보다 우선합니다. 에이전트는 SIGTERM에 깔끔하게 멈추고 대기 중인 이벤트를 보내거나 스풀링하며, Elasticsearch는 받은 것을 유지합니다.
6. **기존 역할을 정책으로 옮기기:** GrantForge 밖에서 사용자가 가진 역할은 에이전트의 역할과 함께 계속 적용됩니다. 대응하는 정책을 만든 뒤 그 역할을 사용자에게서 빼세요. 에이전트의 경고가 정책이 거부하는 것을 주는 역할을 알려 줍니다.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 에이전트의 이름, 그리고 서명된 스냅샷, 스풀된 이벤트와 감사 로그 읽기 위치를 둘 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사합니다. 생략하면 처음 연결할 때 서버에서 가져옵니다 |
| `grantforge.elasticsearch.hosts` | 필수: URL 형식의 노드, 하나에 연결할 수 없으면 차례로 시도합니다. 스킴이 없으면 `https`, 포트 기본값은 `9200` |
| `grantforge.elasticsearch.user`, `grantforge.elasticsearch.password.file` | 에이전트의 사용자와 그 비밀번호 파일 |
| `grantforge.elasticsearch.api.key.file` | 대신: Elasticsearch가 생성할 때 돌려주는 그대로의 인코딩된 API 키 파일 |
| `grantforge.elasticsearch.tls.cert`, `grantforge.elasticsearch.tls.key` | 대신: 인증서로 로그인하기 위한 PEM 클라이언트 인증서와 그 개인 키, 함께 지정. `grantforge.elasticsearch.tls.key.passphrase.file`에는 암호화된 키의 암호 문구를 둡니다 |
| `grantforge.elasticsearch.ssl.verification.mode` | `full`(기본값)은 노드의 인증서와 이름을, `certificate`는 인증서만 확인하고, `none`은 확인하지 않습니다 |
| `grantforge.elasticsearch.tls.ca` | 노드를 검증하는 PEM CA 인증서, 예: Elasticsearch의 `http_ca.crt`. 비우면 Java가 신뢰하는 것을 신뢰합니다 |
| `grantforge.elasticsearch.timeout.ms` | 연결과 요청에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |
| `grantforge.elasticsearch.role.prefix` | 에이전트 역할 이름의 시작, 기본값 `grantforge_`. 소문자, 숫자, 하이픈과 밑줄로 이루어지고 밑줄로 끝나며 최대 32자입니다. 이름이 이것으로 시작하고 에이전트가 표시한 역할이 에이전트의 것입니다 |
| `grantforge.elasticsearch.unmanaged.users` | 에이전트가 건드리지 않는 사용자, 쉼표로 구분, 예: 서비스 계정. 자신의 사용자, Elasticsearch의 예약 사용자와 `superuser` 역할을 가진 사용자는 항상 건드리지 않습니다 |
| `grantforge.sync.interval.ms` | 에이전트가 Elasticsearch를 맞추는 주기, 기본값 `10000`, 범위 `1000..3600000` 밀리초 |
| `grantforge.audit.log.files` | 읽을 감사 로그 파일, 쉼표로 구분. 지정하지 않으면 감사 로그를 읽지 않고 접근을 보고하지 않습니다 |
| `grantforge.audit.log.interval.ms`, `grantforge.audit.log.max.lines` | 파일을 읽는 주기, 기본값 `5000` 밀리초; 한 번 읽을 때 각 파일에서 가져오는 최대 줄 수, 기본값 `5000`으로, 밀린 것은 여러 번에 걸쳐 읽습니다 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결·읽기 시간 초과, 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 요청하는 주기, 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 주기(기본값 `5000` 밀리초)와 디스크 스풀 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 같은 허용 접근을 이벤트 하나로 묶는 시간, 기본값 `10000`, 최대 `3600000` 밀리초. `0`은 하나씩 보냅니다 |

## 사용자, 그룹과 역할

정책은 Elasticsearch 사용자를 사용자 이름으로 지정합니다. 에이전트는 네이티브 사용자, 다른 렐름으로 로그인할 수 있는 정책에 지정된 사용자, 그리고 이전에 역할을 유지하던 사용자의 역할을 유지하며, `kibana_system` 같은 Elasticsearch의 예약 사용자는 건드리지 않습니다. 그룹은 네이티브 사용자의 다른 역할과 GrantForge가 사용자를 넣은 그룹입니다. 디렉터리가 로그인 때 주는 그룹은 에이전트가 알 수 없으니, 그런 사용자의 그룹은 GrantForge에서 지정하세요. 역할은 GrantForge가 사용자에게 주는 역할입니다.

## 결정과 제한 사항

- 스냅샷이 없으면 에이전트는 아무것도 바꾸지 않습니다. 사용하지 않는 서비스나 유형이 `elasticsearch`가 아닌 서비스에 묶인 토큰은 아무것도 부여하지 않습니다: 에이전트는 자신의 역할을 회수하고 역할 매핑과 함께 삭제하며, Elasticsearch 자체의 역할이 적용됩니다.
- 에이전트가 실행되지 않는 동안 Elasticsearch는 마지막으로 받은 것을 유지하고, 정책 변경은 에이전트를 기다립니다. 변경은 갱신 주기와 동기화 주기 안에 Elasticsearch에 반영됩니다.
- 패턴이 아닌 이름으로 부여되는 인덱스는 다음 동기화 때 권한을 받습니다.
- Platinum 미만에서는 정책이 인덱스 읽기를 제한하는 사용자가 그 인덱스를 전혀 읽지 못하며, Gold 미만에서는 에이전트가 접근을 보고하지 않습니다.
- 사용자 이름에 대한 역할 매핑은 그 사용자 이름을 가진 모든 렐름에 적용됩니다. 정책은 렐름이 아니라 사용자를 지정합니다.
- 별칭은 그 뒤의 인덱스에 닿으므로, 정책은 별칭이 아니라 인덱스와 데이터 스트림을 지정합니다. 이름이 점으로 시작하는 인덱스는 점으로 시작하는 정책 패턴으로만 닿습니다.
- 에이전트는 Kibana의 스페이스와 애플리케이션 권한을 관리하지 않습니다.

## 관련 가이드

- [Elasticsearch](/ko/plugins/elasticsearch/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
