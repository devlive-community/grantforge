---
title: Apache Hadoop HDFS
description: HDFS 플러그인을 설치하고 연결을 설정하며 디렉터리 탐색과 경로 접근 정책을 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Hadoop HDFS 플러그인은 GrantForge에서 클러스터 연결, 경로 조회와 정책 관리를 제공합니다. 플러그인 ID와 서비스 타입은 모두 `hdfs`입니다.

## 설치

배포 패키지는 `plugins/hdfs`에 플러그인을 포함합니다. **플랫폼 관리 → 플러그인**에서 `hdfs`가 활성화되어 있는지 확인하고, 업데이트 후 플러그인 디렉터리를 다시 스캔합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**에서 서비스를 추가하고 HDFS(`hdfs`)를 선택합니다.
2. 클러스터 URI와 조회 사용자를 입력합니다. Hadoop 2.x는 `webhdfs://namenode:50070`을 사용하고, 3.x는 `hdfs://namenode:8020` 또는 `webhdfs://namenode:9870`을 사용할 수 있습니다. HTTPS는 `swebhdfs://`와 실제 클러스터 포트를 사용합니다.
3. 조회 디렉터리를 설정하고 연결을 테스트합니다. 디렉터리의 존재와 목록 조회를 확인한 후 서비스를 저장합니다.

| 설정 | 용도 |
| --- | --- |
| `fs.default.name` | 필수 클러스터 URI. 하위 디렉터리, 자격 증명, 쿼리를 포함하지 않습니다. HA는 `hdfs://nameservice1`과 해당 추가 속성을 사용할 수 있습니다 |
| `username` | 필수 조회 사용자. Kerberos에서는 `grantforge@EXAMPLE.COM`과 같은 principal |
| `hadoop.security.authentication` | 기본값 `simple`. Kerberos 클러스터에서는 `kerberos` 선택 |
| `hadoop.security.authorization` | Hadoop가 권한을 검사하는지 여부. 기본값 `false`. 클러스터의 core-site.xml과 일치 |
| `hadoop.security.auth_to_local` | Kerberos principal을 사용자 이름에 매핑하는 규칙. 클러스터의 core-site.xml과 일치 |
| `password` / `keytab` | Kerberos 암호 또는 GrantForge 서버의 keytab 파일 경로 |
| `dfs.namenode.kerberos.principal`, `dfs.datanode.kerberos.principal`, `dfs.secondary.namenode.kerberos.principal` | Kerberos 클러스터의 각 구성 요소 principal. `nn/_HOST@EXAMPLE.COM` 등, 클러스터 설정과 일치 |
| `lookup.path` | 조회와 탐색의 시작 디렉터리. 기본값 `/`, 예: `/data`. 탐색은 해당 디렉터리 안으로 제한 |
| `lookup.max.entries` | 전체 디렉터리 스캔 한도. 기본값 `10000`, 범위 `1..100000` |
| `hadoop.config` | HA 등 Hadoop 속성을 한 줄에 하나의 `key=value`로 설정. 동일한 연결 설정을 덮어씀 |
| `hadoop.rpc.protection` | 클러스터에 맞춰 `authentication`, `integrity`, `privacy` 선택 |
| `ssl.client.truststore.location` | `swebhdfs://` NameNode 인증서를 검증할 트러스트스토어의 GrantForge 서버 경로. 비우면 서버의 Java가 신뢰하는 인증서를 신뢰합니다 |
| `ssl.client.truststore.password` | 트러스트스토어가 보호된 경우의 비밀번호. 암호화해 저장합니다 |
| `ssl.client.truststore.type` | `jks`(기본값) 또는 `pkcs12` |

Hadoop 2.7.7에서 NameNode 속성 확장을 활성화하면 일반 사용자의 루트 경로 `/` 조회가 확인된 업스트림 `NullPointerException`을 유발하므로 `lookup.path`를 `/data` 같은 실제 디렉터리로 설정하세요([에이전트 가이드](/ko/external/hdfs-agent/) 참조).

Kerberos는 접근 가능한 KDC, 서버의 `krb5.conf`, 클러스터에 맞는 `hadoop.security.auth_to_local` 규칙과 서비스 principal도 필요합니다. 조회 계정은 디렉터리 메타데이터를 가져옵니다. Hadoop 3.5.0 Kerberos 클러스터에서 실제로 검증했습니다. RPC(keytab 또는 비밀번호)와 swebhdfs(서비스 자체 트러스트스토어와 SPNEGO)로 조회와 경로 탐색을 확인했고, 잘못된 자격 증명, 누락된 트러스트스토어, simple 클라이언트는 모두 실패합니다. 다른 버전은 아직 검증되지 않았습니다.

Kerberos를 쓰면 GrantForge는 조회할 때마다 KDC에 묻지 않고 로그인을 재사용합니다. keytab 로그인은 티켓 만료가 가까워지면 Hadoop이 갱신하고, 비밀번호 로그인은 티켓 수명이 5분의 1(최소 1분) 미만으로 남으면 다시 로그인합니다. 비밀번호를 바꾸거나 keytab 파일이 갱신되면 다시 로그인합니다. 각 서비스는 자신의 트러스트스토어를 쓰며, 서버 클래스패스의 `ssl-client.xml`이 이를 대신하지 않습니다.

설정은 저장할 때 검증됩니다: `hadoop.config`의 `fs.defaultFS`와 `fs.default.name`은 별칭이므로 하나만 설정합니다. 클러스터 URI에 자격 증명, 경로, 쿼리, 프래그먼트를 포함해서는 안 됩니다. `kerberos`를 선택하면 `password` 또는 `keytab`이 필요합니다. `lookup.path`는 `..`를 포함하지 않는 절대 경로여야 합니다. `lookup.max.entries`는 `1`에서 `100000` 사이여야 합니다. 추가 속성은 동일한 연결 설정을 덮어쓰며, 검증과 로그인 모두 덮어쓴 값을 사용합니다.

## 경로 탐색

**데이터 권한 → 정책**에서 서비스를 선택하고 `path` 옆의 **찾아보기**를 사용합니다.

- 디렉터리를 열고 경로로 이동하거나 상위로 돌아가며 필요할 때 다음 페이지를 불러옵니다.
- 파일/디렉터리 표시, 소유자, 그룹, 권한, 파일 크기와 수정 시간을 확인합니다.
- 여러 파일이나 디렉터리 또는 현재 디렉터리를 정책에 추가합니다. 경로를 입력해 후보를 찾는 방식도 사용할 수 있습니다.

RPC와 배치 목록을 지원하는 WebHDFS는 네이티브 페이지 나누기를 사용합니다. 이전 엔드포인트는 스캔 한도 내에서 디렉터리를 읽고 초과 시 오류를 반환합니다. 권한, 인증, 연결 실패는 원인을 표시하며 재시도할 수 있습니다.

입력에서 앞의 `/`는 생략할 수 있고, 연속된 `/`와 `.`는 허용되며, `..`와 `lookup.path` 범위를 벗어난 절대 경로는 거부됩니다. 존재하지 않는 디렉터리는 후보를 반환하지 않습니다. 탐색은 HDFS 자체의 접근 제어를 대체하지 않으며, 심볼릭 링크와 ViewFS 마운트는 클러스터 설정을 따릅니다.

## 정책 적용

리소스 계층은 `path` 하나이며 접근 타입은 `read`, `write`, `execute`입니다. 경로 정책은 재귀와 제외를 지원합니다.

서버 플러그인은 관리와 조회를 담당합니다. 실제 접근을 제어하려면 Hadoop 버전에 맞는 NameNode 에이전트도 필요하며, 사용자는 HDFS 네이티브 권한과 GrantForge 정책을 모두 충족해야 합니다. 번호별 에이전트는 2.7, 2.10, 3.2, 3.3, 3.4, 3.5를 대상으로 합니다. 검증된 조합, 인증 및 HA 검증 범위와 슈퍼유저 제한은 에이전트 가이드를 참조하세요.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [Apache Hadoop HDFS NameNode 에이전트](/ko/external/hdfs-agent/)
- [플러그인과 서비스 타입 개발](/ko/develop/plugins/)
