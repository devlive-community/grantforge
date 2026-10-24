---
title: Apache Pulsar
description: Pulsar 서비스 플러그인을 설치하고 설정하여 하나 이상의 broker에 연결하고, 토큰, 비밀번호, OAuth 2.0 또는 클라이언트 인증서로 로그인하고, 테넌트, 네임스페이스, 토픽과 구독을 조회하며, 누가 어디에 생산하고 소비하고 무엇을 관리할 수 있는지 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Pulsar 플러그인은 테넌트, 네임스페이스, 토픽, 구독과 클러스터를 조회하고, Pulsar가 결정하는 일, 즉 생산, 소비, Functions, Sources, Sinks와 Packages, 그리고 토픽, 네임스페이스, 테넌트와 클러스터 관리에 대한 접근 정책을 관리합니다. 플러그인 ID와 서비스 타입은 모두 `pulsar`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, broker에 설치한 [Pulsar 에이전트](/ko/external/pulsar-agent/)가 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 Pulsar `3.0.17`, `3.3.9`, `4.0.14`, `4.2.5`의 broker를 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/pulsar`에 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `pulsar`가 활성화되어 있는지 확인하고, 업데이트 후에는 플러그인 디렉터리를 다시 스캔합니다. 플러그인은 자체 클래스 로더 안에서 Pulsar 자체의 admin 클라이언트 4.2.5로 broker의 웹 서비스와 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 Apache Pulsar(`pulsar`)를 선택합니다.
2. broker 하나 또는 여러 broker의 웹 서비스 URL을 입력합니다.
3. 조회 역할의 로그인 방식을 선택하고 자격 증명을 입력한 뒤 연결을 테스트하고 서비스를 저장합니다. 테스트는 로그인한 뒤 클러스터를 설명합니다. 어떤 클러스터가 있고 테넌트가 몇 개인지 보여 줍니다.

설정은 Pulsar 자체 `client.conf`의 이름을 쓰므로 그대로 옮길 수 있습니다. 조회 역할은 GrantForge가 리소스를 찾는 데 쓰이며, 생산하고 소비하는 역할과는 별개입니다. 연결 테스트가 성공했다고 해서 접근이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `webServiceUrl` | 필수: broker의 웹 서비스, `http://host:port` 또는 `https://host:port`, 기본 포트 `8080` 또는 `8443`. 여러 broker는 쉼표로 구분, 예: `http://broker1:8080,broker2:8080` |
| `authentication` | `none`(기본값), `token`, `basic`, `oauth2` 또는 `tls` |
| `token` | token: 조회 역할의 토큰. 암호화되어 저장됩니다 |
| `username`, `password` | basic: 조회 역할의 사용자 이름과 비밀번호. 비밀번호는 암호화되어 저장됩니다 |
| `issuerUrl`, `privateKey`, `audience`, `scope` | oauth2: 권한 부여 서버, **GrantForge 서버**에 있는 `client_id`와 `client_secret`을 담은 JSON 자격 증명 파일의 경로, broker가 요구할 때의 audience와 scope |
| `tlsCertificateFilePath`, `tlsKeyFilePath` | tls: **GrantForge 서버**에 있는 클라이언트 인증서와 그 개인 키의 경로, PEM |
| `tlsTrustCertsFilePath` | https: broker 인증서를 검증할 CA 인증서, PEM. 비우면 JVM의 것을 사용합니다 |
| `tlsEnableHostnameVerification` | https: broker 인증서에 호스트 이름이 있어야 하는지 여부. 기본값 `true` |
| `timeout` | 요청에 허용되는 시간. 기본값 `30000`, 범위 `1..600000` 밀리초 |

한 로그인 방식의 설정은 다른 방식에서 거부되므로, 서비스는 어떻게 로그인하는지 정확히 나타냅니다. 거부된 로그인, 무언가를 볼 수 없는 역할, 연결할 수 없는 broker(대신 응답하는 프록시 뒤에 있는 경우 포함)는 각각 그대로 보고됩니다.

### 조회 역할이 볼 수 있는 것

Pulsar는 슈퍼유저만 클러스터와 테넌트를 나열할 수 있게 합니다. 조회 역할을 broker의 `superUserRoles`에 적거나, 에이전트를 설치한 뒤 클러스터의 `admin`을 허용하십시오. 이 역시 역할을 슈퍼유저로 만듭니다. 조회 역할이 볼 수 없는 네임스페이스, 토픽과 구독은 제안에서 빠집니다.

## 리소스와 접근 유형

| 수준 | 매칭 | 접근 유형 |
| --- | --- | --- |
| `tenant` | 와일드카드, 대소문자 구분 | `admin`을 제외한 전부 |
| `namespace` | 와일드카드, 대소문자 구분. 테넌트 아래. 네임스페이스 자체의 이름, 예: `orders` | `produce`, `consume`, `functions`, `sources`, `sinks`, `packages`, `admin_topic`, `admin_namespace` |
| `topic` | 와일드카드, 대소문자 구분. 네임스페이스 아래. 토픽 자체의 이름, 예: `payments` | `produce`, `consume`, `admin_topic` |
| `subscription` | 와일드카드, 대소문자 구분. 토픽 아래. 구독의 이름 | `consume` |
| `cluster` | 와일드카드, 대소문자 구분. 클러스터의 이름, 예: `standalone` | `admin`: Pulsar의 슈퍼유저 권한 |

| 접근 유형 | 포함 |
| --- | --- |
| `produce`, `consume`, `functions`, `sources`, `sinks`, `packages` | — |
| `admin_topic`: 토픽 종료, 압축, 계층형 스토리지로 오프로드, 언로드와 잘라내기, 정책 변경 | `produce`, `consume` |
| `admin_namespace`: 토픽 생성과 삭제, 네임스페이스 정책 변경 | `admin_topic` |
| `admin_tenant`: 네임스페이스 생성, 나열과 삭제 | `admin_namespace`, `functions`, `sources`, `sinks`, `packages` |
| `admin`: Pulsar 슈퍼유저가 할 수 있는 모든 것 | `admin_tenant` |

Pulsar 자체의 권한과 마찬가지로 테넌트나 네임스페이스에 대한 권한은 그 안의 것에 적용됩니다. 테넌트 `sales`의 네임스페이스 `orders`에 대한 `produce`로 그 안의 모든 토픽에 생산할 수 있습니다. 검사는 테넌트, 네임스페이스, 토픽, 구독에서 차례로 결정되며, 그중 하나라도 거부하면 거부됩니다. 따라서 정책 하나로 네임스페이스를 허용하면서 그 안의 토픽 하나를 거부할 수 있습니다. 토픽 아래의 구독 수준은 역할이 어떤 구독으로 소비할 수 있는지 지정합니다. 토픽 `*`, 구독 `billing-*`에 대한 `consume`은 역할이 네임스페이스의 모든 토픽을 소비하게 하지만, 이름이 `billing-`로 시작하는 구독으로만 가능합니다.

토픽은 도메인을 뺀 자체 이름으로 결정되므로 같은 이름의 영구 토픽과 비영구 토픽은 정책을 공유합니다. 파티션 토픽의 파티션은 파티션 토픽 자체로 결정됩니다. 정책 값에서 `*`는 임의 개수의 문자, `?`는 한 문자와 일치합니다. 정책 편집기는 클러스터, 테넌트, 그리고 정책이 지정한 범위의 네임스페이스, 토픽과 구독을 제안합니다.

### 클라이언트 주소

정책 항목은 일부 클라이언트에만 적용될 수 있습니다. **클라이언트 주소**(`ip-range`) 조건은 주소와 `10.1.0.0/16`이나 `2001:db8::/32` 같은 범위를 받습니다. 호스트 이름은 조회하지 않습니다. 프록시 뒤에서는 프록시가 클라이언트입니다. 단, 프록시가 클라이언트 자신의 주소를 전달하면 그 주소가 클라이언트입니다. 생산자와 소비자는 HAProxy 프로토콜로, 웹 서비스는 broker가 신뢰할 때 `X-Forwarded-For`로 전달합니다.

## 정책 예시

테넌트 `sales`에 네임스페이스 `orders`와 `returns`가 있다고 가정합니다. 같은 데이터 서비스에서 다음과 같은 활성화된 접근 정책을 만듭니다.

| 리소스 | 규칙 |
| --- | --- |
| `tenant=sales`, `namespace=orders` | 그룹 `analysts`에 `produce` 허용 |
| `tenant=sales`, `namespace=orders`, `topic=audit` | 사용자 `alice`에 `produce` 거부 |
| `tenant=sales`, `namespace=orders`, `topic=*`, `subscription=billing-*` | 사용자 `alice`에 `consume` 허용 |
| `tenant=sales` | 사용자 `bob`에 `admin_tenant` 허용 |
| `tenant=public` | 그룹 `ingest`에 `10.0.0.0/8`에서의 `produce` 허용 |
| `cluster=standalone` | 그룹 `pulsar-admins`에 `admin` 허용 |

에이전트가 새 스냅샷을 적용하면 `analysts` 구성원은 `sales/orders`의 모든 토픽에 생산할 수 있고, `alice`는 `audit`을 제외한 모든 토픽에 생산할 수 있습니다. `alice`는 그곳에서 `billing-` 구독으로만 소비합니다. `bob`은 `sales`에서 네임스페이스와 토픽을 만들고 관리합니다. `ingest` 구성원은 내부 네트워크에서만 `public` 테넌트에 생산합니다. `pulsar-admins` 구성원은 Pulsar의 슈퍼유저입니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 모든 broker에 Pulsar 에이전트가 필요합니다. 배포와 검사는 [Pulsar 에이전트](/ko/external/pulsar-agent/)에서 설명합니다.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [Pulsar 에이전트](/ko/external/pulsar-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
