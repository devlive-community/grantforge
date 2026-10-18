---
title: Apache HBase
description: HBase 서비스 플러그인을 설치하고 구성해 ZooKeeper나 부트스트랩 서버로 클러스터를 찾고, simple 또는 Kerberos로 로그인하며, 네임스페이스·테이블·컬럼 패밀리를 조회하고, HBase 자체 범위로 접근 정책을 관리합니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

HBase 플러그인은 네임스페이스, 테이블, 컬럼 패밀리를 조회하고 HBase 자체 권한의 범위로 접근 정책을 관리합니다. 범위는 전역, 네임스페이스, 테이블, 컬럼 패밀리, 컬럼입니다. 플러그인 ID와 서비스 타입은 모두 `hbase`입니다. 서비스 플러그인은 GrantForge 서버에서 실행되며, master와 region server에 설치된 [HBase 에이전트](/ko/external/hbase-agent/)가 정책을 적용합니다.

저장소의 Testcontainers 통합 테스트는 SHA-512로 검증한 Apache 릴리스로 빌드한 HBase `2.5.16`, `2.6.7`, `3.0.0`과 `2.6.7` Kerberos 클러스터를 다룹니다. 이것이 다른 모든 버전을 인증하는 것은 아닙니다.

## 설치

배포판의 `plugins/hbase`에 플러그인이 포함되어 있습니다. **플랫폼 관리 → 플러그인**에서 `hbase`가 활성화되어 있는지 확인하고, 플러그인 디렉터리를 갱신한 뒤에는 다시 스캔하세요. 플러그인은 자체 클래스 로더에서 Hadoop shaded 클라이언트 3.5.0 위의 HBase 클라이언트(`hbase-shaded-client-byo-hadoop` 2.6.7)로 클러스터와 통신하므로 HBase 2.x와 3.x 모두에서 동작합니다.

## 데이터 서비스 추가

1. **데이터 권한 → 데이터 서비스**를 열고 서비스를 추가한 뒤 Apache HBase(`hbase`)를 선택합니다.
2. ZooKeeper quorum 또는 HBase 2.5 이상 클러스터의 부트스트랩 서버를 입력합니다.
3. 조회 사용자의 로그인 방식을 고르고 사용자를 입력한 뒤 연결을 테스트하고 서비스를 저장합니다. 테스트는 로그인해 클러스터를 설명합니다. 클러스터 ID, 릴리스, 활성 region server 수입니다.

설정은 HBase 클라이언트 자체 이름을 사용하므로 `hbase-site.xml`에서 그대로 복사할 수 있습니다. 조회 사용자는 GrantForge가 리소스를 찾는 데만 쓰이며, 데이터를 읽고 쓰는 클라이언트와는 별개입니다. 연결 테스트가 성공해도 요청이 이미 정책을 따르는 것은 아닙니다.

| 설정 | 용도 |
| --- | --- |
| `hbase.zookeeper.quorum` | ZooKeeper 호스트, `호스트` 또는 `호스트:포트`를 쉼표로 구분, 예: `zk1,zk2,zk3:2181`. 부트스트랩 서버가 없으면 필수 |
| `hbase.zookeeper.property.clientPort` | 포트를 지정하지 않은 quorum 호스트의 포트, 기본값 `2181` |
| `zookeeper.znode.parent` | ZooKeeper에서 클러스터의 루트, 기본값 `/hbase` |
| `hbase.client.bootstrap.servers` | ZooKeeper 대신, HBase 2.5부터: master 또는 region server, `호스트:포트`를 쉼표로 구분 |
| `hbase.security.authentication` | `simple`(기본값): 사용자 이름, `kerberos`: keytab 또는 비밀번호를 쓰는 주체 |
| `username` | 필수 조회 사용자. Kerberos에서는 전체 주체, 예: `grantforge@EXAMPLE.COM` |
| `password` | Kerberos: keytab이 없을 때 주체의 비밀번호. 암호화되어 저장됩니다 |
| `keytab` | Kerberos: **GrantForge 서버**에서 읽을 수 있는 주체 keytab 경로 |
| `kerberos.kdc` | Kerberos: 주체 영역의 KDC, `호스트[:포트]`를 쉼표로 구분. 비우면 서버의 `krb5.conf`를 사용합니다 |
| `hbase.master.kerberos.principal` | Kerberos: master 주체, 기본값 `hbase/_HOST@`와 조회 주체의 영역 |
| `hbase.regionserver.kerberos.principal` | Kerberos: region server 주체, 기본값은 master와 같음 |
| `hbase.rpc.protection` | `authentication`(기본값), `integrity`, `privacy`. 클러스터와 같아야 합니다 |
| `hbase.rpc.timeout` | RPC 시간 제한, 기본값 `30000`, 범위 `1..600000` |

서비스는 설정에 적힌 그대로입니다. GrantForge 서버의 `hbase-site.xml`은 읽지 않습니다. simple 인증에서는 Kerberos 설정을 거부하며, Kerberos에는 비밀번호나 keytab이 필요합니다. 조회는 HBase 기본값처럼 몇 분을 기다리지 않고 몇 번 시도한 뒤 포기합니다.

### Kerberos

`kerberos`를 사용하면 GrantForge는 Hadoop 자체 로그인으로 keytab이나 비밀번호를 써서 주체를 로그인시키고, 로그인을 유지하며 keytab으로 갱신합니다. GrantForge 서버에는 다른 Kerberos 설정이 필요 없습니다. `kerberos.kdc`에 KDC를 입력한 영역은 HDFS·Hive 플러그인과 같은 방식으로 GrantForge 서버 전체에 선언되므로, 여러 서비스가 서로 다른 영역을 나란히 사용할 수 있습니다.

클러스터는 예상과 다르게 로그인하는 클라이언트의 연결을 이유를 밝히지 않고 닫습니다. 플러그인은 이를 로그인 거부로 보고하고, 클러스터와 일치해야 하는 `hbase.security.authentication`과 `hbase.rpc.protection`을 안내합니다.

### 조회 사용자에게 보이는 것

[HBase 에이전트](/ko/external/hbase-agent/)가 정책을 적용하면 목록에서 조회 사용자가 사용할 수 없는 네임스페이스와 테이블이 빠집니다. 네임스페이스 `*`에 `read`를 허용하는 정책을 주거나, 클러스터의 `hbase.superuser`에 넣으세요.

## 리소스와 접근 유형

| 레벨 | 일치 방식 | 접근 유형 |
| --- | --- | --- |
| `global` | 유일한 값 `*` | `admin`: 밸런서, 할당량, 복제처럼 클러스터 전체에 대한 작업 |
| `namespace` | 와일드카드, 대소문자 구분 | `read`, `write`, `create`, `admin` |
| `table` | 와일드카드, 대소문자 구분. 네임스페이스 아래에 있으며 네임스페이스 없이 이름 지정 | `read`, `write`, `create`, `admin` |
| `column_family` | 와일드카드, 대소문자 구분. 테이블 아래 | `read`, `write` |
| `column` | 와일드카드, 대소문자 구분. 컬럼 패밀리 아래. UTF-8 텍스트로 이름 지정 | `read`, `write` |

접근 유형은 HBase의 `read`, `write`, `create`, `admin`입니다. 코프로세서 엔드포인트용 HBase `exec`는 제공하지 않습니다. 에이전트가 모든 HBase 릴리스에서 엔드포인트 호출을 검사할 수 없기 때문입니다.

HBase와 마찬가지로 권한은 범위 안의 모든 것을 포함합니다. 네임스페이스 `sales`의 `read`는 그 안의 모든 테이블을 읽고, 컬럼 패밀리의 `write`는 모든 컬럼을 씁니다. 요청은 지정한 각 범위에서 결정되며, 어느 범위에서든 거부되면 요청이 거부됩니다. 따라서 정책으로 테이블을 허용하면서 그중 한 컬럼을 거부할 수 있습니다. 전역 범위는 클러스터 자체이지 모든 테이블이 아니며, 모든 테이블은 네임스페이스 `*` 정책이 포함합니다.

정책 값에서 `*`는 임의 개수의 문자, `?`는 한 문자와 일치합니다. 정책 편집기는 네임스페이스, 정책이 지정한 네임스페이스의 테이블, 그 테이블의 컬럼 패밀리를 제안합니다. 컬럼은 데이터이므로 제안하지 않습니다.

## 정책 예시

네임스페이스 `sales`에 컬럼 패밀리 `d`(`name`과 `ssn` 포함)와 `meta`를 가진 테이블 `orders`가 있다고 합시다. 같은 데이터 서비스에 다음 활성 접근 정책을 만듭니다.

| 리소스 | 규칙 |
| --- | --- |
| `namespace=sales` | 사용자 `alice`에게 `read` 허용 |
| `namespace=sales`, `table=orders`, `column_family=d` | 사용자 `bob`에게 `read` 허용 |
| `namespace=sales`, `table=orders` | 그룹 `auditors`에게 `read` 허용 |
| `namespace=sales`, `table=orders`, `column_family=d`, `column=ssn` | 그룹 `auditors`에게 `read` 거부 |
| `namespace=analytics` | 사용자 `dave`에게 `create`와 `admin` 허용 |

에이전트가 새 스냅숏을 적용하면 `alice`는 `sales`의 모든 테이블을 읽습니다. `bob`은 `orders`를 읽을 때 `d`의 컬럼만 받고, 테이블 목록에서도 `orders`만 봅니다. `auditors`의 구성원은 `d:ssn`을 제외한 `orders` 전체를 읽습니다. `dave`는 네임스페이스 `analytics`와 그 테이블을 만들고 관리하며, 다른 곳의 테이블은 거부됩니다.

## 정책 적용

서비스 플러그인은 정책을 관리하고 리소스를 조회합니다. 정책을 적용하려면 모든 master와 region server에 HBase 에이전트가 필요합니다. 배포 방법과 검사 내용은 [HBase 에이전트](/ko/external/hbase-agent/)를 참고하세요.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [HBase 에이전트](/ko/external/hbase-agent/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
