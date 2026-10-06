---
title: 설정 참조
description: 모든 설정 항목, 기본값과 대응하는 환경 변수.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

설정은 `configure/application.properties`에 적거나 환경 변수로 덮어쓸 수 있습니다. Spring Boot의 완화된 바인딩 규칙도 그대로 적용됩니다. `grantforge.security.mfa.step-up-window`는 환경 변수 `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW`로 쓸 수 있습니다. 기간은 `30m`, `12h`, `90d` 같은 형태로 적습니다.

## 서비스와 데이터베이스

| 설정 항목 | 환경 변수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | HTTP 포트 |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | 내장 H2 파일 데이터베이스 | JDBC 주소, [데이터베이스](/ko/deploy/databases/) 참고 |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | 데이터베이스 사용자 |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | 빈 값 | 데이터베이스 비밀번호 |
| — | `GRANTFORGE_HOME` | 설치 디렉터리 | H2 데이터와 로그가 있는 디렉터리 |
| — | `GRANTFORGE_ID_NODE` | 자동 | 클러스터에서 인스턴스마다 고유한 노드 번호(0–1023) |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | CSV 가져오기 파일 크기 상한 |

## 초기화와 등록

| 설정 항목 | 기본값 | 설명 |
| --- | --- | --- |
| `grantforge.setup.token` | 빈 값 | 고정 초기화 토큰(`GRANTFORGE_SETUP_TOKEN`), 비어 있으면 무작위로 생성해 로그에 출력 |
| `grantforge.security.registration-enabled` | `false` | 방문자가 직접 가입할 수 있는지 여부 |
| `grantforge.security.registration-tenant` | `default` | 직접 가입한 계정이 속할 테넌트 |

## 비밀번호와 잠금

| 설정 항목 | 기본값 | 설명 |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | 최소 길이, 8 이상 |
| `grantforge.security.password.max-length` | `128` | 최대 길이, 1024 이하 |
| `grantforge.security.password.required-character-classes` | `1` | 섞어야 하는 문자 종류의 수(소문자, 대문자, 숫자, 기타), 1–4 |
| `grantforge.security.password.history-size` | `0` | 새 비밀번호는 최근 몇 개와도 같을 수 없음, 0–24 |
| `grantforge.security.password.max-age` | 만료 없음 | 비밀번호 유효 기간, 만료되면 로그인 시 반드시 변경해야 함 |
| `grantforge.security.lockout.max-attempts` | `5` | 연속으로 몇 번 실패하면 잠금 |
| `grantforge.security.lockout.duration` | `15m` | 잠금 시간 |

비밀번호에는 사용자 이름을 포함할 수 없습니다.

## 세션과 Cookie

| 설정 항목 | 기본값 | 설명 |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | 세션 유휴 타임아웃(`GRANTFORGE_SESSION_TIMEOUT`) |
| `grantforge.security.sessions.max-per-account` | `0` | 계정당 동시 온라인 세션 수 상한, 0이면 무제한 |
| `grantforge.security.sessions.activity-interval` | `1m` | 세션의 최근 활동을 기록하는 간격 |
| `grantforge.security.cookie-secure` | `false` | TLS를 프록시에서 종료할 때 `true`로 설정(`GRANTFORGE_COOKIE_SECURE`) |

## 2단계 인증

| 설정 항목 | 기본값 | 설명 |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | 한 번의 2단계 인증이 민감한 작업을 얼마 동안 커버하는지, 1분에서 12시간 |
| `grantforge.security.mfa.required-for-sensitive` | `false` | 민감한 작업에 계정이 2단계 인증을 켜야 하는지 여부 |

## 암호화와 권한 부여 서버

| 설정 항목 | 기본값 | 설명 |
| --- | --- | --- |
| `grantforge.security.encryption-key` | 자동 생성 | 저장된 비밀을 암호화하는 32바이트 Base64 키, 운영 환경에서는 반드시 설정 |
| `grantforge.oauth.issuer` | 요청 주소 | OIDC 발급자, 예: `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | 서명 키 자동 교체 주기, 0이면 끔 |
| `grantforge.oauth.signing-key-retention` | `2d` | 이전 키를 계속 공개하는 시간, 모든 토큰의 유효 기간보다 길어야 함 |

## 감사, 플러그인과 에이전트

| 설정 항목 | 기본값 | 설명 |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | 감사 로그 보관 기간 |
| `grantforge.audit.archive-directory` | 빈 값 | 만료된 감사 기록을 삭제하기 전에 아카이브할 디렉터리 |
| `grantforge.access-audit.retention` | `90d` | 에이전트가 보고한 접근 감사 보관 기간 |
| `grantforge.plugins.directory` | `plugins` | 플러그인 디렉터리 |
| `grantforge.plugins.call-timeout` | `10s` | 플러그인 호출(연결 테스트, 리소스 조회)의 타임아웃 |
| `grantforge.agents.refresh-interval` | `30s` | 에이전트가 정책을 가져오도록 권장하는 간격 |

## 관측 가능성

| 설정 항목 | 기본값 | 설명 |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | `/actuator/prometheus`를 로그인 없이 쓸 수 있는지 여부(`GRANTFORGE_PROMETHEUS_PUBLIC`) |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | `ecs` 또는 `logstash`로 설정하면 JSON 로그를 출력 |
