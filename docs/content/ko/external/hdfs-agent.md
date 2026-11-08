---
title: Apache Hadoop HDFS NameNode 에이전트
description: Hadoop 버전에 맞는 NameNode 에이전트로 GrantForge 경로 정책을 적용하고 접근 감사를 보고합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

서버 측 `grantforge-plugin-hdfs`가 `hdfs` 서비스 유형, 리소스, 연결 설정을 한곳에서 제공합니다. NameNode 에이전트는 Hadoop 버전별로 빌드되며, 해당 버전의 `INodeAttributeProvider`와 `AccessControlEnforcer`로 접근을 검사하고 서명된 정책, 캐시, 정책 평가, 감사 로직을 공유합니다.

소스에서 서버 측 플러그인은 `plugins/grantforge-plugin-hdfs`, 버전별 에이전트는 `agents/grantforge-agent-hdfs-<line>`, Hadoop에 의존하지 않는 공통 운영 로직은 `agents/grantforge-agent-hdfs-common`에 있습니다. 공통 네이티브 어댑터는 정식 Maven 운영 모듈인 `agents/grantforge-agent-hdfs-native`에 있으며 Java 8 / Hadoop 2.7.7을 기준으로 합니다. 번호가 붙은 각 모듈은 Maven 바이너리 의존성으로 이를 참조하고 버전별 진입점과 고유 콜백만 유지하며, 공통 운영 소스를 다시 컴파일하지 않습니다. 프로토콜, 스냅숏 캐시, 감사 기반은 여전히 `core/grantforge-agent-core`에 있습니다.

서버 측 플러그인은 `hdfs` 유형 하나만 유지하며 클라이언트 버전은 에이전트 버전과 독립적입니다. Hadoop 2.x의 연결과 경로 조회에는 `webhdfs://namenode:50070`(HTTPS를 켠 경우 대응하는 `swebhdfs://` 주소)을 사용하고, Hadoop 3.x는 RPC `hdfs://`나 WebHDFS를 사용할 수 있습니다. 컨테이너 매트릭스는 2.x의 WebHDFS와 3.x의 RPC 및 WebHDFS를 검증합니다. 2.x RPC는 인증하지 않았으며 연결 프로토콜을 자동으로 전환하지 않습니다.

Apache Hadoop 2.7.7에서 inode 속성 확장을 켜면, 일반 사용자가 루트 경로 `/`를 직접 조회할 때 에이전트 콜백보다 먼저 네이티브 코드에서 널 포인터가 발생합니다. 이 버전에서는 데이터 작업과 서비스의 `lookup.path`에 `/data` 같은 실제 데이터 디렉터리를 사용하십시오. 테스트에는 루트 경로 실패에 대한 별도 단언이 남아 있습니다.

## 버전 선택

| 에이전트 모듈 접미사 | 검증된 Apache Hadoop | 컨테이너 JVM | 권한 인터페이스 | 슈퍼유저 경로 콜백 |
| --- | --- | --- | --- | --- |
| `2.7` | 2.7.7 | Java 8 | 매개변수 방식 | 없음 |
| `2.10` | 2.10.2 | Java 8 | 매개변수 방식 | 없음 |
| `3.2` | 3.2.4 | Java 8 | 매개변수 방식 | 없음 |
| `3.3` | 3.3.6 | Java 8 | 컨텍스트 방식 | 없음 |
| `3.4` | 3.4.3 | Java 11 | 컨텍스트 방식, 슈퍼유저 및 거부 콜백 | 있음 |
| `3.5` | 3.5.0 | Java 17 | 컨텍스트 방식, 슈퍼유저 및 거부 콜백 | 있음 |

예를 들어 Hadoop 2.10.2에는 `agents/hdfs/2.10/grantforge-agent-hdfs-2.10-<GrantForge-version>.jar`를 설치합니다. NameNode마다 한 버전의 에이전트만 설치하고, 업그레이드할 때 이전 jar를 제거하며 구성의 provider 클래스 이름은 그대로 둡니다. 에이전트는 시작할 때 Hadoop 주/부 버전과 필수 콜백을 확인하고 버전이 맞지 않으면 시작을 멈춥니다. 같은 주/부 버전의 다른 패치나 벤더 배포판은 따로 검증해야 합니다.

`common`과 `2.7`~`3.4`의 에이전트 본체는 Java 8 바이트코드를, `3.5`는 Java 17을 사용합니다. 위 표는 통합 테스트에서 실제로 사용한 JVM이며, 3.4.3을 Java 8로 검증했다는 뜻은 아닙니다. 3.3.6 공식 테스트 이미지는 amd64만 제공하므로 ARM 호스트에서는 명시적으로 amd64 에뮬레이션으로 실행합니다.

## 권한 관계

에이전트는 먼저 HDFS 네이티브 권한 검사를 수행한 뒤 GrantForge 정책을 적용합니다. 사용자는 네이티브 권한과 정책 요구를 모두 충족해야 합니다. GrantForge의 허용 정책은 POSIX 권한, ACL, 소유자 검사, sticky bit를 우회하지 않으며, 거부 정책은 항상 거부합니다. 네이티브 권한 설정은 계속 Hadoop 관리 도구로 관리합니다.

기본값은 `grantforge.hdfs.native.fallback=false`입니다. 로컬 정책 스냅숏이 없거나, 일치하는 정책이 없거나, 에이전트가 아직 시작되지 않았으면 데이터 접근을 거부합니다. `true`로 설정하면 정책이 결정하지 않은 접근에는 네이티브 권한을 사용하며, 명시적인 거부 정책은 여전히 유효합니다. 서버에 일시적으로 연결할 수 없으면 서명 검증을 통과한 마지막 로컬 스냅숏을 계속 사용합니다.

한 번의 인가 콜백 안에서 조상, 대상, 하위 트리, 스냅숏 경로 투영은 같은 버전의 정책 스냅숏을 사용합니다. 갱신된 정책은 다음 콜백부터 적용되므로 서로 다른 버전의 허용 규칙이 섞이지 않습니다. 접근 감사에는 실제로 사용한 정책 버전이 기록됩니다.

에이전트는 일반 사용자가 대상에 접근하는 데 필요한 `read`, `write`, `execute`를 검사하고, 상위 디렉터리, 조상 디렉터리, 재귀 검증이 필요한 하위 디렉터리도 검사합니다. 생성, 삭제, 이름 변경 같은 작업은 여러 경로에 걸치므로 허용 정책이 이를 모두 포함해야 합니다. 엄격 모드에서는 대상 파일의 `read` 정책만으로는 부족하며, 사용자에게 조상 디렉터리의 `execute` 정책도 구성해야 합니다. 예를 들어 `/`에 대한 `execute`를 재귀로 허용하고, 실제 데이터 디렉터리에 읽기와 쓰기 권한을 구성합니다.

스냅숏 경로는 실제 요청 경로와 `.snapshot/<스냅숏 이름>`을 뺀 원래 경로를 모두 검사합니다. 예를 들어 `/data/.snapshot/s1/secret`은 `/data/secret`도 검사합니다. 따라서 원래 경로의 거부 정책은 스냅숏에도 적용되며, 명시적인 스냅숏 경로에 더 엄격한 제한을 추가할 수 있습니다. 메타데이터 조회는 HDFS 디렉터리 탐색 권한 의미를 따릅니다.

한 번의 재귀 인가는 최대 `100000`개의 inode를 검사하며, 상한을 넘으면 작업을 거부해 NameNode 안에서 메모리를 무한히 할당하지 않도록 합니다. 아주 긴 경로는 전체 경로로 정책을 판정합니다. 감사 리소스 표시는 `1000`자로 제한되며, 요청 세부 정보에 원래 길이와 SHA-256 다이제스트를 기록합니다.

Hadoop 2.7, 2.10, 3.2, 3.3은 에이전트를 호출하기 전에 슈퍼유저를 건너뛰므로 에이전트가 이 접근을 통제하거나 감사할 수 없습니다. Hadoop 3.4, 3.5의 경로가 있는 슈퍼유저 콜백은 먼저 네이티브 검사를 통과한 뒤 작업 이름에 따라 정책을 검사합니다. 파일 읽기와 메타데이터 조회에는 `read`, 디렉터리 나열에는 `read` + `execute`, 알려진 수정 작업에는 `write`가 필요합니다. 알 수 없거나 누락되었거나 정확히 추론할 수 없는 작업(예: `checkAccess`, `concat`)에는 보수적으로 세 가지 권한을 모두 요구합니다.

슈퍼유저 콜백에는 완전한 inode와 하위 트리 문맥이 없으므로, 경로가 없는 클러스터 관리 호출은 네이티브 검사를 유지합니다. 하위 디렉터리 정책으로 슈퍼유저의 모든 재귀 작업을 제한할 수는 없습니다. 데이터 사용자는 일반 Hadoop 사용자를 사용해야 합니다.

## 지표

에이전트는 Hadoop의 Metrics2 체계로 지표를 보고하며 NameNode 자체의 dfs 지표와 같은 sink를 사용합니다. NameNode의 JMX에서는 `Hadoop:service=NameNode,name=GrantForgeHdfsAgent`로 나타납니다(Prometheus JMX 익스포터로 바로 수집할 수 있습니다). 각 지표에는 `instance`(데이터 서비스 인스턴스 이름)와 `agentVersion` 레이블이 붙어 HA의 두 NameNode를 따로 볼 수 있습니다. 지표 등록에 실패해도 잃는 것은 지표뿐이며, 에이전트는 경고를 기록하고 지표 없이 인가를 계속합니다.

| 지표 | 설명 |
| --- | --- |
| `Callbacks` | 에이전트가 실행한 인가 콜백 수 |
| `SuperuserCallbacks` | 에이전트가 실행한 슈퍼유저 콜백 수 |
| `NativeDenies` | 에이전트보다 먼저 Hadoop이 거부한 접근 수 |
| `EvaluationFailures` | 정책 평가 예외로 실패 시 닫힘 처리된 콜백 수 |
| `DecisionsAllowed` / `DecisionsDenied` / `DecisionsUndetermined` | 정책이 허용 / 거부 / 미결정으로 판정한 권한 수. 미결정도 엄격 모드에서는 거부됩니다 |
| `MissingSnapshots` | 검증된 정책 스냅숏 없이 처리한 콜백 수 |
| `SnapshotVersion` | 현재 사용 중인 정책 스냅숏 버전. 0은 없음을 뜻합니다 |
| `QueuedEvents` / `DroppedEvents` | 메모리에서 보고를 기다리는 감사 이벤트 수 / 큐나 디스크 버퍼가 가득 차서 버린 이벤트 수 |
| `ServerReachable` | 마지막 정책 서버 접근이 성공했는지 여부(1/0) |

## 배포

1. GrantForge의 데이터 서비스에 `hdfs` 서비스를 추가하고 구성을 저장한 뒤 연결을 테스트합니다. 실제 Hadoop 짧은 사용자 이름, 사용자 그룹 또는 역할에 대해 경로 정책을 구성합니다.
2. "데이터 권한 → 에이전트"에서 이 서비스의 토큰을 발급합니다. 토큰 원문을 각 NameNode의 로컬 파일(예: `/etc/hadoop/grantforge/token`)에 쓰고 NameNode 실행 사용자가 읽을 수 있게 합니다.
3. 위 표에 따라 배포 패키지의 `agents/hdfs/<Hadoop 버전 계열>/`에서 해당 에이전트 jar를 골라 NameNode 클래스 경로(예: `$HADOOP_HOME/share/hadoop/hdfs/lib/`)에 둡니다. 에이전트 jar에는 자체 정책 엔진, Jackson, 서명 라이브러리가 들어 있고 Hadoop 클래스는 NameNode가 제공합니다. 하트비트의 에이전트 버전에는 제품 버전과 컴파일에 사용한 Hadoop 버전이 포함됩니다.
4. 각 NameNode의 `hdfs-site.xml`에 다음 속성을 구성합니다. HA의 두 NameNode는 서로 다른 `instance`와 각자의 로컬 캐시 디렉터리를 사용합니다.

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

5. `dfs.permissions.enabled=true`이고 `dfs.namenode.inode.attributes.provider.bypass.users`가 비어 있는지 확인합니다. 에이전트는 인가 콜백을 우회할 수 있는 구성에서는 시작을 거부합니다. NameNode를 다시 시작한 뒤 GrantForge 에이전트 화면에서 하트비트와 정책 버전을 확인합니다. 에이전트는 NameNode의 기존 구성을 읽기만 하며 inode의 네이티브 속성은 바꾸지 않습니다.

처음 배포할 때는 `native.fallback=true`로 시작해 정책 스냅숏이 동기화되었는지 확인하고 조상 디렉터리 권한을 보완한 뒤 엄격 모드로 전환할 수 있습니다. 토큰에 연결된 서비스 유형은 `hdfs`여야 하며, 구성이 잘못되었거나 다른 서비스 유형에 연결되면 접근이 거부됩니다.

## 선택 설정

| 속성 | 기본값 | 용도 |
| --- | --- | --- |
| `grantforge.hdfs.connect.timeout.ms` | `5000` | GrantForge 연결 시간 제한 |
| `grantforge.hdfs.read.timeout.ms` | `8000` | 응답 읽기 시간 제한 |
| `grantforge.hdfs.refresh.interval.ms` | `30000` | 서버에 연결할 수 없을 때의 정책 갱신 간격. 최소 `1000`이며, 정상 하트비트는 서버가 권장하는 간격을 사용합니다 |
| `grantforge.hdfs.signing.key.file` | 설정 안 함 | 선택적 서명 공개 키 파일. 내용은 콘솔이 제공하는 Base64 X.509 공개 키이며, 설정하면 이 키의 서명만 받아들입니다 |
| `grantforge.hdfs.audit.batch.size` | `500` | 한 번에 보고하는 최대 이벤트 수. 범위는 `1..1000` |
| `grantforge.hdfs.audit.queue.capacity` | `10000` | 메모리 감사 큐 용량. 범위는 `1..1000000`이며 최소 한 배치를 담을 수 있어야 합니다. 큐가 가득 차면 개수를 세고 새 이벤트를 버립니다 |
| `grantforge.hdfs.audit.flush.interval.ms` | `5000` | 감사 플러시 간격. 양의 정수이며 최대 `2147483647`. 줄이면 보고 지연이 줄어듭니다 |
| `grantforge.hdfs.audit.spool.limit.bytes` | `67108864` | 서버에 연결할 수 없을 때의 디스크 버퍼 상한. 음이 아닌 정수이며 `0`은 디스크 버퍼를 끕니다 |

접근이 갑자기 몰릴 때는 감사 큐를 키우면 큐 넘침이 줄어듭니다. 플러시 간격을 줄이면 감사 지연은 낮아지지만 보고 빈도는 늘어납니다. 디스크 버퍼 상한은 오랜 연결 끊김 동안의 디스크 사용량을 제한하기 위한 것이며, 버퍼를 끄거나 다 쓰면 이벤트가 유실될 수 있습니다. 감사 보고는 백그라운드에서 실행되며 정책 서버의 응답을 기다리지 않습니다.

서명 공개 키를 구성하지 않으면 에이전트는 처음에 서버에서 공개 키를 가져와 스냅숏과 함께 저장합니다. 에이전트는 Hadoop이 넘겨준 짧은 사용자 이름과 사용자 그룹을 사용하며, 역할과 추가 그룹은 서명된 스냅숏에서 가져옵니다. Kerberos principal의 짧은 이름 매핑은 클러스터의 `hadoop.security.auth_to_local`이 결정합니다.

## 소스에서 빌드 및 검증

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am package
./mvnw -pl plugins/grantforge-plugin-hdfs,agents/grantforge-agent-hdfs-3.5,core/grantforge-plugin-host -am test
```

에이전트 산출물은 `agents/grantforge-agent-hdfs-3.5/target/grantforge-agent-hdfs-3.5-<GrantForge-version>.jar`에 있습니다. 단위 테스트는 NameNode 인가 콜백, 구성, 버전 메타데이터, 정책 결정을 다루며, WebHDFS와 Kerberos 테스트는 로컬에 임시 서비스를 시작합니다. 운영에 투입하기 전에 대상 클러스터에서 읽기와 쓰기, 생성, 이름 변경, 재귀 삭제, HA 전환, 연결 끊김 뒤의 캐시 동작도 검증해야 합니다.

통합 검증은 `verify` 단계에서 Testcontainers로 실행됩니다(단위 테스트는 클러스터를 시작하지 않으며, `verify`에는 사용 가능한 Docker 데몬이 필요합니다).

```sh
./mvnw -pl agents/grantforge-agent-hdfs-3.5 -am verify
# nightly와 같은 진입점
bash script/ci/hdfs_integration.sh
# 지정한 버전 계열만 검증
bash script/ci/hdfs_integration.sh 2.10
```

테스트는 버전을 고정한 Apache Hadoop 이미지를 사용합니다. 이전 버전 이미지는 다이제스트를 고정한 Java 8 기본 이미지와 SHA-512로 검증한 Apache 배포본으로 빌드합니다. 해당 버전의 실제 에이전트 jar를 NameNode 클래스 경로에 넣고 실제 Hadoop과 JVM 버전을 단언합니다. Testcontainers는 격리된 네트워크를 만들고 NameNode와 DataNode의 수명 주기를 관리하며, 읽기와 쓰기, 생성, 추가, 이름 변경, 삭제, 재귀 및 스냅숏 거부, 네이티브 권한과 감사, 정책 갱신, 정책 서버와 끊긴 상태에서 NameNode를 다시 시작해 서명된 캐시를 사용하는 것, 스냅숏이 없을 때의 엄격 모드와 네이티브 권한 대체를 검증합니다.

실행 중인 Docker 데몬과 테스트 이미지 다운로드 허용이 필요합니다. Docker를 사용할 수 없으면 테스트는 조용히 건너뛰지 않고 실패합니다. 파일 시스템 클라이언트는 Hadoop 컨테이너 안에서 실행되고 정책 HTTP 서비스는 Testcontainers의 호스트 포트 전달을 사용하므로 외부 Hadoop 클러스터가 필요 없습니다. 테스트가 끝나면 컨테이너와 테스트 네트워크를 정리하고, 로그는 `agents/grantforge-agent-hdfs-3.5/target/hdfs-testcontainers`에 저장합니다.

HA 테스트는 NameNode 두 개, DataNode 하나, JournalNode 하나를 시작하고 두 에이전트에 별도의 인스턴스 이름과 캐시 디렉터리를 구성합니다. 논리 HDFS 클라이언트로 활성 노드를 수동 전환하고 전환 후의 읽기, 쓰기, 거부 정책을 검증합니다. JournalNode 하나는 테스트 전용이며 과반수 내결함성은 검증하지 않고 ZooKeeper 자동 장애 조치도 다루지 않습니다.

자동 HA 테스트(Hadoop 2.10~3.5, Java 17과 21)는 ZooKeeper, JournalNode 세 개, 각각 ZKFC가 있는 NameNode 두 개, DataNode 하나를 시작합니다. 테스트는 다음을 검증합니다. 활성 NameNode를 직접 멈추면 ZooKeeper가 다른 노드를 골라 넘겨받고, 새 활성 노드가 계속 정책을 적용하며 거부 이벤트는 그 노드의 인스턴스에 귀속됩니다. 노드가 빠져 있는 동안 게시된 새 정책은 그 노드가 Standby로 복귀한 뒤 적용되고, 다시 전환해도 이전 버전으로 돌아가지 않고 새 버전을 적용합니다. 정책 서비스를 사용할 수 없으면 넘겨받은 노드가 이미 불러온 스냅숏으로 계속 적용하고, 서비스가 복구되면 감사를 다시 보냅니다. JournalNode 세 개 중 하나를 멈춰도 쓰기는 성공하고, 두 개를 멈추면 쓰기가 실패하고 활성 NameNode가 멈추며 과반수 없이 계속하지 않습니다. Kerberos 버전(Hadoop 3.5.0만)도 있습니다. JournalNode도 keytab으로 로그인하고 HTTPS만 열며, ZKFC는 SASL로 ZooKeeper에 인증하고, 선출 노드에는 NameNode의 principal만 접근할 수 있습니다(인증하지 않은 ZooKeeper 클라이언트는 읽기조차 거부됩니다). 테스트는 활성 NameNode를 멈추면 다른 노드가 넘겨받아 Kerberos 사용자에게 계속 정책을 적용하고, 티켓이 없는 클라이언트는 항상 거부되며, 원래 노드가 복귀하면 다시 전환할 수 있음을 검증합니다. JournalNode는 NameNode와 같은 짧은 이름의 principal로 실행합니다. JournalNode는 edits를 NameNode의 전체 principal이나 자신과 같은 짧은 이름의 요청자에게만 넘기는데, HTTP 인증은 짧은 이름만 제공하기 때문입니다. Hadoop 2.7의 자동 HA는 검증하지 않았습니다. 그 클라이언트는 모든 NameNode 호스트 이름을 확인할 수 있을 때만 전환하는데, 테스트에서 멈춘 컨테이너 이름은 더 이상 확인되지 않으므로 클라이언트가 다른 노드를 시도하지 않고 바로 UnknownHostException을 보고합니다.

Kerberos 테스트는 여섯 개 버전 계열 모두에서 실행됩니다(Java 17과 21). KDC는 테스트 JVM 안에서 실행되고, NameNode와 DataNode는 각자 keytab을 사용해 보안 모드로 시작합니다. DataNode의 데이터 전송에는 SASL이 필요하고 HTTPS만 열며, WebHDFS는 SPNEGO를 사용합니다. 테스트는 principal을 짧은 이름으로 매핑한 뒤 GrantForge 정책이 읽기와 쓰기에 적용되는지, 정책이 없는 사용자가 거부되는지, 거부 이벤트가 짧은 이름으로 감사되는지, 티켓이 없는 클라이언트가 거부되고 simple 인증으로 대체되지 않는지, NameNode가 다시 시작된 뒤 다시 로그인해 정책을 계속 적용하는지를 검증합니다.

테스트 소스는 실제 운영 모듈의 `src/test`에 있습니다. 공통 정책 단위 테스트는 common에서, 공통 네이티브 단위 테스트는 `agents/grantforge-agent-hdfs-native/src/test`에서 실행되며 여섯 개 버전 에이전트에는 컴파일되지 않습니다. 버전별 콜백 테스트는 해당 번호 모듈에 남아 있고, 공통 컨테이너 테스트 소스 `agents/grantforge-agent-hdfs-common/src/test/shared`는 여전히 각 번호 모듈에 컴파일됩니다. 별도의 Maven 테스트 프로젝트는 없습니다. Testcontainers는 test 범위에만 있습니다. nightly는 Java 17과 21 호스트에서 여섯 개 Hadoop 버전을 검증하며, Hadoop 컨테이너는 위 표의 JVM을 사용합니다. 현재 컨테이너는 Simple 인증, 수동 HA, 여섯 개 버전 계열 모두의 Kerberos와 HTTPS, 2.10~3.5의 자동 HA, Hadoop 3.5.0에서 Kerberos 기반 자동 HA를 다룹니다. 2.7의 자동 HA, 다른 버전의 Kerberos 기반 자동 HA, 벤더 패치는 대상 환경에서 검증해야 합니다.

Hadoop 확장 지점과 권한 의미는 [Apache Hadoop 3.5.0 API](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/build/source/hadoop-hdfs-project/hadoop-hdfs/target/api/org/apache/hadoop/hdfs/server/namenode/INodeAttributeProvider.html)와 [HDFS 권한 가이드](https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-hdfs/HdfsPermissionsGuide.html)를 참고하십시오.
