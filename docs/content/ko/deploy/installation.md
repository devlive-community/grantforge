---
title: 릴리스 패키지 설치
description: 물리 머신이나 가상 머신에 GrantForge 릴리스 패키지를 설치하고 시작, 중지 및 업그레이드합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 환경 요구 사항

| 항목 | 요구 사항 |
| --- | --- |
| Java | 17 이상(릴리스 패키지는 Java 17로 컴파일되며 21 권장) |
| 메모리 | 최소 1 GB, 프로덕션에서는 2 GB 이상 권장 |
| 데이터베이스 | 체험용으로는 내장 H2, 프로덕션에서는 PostgreSQL, MySQL, MariaDB, Oracle 또는 SQL Server를 사용하며 자세한 내용은 [데이터베이스](/ko/deploy/databases/)를 참고하세요 |
| 브라우저 | Chrome, Edge, Firefox, Safari의 최근 두 메이저 버전 |

## 디렉터리 구조

`grantforge-release.tar.gz`의 압축을 풀면 `grantforge/` 디렉터리가 만들어집니다.

| 디렉터리 | 내용 |
| --- | --- |
| `bin/` | `startup.sh`, `shutdown.sh`, `restart.sh`, `debug.sh`, `import-legacy.sh` |
| `configure/` | `application.properties`; 기본 설정은 여기서 덮어씁니다 |
| `lib/` | 서버와 의존 라이브러리의 jar |
| `drivers/` | 추가 JDBC 드라이버(MySQL용은 직접 넣어야 합니다) |
| `plugins/` | 서비스 타입 플러그인, [플러그인과 서비스 타입](/ko/develop/plugins/) 참고 |
| `agents/` | 대상 시스템에 배포하는 에이전트 jar, 예: [HDFS NameNode 에이전트](/ko/external/hdfs-agent/) |
| `data/` | 내장 H2 데이터베이스 파일(최초 시작 시 생성) |
| `logs/` | `grantforge.log`; `console.out`은 로깅 시스템이 시작되기 전의 출력을 기록합니다 |

## 시작과 중지

```bash
bin/startup.sh     # 백그라운드로 시작, 프로세스 ID를 pid 파일에 기록
bin/shutdown.sh    # pid 파일을 보고 정상 종료
bin/restart.sh     # 중지한 뒤 다시 시작
bin/debug.sh       # 포그라운드로 실행, 로그를 콘솔에도 함께 출력, Ctrl+C로 중지
```

스크립트는 어떤 디렉터리에서든 실행할 수 있습니다. 설치 디렉터리는 스크립트가 있는 디렉터리의 상위 디렉터리이며, 환경 변수 `GRANTFORGE_HOME`으로 직접 지정할 수도 있습니다.

## 데이터베이스 선택

기본적으로 GrantForge는 `data/grantforge` 아래의 H2 파일 데이터베이스를 사용하며, 이는 체험 용도로 적합합니다. 프로덕션 환경에서는 `configure/application.properties`나 환경 변수로 데이터베이스를 지정합니다.

```bash
export GRANTFORGE_DB_URL=jdbc:postgresql://db.example.com:5432/grantforge
export GRANTFORGE_DB_USER=grantforge
export GRANTFORGE_DB_PASSWORD=******
bin/startup.sh
```

최초 연결에서 GrantForge는 Liquibase로 전체 테이블을 자동 생성하고, 그 이후의 시작에서는 아직 실행되지 않은 마이그레이션을 적용합니다.

## 최초 초기화

최초 시작 시 로그에 1회용 초기화 토큰이 출력됩니다. 콘솔을 열어 토큰을 입력하고 첫 관리자를 만들면 되며, 과정은 [5분 만에 시작하기](/ko/start/quick-start/)를 참고하세요.

## 상태 확인과 모니터링

| 엔드포인트 | 용도 |
| --- | --- |
| `/actuator/health/liveness` | 라이브니스 프로브 |
| `/actuator/health/readiness` | 레디니스 프로브: 데이터베이스를 사용할 수 있고 마이그레이션이 완료되면 200을 반환 |
| `/actuator/prometheus` | Prometheus 메트릭; 기본적으로 로그인이 필요하며, `GRANTFORGE_PROMETHEUS_PUBLIC=true`로 신뢰할 수 있는 네트워크에 개방 |

구조화된 로그가 필요하면 `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`(또는 `logstash`)를 설정합니다. 모든 로그 줄에는 요청 ID가 포함되며, 이는 인터페이스 오류 응답의 `requestId`와 대응합니다.

## 클러스터 배포

여러 인스턴스가 하나의 데이터베이스를 공유하면서 동시에 요청을 처리할 수 있습니다. 세션이 데이터베이스에 저장되므로 어떤 인스턴스든 모든 요청을 처리할 수 있습니다. 각 인스턴스는 서로 다른 `GRANTFORGE_ID_NODE`(0–1023)가 필요하며, 이 값이 ID 생성에 사용되는 노드 번호를 결정합니다. 로드 밸런서에 세션 고정은 필요하지 않습니다.

## 업그레이드

서비스를 중지하고 이전 버전의 `lib/`를 새 버전의 것으로 교체한 다음(`configure/`, `data/`, `drivers/`, `plugins/`는 그대로 유지) 다시 시작하면 데이터베이스 마이그레이션이 자동으로 실행됩니다. 업그레이드 전에 데이터베이스를 백업해야 합니다. 1.x에서 업그레이드하려면 [업그레이드와 이전 버전 마이그레이션](/ko/deploy/upgrade/)을 참고하세요.
