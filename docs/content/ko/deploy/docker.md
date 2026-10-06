---
title: Docker, Compose와 Helm
description: 컨테이너 이미지로 GrantForge를 실행하고, Compose로 여러 데이터베이스와 함께 시험하며, Helm으로 Kubernetes에 배포합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 이미지

모든 릴리스는 이미지 `ghcr.io/devlive-community/grantforge:<버전>`(linux/amd64와 linux/arm64)을 배포하며, 정식 버전에서는 `latest`도 함께 갱신합니다.

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

이미지는 릴리스 패키지로 빌드되고 `eclipse-temurin:21-jre`를 기반으로 하며, 비특권 사용자(UID 10001)로 실행되고, 로그는 콘솔로 출력되며 데이터베이스는 포함되지 않습니다. 소스에서 직접 빌드할 수도 있습니다.

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

이미지 컨벤션:

| 경로 / 변수 | 설명 |
| --- | --- |
| `/opt/grantforge/data` | 볼륨: 내장 H2의 데이터 파일 |
| `/opt/grantforge/plugins` | 볼륨: 서비스 타입 플러그인 |
| `/opt/grantforge/drivers` | 추가 JDBC 드라이버(MySQL Connector/J를 여기에 넣습니다) |
| `9999` | 서비스 포트 |
| `HEALTHCHECK` | `/actuator/health/readiness`를 호출합니다 |

## Compose 예제

`deploy/compose/`에는 데이터베이스별로 예제가 하나씩 준비되어 있습니다: `h2`, `postgres`, `mariadb`, `mysql`, `sqlserver`, `oracle`.

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

그 다음 http://127.0.0.1:9999/을 엽니다. 예제에서 사용하는 기본 데이터베이스 비밀번호는 체험 용도로만 적합하므로, 실제 사용 전에 `GRANTFORGE_DB_PASSWORD`로 변경하세요. MySQL 예제는 먼저 `mysql-connector-j-<버전>.jar`를 `deploy/compose/drivers/`에 넣어야 합니다.

## Helm

`deploy/helm/grantforge`는 하나의 StatefulSet과 외부 데이터베이스로 이루어진 Helm Chart입니다. 각 복제본은 Pod 순서에 따라 자신의 ID 노드 번호를 받습니다(Kubernetes 1.28 이상 필요).

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

주요 파라미터:

| 파라미터 | 기본값 | 설명 |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / Chart의 appVersion | 이미지 |
| `replicaCount` | `1` | 복제본 수, 1보다 크게 할 수 있습니다 |
| `database.url` / `username` / `password` | — | 데이터베이스 연결; 비밀번호는 `existingSecret`에 두는 것이 좋습니다 |
| `setupToken` | 비어 있음 | 초기화 토큰을 미리 지정하며, 비어 있으면 로그에 출력됩니다 |
| `encryptionKey` | 비어 있음 | 저장된 비밀(신원 소스 비밀번호, 인증자 시크릿, 서명 개인 키 등)을 암호화하는 32바이트 Base64 키; 비어 있으면 자동 생성되어 데이터베이스에 저장됩니다 |
| `cookieSecure` | `false` | TLS가 인그레스에서 종료될 때 `true`로 설정하며, 세션 쿠키는 항상 Secure가 붙습니다 |
| `ingress.*` | 비활성화 | 콘솔과 API를 노출합니다 |
| `plugins.persistence.enabled` | `false` | 플러그인 디렉터리에 퍼시스턴트 볼륨을 마운트합니다 |
| `podDisruptionBudget.enabled` | `false` | 복제본이 여러 개일 때 활성화를 권장합니다 |

> [!IMPORTANT]
> 프로덕션 환경에서는 반드시 `encryptionKey`를 설정하세요. 설정하지 않으면 키가 데이터베이스에 저장되므로, 데이터베이스 백업을 입수한 사람이라면 누구나 그 안에 암호화되어 보관된 비밀을 복호화할 수 있습니다.
