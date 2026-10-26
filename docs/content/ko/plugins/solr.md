---
title: Apache Solr
description: Solr 서비스 플러그인을 설치·구성하고, 하나 또는 여러 노드에 연결하며, basic 인증이나 JSON Web Token으로 로그인하고, 컬렉션과 그 요청 경로를 조회하며, 누가 무엇을 조회·업데이트·관리할 수 있는지 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Solr 플러그인은 컬렉션과 그 안의 요청 경로를 조회하고, Solr가 결정하는 일에 대한 접근 정책을 관리합니다: 조회, 업데이트, schema와 config의 읽기와 수정, 컬렉션 관리, 클러스터 관리입니다. 플러그인 ID와 서비스 타입은 모두 `solr`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되고, 노드에 설치된 [Solr 에이전트](/ko/external/solr-agent/)가 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 Solr `9.8.1`, `9.10.1`, `9.11.0`, `10.0.0`의 SolrCloud 노드를 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/solr`에 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `solr`가 활성화되어 있는지 확인하고, 업데이트 후에는 플러그인 디렉터리를 다시 스캔합니다. 플러그인은 자체 클래스 로더 안에서 SolrJ 9.11의 JDK HTTP 클라이언트로 노드와 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 Apache Solr(`solr`)를 선택합니다.
2. 노드 하나 또는 여러 노드의 기본 URL을 입력합니다.
3. 조회 사용자의 로그인 방식을 선택하고 자격 증명을 입력한 뒤 연결을 테스트하고 서비스를 저장합니다. 테스트는 로그인하여 클러스터를 설명합니다: Solr 버전, SolrCloud로 실행되는지, 컬렉션이 몇 개인지입니다.

조회 사용자는 GrantForge가 리소스를 찾게 해 주며, 조회하고 업데이트하는 사용자와는 별개입니다. 연결 테스트가 성공했다고 해서 접근이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `solr.urls` | 필수: 노드의 기본 URL, 예: `https://solr1.example.com:8983/solr`. 여러 개는 쉼표로 구분하며, 하나에 연결할 수 없으면 차례로 시도합니다 |
| `authentication` | `none`(기본값), `basic` 또는 `jwt` |
| `username`, `password` | basic: 조회 사용자와 비밀번호. 비밀번호는 암호화되어 저장됩니다 |
| `token` | jwt: 조회 사용자의 JSON Web Token. 암호화되어 저장됩니다 |
| `javax.net.ssl.trustStore`, `javax.net.ssl.trustStorePassword`, `javax.net.ssl.trustStoreType` | https: **GrantForge 서버**에서 노드 인증서를 검증하는 신뢰 저장소, 그 비밀번호와 유형, 기본값 `PKCS12`. 비우면 JVM의 것을 사용합니다 |
| `timeout` | 요청에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |

한 로그인 방식의 설정은 다른 방식에서 거부되므로, 서비스는 어떻게 로그인하는지 정확히 밝힙니다. 거부된 로그인, 무언가를 볼 수 없는 사용자, 연결할 수 없는 노드는(그 앞의 프록시가 대신 응답하는 경우도) 그대로 보고됩니다.

### 조회 사용자에게 보이는 것

컬렉션 목록은 Solr의 `collection-admin-read` 권한이고, 컬렉션의 요청 경로는 그 config, 즉 `config-read`에서 옵니다. Solr 자체 규칙에서 조회 사용자에게 이 권한을 주거나, 에이전트를 설치한 뒤 클러스터의 `admin_read`와 컬렉션의 `config_read`를 줍니다. 조회 사용자가 볼 수 없는 컬렉션의 경로는 제안에서 빠집니다. SolrCloud 없이 실행되는 노드에는 컬렉션 대신 core가 있으며, 플러그인은 이를 컬렉션으로 보여 줍니다.

## 리소스와 접근 유형

| 수준 | 일치 방식 | 접근 유형 |
| --- | --- | --- |
| `collection` | 와일드카드, 대소문자 구분; 컬렉션 이름 | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit`, `admin_collection` |
| `path` | 와일드카드, 대소문자 구분; 컬렉션 아래; 요청 경로, 예: `/select`, `/export`, `/update/json` | `query`, `update`, `schema_read`, `schema_edit`, `config_read`, `config_edit` |
| `cluster` | 유일한 값 `*` | `health`, `metrics`, `admin_read`, `security`, `admin` |

| 접근 유형 | Solr 권한 | 포함 |
| --- | --- | --- |
| `query` | `read`: 검색, 가져오기, 내보내기 | — |
| `update` | `update` | — |
| `schema_read`, `schema_edit` | `schema-read`, `schema-edit` | `schema_edit`는 `schema_read`를 포함 |
| `config_read`, `config_edit` | `config-read`, `config-edit` | `config_edit`는 `config_read`를 포함 |
| `admin_collection` | 그 컬렉션을 지정하는 Collections API 요청의 `collection-admin-read`와 `collection-admin-edit`: 생성, 다시 로드, 백업, 삭제 등 | `query`, `update`, `schema_edit`, `config_edit` |
| `health`, `metrics` | `health`, `metrics-read` | — |
| `admin_read` | 클러스터 전체의 `collection-admin-read`, `core-admin-read`, `zk-read`, `filestore-read`, `package-read`, 노드의 config와 schema 읽기 | `health`, `metrics` |
| `security` | `security-read`, `security-edit` | — |
| `admin` | 모든 것 | `admin_read`, `security`, `admin_collection` |

컬렉션에 대한 요청은 컬렉션과 "컬렉션과 요청 경로"에서 판정되며, 어느 한쪽의 거부가 요청을 거부합니다. 따라서 정책은 컬렉션을 허용하면서 그 `/export` 핸들러를 거부하거나, `/update*`를 통한 업데이트만 허용할 수 있습니다. 여러 컬렉션에 대한 요청은 각 컬렉션이 모두 필요합니다. 컬렉션 아래에서 요청 경로는 Solr가 요청을 라우팅하는 핸들러이며, 이름은 Solr와 같습니다.

`name` 또는 `collection` 매개변수로 컬렉션을 지정하는 Collections API 요청은 지정된 각 컬렉션의 `admin_collection`이 필요합니다. 따라서 `team-*`에 대한 정책은 팀이 이름이 `team-`으로 시작하는 컬렉션을 만들고 다시 로드·백업·삭제할 수 있게 하지만 다른 컬렉션은 다룰 수 없게 합니다. 컬렉션을 지정하지 않는 요청(예: 모든 컬렉션 나열)은 클러스터에 속합니다. 클러스터의 `admin`은 Solr 자체의 `all` 권한처럼 모든 것을 허용합니다. 정책 값에서 `*`는 임의의 문자열, `?`는 한 문자와 일치합니다. 정책 편집기는 컬렉션과, 정책이 지정한 컬렉션의 요청 경로를 제안합니다.

### 클라이언트 주소

정책 항목은 일부 클라이언트에만 적용될 수 있습니다: **클라이언트 주소** 조건(`ip-range`)은 주소와 `10.1.0.0/16`, `2001:db8::/32` 같은 범위를 받습니다. 호스트 이름은 해석하지 않습니다. 리버스 프록시 뒤에서는 에이전트가 그 프록시를 신뢰하지 않는 한 클라이언트는 프록시입니다. 신뢰하는 경우 클라이언트는 신뢰하는 프록시가 추가하지 않은 `X-Forwarded-For`의 마지막 주소이므로, 클라이언트가 임의의 주소를 사칭할 수 없습니다.

## 정책 예시

클러스터에 컬렉션 `sales`와 `hr`가 있다고 가정합니다. 같은 데이터 서비스에 다음과 같은 활성화된 접근 정책을 만듭니다.

| 리소스 | 규칙 |
| --- | --- |
| `collection=sales` | 그룹 `analysts`에 `query` 허용 |
| `collection=sales`, `path=/export` | 사용자 `alice`에 `query` 거부 |
| `collection=sales`, `path=/update*` | 사용자 `ingest`에 `update` 허용 |
| `collection=team-*` | 그룹 `search-team`에 `admin_collection` 허용 |
| `cluster=*` | 사용자 `monitoring`에 `metrics`와 `health` 허용 |
| `collection=hr` | 그룹 `hr-staff`에 `query` 허용, `10.0.0.0/8`에서만 |
| `cluster=*` | 그룹 `solr-admins`에 `admin` 허용 |

에이전트가 새 스냅샷을 적용하면 `analysts`의 구성원은 `sales`를 조회하고, `alice`는 내보내기 핸들러를 제외한 모든 것을 할 수 있습니다. `ingest`는 업데이트 핸들러로 `sales`를 업데이트하지만 조회할 수는 없습니다. `search-team`의 구성원은 자신의 `team-` 컬렉션을 만들고 관리합니다. `monitoring`은 지표와 상태를 읽습니다. `hr-staff`의 구성원은 내부 네트워크에서만 `hr`를 조회합니다. `solr-admins`의 구성원은 무엇이든 할 수 있습니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 모든 노드에 Solr 에이전트가 필요합니다. 배포와 확인 사항은 [Solr 에이전트](/ko/external/solr-agent/)를 참조하세요.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [Solr 에이전트](/ko/external/solr-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
