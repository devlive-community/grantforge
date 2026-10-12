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
| `connection.timeout.ms` | 초기 연결과 인증만 기다리는 상한, 기본값 `10000` ms, 범위 `1000..60000`; 연결 이후의 동기 요청에는 적용되지 않음 |
| `request.timeout.ms` | 단일 동기 요청의 최대 대기 시간, 기본값 `8000` ms, 범위 `1000..60000` |
| `lookup.path` | chroot 아래에서 조회와 탐색을 시작할 znode, 기본값 `/`(예: `/brokers`); 탐색은 이 znode 안으로 제한 |
| `lookup.max.entries` | 한 znode에서 한 번에 읽는 자식 수 상한, 기본값 `10000`, 범위 `1..100000` |

## 인증

- **none**: 로그인하지 않으며 ACL이 `world:anyone`에 열린 znode만 읽을 수 있습니다.
- **digest**: `username:password`로 로그인하며 ZooKeeper ACL의 `digest` 신원과 대응합니다. 비밀번호가 틀려도 로그인이 거부되지는 않고 다른 신원이 되므로, 보호된 znode는 접근 거부로 표시됩니다.
- **kerberos**: keytab으로 SASL 로그인하며, 서버의 `krb5.conf`가 KDC를 찾을 수 있어야 합니다. principal과 keytab마다 프로세스의 기존 설정 옆에 자체 JAAS 섹션을 두므로, 하나의 GrantForge가 서로 다른 principal로 여러 앙상블에 연결할 수 있습니다. 비밀번호만으로 하는 Kerberos 로그인은 아직 지원하지 않습니다.

저장할 때 연결 문자열 형식, digest의 사용자와 비밀번호, Kerberos의 principal과 keytab, Kerberos에서만 쓰는 keytab과 서버 principal, 그리고 시간 제한과 상한의 범위를 검사합니다.

TLS를 끈 경우 트러스트스토어와 키스토어의 경로와 비밀번호를 비워 두어야 합니다. 스토어 비밀번호를 지정하려면 해당 스토어 경로도 필요합니다. 두 스토어의 유형은 `jks`, `pkcs12`, `pem`만 허용됩니다.

TLS 스토어 비밀번호의 앞뒤 공백은 그대로 유지됩니다. 서비스 설정이 이 연결의 TLS 모드와 사용할 스토어를 결정하며 JVM에 이미 설정된 ZooKeeper TLS 및 스토어 설정을 덮어씁니다. 서버 인증서와 호스트 이름은 계속 검증합니다.

## znode 탐색

**데이터 권한 → 정책**에서 서비스를 선택하고 `path` 옆의 **찾아보기**를 사용합니다.

- 자식을 이름순으로 페이지 단위로 나열하며, 자식이 있는 znode는 더 열 수 있습니다.
- 각 znode의 ACL(ZooKeeper 도구와 같은 `scheme:id:cdrwa` 형식), 데이터 크기와 수정 시간을 표시합니다.
- 조회 사용자가 ACL을 읽을 수 없는 znode도 이름은 나열되며, 열면 거부 이유를 보여 줍니다.

페이지를 탐색하는 동안 znode가 삭제되어도 커서는 마지막으로 검사한 자식 이름을 기준으로 계속 진행하며, 해당 자식이 여전히 존재할 필요는 없습니다. 자식 목록은 스냅샷이 아닙니다. 동시 추가와 삭제에는 ZooKeeper의 동작 의미가 적용되므로 이후 페이지의 내용이 달라질 수 있습니다.

경로를 입력하면 후보가 제시됩니다. 존재하지 않는 부모는 후보가 없고, `lookup.path` 밖의 경로는 거부됩니다. 자식이 `lookup.max.entries`보다 많은 znode는 일부만 보여 주지 않고 오류를 반환합니다.

## 정책 적용

리소스는 `path` 한 단계뿐이며, 접근 유형은 ZooKeeper ACL 권한과 같은 `read`, `write`, `create`, `delete`, `admin`입니다. 경로 정책은 재귀와 제외를 지원합니다.

서비스 플러그인은 관리, 조회, 탐색을 담당하고, 서버 내부 에이전트는 SASL 인증 provider를 통해 마커 ACL에 정책을 적용합니다. `mvn -pl agents/grantforge-agent-zookeeper -am package`로 빌드하면 의존성을 포함한 Java 8 호환 단일 shaded JAR가 생성됩니다. 배포판 경로는 `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`입니다. 구현은 ZooKeeper 3.6–3.9 API를 대상으로 하지만 실제 버전별 호환성은 추가 검증이 필요합니다. 전체 버전 매트릭스 검증이나 운영 환경 인수 검증을 완료했다는 의미는 아닙니다.

### 배포

1. 클라이언트 요청을 받을 수 있는 모든 노드를 포함하여 **각 ZooKeeper 서버**의 `lib` 디렉터리에 JAR를 설치합니다. GrantForge의 해당 `zookeeper` 데이터 서비스에서 **Agent 자격 증명**을 생성합니다. 토큰은 별도 파일에 저장하고 ZooKeeper 실행 계정만 읽을 수 있도록 권한을 제한합니다. 토큰 자체를 `zoo.cfg`에 기록하지 마세요.
2. 각 서버의 `zoo.cfg`에 다음 설정을 추가하고 예제 주소, 파일 경로, 인스턴스 식별자를 바꿉니다. `grantforge.server.url`(`http[s]://GrantForge`), `grantforge.token.file`, 로컬 `grantforge.cache.dir`, `grantforge.instance`는 모두 필수입니다. 캐시 디렉터리는 ZooKeeper 실행 계정이 쓸 수 있어야 합니다.

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. 이 provider는 **기존 SASL provider를 대체합니다**. 다른 `authProvider.N` 항목에 동일한 `sasl` scheme으로 등록된 provider를 남겨 두면 안 됩니다. `enableEagerACLCheck=false`가 필수이며, 해당 검사를 켜면 provider 등록이 거부되고 마커도 접근을 허용하지 않습니다. 정상적인 ZooKeeper SASL, JAAS, Kerberos 인증 설정을 유지하고 동작을 확인하세요. 에이전트는 인증을 대체하지 않습니다. 앙상블의 유지보수 절차에 따라 서버를 재시작합니다.
4. 선택적으로 `grantforge.signing.key.file`에 신뢰할 서명 공개키를 고정하는 파일 경로를 지정할 수 있습니다. 기본값인 `grantforge.principal.mapping=short`는 `kafka/host@REALM`을 `kafka`로 매핑하며, `full`은 전체 principal을 유지합니다. 정책의 사용자는 매핑된 신원과 일치해야 합니다.

### ACL과 제한 사항

- `sasl:grantforge` 마커 ACL만 GrantForge 정책에 판단을 위임합니다. 예를 들어 `sasl:grantforge:cdrwa`에서 `c=create`, `d=delete`, `r=read`, `w=write`, `a=admin`입니다. 다른 SASL 신원을 포함한 기존 ACL은 평소처럼 판단합니다.
- 에이전트가 실행되지 않거나, 사용 가능한 정책 스냅샷이 없거나, 스냅샷의 `serviceType`이 `zookeeper`가 아니거나, 정책이 일치하지 않거나 허용하지 않으면 마커는 접근을 거부합니다. **전역 deny는 아닙니다**. 다른 ACL, `world:anyone`, `super` 신원은 여전히 요청을 허용할 수 있습니다.
- 정책은 서버의 절대 대상 경로와 비교합니다. `create`/`delete`는 ACL을 제공하는 부모가 아니라 생성/삭제할 znode 경로를 사용합니다. chroot 요청은 정책 경로에 실제 chroot 접두사를 포함해야 합니다. chroot `/kafka` 아래의 `/brokers`는 `/kafka/brokers`에 해당합니다. 순차 `create`는 순번을 붙이기 전 서버의 절대 요청 접두사를 검사하며, 후속 `read`/`delete`는 생성된 전체 노드 이름을 검사합니다.
- 기존 ACL은 자동으로 이전되지 않으며 ACL이 재귀적으로 상속되지도 않습니다. 새 znode에는 마커 ACL을 명시적으로 설정해야 합니다. `setACL`을 사용하기 전에 작은 범위에서 허용과 거부를 검증하고 독립적인 관리 접근 경로를 유지하세요. 루트 노드에 마커를 성급하게 적용하면 접근이 차단될 수 있습니다.
- 에이전트를 설치하지 않은 서버는 `sasl:grantforge`를 기존 SASL 신원 `grantforge`와 비교하며 정책을 평가하지 않습니다. 따라서 모든 노드에 배포해야 하며 일부 노드에만 배포해서는 앙상블을 보호할 수 없습니다.
- follower/observer가 leader로 전달한 쓰기 요청은 이 provider 인터페이스에서 클라이언트 연결이 없을 수 있으므로 `clientAddress` 컨텍스트가 비어 있을 수 있습니다. 클라이언트 IP 기반의 앙상블 전체 엔드투엔드 정책 적용은 보장할 수 없습니다.
- 이 provider는 요청의 최종 결과를 알 수 없으므로 최종 `AccessEvent` 감사 이벤트를 전송하지 않으며 완전한 접근 감사를 제공하지 않습니다.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [플러그인과 서비스 타입 개발](/ko/develop/plugins/)
