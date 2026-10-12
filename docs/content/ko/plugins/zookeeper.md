---
title: Apache ZooKeeper
description: ZooKeeper 플러그인을 설치하고 설정하며 znode를 탐색하고 znode 경로의 접근 정책을 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache ZooKeeper 플러그인은 GrantForge에 앙상블 연결, znode 경로 조회와 정책 관리를 제공합니다. 플러그인 ID와 서비스 타입은 모두 `zookeeper`입니다.

## 설치

배포판의 `plugins/zookeeper`에 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `zookeeper`가 활성화되어 있는지 확인하고, 업데이트한 뒤에는 플러그인 디렉터리를 다시 스캔합니다. 플러그인은 자체 클래스 로더에서 ZooKeeper 3.9 클라이언트를 사용하며 서버의 라이브러리와 분리됩니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 ZooKeeper(`zookeeper`)를 선택합니다.
2. 연결 문자열을 입력합니다. 앙상블의 서버 목록이며 chroot를 붙일 수 있습니다(예: `zk1:2181,zk2:2181,zk3:2181/kafka`). 클라이언트가 서버 사이를 자동으로 전환합니다.
3. 인증 방식을 선택하고 조회 znode를 설정한 뒤 연결을 테스트하고(znode가 존재하며 자식 목록을 가져올 수 있는지 확인) 서비스를 저장합니다.

| 설정 | 용도 |
| --- | --- |
| `zookeeper.connect` | 필수 연결 문자열: 쉼표로 구분한 `host[:port]`, 필요하면 뒤에 `/chroot`; chroot는 `/`로 끝날 수 없음 |
| `authentication` | `none`(기본값), `digest` 또는 `kerberos` |
| `username` | digest: ACL의 digest 사용자(콜론 불가); Kerberos: principal(예: `grantforge@EXAMPLE.COM`) |
| `password` | digest 사용자의 비밀번호, 암호화하여 저장 |
| `keytab` | Kerberos: GrantForge 서버에 있는 principal의 keytab 경로 |
| `zookeeper.sasl.client.username` | Kerberos: 서버 principal의 첫 부분, 기본값 `zookeeper`, 즉 `zookeeper/<host>@<REALM>` |
| `zookeeper.server.principal` | Kerberos: 서버 principal이 위 형식이 아닐 때의 전체 principal(예: `zookeeper/zk.example.com@EXAMPLE.COM`) |
| `zookeeper.client.secure` | 서버의 TLS 클라이언트 포트에 연결할지 여부, 기본값 `false` |
| `zookeeper.ssl.trustStore.location` / `.password` / `.type` | 서버를 검증하는 트러스트스토어; 비우면 서버 Java가 신뢰하는 인증서를 신뢰; 유형은 `jks`, `pkcs12` 또는 `pem` |
| `zookeeper.ssl.keyStore.location` / `.password` / `.type` | 클라이언트 인증서를 요구하는 서버용 키스토어 |
| `session.timeout.ms` | 세션 시간 제한, 기본값 `15000`, 범위 `2000..120000` |
| `connection.timeout.ms` | 서버와 로그인을 기다리는 시간, 기본값 `10000`, 범위 `1000..60000` |
| `lookup.path` | chroot 아래에서 조회와 탐색을 시작할 znode, 기본값 `/`(예: `/brokers`); 탐색은 이 znode 안으로 제한 |
| `lookup.max.entries` | 한 znode에서 한 번에 읽는 자식 수 상한, 기본값 `10000`, 범위 `1..100000` |

## 인증

- **none**: 로그인하지 않으며 ACL이 `world:anyone`에 열린 znode만 읽을 수 있습니다.
- **digest**: `username:password`로 로그인하며 ZooKeeper ACL의 `digest` 신원과 대응합니다. 비밀번호가 틀려도 로그인이 거부되지는 않고 다른 신원이 되므로, 보호된 znode는 접근 거부로 표시됩니다.
- **kerberos**: keytab으로 SASL 로그인하며, 서버의 `krb5.conf`가 KDC를 찾을 수 있어야 합니다. principal과 keytab마다 프로세스의 기존 설정 옆에 자체 JAAS 섹션을 두므로, 하나의 GrantForge가 서로 다른 principal로 여러 앙상블에 연결할 수 있습니다. 비밀번호만으로 하는 Kerberos 로그인은 아직 지원하지 않습니다.

저장할 때 연결 문자열 형식, digest의 사용자와 비밀번호, Kerberos의 principal과 keytab, Kerberos에서만 쓰는 keytab과 서버 principal, 그리고 시간 제한과 상한의 범위를 검사합니다.

## znode 탐색

**데이터 권한 → 정책**에서 서비스를 선택하고 `path` 옆의 **찾아보기**를 사용합니다.

- 자식을 이름순으로 페이지 단위로 나열하며, 자식이 있는 znode는 더 열 수 있습니다.
- 각 znode의 ACL(ZooKeeper 도구와 같은 `scheme:id:cdrwa` 형식), 데이터 크기와 수정 시간을 표시합니다.
- 조회 사용자가 ACL을 읽을 수 없는 znode도 이름은 나열되며, 열면 거부 이유를 보여 줍니다.

경로를 입력하면 후보가 제시됩니다. 존재하지 않는 부모는 후보가 없고, `lookup.path` 밖의 경로는 거부됩니다. 자식이 `lookup.max.entries`보다 많은 znode는 일부만 보여 주지 않고 오류를 반환합니다.

## 정책 적용

리소스는 `path` 한 단계뿐이며, 접근 유형은 ZooKeeper ACL 권한과 같은 `read`, `write`, `create`, `delete`, `admin`입니다. 경로 정책은 재귀와 제외를 지원합니다.

서버 플러그인은 관리, 조회, 탐색을 담당합니다. ZooKeeper 서버 안에서 이 정책을 적용하는 에이전트는 아직 제공되지 않으며, 그 전까지는 ZooKeeper 자체 ACL이 접근을 결정합니다.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [플러그인과 서비스 타입 개발](/ko/develop/plugins/)
