---
title: Apache Solr 에이전트
description: Solr 노드에 권한 부여 플러그인을 설치하여, GrantForge 정책으로 누가 어떤 컬렉션을 어떤 요청 경로로 조회·업데이트하고, 어떤 컬렉션과 클러스터를 관리하며, 어떤 네트워크에서 접근할 수 있는지 결정합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Solr 에이전트는 노드의 권한 부여 플러그인입니다. Solr는 모든 요청에 대해 에이전트에 묻고, 에이전트는 `solr` 데이터 서비스의 서명된 정책으로 답합니다. 배포판은 이를 Java 11 JAR 하나, `agents/solr/grantforge-agent-solr-<version>.jar`로 제공하며 의존성은 내장되고 재배치되어 있습니다.

저장소의 Testcontainers 통합 테스트는 Solr `9.8.1`, `9.10.1`, `9.11.0`, `10.0.0`의 SolrCloud 노드 안에서 JAR를 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다. 서비스 플러그인은 [Apache Solr](/ko/plugins/solr/)를 참조하세요.

## 동작 방식

- **요청은 Solr가 분류하는 방식대로 분류됩니다.** 각 요청 핸들러는 `read`나 `collection-admin-edit`처럼 필요한 권한을 Solr 자체의 규칙 기반 플러그인에 하듯 밝힙니다. 에이전트는 그 이름을 접근 유형으로 바꾸며, 아무것도 밝히지 않는 핸들러는 요청 유형으로 판정합니다.
- **컬렉션에 대한 모든 요청은 요청 경로에서도 판정됩니다.** 에이전트는 `/select`를 통한 `sales` 조회를 컬렉션 `sales`와 "`sales`와 경로 `/select`"에서 판정합니다. 어느 한쪽의 거부가 요청을 거부하므로, 정책은 컬렉션의 핸들러 하나를 거부할 수 있습니다.
- **팀은 자신의 컬렉션을 관리합니다.** 컬렉션을 지정하는 Collections API 요청은 지정된 각 컬렉션의 `admin_collection`이 필요하므로, 정책은 팀이 이름이 자신의 접두사로 시작하는 컬렉션만 만들고 다시 로드하거나 삭제하게 할 수 있습니다.
- **클러스터 관리자는 무엇이든 할 수 있습니다.** 클러스터의 `admin`은 Solr 자체의 `all` 권한처럼 모든 요청을 허용합니다.
- **정책은 일부 네트워크에만 적용될 수 있습니다.** 모든 확인에는 클라이언트 주소가 포함됩니다. 에이전트가 신뢰하는 리버스 프록시 뒤에서 클라이언트는 신뢰하는 프록시가 추가하지 않은 `X-Forwarded-For`의 마지막 주소입니다.
- **JSON Web Token이 검증한 역할은 그룹으로 취급됩니다.** Solr의 JWT 인증을 사용하면 토큰의 클레임이 사용자에게 주는 역할이 결정의 그룹이 되며, GrantForge가 사용자를 넣은 그룹과 함께 사용됩니다.
- **거부는 이유와 함께 로그에 남습니다.** Solr는 거부된 요청에 자체 403으로 응답하고, 노드 로그에는 이를 거부한 GrantForge 정책이나 허용하는 정책이 없다는 사실이 기록됩니다.
- **결정은 캐시되고 접근은 합쳐집니다.** 현재 스냅샷의 결정은 기본적으로 100,000개를 10초 동안 보관하며, 새 스냅샷은 처음부터 시작합니다. 창(기본 10초) 안의 동일한 허용 접근은 횟수와 함께 하나의 접근 이벤트로 보고되고, 거부는 즉시 하나씩 보고됩니다. 모든 이벤트에는 컬렉션과 경로 또는 클러스터, 그리고 클라이언트 주소가 담깁니다.
- **노드 자체는 확인하지 않습니다.** Solr가 노드 자신으로 서명하는 노드 간 요청은 통과하며, 사용자가 없는 요청은 로그인하라는 응답을 받습니다.

| 요청 | 필요한 접근 |
| --- | --- |
| 읽기 핸들러를 통한 검색, 가져오기, 내보내기, 조회 | 컬렉션과 경로의 `query` |
| 문서 추가, 업데이트 또는 삭제 | 컬렉션과 경로의 `update` |
| 컬렉션 schema 읽기 또는 변경 | `schema_read` 또는 `schema_edit` |
| 컬렉션 config 읽기 또는 변경 | `config_read` 또는 `config_edit` |
| 컬렉션을 지정하는 Collections API 요청: 생성, 다시 로드, 백업, 복원, 삭제, 수정, 그 클러스터 상태 | 지정된 각 컬렉션의 `admin_collection` |
| 상태 확인; 지표 | 클러스터의 `health`; `metrics` |
| 컬렉션 나열, core 상태, ZooKeeper·파일 저장소·패키지 읽기 | 클러스터의 `admin_read` |
| 인증과 권한 부여 설정 읽기 또는 변경 | 클러스터의 `security` |
| core 관리, 파일 저장소와 패키지 변경, 어떤 핸들러도 밝히지 않는 작업 | 클러스터의 `admin` |

## 배포

1. `solr` 데이터 서비스를 추가하고 정책을 설정합니다. Solr 관리자를 위한 클러스터의 `admin`도 포함합니다. **데이터 권한 → 에이전트**를 열고 서비스를 선택한 뒤 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되므로 Solr 실행 계정만 읽을 수 있는 파일에 저장합니다.
2. JAR를 **모든 노드**의 `server/solr-webapp/webapp/WEB-INF/lib`(예: `/opt/solr/server/solr-webapp/webapp/WEB-INF/lib`)에 복사하고 노드를 하나씩 다시 시작합니다.
3. 클러스터 `security.json`의 권한 부여 섹션을 인증 플러그인 옆에 설정하고, 예시의 주소, 경로, 인스턴스 이름을 바꿉니다.

   ```json
   {
     "authentication": {
       "class": "solr.BasicAuthPlugin",
       "blockUnknown": true,
       "credentials": { "solr": "<salted SHA-256 hash> <salt>" }
     },
     "authorization": {
       "class": "org.devlive.grantforge.solr.agent.GrantForgeSolrAuthorizer",
       "grantforge.server.url": "https://grantforge.example.com",
       "grantforge.token.file": "/var/solr/data/grantforge.token",
       "grantforge.instance": "solr-cluster",
       "grantforge.cache.dir": "/var/solr/data/grantforge-cache"
     }
   }
   ```

   `bin/solr zk cp file:security.json zk:/security.json -z <zookeeper>`로 업로드하면 노드가 다시 시작하지 않고 적용합니다. 요청에 사용자가 있도록 인증이 활성화되어 있어야 합니다.
4. **Solr 9에서는 Solr가 기본으로 활성화하는 Java 보안 관리자에 주의하세요.** 토큰, 고정한 키, 캐시 디렉터리는 `/var/solr/data` 같은 `SOLR_HOME` 아래에 두고, Solr 자체 정책 외에 에이전트에 필요한 권한을 `server/etc/security.policy` 또는 `-Djava.security.properties`가 `policy.url.3`으로 추가하는 별도 정책 파일에 부여합니다.

   ```text
   grant {
     permission java.security.SecurityPermission "getProperty.org.devlive.grantforge.solr.agent.internal.bouncycastle.*";
   };
   ```

   Solr 10은 보안 관리자 없이 실행됩니다.
5. **기존 권한을 먼저 옮기세요:** Solr 규칙 기반 플러그인의 권한과 사용자 역할은 더 이상 적용되지 않습니다. 대응하는 정책을 미리 만드세요. 에이전트가 첫 스냅샷을 받을 때까지 노드 간 요청을 제외한 요청은 거부됩니다.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 클러스터 노드의 이름, 서명된 스냅샷과 대기 중인 이벤트를 위한 쓰기 가능한 디렉터리 |
| `grantforge.trusted.proxies` | 에이전트가 `X-Forwarded-For`를 신뢰하는 리버스 프록시, 쉼표로 구분한 주소 또는 범위. 기본값은 없음 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택적 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사합니다. 생략하면 첫 연결 때 서버에서 키를 가져옵니다 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결 및 읽기 타임아웃, 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 요청하는 주기, 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 간격(기본값 `5000` 밀리초), 디스크 버퍼 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 동일한 허용 접근을 하나의 이벤트로 합치는 시간, 기본값 `10000`, 최대 `3600000` 밀리초. `0`은 하나씩 보고 |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | 보관하는 결정의 최대 수, 기본값 `100000`, 최대 `10000000`, `0`은 보관하지 않음. 그리고 보관 기간, 기본값 `10000`, `1..3600000` 밀리초 |

## 사용자, 그룹과 역할

정책은 Solr 인증이 로그인시킨 사용자, 예컨대 basic 인증 사용자나 JSON Web Token의 주체를 지정합니다. 그룹은 JSON Web Token이 사용자에 대해 검증한 역할과 GrantForge가 사용자를 넣은 그룹이고, 역할은 GrantForge가 사용자에게 준 역할입니다.

## 결정과 제한 사항

- 사용할 수 있는 스냅샷이 없으면 노드 간 요청을 제외한 모든 것이 거부됩니다. 사용하지 않는 서비스는 권한 부여 플러그인이 없을 때처럼 Solr를 확인하지 않습니다.
- 타입이 `solr`가 아닌 서비스에 바인딩된 토큰은 모든 것을 거부합니다.
- 일부 v2 API 요청처럼 JSON 본문에서만 컬렉션을 지정하는 Collections API 요청은 에이전트가 볼 수 있는 컬렉션을 지정하지 않으므로 클러스터의 `admin`이 필요합니다.
- Solr는 묻기 전에 별칭을 컬렉션으로 해석하므로, 정책에는 별칭이 아닌 컬렉션을 적습니다.
- Solr의 `/admin/authorization` API로는 에이전트의 규칙을 편집할 수 없습니다. 정책은 GrantForge가 보관합니다.
- 클라이언트 주소는 주소와 범위만 가능하며, 호스트 이름은 아무것과도 일치하지 않습니다.
- 결정은 `grantforge.decision.cache.ttl.ms` 동안 보관되므로, 정책이 적용되거나 해제되기까지 최대 그만큼 늦어질 수 있습니다.

## 관련 가이드

- [Apache Solr](/ko/plugins/solr/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
