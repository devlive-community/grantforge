<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<div align="center">

<img src="docs/brand/grantforge-logo.png" width="128" height="128" alt="GrantForge 로고" />

# GrantForge

통합 권한 플랫폼 · 사용자, 역할, 메뉴, API, 데이터 행과 필드 · 외부 데이터 시스템

Language: [English](README.md) · [中文说明](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [Русский](README.ru.md) · [日本語](README.ja.md) · 한국어 · [Deutsch](README.de.md) · [프랑스어](README.fr.md) · [스페인어](README.es.md) · [포르투갈어](README.pt-BR.md) · [이탈리아어](README.it.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-2026.1.0-4F46E5)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00)
[![Docs](https://img.shields.io/badge/docs-grantforge.devlive.org-4F46E5)](https://grantforge.devlive.org)
[![Docker](https://img.shields.io/badge/ghcr.io-grantforge-2496ED)](https://ghcr.io/devlive-community/grantforge)

</div>

GrantForge(이전 이름 AuthX)는 오픈 소스(MIT) 통합 권한 플랫폼입니다. 두 가지 질문에 한곳에서 답합니다. **누가 무엇을 할 수 있는지**(기능 권한 부여)와 **누가 어떤 데이터를 볼 수 있는지**(데이터와 필드 권한 부여)입니다. 권한은 콘솔에서 정의·설명·감사되고, 애플리케이션은 표준 프로토콜로 연동하며, HDFS 같은 외부 데이터 시스템은 플러그인과 에이전트로 같은 정책 모델에 편입됩니다.

<p align="center">
  <img src="docs/public/screenshots/dashboard.png" width="760" alt="GrantForge 콘솔" />
</p>

## 기능

| 영역 | 내용 |
| --- | --- |
| 신원과 조직 | 멀티 테넌시, 부서 트리, 사용자 그룹과 직위, CSV 대량 가져오기·내보내기, LDAP / Active Directory 로그인과 동기화, OIDC 연합 로그인 |
| 계정 보안 | 세션 관리와 강제 로그아웃, 잠금이 있는 비밀번호 정책, 복구 코드가 있는 TOTP 2단계 인증, 민감한 작업의 재차 검증 |
| 기능 권한 부여 | 리소스 카탈로그(모듈, 메뉴, 페이지, 탭, 버튼, API), 역할 상속, 권한 부여 매트릭스, 부여 전 영향 분석 |
| 데이터 권한 | 행 단위 조건(본인, 소속 부서와 하위 부서, 지정 부서, 사용자 정의 조건)으로 읽기와 쓰기를 따로 제어 |
| 필드 권한 | 필드를 숨기거나 마스킹(이메일, 휴대폰 번호, 주민등록번호 등)하거나 읽기 전용으로 설정 |
| 설명 가능성과 감사 | 권한 설명(권한 부여의 출처), 권한 부여 시뮬레이션, 감사 로그 조회와 내보내기 |
| 거버넌스 | 직무 분리 제약, 승인이 있는 권한 요청, 정기 권한 검토 |
| 애플리케이션 연동 | OAuth 2.1 / OIDC 인가 서버, 권한 조회 오픈 API, Java(Spring Boot starter)와 JavaScript SDK |
| 외부 시스템 | 플러그인 서비스 타입과 정책 엔진: 데이터 서비스, 접근 정책, 에이전트와 접근 감사 |
| 제공 형태 | 실행 가능한 릴리스 하나, Docker 이미지, Compose 예제, Helm 차트, H2 / PostgreSQL / MySQL / MariaDB / Oracle / SQL Server |

외부 시스템 지원에는 플러그인 프레임워크, 정책 편집기, 서명된 배포, 접근 감사와 HDFS, ZooKeeper, MySQL, Hive, Kafka, HBase, Trino, YARN, Pulsar 서비스 플러그인이 포함됩니다. HDFS는 번호별 NameNode 에이전트, ZooKeeper는 서버 내 에이전트, MySQL은 독립 네트워크 프록시, Hive는 HiveServer2 내부의 권한 부여자, Kafka는 브로커와 컨트롤러 내부의 권한 부여자, HBase는 master와 region server 내부의 코프로세서, Trino는 coordinator 내부의 시스템 접근 제어, YARN은 ResourceManager 내부의 권한 부여 공급자, Pulsar는 브로커 내부의 권한 부여 공급자를 사용합니다. 연결 설정, 데이터베이스/테이블/열 조회와 정책 기능은 [MySQL 플러그인 가이드](https://grantforge.devlive.org/ko/plugins/mysql/), 배포와 적용 제한은 [MySQL 프록시 에이전트 가이드](https://grantforge.devlive.org/ko/external/mysql-agent/)를 참조하세요. Hive는 [Hive 플러그인 가이드](https://grantforge.devlive.org/ko/plugins/hive/)와 [Hive 에이전트 가이드](https://grantforge.devlive.org/ko/external/hive-agent/)를 참조하세요. Kafka는 [Kafka 플러그인 가이드](https://grantforge.devlive.org/ko/plugins/kafka/)와 [Kafka 에이전트 가이드](https://grantforge.devlive.org/ko/external/kafka-agent/)를 참조하세요. HBase는 [HBase 플러그인 가이드](https://grantforge.devlive.org/ko/plugins/hbase/)와 [HBase 에이전트 가이드](https://grantforge.devlive.org/ko/external/hbase-agent/)를 참조하세요. Trino는 [Trino 플러그인 가이드](https://grantforge.devlive.org/ko/plugins/trino/)와 [Trino 에이전트 가이드](https://grantforge.devlive.org/ko/external/trino-agent/)를 참조하세요. YARN은 [YARN 플러그인 가이드](https://grantforge.devlive.org/ko/plugins/yarn/)와 [YARN 에이전트 가이드](https://grantforge.devlive.org/ko/external/yarn-agent/)를 참조하세요. Pulsar는 [Pulsar 플러그인 가이드](https://grantforge.devlive.org/ko/plugins/pulsar/)와 [Pulsar 에이전트 가이드](https://grantforge.devlive.org/ko/external/pulsar-agent/)를 참조하세요.


## 지원 구성 요소와 버전

아래는 저장소의 Testcontainers 통합 테스트가 실행한 버전입니다. 그 밖의 버전은 인증되지 않았습니다.

| 구성 요소 | 서비스 플러그인 | 검증된 버전 | 적용 에이전트 | 에이전트 버전 | 에이전트 Java |
| --- | --- | --- | --- | --- | --- |
| Apache Hadoop HDFS | `plugins/grantforge-plugin-hdfs` | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 | NameNode 에이전트 | 2.7, 2.10, 3.2, 3.3, 3.4, 3.5 | 8 (3.5: 17) |
| Apache Hadoop YARN | `plugins/grantforge-plugin-yarn` | 3.2.4, 3.3.6, 3.4.3, 3.5.0 | ResourceManager 권한 제공자 | 테스트한 Hadoop 버전 라인과 동일 | 8 |
| Apache Hive | `plugins/grantforge-plugin-hive` | HiveServer2 3.1.3, 4.0.1, 4.1.0 | HiveServer2 인가자 | 테스트한 HiveServer2 버전 라인과 동일 | 8 |
| Apache Kafka | `plugins/grantforge-plugin-kafka` | 3.7.2, 3.9.2, 4.1.2, 4.3.1 | 브로커 및 컨트롤러 인가자 | 테스트한 Kafka 버전 라인과 동일 | 8 |
| Apache Pulsar | `plugins/grantforge-plugin-pulsar` | 3.0.17, 3.3.9, 4.0.14, 4.2.5 | 브로커 권한 제공자 | 테스트한 Pulsar 버전 라인과 동일 | 17 |
| Apache HBase | `plugins/grantforge-plugin-hbase` | 2.5.16, 2.6.7, 3.0.0 | 마스터 및 리전 서버 코프로세서 | 테스트한 HBase 버전 라인과 동일 | 8 |
| Trino | `plugins/grantforge-plugin-trino` | 470, 476, 479, 484 | 시스템 액세스 제어 | 470(470~478), 479(479 이후) | 23 / 25 |
| MySQL | `plugins/grantforge-plugin-mysql` | 8.0, 8.4 (MySQL 5.7 및 MariaDB 서버는 지원 안 함) | 독립 네트워크 프록시 | 단일 프록시 JAR | 17 |
| Apache ZooKeeper | `plugins/grantforge-plugin-zookeeper` | 저장소 테스트에 나열되지 않음 | 서버 내부 에이전트 | 3.6.4 (빌드 대상) | 8 |

**에이전트 Java** 열은 각 에이전트가 컴파일되는 바이트코드 대상입니다. HDFS 에이전트는 Hadoop 버전 라인으로 선택합니다(예: Hadoop 2.10.2는 `agents/hdfs/2.10/`). 자세한 내용은 [HDFS 에이전트 가이드](https://grantforge.devlive.org/ko/external/hdfs-agent/)를 참고하세요. Hadoop 2.7, 2.10, 3.2, 3.3은 슈퍼유저 인가 콜백을 제공하지 않으므로 해당 라인에서 슈퍼유저 접근은 여전히 Hadoop이 제어합니다.

## 동작 방식: 두 개의 평면

- **관리 평면**: GrantForge 서버(Spring Boot 4.1, Java 17 바이트코드)와 Vue 3 콘솔이 테넌트, 계정, 조직, 역할, 권한 부여, 감사, 데이터 서비스와 정책을 담당합니다.
- **데이터 평면**: 보호 대상 시스템 안에 포함되거나 독립 네트워크 프록시로 배포되는 에이전트. 토큰으로 Ed25519 서명된 정책 스냅샷을 받아 로컬에 캐시하고 리소스 접근을 인가하며 감사 이벤트를 전송합니다. 서버에 일시적으로 연결할 수 없어도 이미 적용한 스냅샷을 계속 사용하며, 구체적인 검사 범위는 각 에이전트 가이드에 설명되어 있습니다.

직접 만든 시스템이 HDFS 패턴을 따를 필요는 없습니다. 일반 애플리케이션은 오픈 API나 Spring Boot starter로 프로세스 안에서 권한을 평가하고, 데이터베이스·파일 시스템 같은 저장소 내부나 네트워크 접근 경로에서 요청을 가로막아야 하는 시스템은 `core/grantforge-agent-core`로 에이전트를 작성하면 됩니다.

## 애플리케이션 연동

- **OAuth 2.1 / OpenID Connect**: GrantForge가 인가 서버이므로 애플리케이션이 GrantForge로 사용자를 로그인시킵니다. 기존 신원 소스(LDAP / AD / OIDC)도 연동할 수 있습니다.
- **Java 애플리케이션**: `sdk/grantforge-spring-boot-starter`가 엔드포인트용 `@RequirePermission`, 데이터 엔터티용 `@GrantForgeEntity`, 플랫폼 데이터 권한을 JPA `Specification`으로 바꾸는 `GrantForgeDataScopes.scope(...)`를 제공합니다.
- **프론트엔드 애플리케이션**: `@grantforge/client`가 자신의 출처에서 OIDC + PKCE로 사용자를 로그인시키고 권한을 조회합니다.
- **오픈 API**: `/api/v1/open/me/authorization`, `/api/v1/open/me/data-access`, `/api/v1/open/catalog/data-entities`.
- **실행 가능한 예제**: `samples/shop`와 `samples/notes`가 제3자가 연동하는 방식을 그대로 보여줍니다.

## 빠른 시작

Java 17 이상이 필요합니다. 서비스는 `9999` 포트로 listen하고, 첫 시작 때 일회용 **설정 토큰**을 출력합니다. 브라우저에서 <http://127.0.0.1:9999/>를 열고 토큰을 입력한 뒤 첫 관리자를 만듭니다.

```bash
# 릴리스에서 (또는 ./mvnw clean package로 소스를 빌드하면 결과물은 dist/에 있습니다)
tar -xzf grantforge-release.tar.gz
cd grantforge
bin/startup.sh

# 또는 Docker로
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0

# 또는 데이터베이스와 함께 Compose로
docker compose -f deploy/compose/postgres.yml up -d

# 또는 Kubernetes에서
helm install grantforge deploy/helm/grantforge \
    --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
    --set database.username=grantforge \
    --set encryptionKey=$(openssl rand -base64 32)
```

기본값은 내장 H2 파일 데이터베이스이므로 설정 없이 바로 시작할 수 있습니다. MySQL 드라이버는 GPL 라이선스 때문에 릴리스에 포함되지 않으므로 `drivers/`에 넣어야 합니다. 설치, 첫 실행 설정, 첫 권한 부여는 [문서](https://grantforge.devlive.org)에 설명되어 있습니다.

## 데이터베이스

기본값은 내장 H2 파일 데이터베이스(`${GRANTFORGE_HOME}/data`)이므로 설정 없이 바로 시작할 수 있습니다. 운영 환경에서는 환경 변수로 전환하고, 스키마는 Liquibase가 관리합니다.

| 데이터베이스 | 버전(CI에서 검증) | `GRANTFORGE_DB_URL` 예제 |
| --- | --- | --- |
| PostgreSQL | 14, 17 | `jdbc:postgresql://host:5432/grantforge` |
| MySQL | 8.0, 8.4 | `jdbc:mysql://host:3306/grantforge` (`mysql-connector-j`를 `lib/`에 추가, GPL 라이선스라 릴리스에는 없음) |
| MariaDB | 10.11, 11.4 | `jdbc:mariadb://host:3306/grantforge` |
| Oracle | 23 | `jdbc:oracle:thin:@//host:1521/FREEPDB1` |
| SQL Server | 2022 | `jdbc:sqlserver://host:1433;databaseName=grantforge;encrypt=true` |

`GRANTFORGE_DB_USER`와 `GRANTFORGE_DB_PASSWORD`도 설정해야 하고, 클러스터의 모든 인스턴스는 각자 `GRANTFORGE_ID_NODE`(0-1023)를 설정해야 합니다.


## 운영과 관측 가능성

- 상태 검사: `/actuator/health/liveness`, `/actuator/health/readiness`(상태만 제공하고 상세 정보는 없음, readiness는 데이터베이스에 연결되고 마이그레이션이 끝나면 200을 반환).
- 메트릭: `/actuator/prometheus`(`application="grantforge"` 라벨, 기본은 로그인 필요, 신뢰할 수 있는 네트워크에만 `GRANTFORGE_PROMETHEUS_PUBLIC=true`로 개방).
- 로그: 기본은 요청 ID가 붙은 읽기 쉬운 텍스트, `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`(또는 `logstash`)로 JSON 로그 사용.
- 릴리스 스크립트: `bin/startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh`, `import-legacy.sh`.

## 개발과 검증

빌드에는 JDK 17 이상이 필요합니다. 서버와 Hadoop 3.5 어댑터는 Java 17, 정책 엔진과 에이전트 코어 및 Hadoop 2.7–3.4 어댑터는 Java 8을 대상으로 합니다. JDK 21 이상에서 Error Prone과 NullAway가 활성화됩니다. 프론트엔드는 Vue 3.5, Tailwind CSS 4, Node.js 22.12+, pnpm 8.10.2를 사용합니다.

```sh
# Java 빌드와 단위 테스트 (콘솔 빌드는 제외)
./mvnw verify
bash script/ci/java.sh test
bash script/ci/java.sh checkstyle

# 지정한 데이터베이스에서의 영속 계층 통합 테스트 (h2를 제외하면 Docker 필요)
bash script/ci/db_integration.sh postgres:17

# 릴리스 패키지 (콘솔 빌드 포함)를 dist/에 생성
./mvnw clean package

# 프론트엔드 개발과 검사
bash script/ci/web.sh install
cd core/grantforge-web && pnpm dev
bash script/ci/web.sh lint

# 예제 애플리케이션과 SDK
cd sdk/grantforge-js && pnpm install && pnpm build
./mvnw -f samples/pom.xml package -DskipTests

# API 계약: 서버를 바꾼 뒤 openapi.json과 프론트엔드 타입을 다시 생성 (CI가 둘 다 검사)
./mvnw -DskipFrontend -pl core/grantforge-server -am test -Dtest=OpenApiContractTest \
    -Dsurefire.failIfNoSpecifiedTests=false -Dgrantforge.openapi.update=true
cd core/grantforge-web && pnpm api:generate

# 문서 사이트 (docs/, Next.js + Tailwind CSS)
bash script/ci/docs.sh install
cd docs && pnpm dev                 # http://localhost:3100
bash script/ci/docs.sh check
bash script/docs/screenshots.sh     # 실제 서비스와 예제 데이터로 스크린샷 다시 생성

# 저장소 검사 (CI가 실행하는 것과 동일)
python3 script/ci/check_license_headers.py
bash script/ci/test_ci_scripts.sh
```

## 링크

- [저장소](https://github.com/devlive-community/grantforge)
- [문서](https://grantforge.devlive.org): 빠른 시작, 사용 가이드, 연동과 기술 참조, 원본은 [`docs/`](docs/)
- [기여 방법](CONTRIBUTING.md) · [행동 강령](CODE_OF_CONDUCT.md) · [변경 기록](CHANGELOG)
