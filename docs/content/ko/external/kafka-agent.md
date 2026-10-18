---
title: Apache Kafka 에이전트
description: Kafka 브로커와 컨트롤러에 에이전트를 설치해 모든 요청을 GrantForge 정책으로 권한 부여하고, 정책을 읽기 전용 ACL로 보여 주며, 반복되는 접근을 하나의 감사 이벤트로 묶습니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Kafka 에이전트는 `authorizer.class.name`에 지정하는 Kafka `Authorizer` API의 권한 부여자입니다. 브로커와 컨트롤러는 모든 요청에 대해 에이전트에 묻고, 에이전트는 `kafka` 데이터 서비스의 서명된 정책으로 답합니다. 배포판은 의존성을 재배치해 포함한 Java 8 호환 JAR 하나, `agents/kafka/grantforge-agent-kafka-<version>.jar`로 제공합니다.

저장소의 Testcontainers 통합 테스트는 브로커이자 컨트롤러인 KRaft 노드에서 Kafka `3.7.2`, `3.9.2`, `4.1.2`, `4.3.1`로 JAR를 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다. 서비스 플러그인은 [Apache Kafka](/ko/plugins/kafka/)를 참고하세요.

## 동작 방식

- **모든 요청을 검사합니다.** 요청이 다루는 각 리소스에 대해 Kafka가 ACL 작업을 묻고, 에이전트는 Kafka 리소스 유형을 리소스 레벨로, 작업을 접근 유형으로 삼아 정책으로 답합니다. 거부는 `TOPIC_AUTHORIZATION_FAILED` 같은 Kafka 자체 오류로 클라이언트에 전달됩니다.
- **멱등 프로듀서에는 토픽 하나면 충분합니다.** Kafka는 프로듀서가 어떤 토픽에 쓸 수 있으면 클러스터의 `idempotent_write` 없이도 시작하게 하며, 이를 권한 부여자에 묻습니다. 에이전트는 정책 자체로 답합니다. 어느 토픽에서든 허용된 `write`는 인정되고, 거부는 모든 토픽을 다루는 정책에서만 인정됩니다.
- **정책이 ACL로 보입니다.** `kafka-acls --list`와 Admin API의 `describeAcls`는 지금 적용되는 정책을 보여 줍니다. 와일드카드가 없는 값은 리터럴 ACL, `orders*` 같은 값은 접두사 ACL, `*`는 Kafka의 와일드카드로 보이며, 사용자는 `User:` 주체, 그룹은 `Group:`, 역할은 `Role:`, `public` 그룹은 `User:*`로 보입니다. ACL로 표현할 수 없는 것은 빠집니다. 다른 와일드카드, 제외된 값, 조건이 있는 항목, 예외가 있는 정책의 해당 항목입니다. ACL 생성과 삭제는 거부됩니다: *the ACLs of this cluster are GrantForge policies; change them in GrantForge*.
- **결정을 보관합니다.** 브로커는 생산과 가져오기마다 같은 질문을 하므로, 에이전트는 현재 스냅숏의 결정을 기본 100,000개까지 10초 동안 보관합니다. 새 스냅숏은 처음부터 다시 시작하므로 에이전트가 정책 변경을 받는 즉시 적용됩니다.
- **반복 접근을 묶습니다.** 기본 10초 창 안의 동일한 허용 접근은 횟수와 마지막 시각을 담은 하나의 접근 이벤트로 보고되며, 콘솔에는 *동일한 접근 N회*로 표시됩니다. 사용자, 클라이언트 주소, 리소스, 접근 유형, 요청 유형, 결정 정책이 같으면 동일한 접근입니다. 거부는 즉시 하나씩 보고됩니다. 무엇을 보고할지는 Kafka가 정합니다. 예를 들어 목록에서 뺀 토픽은 보고를 요청하지 않습니다.
- **슈퍼 사용자는 검사하지 않습니다.** `super.users`의 주체는 Kafka 자체 권한 부여자와 마찬가지로 모든 것을 할 수 있으며 감사되지 않습니다.

## 배포

1. `kafka` 데이터 서비스를 추가하고 정책을 구성합니다. **데이터 권한 → 에이전트**를 열어 서비스를 선택하고 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되므로 Kafka 계정만 읽을 수 있는 파일에 저장하세요.
2. JAR를 **모든 브로커와 컨트롤러**의 `libs`(예: `/opt/kafka/libs`)에 복사합니다.
3. 각 노드의 `server.properties`에 설정을 추가하고, 예시 주체·주소·경로·인스턴스 이름을 바꿉니다.

   ```properties
   authorizer.class.name=org.devlive.grantforge.kafka.agent.GrantForgeKafkaAuthorizer
   super.users=User:kafka
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/kafka/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/var/lib/kafka/grantforge-cache
   ```

   `super.users`에는 브로커 간 리스너의 주체처럼 브로커와 컨트롤러가 서로 통신할 때 쓰는 주체를 넣어야 합니다. 그렇지 않으면 클러스터의 `cluster_action`처럼 이들이 하는 일을 정책으로 허용해야 합니다. `PLAINTEXT` 리스너로의 연결은 `User:ANONYMOUS`입니다. 클라이언트가 그런 리스너에 닿을 수 없을 때만 넣으세요. 캐시 디렉터리는 Kafka 계정이 쓸 수 있어야 합니다.
4. 브로커와 컨트롤러를 하나씩 재시작합니다. 컨트롤러 리스너가 아닌 리스너는 에이전트가 첫 스냅숏을 받으면 열리며, 최대 `grantforge.start.timeout.ms`까지 기다립니다. 그 전과 스냅숏이 없는 동안에는 요청이 거부됩니다.
5. **기존 ACL을 먼저 옮기세요.** 에이전트가 Kafka 자체 권한 부여자를 대체하면 그것이 저장한 ACL은 더 이상 적용되지 않습니다. 미리 `kafka-acls --list`로 나열하고 대응하는 정책을 만드세요.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 브로커나 컨트롤러의 이름, 서명된 스냅숏과 대기 이벤트를 위한 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅숏 서명 키를 고정하는 선택적 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사하세요. 생략하면 첫 연결 시 서버에서 키를 가져옵니다 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결·읽기 시간 제한, 기본값 `5000` / `8000`밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 묻는 주기, 기본값 `30000`, 최소 `1000`밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 대기열 크기(기본값 `10000`), 전송 간격(기본값 `5000`밀리초), 디스크 대기열 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 동일한 허용 접근을 하나의 이벤트로 묶는 시간, 기본값 `10000`, 최대 `3600000`밀리초. `0`이면 하나씩 보고합니다 |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | 보관할 최대 결정 수, 기본값 `100000`, 최대 `10000000`, `0`이면 보관하지 않음. 그리고 보관 시간, 기본값 `10000`, `1..3600000`밀리초 |
| `grantforge.start.timeout.ms` | 리스너가 첫 스냅숏을 기다리는 시간, 기본값 `30000`, `0..600000`밀리초 |

에이전트는 Kafka가 권한 부여자에 넘기는 설정에서 값을 읽습니다. 브로커이자 컨트롤러인 노드는 역할마다 권한 부여자를 하나씩 만들며, 둘은 에이전트 하나를 공유합니다.

## 사용자, 그룹과 역할

정책에는 Kafka가 연결에 부여한 주체의 이름을 씁니다. SASL 사용자 이름, `sasl.kerberos.principal.to.local.rules`에 따른 Kerberos 주체의 짧은 이름, 또는 `ssl.principal.mapping.rules`에 따른 TLS 인증서 이름입니다. Kafka에는 그룹과 역할이 없으며, 정책의 그룹과 역할은 GrantForge가 사용자에게 부여한 것입니다.

## 결정과 제한 사항

- 사용 가능한 스냅숏이 없으면 슈퍼 사용자의 요청을 제외한 모든 요청이 거부됩니다. 사용하지 않는 서비스는 권한 부여자가 없을 때처럼 Kafka를 검사하지 않습니다.
- 토큰이 `kafka`가 아닌 유형의 서비스에 연결되어 있으면 모두 거부합니다.
- 접근 유형을 거부하면 그것이 포함하는 유형도 거부됩니다. 토픽의 `write`를 거부하면 `describe`도 거부되어 사용자가 그 토픽을 보지 못합니다. 이 점은 Kafka 자체 ACL과 다릅니다.
- 사용자가 어떤 유형의 아무 리소스에나 작업할 수 있는지 묻는 Kafka의 질문은 접근이 아니므로 보고되지 않습니다.
- 결정은 `grantforge.decision.cache.ttl.ms` 동안 보관되므로 정책의 유효 기간은 최대 그만큼 늦게 시작하거나 끝날 수 있습니다.
- 에이전트는 브로커와 컨트롤러에서 동작합니다. Kafka Connect의 REST API, 스키마 레지스트리 등 Kafka 주변 서비스는 다루지 않습니다.

## 관련 가이드

- [Apache Kafka](/ko/plugins/kafka/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
