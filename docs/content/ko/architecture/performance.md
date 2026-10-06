---
title: 성능과 벤치마크
description: 백만 계정 규모의 성능 목표, 벤치마크의 데이터와 방법, 로컬에서 실행하는 방법.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge의 성능 목표는 대규모 조직을 대상으로 합니다. **계정 100만 개, 역할 1만 개, 리소스 10만 개**. 시스템 벤치마크는 매일 밤 PostgreSQL 17에서 실행되며, 어떤 지표라도 상한을 넘으면 빌드가 실패합니다.

## 목표

| 지표 | 의미 | 상한 |
| --- | --- | --- |
| `authz.snapshot.hit` | 이미 캐시된 권한 스냅샷(인터페이스를 호출할 때마다 사용) | p99 1 ms |
| `authz.snapshot.build` | 한 계정의 콘솔 스냅샷을 처음 계산 | p95 50 ms |
| `authz.snapshot.app` | 리소스 트리가 큰 애플리케이션에서 스냅샷을 계산 | p95 50 ms |
| `api.users.page` | 사용자 목록의 한 페이지(처음 500페이지 안) | p95 200 ms |
| `api.users.search` | 이름으로 사용자를 검색 | p95 200 ms |
| `api.groups.page` | 사용자 그룹 목록의 한 페이지 | p95 200 ms |
| `api.roles.list` | 역할 목록(페이징 없음) | p95 200 ms |
| `api.me.authorization` | 현재 계정의 권한 | p95 200 ms |
| `api.member.groups` | 페이지 권한이 하나뿐인 계정이 보호된 목록에 접근 | p95 200 ms |
| `jmh.derivation.*` | 큰 리소스 트리의 도출 준비, 권한 부여 20건 도출(JMH) | 평균 50 / 5 ms |

상한은 낮추는 것만 허용됩니다. 어느 하나라도 높이려면 기록으로 남은 결정이 필요합니다.

## 데이터

벤치마크는 실제 서비스를 시작하고 초기화를 마친 뒤, 애플리케이션 자신의 엔터티로 데이터를 기록합니다.

- 부서 100개, 계정 1000명마다 사용자 그룹 1개, 직위 100개;
- 모든 계정은 부서 1개와 사용자 그룹 1개에 속하고, 10개 계정마다 1개는 직위를, 20개 계정마다 1개는 직접 배정된 역할을 가짐;
- 역할 10개마다 1개는 앞쪽 100개 역할 중 하나를 상속함. 사용자 그룹마다 역할 3개, 부서마다 2개, 직위마다 1개가 배정되고, 각 역할은 콘솔 페이지 5개를 부여받음;
- 애플리케이션 1개. 여러 모듈 아래 페이지 100개, 페이지마다 동작 9개. 역할의 10분의 1이 그중 페이지와 동작을 20개씩 부여받음.

각 지표는 예열한 뒤에 호출 단위로 하나씩 측정합니다.

## 로컬에서 실행

```bash
bash script/ci/perf_benchmark.sh full            # 목표 규모, postgres:17(Docker 필요), 상한 검사
bash script/ci/perf_benchmark.sh smoke           # H2 위의 소규모, 벤치마크가 실행되는지만 확인
bash script/ci/perf_benchmark.sh full mysql:8.4  # 다른 데이터베이스
```

보고서는 `perf/target/perf-report.json`에 기록되고, 로그에도 표가 함께 출력됩니다. `PERF_OPTS`로 추가 파라미터를 넘길 수 있습니다.

| 파라미터 | 기본값 | 의미 |
| --- | --- | --- |
| `perf.database` | `postgres:17` | `h2` 또는 `<엔진>:<버전>` |
| `perf.users` / `perf.roles` / `perf.resources` | 1000000 / 10000 / 100000 | 데이터 규모 |
| `perf.samples` / `perf.warmup` | 1000 / 200 | 각 지표의 측정과 예열 횟수 |
| `perf.jmh` | `true` | JMH 벤치마크도 함께 실행 |
| `perf.enforce` | `true` | 상한을 넘으면 종료 코드 1로 종료 |

## 왜 빠른가

- 권한 스냅샷은 계정별로 캐시하고 카탈로그와 테넌트의 버전 번호에 따라 한 번에 무효화하므로, 캐시에 맞으면 메모리 읽기 한 번으로 끝납니다.
- 도출은 메모리에서 수행합니다. 리소스 트리, 상속 관계, 배정을 미리 읽어 압축된 구조로 만들어 두어 항목마다 조회하는 일을 피합니다.
- 목록 인터페이스는 모두 페이징하고, 정렬과 필터는 인덱스가 있는 열에만 적용합니다. 지연 로딩한 연관은 64개 묶음으로 읽어오고, 쓰기는 50건 묶음으로 커밋합니다(ID를 애플리케이션이 생성하므로 일괄 삽입이 가능합니다).
