---
title: MongoDB
description: MongoDB 서비스 플러그인을 설치하고 구성하며, 복제 세트나 샤드 클러스터에 연결하고, SCRAM, 클라이언트 인증서, LDAP 또는 Kerberos로 로그인하며, 데이터베이스와 컬렉션을 조회하고, 누가 무엇을 읽고 쓰고 관리할 수 있는지를 컬렉션별로 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

MongoDB 플러그인은 데이터베이스와 컬렉션을 조회하고, MongoDB 자체의 방식으로 접근 정책을 관리합니다: 컬렉션별로 읽기, 변경 스트림, 삽입, 업데이트와 삭제, 인덱스, 컬렉션 옵션, 문서 검증 건너뛰기와 컬렉션 삭제를, 데이터베이스별로 컬렉션 생성과 데이터베이스 삭제를, 배포 전체에 대해 모든 데이터베이스 나열과 모니터링을 결정합니다. 플러그인 ID와 서비스 타입은 모두 `mongodb`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, 배포 옆에서 실행되는 [MongoDB 에이전트](/ko/external/mongodb-agent/)가 MongoDB 자체의 역할로 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 MongoDB `6.0`, `7.0`, `8.0`, `9.0` 복제 세트와 Percona Server for MongoDB `8.0`의 Kerberos를 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/mongodb`에 이 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `mongodb`가 활성화되어 있는지 확인하고, 플러그인 디렉터리를 갱신한 뒤에는 다시 스캔하세요. 플러그인은 자체 클래스 로더 안에서 공식 MongoDB Java 드라이버로 MongoDB와 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 MongoDB(`mongodb`)를 선택합니다.
2. 모든 MongoDB 클라이언트가 받는 형식으로 배포의 연결 문자열을 입력합니다.
3. 조회 사용자의 로그인 방식을 입력하고 연결을 테스트한 뒤 서비스를 저장합니다. 테스트는 로그인하여 배포를 설명합니다: MongoDB 버전, 복제 세트인지 샤드 클러스터인지 단일 서버인지, 누가 로그인했는지, 그 사용자에게 보이는 데이터베이스 수입니다.

조회 사용자는 GrantForge가 리소스를 찾게 해 주며, 읽고 쓰는 사용자와는 별개입니다. 연결 테스트가 성공했다고 해서 접근이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `mongodb.uri` | 필수: 연결 문자열, 예: `mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0` 또는 `mongodb+srv://cluster.example.com/`. `tls=true` 같은 옵션은 여기에 쓰고 자격 증명은 쓰지 않으며, 자격 증명이 든 문자열은 거부됩니다 |
| `auth.mechanism` | 비밀번호가 있는 사용자는 `SCRAM-SHA-256`(기본값) 또는 `SCRAM-SHA-1`, 클라이언트 인증서는 `MONGODB-X509`, LDAP은 `PLAIN`, Kerberos는 `GSSAPI` |
| `username` | 조회 사용자. `GSSAPI`에서는 주체이고, `MONGODB-X509`에서 비우면 인증서에서 가져옵니다 |
| `password` | 조회 사용자의 비밀번호, 암호화되어 저장됨 |
| `auth.source` | SCRAM: 사용자가 정의된 데이터베이스, 기본값 `admin`. 다른 메커니즘은 `$external`을 사용합니다 |
| `tls.ca.file` | `tls=true`일 때: 서버를 검증하는 **GrantForge 서버**의 PEM CA 인증서 경로. 비우면 JVM의 것을 사용합니다 |
| `tls.certificate.key.file` | `tls=true`일 때: `mongosh`가 받는 형식처럼 클라이언트 인증서와 키가 함께 든 PEM 파일의 **GrantForge 서버** 경로 |
| `tls.certificate.key.password` | 암호화된 키의 비밀번호, 암호화되어 저장됨 |
| `kerberos.keytab` | `GSSAPI`: 주체의 keytab이 있는 **GrantForge 서버** 경로. 비우면 비밀번호를 사용합니다 |
| `kerberos.kdc` | `GSSAPI`: 영역의 KDC, 쉼표로 구분. 비우면 서버의 `krb5.conf`를 사용합니다 |
| `kerberos.service.name` | `GSSAPI`: 서버 주체의 서비스 이름, 기본값 `mongodb` |
| `timeout` | 서버 찾기, 연결과 명령 하나에 허용되는 시간, 기본값 `30000`, 범위 `1..600000` 밀리초 |

거부된 로그인, 필요한 권한이 없는 조회 사용자와 연결할 수 없는 배포는 그대로 보고됩니다.

### 조회 사용자에게 보이는 것

MongoDB는 사용자가 어떤 권한이든 가진 데이터베이스와 컬렉션만 나열하므로, 조회 결과는 조회 사용자에게 보이는 것입니다. 정책을 작성할 대상에 `read`를, 전체에는 `readAnyDatabase`를 주세요. MongoDB 자체의 데이터베이스 `admin`, `config`, `local`과 시스템 컬렉션은 제외됩니다: 정책이 이들을 결정하지 않습니다.

## 리소스와 접근 유형

| 수준 | 일치 방식 | 접근 유형 |
| --- | --- | --- |
| `deployment` | 와일드카드, 대소문자 구분. 복제 세트의 이름, 샤드 클러스터는 `sharded`, 단일 서버는 `standalone` | `list_databases`, `monitor` |
| `database` | 와일드카드, 대소문자 구분 | `create`, `drop_database` |
| `collection` | 와일드카드, 대소문자 구분. 데이터베이스의 컬렉션 또는 뷰 | `read`, `change_stream`, `insert`, `update`, `remove`, `create_index`, `drop_index`, `alter`, `bypass_validation`, `drop` |

| 접근 유형 | MongoDB의 동작 |
| --- | --- |
| `read` | `find`, `listIndexes`, `collStats`, `killCursors`; 데이터베이스 전체에서는 `listCollections`와 `dbStats`도 |
| `change_stream` | `changeStream`; 여는 데 MongoDB는 `find`도 요구합니다 |
| `insert`, `update`, `remove` | `insert`, `update`, `remove` |
| `create_index`, `drop_index` | `createIndex`, `dropIndex` |
| `alter` | `collMod`, `validate`, `convertToCapped` |
| `bypass_validation` | `bypassDocumentValidation` |
| `drop` | `dropCollection` |
| `create` | 데이터베이스에 대한 `createCollection`: 컬렉션과 뷰 생성 |
| `drop_database` | `dropDatabase` |
| `list_databases` | `listDatabases`: 사용자에게 권한이 있는 것뿐 아니라 모든 데이터베이스의 이름 |
| `monitor` | `serverStatus`, `top`, `replSetGetStatus`, `connPoolStats`, `hostInfo`, `inprog` |

정책 값에서 `*`는 임의의 문자열에, `?`는 한 글자에 일치합니다. 정책 편집기는 배포, 데이터베이스, 그리고 정책이 지정한 데이터베이스의 컬렉션을 제안합니다.

MongoDB의 데이터베이스 삭제 권한은 그 안의 모든 컬렉션을 삭제하는 권한이므로, 에이전트는 정책이 모든 컬렉션 삭제도 허용할 때만 `drop_database`를 부여합니다. 그렇지 않으면 정책이 허용하는 것보다 더 주는 대신 권한을 보류하고 그 사실을 알립니다. 사용자와 역할 관리는 접근 유형이 아니며, 어떤 정책도 이들을 주지 않습니다.

### 행과 필드

MongoDB는 역할별로 행을 필터링하거나 필드를 마스킹하지 않으므로, 이 서비스 타입은 둘 다 제공하지 않습니다. 뷰는 제한된 읽기를 제공합니다: MongoDB는 뷰를 읽는 사용자를 뷰에 대해서만 검사하므로, 뷰에 `read`를 허용하고 원본에는 아무것도 허용하지 않는 정책은 사용자에게 뷰가 보여 주는 것만 보여 줍니다.

```javascript
db.getSiblingDB("sales").createView("orders_public", "orders", [{ $project: { card: 0 } }])
```

## 정책 예시

배포에 데이터베이스 `sales`와 `hr`가 있다고 합시다. 같은 데이터 서비스에 다음과 같은 활성화된 정책을 만듭니다:

| 리소스 | 규칙 |
| --- | --- |
| `database=sales`, `collection=*` | 그룹 `analysts`에 `read` 허용 |
| `database=sales`, `collection=orders_public` | 사용자 `support`에 `read` 허용 |
| `database=sales`, `collection=orders` | 사용자 `ingest`에 `insert`와 `update` 허용; 그룹 `contractors`에 `read` 거부 |
| `database=team_*` | 그룹 `data-team`에 `create` 허용 |
| `database=team_*`, `collection=*` | 그룹 `data-team`에 `read`, `insert`, `update`, `remove`, `create_index`, `drop` 허용 |
| `deployment=*` | 사용자 `dashboards`에 `monitor` 허용 |

에이전트가 스냅샷을 적용하면 `analysts`의 구성원은 나중에 만든 컬렉션까지 `sales` 전체를 읽고, `support`는 원본 없이 뷰를 읽으며, `ingest`는 `orders`를 읽지 않고 쓰고, `data-team`의 구성원은 `team_` 데이터베이스 안에서 자유롭게 작업하며, `dashboards`는 데이터 없이 서버 상태만 봅니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 배포마다 하나씩 MongoDB 에이전트가 필요합니다. 배포와 점검은 [MongoDB 에이전트](/ko/external/mongodb-agent/)에 있습니다.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [MongoDB 에이전트](/ko/external/mongodb-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
