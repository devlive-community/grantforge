---
title: Apache Hadoop YARN
description: YARN 서비스 플러그인을 설치하고 설정하여 ResourceManager 또는 고가용성 쌍에 연결하고, simple 또는 Kerberos로 로그인하고, 큐와 애플리케이션 이름을 조회하며, 누가 어떤 애플리케이션을 어디에 제출할 수 있는지 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

YARN 플러그인은 큐와 그 안의 애플리케이션 이름을 조회하고, YARN이 결정하는 일, 즉 큐에 애플리케이션 제출, 큐 관리, 클러스터 관리에 대한 접근 정책을 관리합니다. 플러그인 ID와 서비스 타입은 모두 `yarn`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, ResourceManager에 설치한 [YARN 에이전트](/ko/external/yarn-agent/)가 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 SHA-512로 검증한 Apache 릴리스로 빌드한 Hadoop `3.2.4`와 `3.3.6`, `3.4.3`, `3.5.0`의 ResourceManager, 그리고 `3.4.3`의 Kerberos ResourceManager를 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/yarn`에 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `yarn`이 활성화되어 있는지 확인하고, 업데이트 후에는 플러그인 디렉터리를 다시 스캔합니다. 플러그인은 자체 클래스 로더 안에서 Hadoop의 shaded 클라이언트 3.5.0 위에 있는 Hadoop 자체의 YARN 클라이언트로 ResourceManager와 통신합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 Apache Hadoop YARN(`yarn`)을 선택합니다.
2. ResourceManager의 클라이언트 주소를 입력하거나, 고가용성 클러스터라면 각 ResourceManager의 주소를 입력합니다.
3. 조회 사용자의 로그인 방식을 선택하고 입력한 뒤 연결을 테스트하고 서비스를 저장합니다. 테스트는 로그인한 뒤 클러스터를 설명합니다. NodeManager가 몇 개이고 큐가 몇 개인지 보여 줍니다.

설정은 Hadoop 자체의 이름을 쓰므로 `yarn-site.xml`에서 그대로 옮길 수 있습니다. 조회 사용자는 GrantForge가 리소스를 찾는 데 쓰이며, 애플리케이션을 제출하는 사용자와는 별개입니다. 연결 테스트가 성공했다고 해서 제출이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `yarn.resourcemanager.address` | 필수: ResourceManager, `host` 또는 `host:port`, 기본 포트 `8032`. 고가용성 클러스터는 쉼표로 구분해 여러 개를 입력하며 클라이언트가 그 사이에서 장애 조치합니다 |
| `hadoop.security.authentication` | `simple`(기본값): 사용자 이름, `kerberos`: keytab 또는 비밀번호가 있는 주체 |
| `username` | 필수 조회 사용자. Kerberos에서는 전체 주체, 예: `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: keytab이 없을 때 주체의 비밀번호. 암호화되어 저장됩니다 |
| `keytab` | Kerberos: 주체의 keytab 경로. **GrantForge 서버**에서 읽을 수 있어야 합니다 |
| `kerberos.kdc` | Kerberos: 주체 realm의 KDC, `host[:port]`, 쉼표로 구분. 비우면 서버의 `krb5.conf`를 사용합니다 |
| `yarn.resourcemanager.principal` | Kerberos: ResourceManager의 주체. 기본값은 `yarn/_HOST@`와 조회 주체의 realm입니다 |
| `hadoop.rpc.protection` | `authentication`(기본값), `integrity` 또는 `privacy`. 클러스터와 동일하게 설정합니다 |
| `timeout` | 요청에 허용되는 시간. 기본값 `30000`, 범위 `1..600000` 밀리초. YARN 자체 클라이언트는 15분 동안 재시도합니다 |

서비스는 설정이 말하는 그대로입니다. GrantForge 서버의 `core-site.xml`이나 `yarn-site.xml`은 읽지 않습니다. simple 인증에서는 Kerberos 설정을 거부하며, Kerberos에는 비밀번호나 keytab이 필요합니다. 로그인을 거부하는 ResourceManager(예: Kerberos를 기대하는 경우)는 로그인 거부로 보고됩니다.

### Kerberos

`kerberos`를 사용하면 GrantForge는 Hadoop 자체 로그인으로 keytab 또는 비밀번호로 주체를 로그인시키고, 그 로그인을 유지하며 keytab으로 갱신합니다. GrantForge 서버의 다른 부분은 Kerberos 설정이 필요 없습니다. `kerberos.kdc`에 KDC를 적은 realm은 HDFS, Hive, Kafka, HBase, Trino 플러그인과 마찬가지로 GrantForge 서버 전체에 선언되므로, 여러 서비스가 서로 다른 realm을 나란히 사용할 수 있습니다.

## 리소스와 접근 유형

| 수준 | 매칭 | 접근 유형 |
| --- | --- | --- |
| `queue` | 와일드카드, 대소문자 구분. 루트부터의 큐 경로, 예: `root.sales.etl` | `submit_app`, `admin_queue` |
| `application` | 와일드카드, 대소문자 구분. 큐 아래. 애플리케이션 이름 | `submit_app` |
| `cluster` | 유일한 값 `*` | `admin`: YARN 관리, 예: 큐, 노드, 노드 레이블 새로 고침 |

큐의 관리자는 그 큐에 제출할 수 있으므로 `admin_queue`는 `submit_app`을 포함합니다.

YARN과 마찬가지로 큐에 대한 권한은 그 아래 큐에도 적용됩니다. `root.sales`의 `submit_app`으로 `root.sales.etl`에 제출할 수 있습니다. 검사는 루트부터 경로의 모든 큐에서 결정되며, 그중 하나라도 거부하면 거부됩니다. 따라서 정책 하나로 `root.sales`를 허용하면서 `root.sales.adhoc`를 거부할 수 있습니다. 큐 아래의 애플리케이션 수준은 권한이 어떤 애플리케이션에 적용되는지 지정합니다. 애플리케이션 `payroll-*`를 지정한 `root.hr`의 `submit_app`은 `root.hr`와 그 아래 큐에서 이름이 `payroll-`로 시작하는 애플리케이션만 받습니다.

정책 값에서 `*`는 임의 개수의 문자, `?`는 한 문자와 일치합니다. 정책 편집기는 클러스터가 어떤 스케줄러를 쓰든 경로로(큐 자체의 이름을 입력하면 이름으로) 큐를 제안하며, 정책이 지정한 큐에서 ResourceManager가 아직 기억하는 애플리케이션 이름도 제안합니다.

### 클라이언트 주소

정책 항목은 일부 클라이언트에만 적용될 수 있습니다. **클라이언트 주소**(`ip-range`) 조건은 Hadoop 자체의 호스트 목록처럼 주소, `10.1.0.0/16` 같은 범위, 호스트 이름을 받습니다. YARN 웹 프록시나 라우터 같은 프록시 뒤에서는 프록시가 전달한 첫 번째 주소가 클라이언트입니다. 호스트 이름은 클라이언트 이름 조회가 필요하므로 주소와 범위를 권장합니다. YARN은 관리 권한을 물을 때 클라이언트 주소를 전달하지 않으므로 `cluster`에서는 이 조건이 결코 성립하지 않습니다.

## 정책 예시

클러스터가 `root.sales.etl`, `root.sales.adhoc`, `root.hr` 큐를 운영한다고 가정합니다. 같은 데이터 서비스에서 다음과 같은 활성화된 접근 정책을 만듭니다.

| 리소스 | 규칙 |
| --- | --- |
| `queue=root.sales` | 그룹 `analysts`에 `submit_app` 허용 |
| `queue=root.sales.adhoc` | 사용자 `alice`에 `submit_app` 거부 |
| `queue=root.hr`, `application=payroll-*` | 사용자 `bob`에 `submit_app` 허용 |
| `queue=root.sales` | 사용자 `carol`에 `admin_queue` 허용 |
| `queue=root` | 그룹 `etl-robots`에 `10.0.0.0/8`에서의 `submit_app` 허용 |
| `cluster=*` | 그룹 `yarn-admins`에 `admin` 허용 |

에이전트가 새 스냅샷을 적용하면 `analysts` 구성원은 `root.sales.etl`과 `root.sales.adhoc`에 제출할 수 있지만 `alice`는 `root.sales.etl`에만 제출할 수 있습니다. `bob`은 급여 애플리케이션만 `root.hr`에 제출합니다. `carol`은 영업 큐에 제출하고 그 애플리케이션을 종료할 수 있습니다. `etl-robots` 구성원은 어느 큐에나 제출하지만 내부 네트워크에서만 가능합니다. `yarn-admins` 구성원은 큐를 새로 고칠 수 있습니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 모든 ResourceManager에 YARN 에이전트가 필요합니다. 배포와 검사는 [YARN 에이전트](/ko/external/yarn-agent/)에서 설명합니다.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [YARN 에이전트](/ko/external/yarn-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
