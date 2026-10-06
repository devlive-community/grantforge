---
title: 개발, 테스트와 CI
description: 로컬 빌드, 테스트, 코드 규칙과 CI 검사.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 개발 환경

- JDK 21(산출물은 Java 17 바이트코드이며, 정책 엔진은 Java 8)
- Node.js 22와 pnpm 8.10.2
- Docker(데이터베이스 통합 테스트, 성능 벤치마크, 이미지)
- Python 3.12(CI 스크립트)

## 자주 쓰는 명령

```bash
./mvnw verify                          # 전체 Java 모듈(콘솔 포함)을 빌드하고 테스트
./mvnw verify -DskipFrontend           # 콘솔 빌드 건너뛰기
bash script/ci/web.sh test             # 콘솔 단위 테스트
bash script/ci/web.sh e2e              # 콘솔 브라우저 테스트(모의 백엔드)
bash script/ci/e2e_fullstack.sh        # 패키징 후 실제 서비스를 시작하고 풀스택 테스트 실행
bash script/ci/db_integration.sh postgres:17   # 지정한 데이터베이스에서 통합 테스트 실행
bash script/ci/perf_benchmark.sh smoke # 소규모 성능 벤치마크
```

콘솔을 개발할 때는 `core/grantforge-web`에서 `pnpm dev`를 실행하세요. Vite가 `/api` 등의 요청을 `localhost:9999`의 서비스로 프록시합니다.

## IDE에서 시작

`org.devlive.grantforge.server.GrantForge`(모듈 `grantforge-server`)를 직접 실행하면 되며, 기본적으로 H2 데이터베이스를 사용합니다. 서비스는 저장소 `plugins/` 아래에서 빌드된 플러그인 모듈을 자동으로 로드합니다([플러그인과 서비스 타입](/ko/develop/plugins/) 참고). 플러그인 모듈을 처음 사용하기 전에 `./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests`를 한 번 실행해 의존성을 복사하세요.

## 코드 규칙

- 백엔드는 Error Prone과 NullAway(기본은 non-null이며, nullable인 곳은 JSpecify `@Nullable`)를 사용하고 Checkstyle, PMD, SpotBugs로 검사합니다. ArchUnit이 공통 규칙을 지킵니다(필드 주입 금지, 네이티브 SQL 금지, 엔터티가 API에 노출되지 않음, `Optional.get()` 금지 등).
- 프론트엔드는 TypeScript strict 모드와 ESLint 경고 0개를 지키며, 문구는 모두 i18n을 거칩니다. 키는 반드시 리터럴이어야 하며 중국어 키와 영어 키가 완전히 일치해야 합니다.
- 모든 소스 파일에는 MIT 라이선스 헤더가 있고, 모든 주 코드 클래스에는 대응하는 테스트 클래스가 필요합니다(예외는 `script/ci/test_mapping_exclusions.txt`에 등록).
- 커버리지 임계값은 모듈별로 설정합니다(`script/ci/coverage_thresholds.txt`).
- 데이터베이스 마이그레이션은 Liquibase YAML이며, 변경마다 파일을 하나씩 두고 추가만 할 수 있고 수정할 수는 없습니다. 타입은 `${text}` 같은 데이터베이스 공통 속성을 사용합니다.
- 커밋 메시지는 Conventional Commits를 따르며, 제목은 72자를 넘기지 않습니다.

## CI

| 작업 | 내용 |
| --- | --- |
| Repository hygiene | 라이선스 헤더, 금지한 경로, 테스트 매핑, i18n, 권한 매니페스트, 파일 형식, 스크립트와 워크플로 검사 |
| Commit messages | 커밋 메시지 형식 |
| CI script unit tests | CI 스크립트 자체의 테스트 |
| Java 17 / 21 / 25 / latest | 전체 Java 모듈의 빌드와 테스트, 바이트코드 버전 검사 |
| Java static analysis | 커버리지, Checkstyle, SpotBugs, PMD |
| Frontend | API 타입과 계약의 일치, 타입 검사와 빌드, ESLint, 단위 테스트, 브라우저 테스트 |
| JavaScript SDK | 타입 검사, 빌드, ESLint, 단위 테스트 |
| Database | H2, PostgreSQL 14/17, MySQL 8.0/8.4, MariaDB 10.11/11.4, Oracle 23, SQL Server 2022에서의 마이그레이션과 통합 테스트 |
| Plugin API compatibility | 이전 릴리스와 플러그인 계약 비교 |
| Full-stack acceptance | PostgreSQL에서 패키징하고 서비스를 시작해 풀스택 브라우저 테스트 실행 |
| Docs | 문서 사이트의 검사, 테스트, 빌드 |

성능 벤치마크는 매일 밤 추가로 실행되며, 보안 워크플로는 의존성과 시크릿을 스캔합니다.

## 릴리스

버전 번호는 `년.마이너.패치` 형태(예: `2026.0.0`)이며, 릴리스 후보에는 `-rc.N`을 붙입니다. 모든 pom, npm 패키지, Helm Chart의 appVersion, 콘솔 사이드바, README의 버전은 반드시 일치해야 하며, CI가 `check_versions.py`로 검사합니다.

`dev` 브랜치에서 한 줄의 명령으로 릴리스합니다:

```bash
bash script/release/tag.sh 2026.1.0 --dry-run          # 검사와 릴리스 노트 미리 보기만 하고 수정하지는 않습니다
bash script/release/tag.sh 2026.1.0 --next 2026.2.0    # 버전을 설정하고 v2026.1.0 태그를 달아 푸시한 뒤 dev를 다음 버전으로 전환합니다
```

스크립트는 작업 트리가 깨끗하고, 로컬 브랜치가 원격보다 뒤처지지 않으며, 태그가 존재하지 않을 것을 요구합니다. 확인 후 `chore(release): prepare <버전>` 커밋을 만들고 주석이 달린 태그를 생성해 푸시합니다. 태그가 `release.yml`을 트리거합니다:

- `script/ci/release.sh`가 배포 패키지와 릴리스 의존성만 담은 CycloneDX SBOM, `SHA256SUMS`를 빌드합니다.
- 멀티 아키텍처 이미지를 `ghcr.io/devlive-community/grantforge`에 푸시합니다.
- Maven 아티팩트(소스와 Javadoc 포함)를 GitHub Packages에 배포합니다. 저장소에 `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`(Central Portal 토큰), `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`가 설정되어 있으면 서명한 뒤 Maven Central에 배포합니다.
- GitHub Release를 생성하며, 본문은 이전 릴리스(`v*` 또는 `1.0.6` 같은 숫자 태그) 이후의 모든 커밋을 새 기능, 문제 수정, 성능 등으로 그룹화해 커밋 링크와 함께 담습니다.

릴리스 후보는 프리릴리스로 표시되며 이미지의 `latest` 태그는 갱신하지 않습니다. 로컬에서 `central` 프로파일을 켜도 기본적으로는 배포하지 않으며(`central.skip=true`), 릴리스 워크플로만 `-Dcentral.skip=false`를 명시적으로 전달합니다.

## 권한 매니페스트

콘솔의 페이지, 버튼과 이들이 필요로 하는 API는 `core/grantforge-web/src/permissions/`에 선언합니다. `check_permission_manifest.py`는 선언한 API가 모두 존재하는지, 권한이 필요한 인터페이스가 모두 어떤 버튼이나 페이지에 의해 커버되는지 확인합니다(직접 호출하는 예외는 `script/ci/permission_direct_apis.txt`에 등록).

## 문서

이 사이트는 `docs/`에 있으며 Next.js 정적 내보내기와 Tailwind CSS를 사용합니다:

```bash
cd docs
pnpm install
pnpm dev        # http://localhost:3100
pnpm check      # 페이지, 링크, 이미지 검사
pnpm build      # docs/out으로 출력
```

페이지는 `docs/content/` 아래의 Markdown이며, 내비게이션은 `docs/lib/navigation.ts`에 정의합니다. API 레퍼런스와 오류 코드는 빌드 시 계약과 소스 코드에서 생성합니다. 스크린샷은 `script/docs/screenshots.sh`가 실제 서비스를 시작하고 예제 데이터를 넣은 뒤 Playwright로 만듭니다.
