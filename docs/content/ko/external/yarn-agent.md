---
title: Apache Hadoop YARN 에이전트
description: YARN ResourceManager에 권한 부여 공급자를 설치하여, 누가 어떤 애플리케이션을 어느 큐에 제출하고 큐와 클러스터를 관리할 수 있는지, 어떤 네트워크에서 가능한지를 GrantForge 정책으로 결정합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

YARN 에이전트는 ResourceManager의 권한 부여 공급자입니다. YARN은 누가 큐에 제출하고, 큐를 관리하고, 클러스터를 관리할 수 있는지 에이전트에 묻고, 에이전트는 `yarn` 데이터 서비스의 서명된 정책으로 답합니다. 배포판은 의존성을 포함하고 재배치한 Java 8 호환 JAR `agents/yarn/grantforge-agent-yarn-<version>.jar`로 제공합니다.

저장소의 Testcontainers 통합 테스트는 Hadoop `3.2.4`, `3.3.6`, `3.4.3`, `3.5.0`의 ResourceManager에서 JAR를 실행합니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다. 서비스 플러그인은 [Apache Hadoop YARN](/ko/plugins/yarn/)에서 설명합니다.

## 동작 방식

- **모든 검사는 경로의 모든 큐에서 결정됩니다.** YARN과 마찬가지로 큐에 대한 권한은 그 아래 큐에도 적용되므로, 에이전트는 `root.sales.etl`로의 제출을 `root`, `root.sales`, `root.sales.etl`에서 결정합니다. 어느 하나라도 거부하면 거부되고, 어느 하나라도 허용하면 허용됩니다.
- **제출은 애플리케이션 이름으로도 결정됩니다.** 에이전트는 각 큐에서 큐와 애플리케이션 이름의 조합도 묻습니다. 따라서 정책 하나로 `payroll-*` 같은 일부 애플리케이션만 큐와 그 아래 큐에 받을 수 있습니다.
- **정책을 일부 네트워크에만 적용할 수 있습니다.** 모든 검사에는 클라이언트 주소(프록시 뒤에서는 첫 번째로 전달된 주소)가 포함되며, 에이전트는 Hadoop 자체의 호스트 목록으로 **클라이언트 주소** 조건을 평가합니다.
- **클러스터 관리는 정책을 따릅니다.** 원래 `yarn.admin.acl`이 허용하는 YARN 관리 작업에는 클러스터의 `admin`이 필요합니다.
- **거부는 이유와 함께 로그에 남습니다.** 클라이언트가 받는 거부 문구는 YARN이 정합니다. ResourceManager 로그에는 어떤 GrantForge 정책이 거부했는지, 또는 허용하는 정책이 없는지가 기록됩니다.
- **결정은 보관되고 접근은 합쳐집니다.** 현재 스냅샷의 결정은 기본적으로 100,000개를 10초 동안 보관하며, 새 스냅샷은 처음부터 다시 시작합니다. 기본 10초 창 안의 동일한 허용 접근은 횟수를 담은 접근 이벤트 하나로 보고되고, 거부는 하나씩 즉시 보고됩니다. 모든 이벤트에는 애플리케이션, 큐, 클라이언트 주소가 담깁니다.
- **ResourceManager 자체는 검사하지 않습니다.** ResourceManager 자신의 사용자(예: `yarn`)는 검사하지도 감사하지도 않습니다.

| 작업 | 필요한 접근 |
| --- | --- |
| 애플리케이션 제출 | 해당 큐의 `submit_app`, 또는 해당 큐와 애플리케이션 이름의 `submit_app`. 큐의 `admin_queue`도 됩니다 |
| 다른 사용자의 애플리케이션 종료, 이동, 우선순위 변경, 전체 세부 정보 보기 | 해당 큐의 `admin_queue`. 이동할 때는 새 큐의 `submit_app`도 필요합니다. 애플리케이션 소유자와 그 자체 ACL이 허용한 사용자는 언제나 가능합니다 |
| `yarn rmadmin`: 큐, 노드, 사용자 그룹, ACL 새로 고침, 노드 레이블과 속성 변경, 기타 관리 작업 | 클러스터의 `admin` |

## 배포

1. `yarn` 데이터 서비스를 추가하고 정책을 설정합니다. **데이터 권한 → 에이전트**를 열고 서비스를 선택한 뒤 에이전트 토큰을 발급합니다. 토큰은 한 번만 표시되므로 YARN 실행 계정만 읽을 수 있는 파일에 저장합니다.
2. JAR를 **모든 ResourceManager**의 `share/hadoop/yarn/lib`(예: `/opt/hadoop/share/hadoop/yarn/lib`)에 복사합니다.
3. 모든 ResourceManager의 `yarn-site.xml`에 다음 설정을 추가하고 예시의 주소, 경로, 사용자, 인스턴스 이름을 바꿉니다.

   ```xml
   <configuration>
     <property>
       <name>yarn.acl.enable</name>
       <value>true</value>
     </property>
     <property>
       <name>yarn.authorization-provider</name>
       <value>org.devlive.grantforge.yarn.agent.GrantForgeYarnAuthorizer</value>
     </property>
     <property>
       <name>yarn.admin.acl</name>
       <value>yarn</value>
     </property>
     <property>
       <name>grantforge.server.url</name>
       <value>https://grantforge.example.com</value>
     </property>
     <property>
       <name>grantforge.token.file</name>
       <value>/etc/hadoop/grantforge.token</value>
     </property>
     <property>
       <name>grantforge.instance</name>
       <value>resourcemanager-1</value>
     </property>
     <property>
       <name>grantforge.cache.dir</name>
       <value>/var/lib/hadoop-yarn/grantforge-cache</value>
     </property>
   ```

   `yarn.admin.acl`에는 ResourceManager 자신의 사용자만 적어야 합니다. YARN은 에이전트에 큐를 묻기 전에 관리자에게 모든 애플리케이션에 대한 모든 작업을 허용합니다. 캐시 디렉터리는 YARN 실행 계정이 쓸 수 있어야 합니다.
4. ResourceManager를 하나씩 다시 시작합니다. 에이전트가 첫 스냅샷을 받기 전까지는 ResourceManager 자신의 요청을 제외한 요청이 거부됩니다.
5. **기존 권한을 먼저 옮기십시오.** `acl_submit_applications`, `acl_administer_queue` 같은 스케줄러 설정의 큐 ACL은 더 이상 적용되지 않습니다. 미리 대응하는 정책을 만드십시오.

| 설정 | 용도 |
| --- | --- |
| `grantforge.server.url`, `grantforge.token.file`, `grantforge.instance`, `grantforge.cache.dir` | 필수: GrantForge 서버, 에이전트 토큰 파일, 이 ResourceManager의 이름, 서명된 스냅샷과 대기 이벤트를 위한 쓰기 가능한 디렉터리 |
| `grantforge.signing.key.file` | 스냅샷 서명 키를 고정하는 선택적 파일. 에이전트 페이지에서 Base64로 인코딩된 X.509 Ed25519 공개 키를 복사합니다. 없으면 첫 연결 때 서버에서 키를 가져옵니다 |
| `grantforge.connect.timeout.ms`, `grantforge.read.timeout.ms` | GrantForge 연결 및 읽기 타임아웃. 기본값 `5000` / `8000` 밀리초 |
| `grantforge.refresh.interval.ms` | 에이전트가 새 정책을 가져오는 간격. 기본값 `30000`, 최소 `1000` 밀리초 |
| `grantforge.audit.batch.size`, `grantforge.audit.queue.capacity`, `grantforge.audit.flush.interval.ms`, `grantforge.audit.spool.limit.bytes` | 접근 이벤트: 배치 크기(기본값 `500`), 큐 크기(기본값 `10000`), 전송 간격(기본값 `5000` 밀리초), 스풀 한도(기본값 64 MiB) |
| `grantforge.audit.fold.window.ms` | 동일한 허용 접근을 하나의 이벤트로 합치는 시간. 기본값 `10000`, 최대 `3600000` 밀리초. `0`은 하나씩 보고합니다 |
| `grantforge.decision.cache.size`, `grantforge.decision.cache.ttl.ms` | 보관할 최대 결정 수(기본값 `100000`, 최대 `10000000`, `0`은 보관하지 않음)와 보관 기간(기본값 `10000`, `1..3600000` 밀리초) |

## 사용자, 그룹과 역할

정책의 사용자는 YARN이 호출자에게 부여하는 짧은 이름입니다. simple 인증의 사용자 이름이거나 `hadoop.security.auth_to_local`에 따른 Kerberos 주체의 짧은 이름입니다. 그룹은 ResourceManager에서 Hadoop 그룹 매핑이 사용자에게 부여한 그룹과 GrantForge에서 사용자가 속한 그룹이며, 역할은 GrantForge가 사용자에게 부여한 역할입니다.

## 결정과 제한 사항

- 사용할 수 있는 스냅샷이 없으면 ResourceManager 자신의 요청을 제외한 모든 것이 거부됩니다. 사용하지 않는 서비스는 `yarn.acl.enable`을 끈 것처럼 YARN을 검사하지 않습니다.
- 타입이 `yarn`이 아닌 서비스에 바인딩된 토큰은 모든 것을 거부합니다.
- YARN은 관리 권한을 물을 때 클라이언트 주소를 전달하지 않으므로 `cluster`에서 클라이언트 주소 조건은 결코 성립하지 않습니다.
- 애플리케이션 소유자와 그 자체 ACL이 지정한 사용자는 YARN과 마찬가지로 정책과 관계없이 애플리케이션을 관리합니다.
- 애플리케이션 우선순위 ACL 같은 다른 접근 유형의 검사는 거부됩니다.
- 클라이언트 주소 조건의 호스트 이름은 결정을 보관된 것에서 가져오지 않고 새로 내릴 때마다 클라이언트 이름 조회가 필요합니다.
- 결정은 `grantforge.decision.cache.ttl.ms` 동안 보관되므로 정책의 적용 시작이나 종료가 최대 그만큼 늦어질 수 있습니다.

## 관련 가이드

- [Apache Hadoop YARN](/ko/plugins/yarn/)
- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
