---
title: "개요"
description: "GrantForge가 접근을 관리하는 모든 시스템에 대해, 에이전트가 정책을 적용하는 방식, 필터링과 마스킹 가능 여부, 통합 테스트가 검증한 버전을 정리합니다."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

GrantForge는 14개 시스템의 접근을 관리합니다. 시스템마다 GrantForge 서버의 서비스 플러그인이 시스템의 리소스와 접근 유형을 정의하고, 정책 편집기를 위해 리소스를 조회하며, 연결을 테스트합니다. 에이전트는 시스템에서 정책을 적용하고 모든 접근을 접근 감사에 보고합니다. 아래 플러그인은 메뉴와 같이 시스템 종류별로 정렬되어 있습니다.

## 에이전트의 적용 방식

에이전트는 다음 세 가지 방식 중 하나로 정책을 적용합니다.

### 시스템 내부

시스템이 요청마다 에이전트에 묻습니다. 에이전트는 시스템 자체의 프로세스에 로드되는 인가 기능, 코프로세서, 접근 제어 또는 플러그인입니다. 각 요청은 메모리의 스냅샷으로 즉시 판단되며 리소스 하위까지 적용됩니다. 에이전트는 시스템에 설치되고 재시작할 때 로드됩니다.

시스템: [Apache Hadoop HDFS](/ko/plugins/hdfs/), [Apache Hadoop YARN](/ko/plugins/yarn/), [Apache ZooKeeper](/ko/plugins/zookeeper/), [Apache Hive](/ko/plugins/hive/), [Apache HBase](/ko/plugins/hbase/), [Trino](/ko/plugins/trino/), [Apache Kafka](/ko/plugins/kafka/), [Apache Pulsar](/ko/plugins/pulsar/), [Apache Solr](/ko/plugins/solr/)

### 시스템 옆

시스템 옆에서 실행되는 서비스가 시스템 자체의 접근 제어(역할, 권한, 행 정책)를 정책과 일치시킵니다. 시스템에 아무것도 설치하지 않으며, 시스템이 모든 프로토콜에서 모든 요청을 직접 검사합니다. 변경은 다음 동기화 때 적용되며 기본값은 10초 후입니다. GrantForge 밖에서 부여한 권한은 그대로 남고, 정책이 거부하는 것은 에이전트가 경고합니다.

시스템: [ClickHouse](/ko/plugins/clickhouse/), [PostgreSQL](/ko/plugins/postgresql/), [Elasticsearch](/ko/plugins/elasticsearch/), [OpenSearch](/ko/plugins/opensearch/)

### 시스템 앞

클라이언트가 서버 대신 연결하는 네트워크 프록시입니다. 구문은 서버에 도달하기 전에 검사, 필터링, 마스킹됩니다. 클라이언트는 프록시를 통해 연결합니다.

시스템: [MySQL](/ko/plugins/mysql/)

## 시스템

| 종류 | 시스템 | 적용 방식 | 행 필터 | 마스킹 | 검증된 버전 |
| --- | --- | --- | --- | --- | --- |
| 관계형 데이터베이스 | [MySQL](/ko/plugins/mysql/) · [에이전트](/ko/external/mysql-agent/) | 시스템 앞 | 지원 | 지원 | 8.0, 8.4 |
| 관계형 데이터베이스 | [PostgreSQL](/ko/plugins/postgresql/) · [에이전트](/ko/external/postgresql-agent/) | 시스템 옆 | 지원 | 미지원 (postgreSQL에는 컬럼 마스킹이 없습니다) | 15, 16, 17, 18 |
| 분석형 데이터베이스(OLAP) | [ClickHouse](/ko/plugins/clickhouse/) · [에이전트](/ko/external/clickhouse-agent/) | 시스템 옆 | 지원 | 미지원 (clickHouse는 자체 클라우드에서만 컬럼을 마스킹합니다) | 24.8, 25.3, 25.8, 26.3, 26.8 |
| 와이드 컬럼 데이터베이스(NoSQL) | [Apache HBase](/ko/plugins/hbase/) · [에이전트](/ko/external/hbase-agent/) | 시스템 내부 | 미지원 | 미지원 | 2.5.16, 2.6.7, 3.0.0 |
| 데이터 웨어하우스와 쿼리 엔진 | [Apache Hive](/ko/plugins/hive/) · [에이전트](/ko/external/hive-agent/) | 시스템 내부 | 지원 | 지원 | 3.1.3, 4.0.1, 4.1.0 |
| 데이터 웨어하우스와 쿼리 엔진 | [Trino](/ko/plugins/trino/) · [에이전트](/ko/external/trino-agent/) | 시스템 내부 | 지원 | 지원 | 470, 476, 479, 484 |
| 메시징과 스트리밍 | [Apache Kafka](/ko/plugins/kafka/) · [에이전트](/ko/external/kafka-agent/) | 시스템 내부 | 미지원 | 미지원 | 3.7.2, 3.9.2, 4.1.2, 4.3.1 |
| 메시징과 스트리밍 | [Apache Pulsar](/ko/plugins/pulsar/) · [에이전트](/ko/external/pulsar-agent/) | 시스템 내부 | 미지원 | 미지원 | 3.0.17, 3.3.9, 4.0.14, 4.2.5 |
| 검색 엔진 | [Apache Solr](/ko/plugins/solr/) · [에이전트](/ko/external/solr-agent/) | 시스템 내부 | 미지원 | 미지원 | 9.8.1, 9.10.1, 9.11.0, 10.0.0 |
| 검색 엔진 | [Elasticsearch](/ko/plugins/elasticsearch/) · [에이전트](/ko/external/elasticsearch-agent/) | 시스템 옆 | 지원 | 미지원 (elasticsearch는 값을 마스킹하지 않습니다) | 8.19, 9.3, 9.4, 9.5 |
| 검색 엔진 | [OpenSearch](/ko/plugins/opensearch/) · [에이전트](/ko/external/opensearch-agent/) | 시스템 옆 | 지원 | 지원 | 2.19, 3.3, 3.6, 3.9 |
| 분산 스토리지 | [Apache Hadoop HDFS](/ko/plugins/hdfs/) · [에이전트](/ko/external/hdfs-agent/) | 시스템 내부 | 미지원 | 미지원 | 2.7.7, 2.10.2, 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| 리소스 관리 | [Apache Hadoop YARN](/ko/plugins/yarn/) · [에이전트](/ko/external/yarn-agent/) | 시스템 내부 | 미지원 | 미지원 | 3.2.4, 3.3.6, 3.4.3, 3.5.0 |
| 분산 코디네이션 | [Apache ZooKeeper](/ko/plugins/zookeeper/) · [에이전트](/ko/external/zookeeper-agent/) | 시스템 내부 | 미지원 | 미지원 | 3.9.5 (에이전트는 ZooKeeper 3.6.4로 빌드됩니다) |

버전은 저장소의 Testcontainers 통합 테스트가 실행되는 버전이며, 다른 버전은 인증되지 않습니다. 자세한 내용은 각 플러그인과 에이전트 페이지에 있습니다.

## 모든 에이전트가 하는 일

모든 에이전트는 서비스의 정책 스냅샷을 내려받아 사용하기 전에 Ed25519 서명을 검증하고, 마지막으로 유효한 스냅샷을 디스크에 보관합니다. 따라서 GrantForge 서버에 연결할 수 없는 동안에도 정책을 계속 적용합니다. 접근은 묶음으로 접근 감사에 전송되며, 동일한 허용 접근은 횟수와 함께 하나의 이벤트로 합쳐집니다. 서버에 연결할 수 없는 동안에는 디스크에 기록됩니다.

## 관련 가이드

- [데이터 서비스, 정책과 에이전트](/ko/external/data-services/)
- [플러그인과 서비스 타입](/ko/develop/plugins/)
