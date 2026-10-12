---
title: Apache ZooKeeper 에이전트
description: 모든 ZooKeeper 노드에 에이전트를 배포해 마커 ACL로 GrantForge 정책을 적용합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

서버 플러그인 `grantforge-plugin-zookeeper`가 `zookeeper` 서비스 타입, 리소스, 연결 설정을 한곳에서 제공합니다. 정책을 적용하려면 ZooKeeper 내부에서 동작하는 두 번째 구성요소인 에이전트가 필요합니다. 에이전트는 SASL 인증 provider로 서버의 ACL 판단에 참여합니다. 에이전트는 `agents/grantforge-agent-zookeeper`에 있으며, `mvn -pl agents/grantforge-agent-zookeeper -am package`로 의존성을 포함한 Java 8 호환 단일 shaded JAR를 빌드하면 배포판 경로 `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`에 포함됩니다. 구현은 ZooKeeper 3.6–3.9 API를 대상으로 하지만 실제 버전별 호환성은 추가 검증이 필요합니다. 전체 버전 매트릭스 검증이나 운영 환경 인수 검증을 완료했다는 의미는 아닙니다.

## 배포

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

## ACL과 제한 사항

- `sasl:grantforge` 마커 ACL만 GrantForge 정책에 판단을 위임합니다. 예를 들어 `sasl:grantforge:cdrwa`에서 `c=create`, `d=delete`, `r=read`, `w=write`, `a=admin`입니다. 다른 SASL 신원을 포함한 기존 ACL은 평소처럼 판단합니다.
- 에이전트가 실행되지 않거나, 사용 가능한 정책 스냅샷이 없거나, 스냅샷의 `serviceType`이 `zookeeper`가 아니거나, 정책이 일치하지 않거나 허용하지 않으면 마커는 접근을 거부합니다. **전역 deny는 아닙니다**. 다른 ACL, `world:anyone`, `super` 신원은 여전히 요청을 허용할 수 있습니다.
- 정책은 서버의 절대 대상 경로와 비교합니다. `create`/`delete`는 ACL을 제공하는 부모가 아니라 생성/삭제할 znode 경로를 사용합니다. chroot 요청은 정책 경로에 실제 chroot 접두사를 포함해야 합니다. chroot `/kafka` 아래의 `/brokers`는 `/kafka/brokers`에 해당합니다. 순차 `create`는 순번을 붙이기 전 서버의 절대 요청 접두사를 검사하며, 후속 `read`/`delete`는 생성된 전체 노드 이름을 검사합니다.
- 기존 ACL은 자동으로 이전되지 않으며 ACL이 재귀적으로 상속되지도 않습니다. 새 znode에는 마커 ACL을 명시적으로 설정해야 합니다. `setACL`을 사용하기 전에 작은 범위에서 허용과 거부를 검증하고 독립적인 관리 접근 경로를 유지하세요. 루트 노드에 마커를 성급하게 적용하면 접근이 차단될 수 있습니다.
- 에이전트를 설치하지 않은 서버는 `sasl:grantforge`를 기존 SASL 신원 `grantforge`와 비교하며 정책을 평가하지 않습니다. 따라서 모든 노드에 배포해야 하며 일부 노드에만 배포해서는 앙상블을 보호할 수 없습니다.
- follower/observer가 leader로 전달한 쓰기 요청은 이 provider 인터페이스에서 클라이언트 연결이 없을 수 있으므로 `clientAddress` 컨텍스트가 비어 있을 수 있습니다. 클라이언트 IP 기반의 앙상블 전체 엔드투엔드 정책 적용은 보장할 수 없습니다.
- 이 provider는 요청의 최종 결과를 알 수 없으므로 최종 `AccessEvent` 감사 이벤트를 전송하지 않으며 완전한 접근 감사를 제공하지 않습니다.
