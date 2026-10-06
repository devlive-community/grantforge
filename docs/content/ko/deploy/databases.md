---
title: 데이터베이스
description: 지원하는 데이터베이스와 버전, 연결 방식, 드라이버와 데이터베이스별 주의 사항.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 지원 범위

| 데이터베이스 | 검증한 버전 | 드라이버 |
| --- | --- | --- |
| H2 | 버전에 내장 | 내장, 체험 용도로만 권장 |
| PostgreSQL | 14, 17 | 내장 |
| MySQL | 8.0, 8.4 | `drivers/`에 직접 넣어야 합니다(Connector/J는 GPL 라이선스라 릴리스 패키지에 포함되지 않습니다) |
| MariaDB | 10.11, 11.4 | 내장 |
| Oracle | Free 23 | 내장 |
| SQL Server | 2022 | 내장 |

모든 버전은 CI에서 "빈 데이터베이스 초기화 + 전체 통합 테스트"를 실행합니다. 중국 국산 데이터베이스(다멍, 킹베이스ES, openGauss, OceanBase 등)는 지원 범위에 포함되지 않습니다.

## 연결 예제

```properties
# PostgreSQL
GRANTFORGE_DB_URL=jdbc:postgresql://db:5432/grantforge
# MySQL
GRANTFORGE_DB_URL=jdbc:mysql://db:3306/grantforge
# MariaDB
GRANTFORGE_DB_URL=jdbc:mariadb://db:3306/grantforge
# Oracle(서비스 이름)
GRANTFORGE_DB_URL=jdbc:oracle:thin:@//db:1521/FREEPDB1
# SQL Server
GRANTFORGE_DB_URL=jdbc:sqlserver://db:1433;databaseName=grantforge;encrypt=true;trustServerCertificate=true
```

사용자 이름과 비밀번호는 각각 `GRANTFORGE_DB_USER`와 `GRANTFORGE_DB_PASSWORD`로 설정합니다. 데이터베이스는 미리 생성되어 있어야 하고, 계정에는 테이블을 생성할 권한이 필요합니다. 최초 시작 시 GrantForge는 Liquibase로 전체 테이블을 생성하며, 이후 버전 업그레이드에서도 Liquibase가 자동으로 마이그레이션합니다. Hibernate는 테이블 구조만 검증하고 절대 수정하지 않습니다.

PostgreSQL에서는 GrantForge가 `pg_trgm` 확장을 활성화하고 사용자의 로그인 이름, 표시 이름, 이메일에 트라이그램 인덱스를 만들어, 수백만 개 계정의 "포함" 검색을 수십 밀리초 수준으로 유지합니다. PostgreSQL 13부터는 신뢰할 수 있는 확장이라 데이터베이스 소유자만 활성화할 수 있습니다. 계정에 해당 권한이 없으면 서비스는 평소처럼 시작되고 검색은 전체 테이블 스캔으로 동작하며, 관리자가 `CREATE EXTENSION pg_trgm`을 실행하면 다음 시작 시 인덱스가 자동으로 보충 생성됩니다.

## 문자 집합

- **MySQL / MariaDB**: 데이터베이스를 생성할 때 `utf8mb4` 문자 집합을 사용해야 중국어와 이모티콘이 온전히 저장됩니다.
- **SQL Server, Oracle**: 중국어가 포함될 수 있는 텍스트 열은 `NVARCHAR`를 사용하고, 긴 텍스트는 SQL Server에서는 `NVARCHAR(MAX)`, Oracle에서는 `CLOB`를 사용하며, 이는 데이터베이스의 기본 문자 집합과 무관합니다.
- **Oracle**: 빈 문자열은 `NULL`로 취급됩니다. GrantForge는 도메인 계층에서 공백 값을 "입력하지 않음"으로 통일해 처리하므로 다른 데이터베이스와 동일하게 동작합니다.

## 백업과 복구

모든 업무 데이터는 데이터베이스에 있습니다(세션도 포함). 따라서 데이터베이스를 백업하는 것으로 충분하며, 플러그인을 사용한다면 `plugins/`도 함께 백업하세요. `grantforge.security.encryption-key`를 설정하지 않으면 암호화 키도 데이터베이스에 저장되므로 백업을 복원하면 복호화할 수 있습니다. 키를 설정한 경우에는 그 키도 함께 안전하게 보관해야 합니다.
