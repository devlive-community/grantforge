---
title: OpenSearch
description: OpenSearch 서비스 플러그인을 설치·구성하고, 하나 또는 여러 노드에 연결하며, 비밀번호나 TLS 클라이언트 인증서로 로그인하고, 인덱스·데이터 스트림·필드·클러스터를 조회하며, 필드와 마스킹된 값, 문서 단위까지 누가 무엇을 읽고 쓰고 관리할 수 있는지 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

OpenSearch 플러그인은 인덱스, 데이터 스트림, 필드, 클러스터를 조회하고, OpenSearch 자체의 방식으로 접근 정책을 관리합니다: 필드 읽기, 인덱스 쓰기·삭제·생성·관리, 그리고 클러스터 자체의 작업이며, 필드 마스킹과 인덱스의 행 필터도 지원합니다. 플러그인 ID와 서비스 타입은 모두 `opensearch`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, 클러스터 옆에서 실행되는 [OpenSearch 에이전트](/ko/external/opensearch-agent/)가 보안 플러그인 자체의 역할, 필드 수준 보안, 마스킹된 필드와 문서 수준 보안으로 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 보안 플러그인이 있는 OpenSearch `2.19`, `3.3`, `3.6`, `3.9`를 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/opensearch`에 이 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `opensearch`가 활성화되어 있는지 확인하고, 플러그인 디렉터리를 갱신한 뒤에는 다시 스캔하세요. 플러그인은 자체 클래스 로더 안에서 OpenSearch 자체의 저수준 REST 클라이언트로 노드와 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 OpenSearch(`opensearch`)를 선택합니다.
2. 노드 하나 또는 여러 노드를 URL로 입력합니다.
3. 조회 사용자의 로그인 방식을 고르고 자격 증명을 입력한 뒤, 연결을 테스트하고 서비스를 저장합니다. 테스트는 로그인하여 클러스터를 설명합니다: OpenSearch 버전과 클러스터 이름, 로그인한 사용자, 클러스터의 인덱스와 데이터 스트림 수입니다. 보안 플러그인이 없는 클러스터는 테스트에 실패합니다. 에이전트가 보안 플러그인으로 정책을 적용하기 때문입니다.

조회 사용자는 GrantForge가 리소스를 찾게 해 주며, 읽고 쓰는 사용자와는 별개입니다. 연결 테스트가 성공했다고 해서 접근이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `opensearch.hosts` | 필수: URL 형식의 노드, 예: `https://opensearch1.example.com:9200`. 여러 개는 쉼표로 구분하며, 하나에 연결할 수 없으면 차례로 시도합니다. 스킴이 없으면 `https`, 포트 기본값은 `9200`이며, 한 서비스의 노드는 모두 `http`이거나 모두 `https`입니다 |
| `authentication` | `password`(기본값) 또는 `certificate`: 보안 플러그인이 사용자에 대응시키는 TLS 클라이언트 인증서 |
| `username`, `password` | password: 조회 사용자와 그 비밀번호, 암호화되어 저장됨 |
| `ssl.verificationMode` | OpenSearch Dashboards의 이름과 같습니다: `full`(기본값)은 노드의 인증서와 이름을, `certificate`는 인증서만 확인하고, `none`은 확인하지 않습니다 |
| `ssl.certificateAuthorities` | 노드를 검증하는 **GrantForge 서버**의 PEM CA 인증서 경로. 비우면 JVM의 것을 사용합니다 |
| `ssl.certificate`, `ssl.key` | certificate: GrantForge 서버에 있는 PEM 클라이언트 인증서와 그 개인 키의 경로, 키는 PKCS #8 또는 이전 OpenSSL 형식. `https` 노드가 필요합니다 |
| `ssl.keyPassphrase` | certificate: 암호화된 개인 키의 암호 문구, 암호화되어 저장됨 |
| `timeout` | 연결과 요청에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |

한 로그인 방식의 설정은 다른 방식에서 거부되므로, 서비스는 어떻게 로그인하는지 정확히 밝힙니다. 거부된 로그인, 부족한 권한, 연결할 수 없는 노드와 검증할 수 없는 인증서는 그대로 보고됩니다.

### 조회 사용자에게 보이는 것

조회 사용자는 자신이 나열하는 것을 읽을 수 있어야 합니다: 인덱스와 데이터 스트림은 OpenSearch의 resolve API, 필드는 필드 기능(field capabilities), 클러스터 이름은 클러스터 설명에서 옵니다. 내장 `readall` 역할이 앞의 두 가지를 줍니다. `cluster:monitor/main`이 없는 사용자도 연결 테스트를 통과하며, 이때 테스트는 버전을 표시하지 않습니다. 이름이 점으로 시작하는 인덱스와 데이터 스트림, 즉 숨김·시스템 인덱스는 입력이 점으로 시작할 때만 나열되고, 데이터 스트림의 백킹 인덱스는 해당 데이터 스트림으로 나열됩니다. 필드는 문서의 필드입니다: OpenSearch 자체의 메타데이터 필드, 객체, 그리고 `name.keyword`처럼 필드를 한 번 더 색인하는 멀티 필드는 제외됩니다. 필드 수준 보안이 이들을 원래 필드와 함께 보이거나 숨기기 때문입니다.

## 리소스와 접근 유형

| 레벨 | 매칭 | 접근 유형 |
| --- | --- | --- |
| `index` | 와일드카드, 대소문자 구분; 인덱스 또는 데이터 스트림의 이름 | `index`, `delete`, `create_index`, `manage_aliases`, `monitor`, `manage` |
| `field` | 와일드카드, 대소문자 구분; 인덱스 아래, `address.city` 같은 필드 경로 | `read` |
| `cluster` | 와일드카드, 대소문자 구분; 클러스터의 이름 | `cluster_monitor`, `manage_snapshots`, `manage_templates`, `manage_pipelines` |

| 접근 유형 | 보안 플러그인의 액션 그룹 |
| --- | --- |
| `read` | `read`: 검색, 조회, 카운트 등 |
| `index` | `index`: 문서 색인과 업데이트, bulk 포함 |
| `delete` | `delete`: 문서 삭제 |
| `create_index` | `create_index`: 인덱스 생성, 첫 쓰기가 인덱스를 만드는 경우 포함 |
| `manage_aliases`, `monitor` | `manage_aliases`, `indices_monitor` |
| `manage` | `manage`: 인덱스 삭제를 포함한 인덱스의 모든 관리 작업. `create_index`, `manage_aliases`, `monitor`를 포함합니다 |
| `cluster_monitor`, `manage_snapshots` | `cluster_monitor`, `manage_snapshots` |
| `manage_templates`, `manage_pipelines` | `cluster_manage_index_templates`, `cluster_manage_pipelines` |

읽기는 필드 수준 보안이 문서의 필드를 보여 주는 방식대로 필드별로 결정됩니다: `read` 정책은 필드를 지정하며 `*`는 모든 필드입니다. 따라서 정책은 인덱스를 허용하면서 그중 한 필드를 거부할 수 있고, 사용자는 나머지 모든 필드를 읽을 수 있습니다. 정책 값에서 `*`는 임의의 문자열에, `?`는 한 문자에 일치합니다. 정책 편집기는 인덱스와 데이터 스트림, 정책이 지정한 인덱스의 필드, 그리고 클러스터를 제안합니다.

정책이 허용하는 것을 쓰려면 OpenSearch에서 더 필요합니다: 읽기에는 멀티 검색, 멀티 조회와 스크롤이, 쓰기에는 bulk가 필요합니다. 이것들은 별도의 접근 유형이 아니며, 에이전트가 클러스터의 복합 작업과 함께 부여합니다.

### 마스킹

필드의 마스킹 정책은 보안 플러그인의 마스킹된 필드처럼 사용자에게 값을 마스킹해 보여 주며, 문서와 집계 모두에 적용됩니다:

| 마스크 | 사용자에게 보이는 것 |
| --- | --- |
| 가리기(Redact) | 대문자는 `X`, 소문자는 `x`, 숫자는 `n` |
| 부분 마스크: 마지막 4자 / 처음 4자 표시 | 나머지 문자는 모두 `x` |
| 해시(SHA-256) | 값의 SHA-256 해시 |
| 클러스터 솔트를 쓴 해시(BLAKE2b) | 클러스터의 솔트를 키로 쓰는 보안 플러그인 자체의 해시. 같은 값은 같은 결과가 되지만 역으로 찾을 수 없습니다 |
| 사용자 지정 | `SHA-512` 같은 해시 알고리즘, 또는 `/[0-9]/::*` 같은 정규식과 그 대체 문자열 |
| 마스킹 안 함 | 값 그대로 |

### 행 필터

인덱스의 행 필터 정책은 쿼리가 일치하는 문서만 사용자에게 보여 줍니다. 쿼리는 문서 수준 보안이 받는 OpenSearch 쿼리 DSL로, 예를 들어 `{"term": {"region": "EU"}}`이며, `${user.name}` 같은 보안 플러그인의 치환을 쓸 수 있습니다. 다른 서비스와 마찬가지로 사용자는 자신에게 적용되는 첫 번째 정책의 필터를 받습니다. 행 필터는 사용자가 읽을 수 있는 필드만 검사할 수 있습니다: 필드 수준 보안이 쿼리에서도 다른 필드를 숨깁니다.

## 정책 예시

클러스터에 인덱스 `logs-2026.10.26`와 `hr`가 있다고 합시다. 같은 데이터 서비스에 다음과 같은 활성화된 정책을 만듭니다:

| 리소스 | 규칙 |
| --- | --- |
| `index=logs-*`, `field=*` | 그룹 `analysts`에 `read` 허용 |
| `index=logs-*`, `field=client_ip` | 그룹 `analysts`에 `read` 거부 |
| `index=logs-*` | 사용자 `logstash`에 `index`와 `create_index` 허용 |
| `index=hr`, `field=*` | 그룹 `hr`에 `read` 허용 |
| `index=hr`, `field=salary` (마스킹) | 그룹 `hr`에 클러스터 솔트를 쓴 해시 표시 |
| `index=hr` (행 필터) | 사용자 `emea-hr`에게 `{"term": {"region": "EMEA"}}`의 문서만 표시 |
| `cluster=*` | 그룹 `ops`에 `cluster_monitor` 허용 |

에이전트가 새 스냅샷을 적용하면 `analysts`의 구성원은 클라이언트 주소를 제외한 모든 로그 필드를 읽고, `logstash`는 내일의 `logs-2026.10.27`을 바로 쓰며 첫 문서로 그 인덱스를 만들고, `hr`의 구성원은 급여를 해시로 보며, `emea-hr`는 해당 지역의 직원만 보고, `ops`의 구성원은 클러스터의 상태와 통계를 읽습니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 클러스터마다 하나씩 OpenSearch 에이전트가 필요합니다. 배포와 점검은 [OpenSearch 에이전트](/ko/external/opensearch-agent/)에 있습니다.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [OpenSearch 에이전트](/ko/external/opensearch-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
