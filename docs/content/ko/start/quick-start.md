---
title: 5분 만에 시작하기
description: 릴리스 패키지나 Docker로 GrantForge를 실행하고, 초기화를 완료하고, 사용자를 생성한 뒤 첫 역할에 권한을 부여합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

이 문서에서는 기본 내장 H2 데이터베이스로 로컬에서 GrantForge를 실행합니다. 운영 환경에서는 [릴리스 패키지 설치](/ko/deploy/installation/)와 [데이터베이스](/ko/deploy/databases/)를 참고하세요.

## 1. 서비스 시작

Java 17 이상이 필요합니다. [GitHub Releases](https://github.com/devlive-community/grantforge/releases)에서 릴리스 패키지를 내려받거나, 소스 디렉터리에서 `./mvnw -DskipTests package`를 실행해 직접 빌드한 뒤(결과물은 `dist/grantforge-release.tar.gz`), 압축을 풀고 실행합니다.

```bash
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh
```

Docker로도 실행할 수 있습니다. 먼저 릴리스 패키지로 이미지를 빌드한 뒤, Compose 예제로 실행합니다.

```bash
docker build -f deploy/docker/Dockerfile -t grantforge dist
docker compose -f deploy/compose/h2.yml up -d
```

서비스는 기본으로 `9999` 포트를 수신합니다. 첫 실행 시 데이터베이스 테이블을 만들고, 로그에 일회용 **초기화 토큰**을 출력합니다.

```text
WARN  ... First-run setup is pending. Open the console and enter this setup token: 7rV2...
```

릴리스 패키지의 로그는 `logs/grantforge.log`에 기록되며, Docker에서는 `docker compose logs`로 확인합니다.

## 2. 초기화 완료

브라우저에서 http://127.0.0.1:9999/을 열면 콘솔이 자동으로 초기화 페이지로 이동합니다. 로그에 출력된 토큰, 조직 이름, 첫 관리자의 사용자 이름과 비밀번호(12자 이상)를 입력합니다.

![초기 설정과 로그인 페이지](/screenshots/login.png)

> [!TIP]
> 자동 설치할 때는 환경 변수 `GRANTFORGE_SETUP_TOKEN`으로 토큰을 미리 지정할 수 있습니다. [설정 참조](/ko/reference/configuration/)를 참고하세요.

초기화를 마치면 이 관리자는 **테넌트 관리자**와 **플랫폼 관리자** 시스템 역할을 모두 갖게 되어 콘솔의 모든 기능을 사용할 수 있습니다. 초기화 페이지는 그 뒤로 영구히 닫힙니다.

## 3. 사용자 생성

**접근 제어 → 사용자 관리**로 이동해 "사용자 생성"을 클릭하고, 사용자명, 초기 비밀번호, 주 부서를 입력합니다. 새 사용자는 첫 로그인 시 비밀번호를 변경해야 합니다.

## 4. 역할 생성과 권한 부여

1. **접근 제어 → 역할 관리**로 이동해 "새 역할"을 클릭하고, 예를 들어 "읽기 전용 감사관"처럼 이름을 지정합니다.
2. 역할 행에서 "권한 부여"를 클릭하고, 권한 부여 매트릭스에서 "감사 로그" 페이지를 선택합니다. 매트릭스가 해당 페이지에 필요한 API를 자동으로 포함합니다.
3. "배정"을 클릭해 방금 만든 사용자에게 역할을 배정합니다.

![역할 관리](/screenshots/roles.png)

## 5. 동작 확인

새 사용자로 로그인하면 왼쪽 메뉴에 "감사 로그"만 나타납니다. 관리자 계정으로 돌아가 사용자 행에서 "유효 권한 보기" 아이콘을 클릭하면, 권한마다 어디서 비롯했는지 확인할 수 있습니다.

## 다음 단계

- [핵심 개념](/ko/start/concepts/)을 알아보세요.
- [사용 가이드](/ko/guide/console/)를 보며 모든 메뉴를 익히세요.
- 애플리케이션을 [GrantForge에 연동](/ko/integration/overview/)하세요.
