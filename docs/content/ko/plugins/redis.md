---
title: Redis
description: Redis와 Valkey용 Redis 서비스 플러그인을 설치하고 구성하며, 서버, 복제본 또는 클러스터에 연결하고, 필요하면 TLS로 ACL 사용자 로그인하며, 키, 채널과 노드를 조회하고, 누가 어떤 키를 읽고 쓰며 어떤 채널을 쓰고 어떤 노드를 모니터링할 수 있는지 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Redis 플러그인은 Redis와 Valkey 배포의 키, 채널과 노드를 조회하고, Redis 자체의 방식으로 접근 정책을 관리합니다: 패턴별 키 읽기와 쓰기, 채널 구독과 발행, 그리고 각 노드 모니터링입니다. 플러그인 ID와 서비스 타입은 모두 `redis`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, 배포 옆에서 실행되는 [Redis 에이전트](/ko/external/redis-agent/)가 모든 노드에서 Redis 자체의 ACL로 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 Redis `7.2`, `7.4`, `8.2`, `8.10`과 Valkey `8.1`, `9.1`의 복제본 하나를 둔 프라이머리, 그리고 Redis `8.10` 클러스터를 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/redis`에 이 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `redis`가 활성화되어 있는지 확인하고, 플러그인 디렉터리를 갱신한 뒤에는 다시 스캔하세요. 플러그인은 자체 클래스 로더 안에서 Jedis로 Redis와 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 Redis(`redis`)를 선택합니다.
2. 노드를 하나 이상 입력합니다. 클러스터의 나머지 노드와 프라이머리의 복제본은 이들로부터 찾습니다.
3. 조회 사용자를 입력하고 연결을 테스트한 뒤 서비스를 저장합니다. 테스트는 로그인하여 배포를 설명합니다: Redis인지 Valkey인지와 그 버전, 단일 서버인지 복제본이 있는 프라이머리인지 클러스터인지, 그리고 누가 로그인했는지입니다.

조회 사용자는 GrantForge가 리소스를 찾게 해 주며, 읽고 쓰는 사용자와는 별개입니다. 연결 테스트가 성공했다고 해서 접근이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `redis.nodes` | 필수: 노드 주소 `host:port`, 예: `redis1.example.com:6379`, 쉼표로 구분하며 차례로 시도합니다. 포트를 쓰지 않으면 `6379`입니다 |
| `username` | 조회 사용자, ACL 사용자. 비우면 기본 사용자로 로그인합니다 |
| `password` | 그 비밀번호, 암호화되어 저장됨 |
| `tls.mode` | `disable`(기본값), 서버 인증서를 확인하는 `verify-ca`, 이름까지 확인하는 `verify-full` |
| `tls.ca.file` | TLS: 서버를 검증하는 **GrantForge 서버**의 PEM CA 인증서 경로. 비우면 JVM의 것을 사용합니다 |
| `tls.certificate.file` | TLS, 클라이언트 인증서를 요구하는 서버용: **GrantForge 서버**의 PEM 클라이언트 인증서 경로, 키를 함께 담을 수 있습니다 |
| `tls.key.file` | 인증서의 PEM 키가 별도 파일일 때 그 **GrantForge 서버** 경로 |
| `tls.key.password` | 암호화된 키의 비밀번호, 암호화되어 저장됨 |
| `timeout` | 연결과 명령 하나에 허용되는 시간, 기본값 `10000`, 범위 `1..600000` 밀리초 |

거부된 로그인, 권한이 없는 조회 사용자와 연결할 수 없는 노드는 그대로 보고됩니다.

### 조회 사용자에게 보이는 것

키는 각 프라이머리에서 `SCAN`으로 찾으며, 키가 아무리 많아도 조회가 빠르도록 최대 몇 번만 돕니다. 채널은 지금 구독자가 있는 것입니다. `SCAN`은 사용자의 키 패턴과 상관없이 모든 키를 나열하므로, 조회 사용자에게 값을 읽을 권한은 필요 없습니다:

```text
ACL SETUSER lookup on >… -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*
```

## 리소스와 접근 유형

| 수준 | 일치 방식 | 접근 유형 |
| --- | --- | --- |
| `key` | 와일드카드, 대소문자 구분. 키 또는 키의 패턴 | `read`, `write` |
| `channel` | 와일드카드, 대소문자 구분. 채널 또는 채널의 패턴 | `subscribe`, `publish` |
| `node` | 와일드카드, 대소문자 구분 안 함. 노드 주소 `host:port` | `monitor` |

| 접근 유형 | Redis가 허용하는 것 |
| --- | --- |
| `read` | 키에 대한 `@read` 명령, 즉 `%R~` |
| `write` | 키에 대한 `@write` 명령, 즉 `%W~`, 삭제 포함 |
| `subscribe` | `SUBSCRIBE`, `PSUBSCRIBE`, `SSUBSCRIBE` 채널에 대해, `&` |
| `publish` | `PUBLISH`, `SPUBLISH` 채널에 대해, `&` |
| `monitor` | `INFO`, `DBSIZE`, `LATENCY LATEST`, `LATENCY HISTORY`, `MEMORY STATS` |

정책 값에서 `*`는 임의의 문자열에, `?`는 한 글자에 일치하며, Redis 자체 패턴과 같습니다. Redis는 키와 채널을 패턴으로 부여하고 에이전트에게 나열해 줄 수 없으므로, 정책 값은 정책이 그 패턴 전체에서 사용자를 똑같이 결정하는 경우에만 독자적인 패턴으로 부여됩니다. 정책이 그 안에서 다르게 결정하는 패턴, 예를 들어 정책이 `sales:card:*`를 거부할 때의 `sales:*`는 보류되고 그 사실이 알려집니다. 대신 정책이 똑같이 결정하는 키나 패턴을 지정하세요. 대괄호, 백슬래시, 괄호나 공백이 든 값은 절대 부여되지 않습니다.

`FLUSHALL`이나 `KEYS`처럼 Redis가 위험하다고 보는 명령은 절대 주지 않으며, 패턴과 상관없이 키를 나열하는 `SCAN`과 `RANDOMKEY`는 모든 키를 읽을 수 있는 사용자에게만 줍니다. Redis의 ACL에는 데이터베이스가 없으므로 패턴은 노드의 모든 데이터베이스에서 키를 가리킵니다.

Redis는 값 안을 필터링하지도 마스킹하지도 않으므로, 이 서비스 타입은 둘 다 제공하지 않습니다.

## 정책 예시

같은 데이터 서비스에 다음과 같은 활성화된 정책을 만듭니다:

| 리소스 | 규칙 |
| --- | --- |
| `key=sales:*` | 그룹 `analysts`에 `read` 허용 |
| `key=sales:card:*` | 사용자 `billing`에 `read`와 `write` 허용 |
| `key=cart:*` | 사용자 `shop`에 `read`와 `write` 허용 |
| `channel=orders.*` | 그룹 `analysts`에 `subscribe` 허용; 사용자 `shop`에 `publish` 허용 |
| `node=*` | 사용자 `dashboards`에 `monitor` 허용 |

에이전트가 스냅샷을 적용하면 `analysts`의 구성원은 모든 `sales:` 키를 읽고 주문 채널을 구독하며, `billing`은 다른 정책이 그 안에서 다르게 결정하지 않는 패턴인 카드 키를 읽고 쓰고, `shop`은 장바구니를 관리하며 주문 이벤트를 발행하고, `dashboards`는 데이터 없이 각 노드의 상태를 봅니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 배포마다 하나씩 Redis 에이전트가 필요합니다. 배포와 점검은 [Redis 에이전트](/ko/external/redis-agent/)에 있습니다.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [Redis 에이전트](/ko/external/redis-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
