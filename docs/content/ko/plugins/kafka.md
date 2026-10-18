---
title: Apache Kafka
description: Kafka 서비스 플러그인을 설치하고 구성해 평문 또는 TLS로 PLAIN, SCRAM, Kerberos 로그인을 하고, 토픽·그룹·트랜잭션 ID·사용자를 조회하며, Kafka 자체 개념으로 접근 정책을 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Kafka 플러그인은 토픽, 컨슈머 그룹, 트랜잭션 ID, SCRAM 사용자와 위임 토큰을 조회하고, Kafka 자체 개념으로 접근 정책을 관리합니다. Kafka의 리소스 유형이 리소스 레벨이고, ACL 작업이 접근 유형입니다. 플러그인 ID와 서비스 타입은 모두 `kafka`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, 브로커와 컨트롤러에 설치된 [Kafka 에이전트](/ko/external/kafka-agent/)가 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 SASL PLAIN·SCRAM 로그인과 Kafka 자체 권한 부여자를 사용해 Kafka `3.7.2`, `3.9.2`, `4.1.2`, `4.3.1`을 다루며, `4.3.1`에서는 Kerberos(keytab과 비밀번호)와 TLS도 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/kafka`에 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `kafka`가 활성화되어 있는지 확인하고, 플러그인 디렉터리를 갱신한 뒤에는 다시 스캔하세요. 플러그인은 자체 클래스 로더에서 Kafka의 Admin 클라이언트(`kafka-clients` 4.3.1)로 브로커와 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 Apache Kafka(`kafka`)를 선택합니다.
2. 부트스트랩 서버와 브로커 리스너의 보안 방식을 입력합니다.
3. 조회 사용자를 입력하고 연결을 테스트한 뒤 서비스를 저장합니다. 테스트는 로그인해 클러스터를 설명합니다. 클러스터 ID와 브로커 수입니다.

설정은 Kafka 클라이언트 자체 이름을 사용하므로 클라이언트 설정에서 그대로 복사할 수 있습니다. 조회 사용자는 GrantForge가 리소스를 찾는 데만 쓰이며, 메시지를 생산하고 소비하는 클라이언트와는 별개입니다. 연결 테스트가 성공해도 요청이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `bootstrap.servers` | 필수 시작 브로커, `호스트:포트`를 쉼표로 구분, 예: `kafka1:9092,kafka2:9092` |
| `security.protocol` | `PLAINTEXT`(기본값), `SSL`, `SASL_PLAINTEXT`, `SASL_SSL` |
| `sasl.mechanism` | SASL 프로토콜 전용: 비밀번호를 쓰는 `PLAIN`, `SCRAM-SHA-256`, `SCRAM-SHA-512`, Kerberos용 `GSSAPI` |
| `username` | SASL: 조회 사용자. Kerberos에서는 전체 주체, 예: `grantforge@EXAMPLE.COM` |
| `password` | 사용자 비밀번호, 또는 keytab이 없을 때 주체의 Kerberos 비밀번호. 암호화되어 저장됩니다 |
| `keytab` | Kerberos: **GrantForge 서버**에서 읽을 수 있는 주체 keytab 경로 |
| `kerberos.kdc` | Kerberos: 주체 영역의 KDC, `호스트[:포트]`를 쉼표로 구분. 비우면 서버의 `krb5.conf`를 사용합니다 |
| `sasl.kerberos.service.name` | Kerberos: 브로커 주체의 첫 부분, 기본값 `kafka` |
| `ssl.truststore.location`, `ssl.truststore.password`, `ssl.truststore.type` | TLS: 브로커를 검증하는 신뢰 저장소(`JKS`, `PKCS12`, `PEM`). 비우면 Java가 신뢰하는 인증서를 신뢰합니다 |
| `ssl.keystore.location`, `ssl.keystore.password`, `ssl.key.password` | TLS: 클라이언트 인증서를 요구하는 브로커를 위한 GrantForge 인증서 |
| `ssl.verify.hostname` | TLS: 브로커 인증서에 연결한 호스트 이름이 있어야 하는지, 기본값 `true` |
| `request.timeout.ms` | 요청 시간 제한, 기본값 `30000`, 범위 `1..600000` |

`PLAINTEXT`와 `SSL`에서는 SASL 설정을, `PLAINTEXT`와 `SASL_PLAINTEXT`에서는 TLS 설정을 거부합니다. `PLAIN`과 SCRAM은 비밀번호가, `GSSAPI`는 비밀번호나 keytab이 필요합니다.

### Kerberos

`GSSAPI`를 사용하면 GrantForge는 Kafka 자체 Kerberos 로그인으로 주체를 로그인시킵니다. keytab을 쓰거나, GrantForge가 로그인 과정에 직접 넘겨주는 비밀번호를 씁니다. GrantForge 서버에는 다른 Kerberos 설정이 필요 없습니다. `kerberos.kdc`에 KDC를 입력한 영역은 HDFS·Hive 플러그인과 같은 방식으로 GrantForge 서버 전체에 선언되므로, 여러 서비스가 서로 다른 영역을 나란히 사용할 수 있습니다.

### 조회 사용자에게 보이는 것

Kafka는 사용자가 설명(describe)할 수 있는 것만 보여 주므로, 조회 사용자에게는 제안할 토픽·그룹·트랜잭션 ID에 대한 `describe`와, SCRAM 사용자를 위한 클러스터의 `describe`가 필요합니다. Kafka는 설명할 수 없는 토픽과 그룹을 빼므로 제안은 거부되지 않고 비어 있게 됩니다. `__consumer_offsets` 같은 내부 토픽은 입력한 텍스트가 `_`로 시작하면 제안됩니다. 위임 토큰이나 SCRAM이 없는 클러스터는 해당 항목을 제안하지 않습니다.

[Kafka 에이전트](/ko/external/kafka-agent/)가 정책을 적용하면 조회 사용자가 무엇을 설명할 수 있는지도 정책이 결정합니다. `describe`를 허용하는 정책을 주거나, 브로커의 `super.users`에 넣으세요.

## 리소스와 접근 유형

| 레벨 | Kafka 리소스 | 접근 유형 |
| --- | --- | --- |
| `topic` | 토픽 | `read`, `write`, `create`, `delete`, `alter`, `describe`, `describe_configs`, `alter_configs`, `all` |
| `group` | 컨슈머 그룹 | `read`, `delete`, `describe`, `all` |
| `transactional_id` | 트랜잭션 ID | `write`, `describe`, `all` |
| `cluster` | 클러스터, 이름은 항상 `kafka-cluster` | `create`, `alter`, `describe`, `cluster_action`, `describe_configs`, `alter_configs`, `idempotent_write`, `all` |
| `delegation_token` | 위임 토큰, 토큰 ID 기준 | `describe`, `all` |
| `user` | 위임 토큰을 만들거나 설명하는 대상 사용자 | `create_tokens`, `describe_tokens`, `all` |

각 레벨은 독립적이며 정책 하나가 레벨 하나를 지정합니다. 값은 대소문자를 구분하며 `*`는 임의 개수의 문자, `?`는 한 문자와 일치합니다. 접근 유형은 Kafka의 ACL 작업과 같은 방식으로 다른 유형을 포함합니다. `read`, `write`, `delete`, `alter`는 `describe`를, `alter_configs`는 `describe_configs`를 포함하고, `all`은 나머지 모두를 포함합니다.

포함 관계는 거부에도 적용됩니다. 토픽의 `write`를 거부하는 정책은 `write`가 포함하는 `describe`도 거부하므로 사용자는 그 토픽을 더 이상 볼 수 없습니다. Kafka 자체 ACL은 허용된 작업에만 포함 관계를 적용합니다.

정책 편집기는 클러스터에서 접두사로 이름을 제안하며, `cluster`에는 `kafka-cluster`를 제안합니다.

## 정책 예시

사용자 `alice`의 프로듀서가 이름이 `orders`로 시작하는 토픽에 쓰고, 그룹 `analysts`가 컨슈머 그룹 `billing`으로 이를 읽는다고 합시다. 같은 데이터 서비스에 활성화된 접근 정책 세 개를 만듭니다.

| 리소스 | 규칙 |
| --- | --- |
| `topic=orders*` | 사용자 `alice`에게 `write` 허용 |
| `topic=orders*` | 그룹 `analysts`에게 `read` 허용 |
| `group=billing` | 그룹 `analysts`에게 `read` 허용 |

에이전트가 새 스냅숏을 적용하면 `alice`는 `orders-eu`에 생산할 수 있으며, 클러스터의 `idempotent_write` 없이 멱등 프로듀서로도 가능합니다. `analysts`의 구성원은 `billing`으로 소비하고, 다른 토픽과 그룹은 둘 다 거부합니다. `kafka-acls --list`는 세 정책을 접두사 ACL과 리터럴 ACL로 보여 줍니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 모든 브로커와 컨트롤러에 Kafka 에이전트가 필요합니다. 배포 방법과 검사 내용은 [Kafka 에이전트](/ko/external/kafka-agent/)를 참고하세요.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [Kafka 에이전트](/ko/external/kafka-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
