---
title: Apache Pulsar 에이전트
description: Pulsar broker에 권한 부여 공급자를 설치하여, 누가 어디에 생산하고 소비할 수 있는지, 어떤 구독으로 가능한지, Functions, Sources와 Sinks를 실행하고 토픽, 네임스페이스, 테넌트와 클러스터를 관리할 수 있는지, 어떤 네트워크에서 가능한지를 GrantForge 정책으로 결정합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Pulsar 에이전트는 broker의 권한 부여 공급자입니다. Pulsar는 누가 생산하고, 소비하고, Functions, Sources와 Sinks를 실행하고, 토픽, 네임스페이스, 테넌트와 클러스터를 관리할 수 있는지 에이전트에 묻고, 에이전트는 `pulsar` 데이터 서비스의 서명된 정책으로 답합니다. 배포판은 의존성을 포함하고 재배치한 Java 17 JAR `agents/pulsar/grantforge-agent-pulsar-<version>.jar`로 제공합니다.

저장소의 Testcontainers 통합 테스트는 Pulsar `3.0.17`, `3.3.9`, `4.0.14`, `4.2.5`의 broker에서 JAR를 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다. 서비스 플러그인은 [Apache Pulsar](/ko/plugins/pulsar/)에서 설명합니다.

## 동작 방식

- **모든 검사는 토픽 이름의 모든 수준에서 결정됩니다.** Pulsar 자체의 권한과 마찬가지로 테넌트나 네임스페이스에 대한 권한은 그 안의 것에 적용되므로, 에이전트는 `persistent://sales/orders/payments`로의 생산을 테넌트 `sales`, 네임스페이스 `orders`, 토픽 `payments`에서 결정합니다. 어느 하나라도 거부하면 거부되고, 어느 하나라도 허용하면 허용됩니다.
- **소비는 구독으로도 결정됩니다.** 에이전트는 각 수준에서 토픽과 구독의 조합도 묻습니다. 따라서 정책으로 역할이 `billing-*` 같은 일부 구독으로만 소비하게 할 수 있습니다.
- **클러스터 관리자는 Pulsar의 슈퍼유저입니다.** broker 클러스터의 `admin`은 역할을 슈퍼유저로 만들고, Pulsar는 슈퍼유저에게 모든 것을 허용하므로 아래 정책은 이들을 제한하지 않습니다. Pulsar의 broker와 클러스터 작업에는 해당 클러스터의 `admin`이 필요합니다.
- **정책을 일부 네트워크에만 적용할 수 있습니다.** 모든 검사에는 클라이언트 주소가 포함되며, 에이전트는 broker 네트워크 스택이 쓰는 Netty의 주소 범위로 **클라이언트 주소** 조건을 평가합니다.
- **Pulsar를 통한 권한 부여는 거부됩니다.** Pulsar 자체의 권한과 테넌트의 관리자 역할은 사용하지 않으므로 `pulsar-admin namespaces grant-permission` 같은 명령은 HTTP 405로 응답받으며, GrantForge에서 권한을 부여하라는 안내가 붙습니다.
- **거부는 이유와 함께 로그에 남습니다.** 클라이언트가 받는 거부 문구는 Pulsar가 정합니다. broker 로그에는 어떤 GrantForge 정책이 거부했는지, 또는 허용하는 정책이 없는지가 기록됩니다.
- **결정은 보관되고 접근은 합쳐집니다.** 현재 스냅샷의 결정은 기본적으로 100,000개를 10초 동안 보관하며, 새 스냅샷은 처음부터 다시 시작합니다. 기본 10초 창 안의 동일한 허용 접근은 횟수를 담은 접근 이벤트 하나로 보고되고, 거부는 하나씩 즉시 보고됩니다. 모든 이벤트에는 토픽, 네임스페이스, 테넌트 또는 클러스터와 클라이언트 주소가 담깁니다.
- **에이전트 하나가 broker 전체를 맡습니다.** broker는 바이너리 프로토콜, 웹 서비스, Functions Worker마다 권한 부여 공급자를 로드하며, 이들은 에이전트 하나를 공유하고 에이전트는 broker와 함께 멈춥니다.

| 작업 | 필요한 접근 |
| --- | --- |
| 생산, 또는 생산으로 토픽 생성 | 토픽의 `produce` |
| 구독, 소비, 구독 해제, 건너뛰기, 엿보기, 메시지 만료, 커서 재설정 | 토픽과 구독의 `consume` |
| 토픽 조회, 통계·메타데이터·정책 읽기, 네임스페이스의 토픽 나열 | `produce` 또는 `consume` |
| 토픽 종료, 압축, 오프로드, 언로드 또는 잘라내기, 정책 변경 | 토픽의 `admin_topic` |
| 토픽 생성과 삭제, 네임스페이스 정책 변경 | 네임스페이스의 `admin_namespace` |
| Functions, Sources, Sinks 실행, Packages 사용 | 네임스페이스의 `functions`, `sources`, `sinks`, `packages` |
| 테넌트의 네임스페이스 생성, 나열과 삭제 | 테넌트의 `admin_tenant` |
| 테넌트와 클러스터 생성, 변경과 삭제, Pulsar의 broker 작업 | 클러스터의 `admin` |

## 배포

1. `pulsar` 데이터 서비스를 추가하고 정책을 설정합니다. **데이터 권한 → 에이전트**를 열고 서비스를 선택한 뒤 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되므로 Pulsar 실행 계정만 읽을 수 있는 파일에 저장합니다.
2. JAR를 **모든 broker**의 `lib`(예: `/pulsar/lib`)에 복사합니다.
3. 모든 broker의 `broker.conf` 또는 `standalone.conf`에 다음 설정을 추가하고 예시의 주소, 경로, 인스턴스 이름을 바꿉니다.

   ```properties
   authorizationEnabled=true
   authorizationProvider=org.devlive.grantforge.pulsar.agent.GrantForgePulsarAuthorizer
   superUserRoles=broker
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/pulsar/conf/grantforge.token
   grantforge.instance=broker-1
   grantforge.cache.dir=/pulsar/data/grantforge-cache
   ```

   클라이언트가 역할을 갖도록 인증도 활성화해야 합니다. `superUserRoles`와 `proxyRoles`에는 broker 자신의 역할과 프록시의 역할만 적어야 합니다. 에이전트는 이들을 검사하지 않으며, Pulsar는 프록시를 거친 원래 클라이언트도 검사합니다. 관리자에게는 대신 클러스터의 `admin`을 부여하십시오. 캐시 디렉터리는 Pulsar 실행 계정이 쓸 수 있어야 합니다. 컨테이너에서는 `grantforge.*` 설정을 파일에 적으십시오. 일부 Pulsar 이미지의 셸은 `PULSAR_PREFIX_grantforge.token.file`처럼 이름에 점이 있는 환경 변수를 버립니다.
4. broker를 하나씩 다시 시작합니다. 에이전트가 첫 스냅샷을 받기 전까지는 broker와 프록시의 요청을 제외한 요청이 거부됩니다.
5. **기존 권한을 먼저 옮기십시오.** 네임스페이스, 토픽, 구독에 대한 Pulsar 자체의 권한과 테넌트의 관리자 역할은 더 이상 적용되지 않습니다. 미리 대응하는 정책을 만드십시오.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 broker의 이름, 서명된 스냅샷과 대기 이벤트를 위한 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택적 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사합니다. 없으면 첫 연결 때 서버에서 키를 가져옵니다 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결 및 읽기 타임아웃. 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 가져오는 간격. 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 간격(기본값 `5000` 밀리초), 스풀 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 동일한 허용 접근을 하나의 이벤트로 합치는 시간. 기본값 `10000`, 최대 `3600000` 밀리초. `0`은 하나씩 보고합니다 |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | 보관할 최대 결정 수(기본값 `100000`, 최대 `10000000`, `0`은 보관하지 않음)와 보관 기간(기본값 `10000`, `1..3600000` 밀리초) |

## 사용자, 그룹과 역할

정책은 Pulsar 인증이 클라이언트에게 부여한 역할(예: 토큰의 subject나 인증서의 common name)을 사용자로 지정합니다. Pulsar에는 자체 그룹이 없습니다. 그룹은 GrantForge에서 그 역할이 속한 그룹이며, 역할은 GrantForge가 부여한 역할입니다.

## 결정과 제한 사항

- 사용할 수 있는 스냅샷이 없으면 broker와 프록시의 요청을 제외한 모든 것이 거부됩니다. 사용하지 않는 서비스는 `authorizationEnabled`를 끈 것처럼 Pulsar를 검사하지 않습니다.
- 타입이 `pulsar`가 아닌 서비스에 바인딩된 토큰은 모든 것을 거부합니다.
- Pulsar는 많은 검사 전에 역할이 슈퍼유저인지 또는 테넌트 관리자인지 먼저 묻습니다. 요청을 통과시키는 답만 보고되며, 요청 자체의 검사는 결과와 관계없이 보고됩니다. Pulsar가 이 질문만으로 결정하는 요청(Pulsar 3.0의 테넌트와 클러스터 작업)은 거부될 때 debug 수준으로만 기록됩니다.
- 클러스터의 `admin`은 그 클러스터 broker의 슈퍼유저로만 만듭니다.
- `pulsar-admin namespaces permissions` 같은 Pulsar의 권한 목록은 실패합니다. 정책은 GrantForge가 보관합니다.
- 클라이언트 주소는 주소와 범위만 가능하며, 호스트 이름은 어떤 클라이언트와도 일치하지 않습니다.
- 결정은 `grantforge.decision.cache.ttl.ms` 동안 보관되므로 정책의 적용 시작이나 종료가 최대 그만큼 늦어질 수 있습니다.

## 관련 가이드

- [Apache Pulsar](/ko/plugins/pulsar/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
