---
title: HDFS NameNode 에이전트
description: Hadoop 버전에 맞는 NameNode 에이전트로 GrantForge 경로 정책을 적용하고 접근 감사를 보고합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

서버 플러그인 `grantforge-plugin-hdfs`가 리소스와 연결을 정의합니다. Hadoop 2.7, 2.10, 3.2, 3.3, 3.4, 3.5용 번호가 붙은 NameNode 에이전트는 해당 SPI로 접근을 검사하고 서명된 스냅샷, 정책 평가, 감사 전송 로직을 공유합니다.

공통 HDFS 로직은 `agents/grantforge-agent-hdfs-common`, 버전별 어댑터는 `agents/grantforge-agent-hdfs-<line>`에 있습니다. `core/grantforge-agent-core`는 공통 프로토콜 및 실행 기반으로 유지됩니다.

서버 플러그인은 하나의 `hdfs` 서비스 타입을 유지하며 클라이언트 버전은 에이전트와 독립적입니다. Hadoop 2.x 연결과 경로 조회에는 `webhdfs://namenode:50070`를, HTTPS에는 해당 `swebhdfs://` 주소를 사용합니다. Hadoop 3.x는 RPC `hdfs://` 또는 WebHDFS를 사용할 수 있습니다. 컨테이너 테스트는 2.x의 WebHDFS와 3.x의 두 프로토콜을 검증합니다. 2.x RPC는 인증하지 않았으며 설정된 프로토콜을 자동으로 변경하지 않습니다.

inode 속성 확장을 활성화한 Apache Hadoop 2.7.7에서는 일반 사용자가 `/`를 직접 조회할 때 에이전트 콜백 전에 네이티브 코드에서 null 포인터 오류가 발생합니다. 이 버전에서는 데이터 작업과 `lookup.path`에 `/data` 같은 실제 데이터 디렉터리를 사용하세요. 컨테이너 테스트는 이 루트 경로 제한도 명시적으로 검증합니다.

## HDFS 에이전트 대상 버전

| Hadoop 기준 버전 | 컨테이너 Java | 에이전트 디렉터리 |
| --- | --- | --- |
| 2.7.7 | Java 8 | `agents/hdfs/2.7/` |
| 2.10.2 | Java 8 | `agents/hdfs/2.10/` |
| 3.2.4 | Java 8 | `agents/hdfs/3.2/` |
| 3.3.6 | Java 8(amd64 이미지) | `agents/hdfs/3.3/` |
| 3.4.3 | Java 11 | `agents/hdfs/3.4/` |
| 3.5.0 | Java 17 | `agents/hdfs/3.5/` |

클러스터의 Hadoop 계열에 맞게 `agents/hdfs/<line>/`에서 `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar`를 선택하세요. 공통 로직은 Java 8, 3.5 어댑터는 Java 17을 대상으로 합니다.

Hadoop 2.7, 2.10, 3.2, 3.3에는 에이전트가 사용하는 슈퍼유저 인가 콜백이 없습니다. 이러한 슈퍼유저 접근은 Hadoop이 관리합니다. GrantForge 정책으로 제어할 데이터 접근에는 일반 사용자를 사용하세요.

## 권한 관계

다음 집행 동작은 일반 사용자의 접근을 설명합니다. 슈퍼유저 제한은 위 내용과 해당 콜백 설명을 참고하세요.

에이전트는 HDFS 기본 권한 검사를 먼저 수행한 뒤 GrantForge 정책을 수행합니다. 사용자는 기본 권한과 정책을 모두 만족해야 합니다. GrantForge의 허용 정책은 POSIX 권한, ACL, 소유자 검사, sticky bit을 우회하지 않으며, 거부 정책은 항상 거부합니다. 기본 권한 설정은 여전히 Hadoop의 관리 도구로 관리합니다.

기본값인 `grantforge.hdfs.native.fallback=false`는 로컬 정책 스냅샷이 없거나, 일치하는 정책이 없거나, 에이전트가 아직 시작하지 않았을 때 데이터 접근을 거부합니다. `true`로 지정하면 정책이 결정하지 않은 접근은 기본 권한을 사용하고, 명시적인 거부 정책은 그대로 효력이 있습니다. 서버가 일시적으로 닿지 않는 동안에도 마지막으로 서명 검증을 통과한 로컬 스냅샷을 계속 사용합니다.

하나의 인가 콜백에 들어 있는 조상, 대상, 서브트리, 스냅샷 경로 투영은 모두 같은 버전의 정책 스냅샷을 사용합니다. 갱신된 정책은 다음 콜백부터 효력이 생기므로, 서로 다른 버전의 허용 규칙이 조합되는 일은 없습니다. 접근 감사에는 실제로 사용된 정책 버전이 기록됩니다.

에이전트는 일반 사용자가 대상에 접근하는 데 필요한 `read`, `write`, `execute`를 검사하고, 부모 디렉터리와 조상 디렉터리, 재귀 검증이 필요한 하위 디렉터리도 검사합니다. 생성, 삭제, 이름 변경 같은 작업은 여러 경로를 얽히므로 허용 정책이 모두를 커버해야 합니다. 엄격 모드에서는 대상 파일에 `read` 정책만 있는 것으로는 부족하고, 사용자에게 조상 디렉터리의 `execute` 정책이 필요합니다. 예를 들어 `/`에서 `execute`를 허용하고 재귀를 체크한 뒤, 실제 데이터 디렉터리에 읽기와 쓰기 권한을 부여하세요.

스냅샷 경로는 실제 요청 경로와 `.snapshot/<스냅샷 이름>`을 뗀 원래 경로를 함께 검사합니다. 예를 들어 `/data/.snapshot/s1/secret`은 `/data/secret`로도 검사됩니다. 따라서 원래 경로의 거부 정책이 스냅샷에도 적용되고, 명시적인 스냅샷 경로에는 더 엄격한 제한을 따로 둘 수 있습니다. 메타데이터 조회는 HDFS의 디렉터리 순회 권한 의미를 그대로 따릅니다.

재귀 인가는 한 번에 최대 `100000`개의 inode를 검사하며, 한도를 넘으면 NameNode 안에서 메모리를 무한히 할당하지 않도록 작업을 거부합니다. 지나치게 긴 경로는 전체 경로로 정책을 판단하고, 감사의 리소스 표시는 `1000`자로 제한하며 요청 상세에 원래 길이와 SHA-256 요약을 기록합니다.

다음 슈퍼유저 콜백 설명은 Hadoop 3.4와 3.5에 적용됩니다. HDFS 슈퍼유저는 여전히 Hadoop이 관리합니다. 경로가 있는 슈퍼유저 콜백은 먼저 Hadoop의 슈퍼유저 검사를 통과한 뒤, Hadoop 3.4 / 3.5이 제공하는 작업 이름으로 정책을 검사합니다. 파일 읽기와 메타데이터 조회는 `read`, 디렉터리 열거는 `read` + `execute`, 알려진 수정 작업은 `write`를 요구합니다. 알 수 없거나, 존재하지 않거나, 정확히 추론할 수 없는 작업(예: `checkAccess`, `concat`)은 보수적으로 세 가지 권한을 모두 요구합니다.

슈퍼유저 콜백에는 전체 inode와 서브트리 컨텍스트가 없고, 경로가 없는 클러스터 관리 호출은 기본 검사를 유지합니다. 하위 디렉터리 정책으로 슈퍼유저의 모든 재귀 작업을 제한할 수는 없습니다. 데이터 소비자는 일반 Hadoop 사용자를 써야 합니다.

## 지표

에이전트는 Hadoop의 Metrics2 체계를 통해 지표를 보고하며, NameNode 자체의 dfs 지표와 같은 sink를 사용합니다. NameNode의 JMX에서는 `Hadoop:service=NameNode,name=GrantForgeHdfsAgent`로 나타나며 Prometheus의 JMX 익스포터가 바로 수집할 수 있습니다. 모든 지표는 `instance`(데이터 서비스 인스턴스 이름)와 `agentVersion` 태그를 가지므로, HA의 두 NameNode를 구분해 볼 수 있습니다. 지표 등록이 실패해도 잃는 것은 지표뿐입니다. 에이전트는 경고를 기록하고 지표 없이 계속 인가를 수행합니다.

| 지표 | 설명 |
| --- | --- |
| `Callbacks` | 에이전트가 수행한 인가 콜백 수 |
| `SuperuserCallbacks` | 에이전트가 수행한 슈퍼유저 콜백 수 (3.4 / 3.5) |
| `NativeDenies` | 에이전트보다 앞서 Hadoop이 거부한 접근 수 |
| `EvaluationFailures` | 정책 평가 예외로 실패 닫힌 콜백 수 |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | 정책이 허용 / 거부 / 미결로 판단한 권한 수. 미결은 엄격 모드에서 역시 거부 |
| `MissingSnapshots` | 검증된 정책 스냅샷이 없는 동안 처리한 콜백 수 |
| `SnapshotVersion` | 현재 사용하는 정책 스냅샷의 버전, 없으면 0 |
| `QueuedEvents` / `DroppedEvents` | 메모리에서 보고를 기다리는 감사 이벤트 수 / 큐나 디스크 버퍼가 차서 버려진 이벤트 수 |
| `ServerReachable` | 마지막 정책 서버 접근이 성공했는지 여부(1/0) |

## 배포

1. GrantForge의 데이터 서비스에 `hdfs` 서비스를 추가하고 설정을 저장한 뒤 연결을 테스트하며, 실제 Hadoop 짧은 사용자 이름, 사용자 그룹 또는 역할에 경로 정책을 설정합니다.
2. "데이터 권한 → 에이전트"에서 이 서비스의 토큰을 발급합니다. 토큰 원문을 각 NameNode의 로컬 파일에 쓰며, 예를 들어 `/etc/hadoop/grantforge/token`으로 두고 NameNode를 실행하는 사용자가 읽을 수 있게 합니다.
3. 클러스터의 Hadoop 계열에 맞게 `agents/hdfs/<line>/`의 `grantforge-agent-hdfs-<line>-<GrantForge-version>.jar`를 NameNode 클래스패스에 복사하세요. 예: `$HADOOP_HOME/share/hadoop/hdfs/lib/`. 호환되는 어댑터 하나만 설치하세요. jar에는 공통 로직, Jackson과 서명 라이브러리가 포함되며 Hadoop 클래스는 NameNode가 제공합니다.
4. 각 NameNode의 `hdfs-site.xml`에 다음 속성을 설정합니다. HA의 두 NameNode는 서로 다른 `instance`와 각자의 로컬 캐시 디렉터리를 사용합니다.

```xml
<property>
  <name>dfs.namenode.inode.attributes.provider.class</name>
  <value>org.devlive.grantforge.hdfs.agent.HdfsAuthorizationProvider</value>
</property>
<property>
  <name>grantforge.hdfs.server.url</name>
  <value>https://grantforge.example.com/</value>
</property>
<property>
  <name>grantforge.hdfs.token.file</name>
  <value>/etc/hadoop/grantforge/token</value>
</property>
<property>
  <name>grantforge.hdfs.instance</name>
  <value>namenode-1</value>
</property>
<property>
  <name>grantforge.hdfs.cache.dir</name>
  <value>/var/lib/hadoop/grantforge</value>
</property>
<property>
  <name>grantforge.hdfs.native.fallback</name>
  <value>false</value>
</property>
```

5. `dfs.permissions.enabled=true`인지, `dfs.namenode.inode.attributes.provider.bypass.users`가 비어 있는지 확인합니다. 인가 콜백을 우회할 수 있는 설정은 에이전트가 시작할 때 거부합니다. NameNode를 재시작한 뒤 GrantForge 에이전트 페이지에서 하트비트와 정책 버전을 확인합니다. 에이전트는 NameNode의 기존 설정을 읽기만 하며 inode의 기본 속성은 수정하지 않습니다.

처음 배포할 때는 `native.fallback=true`로 시작해 정책 스냅샷이 동기화되고 조상 디렉터리 권한이 갖추어졌는지 확인한 뒤 엄격 모드로 전환할 수 있습니다. 토큰이 바인딩된 서비스 타입은 반드시 `hdfs`여 합니다. 설정이 잘못되거나 다른 서비스 타입에 바인딩되면 접근이 거부됩니다.

## 선택 설정

| 속성 | 기본값 | 용도 |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | GrantForge에 연결할 때의 타임아웃 |
| `grantforge.hdfs.read.timeout.ms` | `8000` | 응답을 읽을 때의 타임아웃 |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | 서버에 닿지 않을 때의 정책 갱신 간격, 최소 `1000`. 정상 하트비트는 서버가 제안한 간격을 사용 |
| `grantforge.hdfs.signing.key.file` | 설정 안 됨 | 선택적인 서명 공개키 파일. 콘솔이 제공한 Base64 X.509 공개키 내용이 들어갑니다. 설정하면 그 공개키의 서명만 받아들입니다 |
| `grantforge.hdfs.audit.batch.size` | `500` | 한 번에 보고할 최대 이벤트 수, 범위 `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | 메모리 감사 큐의 용량, 범위 `1..1000000`, 배치 하나는 반드시 담을 수 있어야 합니다. 큐가 차면 새 이벤트를 세고 버립니다 |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | 감사 플러시 간격, 양의 정수, 최대 `2147483647`. 줄이면 보고 지연을 낮출 수 있습니다 |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | 서버에 닿지 않을 때의 디스크 버퍼 상한, 음이 아닌 정수. `0`이면 디스크 버퍼를 끕니다 |

접근량이 갑자기 많을 때는 감사 큐를 늘려 큐 넘침을 줄이고, 플러시 간격을 줄이면 감사 지연은 낮아지지만 보고 빈도는 늘어납니다. 디스크 버퍼 제한은 장시간 단절 동안의 디스크 사용량을 조절하며, 버퍼를 끄거나 다 쓰면 이벤트를 잃을 수 있습니다. 감사 보고는 백그라운드에서 수행되며 정책 서버의 응답을 기다리지 않습니다.

서명 공개키를 설정하지 않으면 에이전트가 처음 서버에 접근할 때 공개키를 가져와 스냅샷과 함께 저장합니다. 에이전트는 Hadoop이 넘겨준 짧은 사용자 이름과 그룹을 사용하고, 역할과 추가 그룹은 서명된 스냅샷에서 옵니다. Kerberos principal의 짧은 이름 매핑은 클러스터의 `hadoop.security.auth_to_local`이 결정합니다.

## 소스 빌드와 검증

다음 명령은 3.5 예시입니다. 대상 어댑터에 맞게 2.7, 2.10, 3.2, 3.3 또는 3.4로 바꾸세요.

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

에이전트 산출물은 `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<버전>.jar`에 생깁니다. 단위 테스트는 NameNode 인가 콜백, 설정, 버전 메타데이터, 정책 결정을 다루며 WebHDFS와 Kerberos 테스트는 로컬 임시 서비스를 띄웁니다. 실제 환경에 내놓기 전에 대상 클러스터에서도 읽기와 쓰기, 생성, 이름 변경, 재귀 삭제, HA 전환, 단절 뒤의 캐시 동작을 검증해야 합니다.

통합 검증은 `verify` 단계에서 Testcontainers로 실행합니다(단위 테스트는 클러스터를 띄우지 않으며, `verify`에는 사용 가능한 Docker daemon이 필요합니다).

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# nightly와 같은 진입점을 사용합니다
bash script/ci/hdfs_integration.sh 3.5
# 한 계열 또는 전체 버전 행렬
bash script/ci/hdfs_integration.sh all
```

테스트는 버전이 고정된 Apache Hadoop 컨테이너 이미지를 사용하고, 실제로 패키징한 에이전트 jar를 NameNode 클래스패스에 넣습니다. Testcontainers는 격리된 네트워크를 만들고 NameNode와 DataNode의 수명 주기를 관리하며, 읽기와 쓰기, 생성, 추가, 이름 변경, 삭제, 재귀와 스냅샷 거부, 기본 권한과 감사, 정책 갱신, 정책 서버를 끊은 뒤 서명된 캐시로 NameNode를 재시작하는 경우, 스냅샷이 없을 때의 엄격 모드와 기본 권한 폴백을 검증합니다.

실행 중인 Docker daemon이 필요하고 테스트 이미지를 내려받을 수 있어야 합니다. Docker를 쓸 수 없으면 테스트는 실패하며 조용히 건너뛰지 않습니다. 파일 시스템 클라이언트는 Hadoop 컨테이너 안에서 실행되고, 정책 HTTP 서비스는 Testcontainers의 호스트 포트 포워딩을 사용하므로 외부 Hadoop 클러스터는 필요 없습니다. 테스트가 끝나면 컨테이너와 테스트 네트워크를 정리하고 로그는 `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`에 저장합니다.

HA 테스트는 NameNode 두 개, DataNode 한 개, JournalNode 한 개를 띄우고 두 에이전트에 서로 다른 인스턴스 이름과 캐시 디렉터리를 설정합니다. 논리 HDFS 클라이언트로 활성 노드를 수동 전환하고 전환 뒤의 읽기와 쓰기, 거부 정책을 검증합니다. JournalNode 한 개는 테스트용일 뿐이므로 과반수 장애 허용을 검증하지 않으며 ZooKeeper 자동 장애 조치와도 무관합니다.

공통 테스트 소스는 `agents/grantforge-agent-hdfs-common/src/test/shared`에 있으며 번호가 붙은 프로덕션 모듈에서 컴파일합니다. 별도의 Maven 테스트 프로젝트는 만들지 않고 Testcontainers는 test 의존성으로 유지합니다. nightly는 Java 17/21 테스트 호스트에서 여섯 Hadoop 버전을 검사하며 컨테이너 내부 Java는 위 표를 따릅니다. 보고서와 컨테이너 로그를 저장합니다.

Hadoop 확장 진입점과 권한 의미는 [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html)와 [HDFS 권한 설명서](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html)를 참고하세요.
