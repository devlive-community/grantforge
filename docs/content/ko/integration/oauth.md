---
title: OAuth 2.1과 OpenID Connect
description: 권한 부여 서버의 엔드포인트, 클라이언트 타입, 토큰 규칙, 서명 키.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge에는 Spring Authorization Server 기반의 권한 부여 서버가 내장되어 있으며, OAuth 2.1의 보안 요구 사항을 따릅니다. 인증 코드 플로(PKCE 필수), 리프레시 토큰, 클라이언트 자격 증명만 지원하며, 암시적 플로와 패스워드 그랜트는 지원하지 않습니다.

![권한 부여 서버](/screenshots/oauth.png)

## 디스커버리 문서와 엔드포인트

디스커버리 문서: `<GrantForge>/.well-known/openid-configuration`. **플랫폼 관리 → 권한 부여 서버** 페이지에서 바로 복사할 수 있습니다. 발급자는 기본적으로 요청이 도달한 주소이며, 리버스 프록시 뒤에 배포할 때는 `grantforge.oauth.issuer`로 고정합니다.

| 엔드포인트 | 설명 |
| --- | --- |
| `/oauth2/authorize` | 인증 코드 플로. 모든 클라이언트는 PKCE(S256)를 사용해야 합니다 |
| `/oauth2/token` | 인증 코드, 리프레시 토큰, 클라이언트 자격 증명. 리프레시 토큰은 사용할 때마다 교체되며, 예전 토큰이 다시 나타나면 전체 그랜트가 철회됩니다 |
| `/oauth2/revoke` | 토큰 철회 |
| `/oauth2/jwks` | 서명 공개 키(RS256) |
| `/userinfo` | `sub`, `tid`, `preferred_username`. profile 범위에는 `name`이, email 범위에는 `email`이 포함됩니다 |

액세스 토큰과 ID 토큰에는 `tid`(테넌트 ID)와 `preferred_username`이 포함됩니다. ID 토큰의 `auth_time`은 사용자가 콘솔에 로그인한 시간입니다.

## 클라이언트

**플랫폼 관리 → 리소스 카탈로그**에서 애플리케이션을 선택하고 "OAuth 클라이언트"를 클릭해 해당 애플리케이션의 클라이언트를 관리합니다.

| 설정 | 규칙 |
| --- | --- |
| 타입 | **공개 클라이언트**는 브라우저, 모바일 등 시크릿을 보관할 수 없는 애플리케이션용입니다. **기밀 클라이언트**는 서버 측 애플리케이션용이며 시크릿을 가집니다 |
| 콜백 주소 | 최대 10개. 절대 주소여야 하며 와일드카드와 fragment는 허용되지 않습니다. https이거나, 로컬 http(localhost, 127.0.0.1, [::1])이거나, 네이티브 애플리케이션의 커스텀 프로토콜이어야 합니다 |
| scope | `openid`, `profile`, `email`, `permissions`(권한 조회), `catalog`(데이터 엔터티 선언, 클라이언트 자격 증명 전용) |
| 인증 방식 | 인증 코드, 리프레시 토큰(인증 코드가 필요하며 기밀 클라이언트만 받음), 클라이언트 자격 증명(기밀 클라이언트 전용) |
| 토큰 유효 기간 | 액세스 토큰 1분–24시간(기본 15분), 리프레시 토큰 1시간–90일(기본 30일) |

기밀 클라이언트의 시크릿은 등록하거나 교체할 때 한 번만 표시되며, GrantForge는 해시만 저장합니다. 교체 시 유예 기간(최대 7일)을 설정할 수 있고, 유예 기간 동안은 예전 시크릿과 새 시크릿이 모두 유효하므로 롤링 업데이트가 쉽습니다.

## 토큰 규칙

- 토큰은 해시로 저장합니다. 데이터베이스가 유출되어도 사용 가능한 토큰을 얻을 수 없습니다.
- 다음 중 하나라도 발생하면 이미 발급된 토큰은 더 이상 갱신되지 않습니다: 클라이언트가 비활성화되거나 삭제된 경우, 계정이 비활성화되거나 잠긴 경우, 계정이 비밀번호를 변경해야 하는 경우, 테넌트가 비활성화된 경우.
- 브라우저 교차 출처 호출: GrantForge는 활성화된 클라이언트의 콜백 주소가 있는 출처가 토큰 엔드포인트와 공개 API를 교차 출처로 호출하는 것을 허용하며, 쿠키는 전송하지 않습니다.

## 서명 키

서명 키는 RSA 2048로 생성하고 개인 키는 암호화해 저장합니다. 기본적으로 90일마다 자동 교체되며(`grantforge.oauth.signing-key-rotation`), 예전 공개 키는 2일 더 JWKS에 게시되어(`signing-key-retention`) 교체 전에 발급된 토큰도 계속 검증할 수 있습니다. 필요하면 권한 부여 서버 페이지에서 즉시 교체할 수 있습니다. 이는 민감한 작업이므로 2단계 인증을 켠 계정은 다시 인증해야 합니다.

## GrantForge로 콘솔 이외의 시스템에 로그인하기

OpenID Connect를 지원하는 모든 시스템(Grafana, GitLab, Jenkins 등)은 GrantForge를 IdP로 사용할 수 있습니다. 리소스 카탈로그에서 해당 애플리케이션과 기밀 클라이언트를 만들고, 디스커버리 문서 주소와 client_id, 시크릿을 상대 시스템의 OIDC 설정에 입력하면 됩니다. 반대로 GrantForge도 다른 IdP로 로그인할 수 있으며, 자세한 내용은 [신원 소스](/ko/guide/identity-sources/)를 참고하세요.
