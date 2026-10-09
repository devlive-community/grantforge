---
title: 데이터 서비스, 정책과 에이전트
description: 플러그인으로 HDFS, Hive 같은 외부 시스템의 권한을 관리합니다. 데이터 서비스, 접근 정책, 에이전트, 접근 감사.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

"데이터 권한" 그룹은 GrantForge 밖의 데이터 시스템에 대한 권한을 관리하며, 아키텍처는 Apache Ranger와 비슷합니다. 플러그인이 서비스 타입을 정의하고, 관리자가 콘솔에서 정책을 작성하면, 대상 시스템 안에 배포된 에이전트가 정책을 내려받아 접근을 로컬에서 판단합니다.

> [!NOTE]
> 현재 버전은 플러그인 프레임워크, 범용 정책 편집기, 정책 배포와 접근 감사, HDFS 서비스 타입과 Hadoop 3.5.0 NameNode 에이전트, 그리고 예제 플러그인(`example`)을 제공합니다. Hive 플러그인과 다른 Hadoop 버전용 에이전트는 아직 개발 중입니다.

```mermaid
flowchart LR
  C[콘솔: 데이터 서비스와 정책] --> S[GrantForge 서버]
  S -->|서명된 정책 스냅샷| A[에이전트(HDFS / Hive 내)]
  A -->|하트비트와 접근 감사| S
  U[사용자 데이터 접근] --> A
```

## 플러그인

**플랫폼 관리 → 플러그인**에는 로드된 서비스 타입 플러그인이 나열됩니다. 기본 플러그인은 서버와 함께 제공되고, 다른 플러그인은 `plugins` 디렉터리에 넣고 "다시 스캔"을 누르면 됩니다. 각 플러그인은 독립적으로 로드되므로 오류가 나도 해당 플러그인만 비활성화됩니다. 플러그인 개발은 [플러그인과 서비스 타입](/ko/develop/plugins/)을 참고하세요.

![플러그인](/screenshots/plugins.png)

## HDFS

배포 패키지에는 HDFS 플러그인(`plugins/hdfs`)이 포함되어 있으며 서비스 타입은 `hdfs`로, Apache Ranger의 HDFS 서비스와 같습니다.

- 리소스는 `path` 한 종류뿐이며 경로로 일치합니다. `/data/sales`는 자기 자신과 일치하고, "재귀"를 체크하면 그 아래의 모든 파일과 디렉터리와도 일치합니다. 제외를 지원합니다.
- 접근 타입은 `read`, `write`, `execute`이며 HDFS의 권한 비트와 대응합니다.
- 플러그인은 Hadoop 자체 클라이언트로 클러스터에 연결합니다. 연결 테스트는 조회 디렉터리가 존재하고 그 내용을 나열할 수 있는지 확인하며, 정책을 작성할 때 경로를 입력하면 해당 디렉터리 아래의 하위 디렉터리와 파일이 나열되고 디렉터리가 파일보다 앞에 옵니다.
- 서버 플러그인이 관리와 조회를 담당합니다. 정책이 HDFS 접근을 실제로 제약하게 하려면 [NameNode 에이전트](/ko/external/hdfs-agent/)도 배포해야 합니다.

| 설정 | 설명 |
| --- | --- |
| `username` | 디렉터리를 조회할 사용자. Kerberos를 쓸 때는 principal이며 예: `grantforge@EXAMPLE.COM` |
| `password` / `keytab` | Kerberos를 쓸 때 둘 중 하나. principal의 비밀번호, 또는 GrantForge 서버에 있는 keytab 파일의 경로 |
| `fs.default.name` | `hdfs://namenode:8020`, 고가용성의 `hdfs://nameservice1`, 또는 `webhdfs://namenode:9870` |
| `hadoop.security.authentication` | `simple` 또는 `kerberos` |
| `hadoop.security.authorization`, `hadoop.security.auth_to_local` | 클러스터의 core-site.xml과 같게 |
| `dfs.namenode.kerberos.principal` 등 | NameNode, DataNode, Secondary NameNode의 principal, 예: `nn/_HOST@EXAMPLE.COM` |
| `hadoop.rpc.protection` | `authentication`, `integrity` 또는 `privacy`, 클러스터와 같게 |
| 추가 Hadoop 설정 | 한 줄에 `key=value` 하나씩이며 고가용성 등에 씁니다. 예: `dfs.nameservices=nameservice1`, `dfs.ha.namenodes.nameservice1=nn1,nn2`, `dfs.namenode.rpc-address.nameservice1.nn1=nn1:8020`, `dfs.client.failover.proxy.provider.nameservice1=org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider` |
| `lookup.path` | 조회 디렉터리, 기본값 `/`. 예를 들어 `/data`로 지정하면 빈 입력은 `/data`의 내용을 나열하고 상대 입력은 여기서부터 완성합니다. 조회 사용자에게 루트 디렉터리를 나열할 권한이 없는 클러스터에 씁니다 |
| `lookup.max.entries` | 디렉터리를 한 번 조회할 때 스캔하는 항목의 최대 수, 기본값 `10000`, 범위 `1..100000`. 한도를 넘으면 오류를 반환해 후보를 조용히 놓치지 않습니다 |

추가 Hadoop 설정은 같은 이름의 연결 설정을 덮어쓰며, 설정 검증과 로그인은 모두 덮어쓴 값을 씁니다. `fs.defaultFS`와 `fs.default.name`은 별칭이며, 추가 설정에서는 둘 중 하나만 지정할 수 있습니다. 키 중복, 클러스터 주소가 아닌 주소, 자격 증명이 없는 Kerberos 설정은 저장할 때 거부됩니다. 클러스터 주소에는 클러스터 URI만 넣고, 조회할 하위 디렉터리는 `lookup.path`에 두세요.

`lookup.path`는 경로 후보를 탐색할 범위를 제한할 뿐 HDFS 자체의 접근 제어를 대체하지 않습니다. 심볼릭 링크와 ViewFS 마운트는 여전히 클러스터 설정을 따릅니다. 입력에서 앞의 `/`는 생략할 수 있고, `/`와 `.`의 반복은 허용되며, `..`와 범위 밖의 절대 경로는 거부됩니다. 존재하지 않는 디렉터리는 빈 후보를 반환하고, 권한이 부족하거나 연결이 실패하면 오류가 표시됩니다.

Kerberos를 쓸 때 GrantForge 서버는 KDC를 찾을 수 있어야 합니다. `/etc/krb5.conf`를 설정하거나 `-Djava.security.krb5.conf=`로 지정하세요.

## 데이터 서비스

**데이터 권한 → 데이터 서비스**: 서비스는 GrantForge가 권한을 관리하는 외부 시스템 인스턴스 하나이며, 예를 들어 HDFS 클러스터 하나입니다. 서비스를 추가할 때 서비스 타입을 선택하고, 플러그인이 정의한 설정 항목에 따라 연결 정보를 입력하며 먼저 **연결 테스트**를 할 수 있습니다. 비밀번호 같은 민감한 설정은 암호화해 저장하고 저장한 뒤에는 더 이상 표시하지 않습니다.

![데이터 서비스](/screenshots/services.png)

## 정책

**데이터 권한 → 정책**은 누가 데이터 서비스의 어떤 리소스로 무엇을 할 수 있는지 결정합니다.

- **접근 정책**은 접근을 허용하거나 거부합니다;
- **마스킹 정책**은 필드를 가립니다;
- **행 필터 정책**은 일부 행만 내보냅니다.

리소스 계층(예: Hive의 데이터베이스, 테이블, 열), 접근 타입(예: select, update), 조건은 모두 서비스 타입의 플러그인에서 옵니다. 리소스를 입력할 때는 대상 시스템에 실제로 존재하는 리소스를 검색할 수 있습니다. 정책의 대상은 사용자, 사용자 그룹 또는 역할입니다.

탐색할 수 있는 레벨(HDFS 경로 등)에는 **탐색** 버튼이 있습니다. 디렉터리를 단계별로 열어 소유자, 그룹, 권한을 보고 여러 파일이나 디렉터리를 한 번에 고를 수 있습니다. 조회나 탐색이 실패하면 이유(권한 없음, 연결 불가, 너무 큰 디렉터리 등)가 표시되고 다시 시도할 수 있습니다.

![정책](/screenshots/policies.png)

## 에이전트

**데이터 권한 → 에이전트**: 에이전트는 대상 시스템 안에 배포되며, 토큰으로 주기적으로 하트비트를 보고 서명된 정책 스냅샷을 내려받아 접근을 로컬에서 판단합니다. 이곳에서 에이전트 토큰을 발급하고(한 번만 표시됨) 각 에이전트가 이미 최신 정책을 사용하고 있는지 확인합니다.

![에이전트](/screenshots/agents.png)

## 접근 감사

**데이터 권한 → 접근 감사**: 에이전트가 보고한 접근 판단마다, 누가 언제 어디서 어떤 리소스로 무엇을 했는지, 허용되었는지 거부되었는지, 어떤 정책이 결정했는지가 기록됩니다. 기록은 기본적으로 90일 보관됩니다.

![접근 감사](/screenshots/access-audit.png)
