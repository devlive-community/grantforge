---
title: 예제 애플리케이션
description: '저장소의 두 가지 완전한 예제: 브라우저에서 바로 연결하는 상점과, 서버 측에서 로그인하는 노트.'
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

`samples/`에는 실행 가능한 두 애플리케이션이 있으며, GrantForge와 함께 엔드투엔드 테스트로 검증됩니다. 연동할 때 가장 좋은 참고 자료입니다.

| 예제 | 포트 | 보여 주는 연동 방식 |
| --- | --- | --- |
| `samples/shop` | 19081 | 브라우저가 공개 클라이언트 + PKCE로 교차 출처 로그인하고, 리소스별로 버튼을 표시하며, 백엔드는 `@RequirePermission`로 API를 검증합니다. `@GrantForgeEntity`로 주문 엔터티를 선언하고 "본인만", "본 테넌트 전체" 데이터 범위로 조회합니다 |
| `samples/notes` | 19082 | 서버가 Spring Security `oauth2Login`(기밀 클라이언트 + PKCE)으로 로그인하고, 커스텀 `AccessTokenResolver`로 세션에서 토큰을 가져옵니다. 권한이 있는 사람에게만 "노트 작성"을 표시합니다 |

## 실행

예제는 독립된 Maven 빌드이며, 이 저장소의 starter와 JavaScript SDK에 의존합니다.

```bash
./mvnw -N install
./mvnw -pl sdk/grantforge-spring-boot-starter install
(cd sdk/grantforge-js && pnpm install && pnpm build)
./mvnw -f samples/pom.xml package
```

예제의 설정은 모두 환경 변수에서 가져옵니다.

| 변수 | 설명 |
| --- | --- |
| `GRANTFORGE_URL` | GrantForge 주소 |
| `SHOP_BROWSER_CLIENT_ID` | 상점 브라우저 측의 공개 클라이언트 |
| `SHOP_CLIENT_ID` / `SHOP_CLIENT_SECRET` | 상점 백엔드가 데이터 엔터티를 선언하는 데 쓰는 기밀 클라이언트(`catalog` scope) |
| `SHOP_SDK_DIRECTORY` | JavaScript SDK의 빌드 산출물 디렉터리(`sdk/grantforge-js/dist`) |

GrantForge에서 두 애플리케이션의 리소스, 클라이언트, 역할, 데이터 정책을 만들어야 합니다. 엔드투엔드 테스트의 준비 스크립트 `core/grantforge-web/tests/samples/setup.ts`가 이 모든 단계를 상세히 보여 주므로 그대로 참고하면 됩니다.

## 엔드투엔드 테스트

`script/ci/e2e_fullstack.sh`는 풀스택 테스트 뒤에 두 예제를 빌드하고 실행하여 교차 출처 PKCE 로그인과 CORS, 버튼 표시 여부, 인터페이스 403, "본인만"과 "본 테넌트 전체" 데이터 범위, 삭제와 로그아웃, 노트 애플리케이션의 서버 측 로그인을 검증합니다. `GRANTFORGE_E2E_SKIP_SAMPLES=1`을 설정하면 건너뛸 수 있습니다.
