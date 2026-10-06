---
title: REST API 참조
description: 전체 REST 인터페이스와 접근 요구 사항으로, OpenAPI 계약에서 생성됩니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

이 페이지는 저장소의 OpenAPI 계약(`core/grantforge-web/src/api/openapi.json`)에서 문서를 빌드할 때 생성되며 서버와 일치합니다. 실행 중인 서비스는 `/v3/api-docs`에서 같은 계약을 제공합니다.

- **공개**: 로그인이 필요하지 않습니다.
- **로그인만 하면 사용 가능**: 로그인한 계정이라면 모두 사용할 수 있습니다.
- 그 외 인터페이스는 필요한 권한 코드를 나열합니다. 권한 코드는 리소스 카탈로그에 API 리소스로 등록됩니다.

콘솔 인터페이스는 세션과 CSRF 토큰을 사용합니다. [보안 설계](/ko/architecture/security/)를 참고하세요. 비즈니스 애플리케이션이 사용하는 오픈 API는 [오픈 API](/ko/integration/open-api/)를 참고하세요.

{{generated:api}}
