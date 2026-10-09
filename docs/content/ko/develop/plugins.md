---
title: 플러그인과 서비스 타입
description: 서비스 타입 플러그인으로 GrantForge의 정책 관리를 외부 데이터 시스템으로 확장합니다. 계약, 패키징, 격리, 배포를 다룹니다.
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

서비스 타입 플러그인은 외부 시스템 하나를 설명합니다. 어떤 리소스 계층이 있는지, 어떤 접근 타입을 지원하는지, 마스킹과 행 필터가 가능한지, 연결에 어떤 설정이 필요한지입니다. GrantForge는 이 설명을 바탕으로 이런 시스템에 데이터 서비스, 범용 정책 편집기, 정책 스냅샷, 접근 감사를 제공합니다([데이터 서비스와 정책](/ko/external/data-services/) 참고).

## 의존성

플러그인은 `grantforge-plugin-api`(그 자체도 JDK와 JSpecify에만 의존)에만 의존하며, `provided` 스코프로 가져옵니다:

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## ServiceTypeProvider 구현

```java
public final class ExampleProvider implements ServiceTypeProvider
{
    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder("example").label("Example warehouse")
                .resources(ResourceDefinition.builder("database").label("Database").lookupSupported(true).validLeaf(true)
                                .excludesSupported(false).build(),
                        ResourceDefinition.builder("table").label("Table").parent("database").lookupSupported(true).validLeaf(true).build(),
                        ResourceDefinition.builder("column").label("Column").parent("table").accessTypes("select").build(),
                        ResourceDefinition.builder("path").label("Path").matcher(MatcherType.PATH).recursiveSupported(true).build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("all", "All", "select", "update"))
                .dataMask(new DataMaskDefinition(Set.of("column"), List.of(new MaskTypeDefinition("redact", "Redact", "redact({col})"))))
                .rowFilter(new RowFilterDefinition(Set.of("table")))
                .conditions(ConditionDefinition.of("ip-range", "Client addresses", "ip-range"))
                .configFields(ConfigField.builder("url").label("Address").type(ConfigFieldType.STRING).mandatory().pattern("example://.+").build(),
                        ConfigField.builder("timeout").label("Timeout (seconds)").type(ConfigFieldType.INTEGER).defaultValue("30").build(),
                        ConfigField.builder("password").label("Password").type(ConfigFieldType.SECRET).mandatory().build())
                .build();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        return "example".equals(config.get("password")) ? ConnectionResult.succeeded()
                : ConnectionResult.failed("the example warehouse refused the password");
    }

    @Override
    public List<String> lookup(LookupRequest request)
    {
        // request.resource() 계층 아래에서 request.userInput()로 시작하는 후보 값을 최대 request.limit()개 반환합니다
        return List.of();
    }
}
```

위 코드는 예제 플러그인 `plugins/grantforge-plugin-example`에서 발췌한 것으로, 그대로 복사해 템플릿으로 쓸 수 있습니다.

정의는 생성 시점에 한 번 검증하며 문제를 모두 보고합니다. 부모를 알 수 없거나 순환하는 경우, 이름이 중복된 경우, 선언하지 않은 접근 타입이나 리소스를 참조한 경우 등입니다. 이름은 `[a-z][a-z0-9_-]{0,63}` 패턴과 일치해야 합니다.

| 구성 요소 | 설명 |
| --- | --- |
| 리소스 | 계층, 매칭 방식(정확, 와일드카드, 경로, 정규식), 대소문자 구분 여부, 필수 여부, 제외와 재귀 지원 여부(경로만), 조회 지원 여부, 리프가 될 수 있는지 |
| 접근 타입 | 이름, 표시 이름, 내포하는 다른 접근 타입(예: `all`은 `select`를 내포), 리소스로 제한할 수 있음 |
| 마스킹, 행 필터 | 어떤 리소스가 지원하는지, 어떤 마스킹 방식이 있는지 선언. 적용은 대상 시스템에서 수행 |
| 조건 | 정책에 붙일 수 있는 조건(예: IP 범위). 정책 엔진의 조건 SPI가 평가 |
| 설정 필드 | 문자열, 긴 텍스트, 정수, 불리언, 시크릿, 열거. 시크릿 필드는 암호화해 저장하며 기본값을 가질 수 없음 |

제공자는 인자 없는 public 생성자를 노출해야 하고 스레드 안전해야 합니다. `validateConfig`, `testConnection`, `lookup`에는 기본 구현이 모두 있으므로 필요할 때만 오버라이드하세요.

### 조회가 실패할 때

`lookup`이 실패하면 이유와 함께 `LookupException`을 던지세요. 콘솔은 값이 없다고 표시하는 대신 이유를 보여 주고 다시 시도할 수 있게 합니다. 메시지는 한 줄로 쓰고 비밀 정보를 넣지 마세요. 서버도 메시지에서 서비스의 비밀 설정을 가립니다.

- `NOT_FOUND`: 조회할 위치(설정된 조회 디렉터리 등)가 없음
- `ACCESS_DENIED`: 대상 시스템이 조회 사용자를 거부함
- `UNREACHABLE`: 대상 시스템에 연결할 수 없음
- `AUTHENTICATION_FAILED`: 대상 시스템 로그인에 실패함
- `LIMIT_EXCEEDED`: 값이 너무 많아 범위를 좁혀야 함
- `INVALID_INPUT`: 입력을 조회할 수 없음(허용된 디렉터리 밖의 경로 등)
- `FAILED`: 그 밖의 실패

플러그인이 던지는 그 밖의 모든 예외는 `FAILED`로 처리되므로 API 1.0용 플러그인은 바꿀 필요가 없습니다. `LookupException`은 API 1.1.0부터 제공됩니다.

### 디렉터리 탐색

경로 레벨에서는 관리자가 디렉터리를 탐색해 값을 고르게 할 수 있습니다. 레벨에 `browseSupported(true)`를 선언하고(`PATH` 매처만 가능) `browse(BrowseRequest)`를 구현해 한 페이지의 `BrowsePage`를 반환하세요. 시작 디렉터리 `root`, 나열한 디렉터리, 항목, 다음 페이지 커서 `nextCursor`(마지막 페이지는 `null`)가 들어 있습니다. 각 `BrowseEntry`에는 이름, 정책에서 쓰는 값, 디렉터리 여부, 그리고 선택적으로 소유자, 그룹, 권한, 크기, 수정 시각이 있습니다.

커서는 플러그인이 정하며 서버는 그대로 돌려줍니다. 페이지 사이의 순서를 유지하고 항목을 빠뜨리거나 반복하지 마세요. 한 페이지는 최대 500개이며, 요청보다 큰 페이지는 서버가 플러그인 실패로 처리합니다. 대상 시스템이 페이지를 나눌 수 없고 디렉터리가 스캔 한도를 넘으면 일부만 반환하지 말고 `LIMIT_EXCEEDED`를 던지세요. 탐색 실패도 조회와 같이 `LookupException`을 사용합니다.

## 디스크립터와 패키징

플러그인 루트에 `grantforge-plugin.yaml`을 둡니다:

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.1"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

플러그인은 다음 형태일 수 있습니다:

- 디스크립터가 jar 루트에 있는 jar 하나
- 디렉터리 또는 zip 하나. `grantforge-plugin.yaml`, `classes/`, `lib/*.jar`로 구성

`grantforge.plugins.directory`(기본값 `plugins`)에 넣고 콘솔의 **플러그인** 페이지에서 다시 스캔을 클릭하면 되며, 재시작은 필요하지 않습니다.

플러그인이 의존성을 가질 때는 `plugins/grantforge-plugin-hdfs`처럼 assembly로 `plugin` 분류의 zip을 만듭니다(디스크립터는 최상위에, `classes/`와 `lib/` 포함). `generate-resources` 단계에서 런타임 의존성을 `target/plugin-lib`에 복사합니다:

```xml
<plugin>
  <artifactId>maven-dependency-plugin</artifactId>
  <executions>
    <execution>
      <id>plugin-lib</id>
      <phase>generate-resources</phase>
      <goals><goal>copy-dependencies</goal></goals>
      <configuration>
        <includeScope>runtime</includeScope>
        <outputDirectory>${project.build.directory}/plugin-lib</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

## 소스에서 시작할 때

IDE에서 `org.devlive.grantforge.server.GrantForge`를 직접 시작하면 서비스의 클래스는 각 모듈의 `target/classes`에서 옵니다. 이때 `grantforge.plugins.directory`가 설정되지 않았고 작업 디렉터리에 `plugins` 디렉터리도 없으면 저장소의 `plugins/` 디렉터리를 사용합니다. 그 안에서 빌드된 플러그인 모듈(`target/classes`에 디스크립터가 있고 빌드가 `target/plugin-lib`를 생성한 경우)은 플러그인으로 바로 로드되며, 클래스는 `target/classes`에서, 의존성은 `target/plugin-lib`에서 가져옵니다. `plugin-lib`를 생성하지 않는 모듈(테스트에 쓰는 예제 플러그인 등)은 로드되지 않습니다. 플러그인 코드를 수정한 뒤에는 IDE가 다시 컴파일하게 하고 콘솔의 **플러그인** 페이지에서 다시 스캔하면 적용됩니다. 플러그인 모듈을 처음 사용하기 전에 Maven으로 한 번 빌드해 의존성을 복사하세요:

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## 호환성

`apiVersion`은 플러그인이 필요로 하는 계약 버전을 선언합니다. 호스트는 현재 `1.1.0`을 제공하며, 주 버전이 같고 제공 버전이 필요한 버전보다 낮지 않은 플러그인만 로드되고, 그렇지 않으면 **호환되지 않음**으로 표시됩니다. 계약이 바뀔 때마다 버전을 올리며, CI는 japicmp로 이전 릴리스와 비교합니다(`script/ci/check_plugin_api_compat.py`). 호환되지 않는 변경은 반드시 주 버전을 올려야 합니다.

GrantForge 2026.1.0은 플러그인 API 1.1.0을 제공합니다. `apiVersion: "1.1"`을 선언한 플러그인은 2026.1.0 이상의 호스트가 필요하고, `1.0`을 선언한 플러그인은 바꾸지 않아도 새 호스트에서 동작합니다. 제품 버전과 플러그인 API 버전은 서로 독립적이며 API 버전은 계약이 바뀔 때만 올라갑니다.

## 격리

- 각 플러그인은 자신만의 클래스 로더를 가지며, 부모는 플랫폼 클래스 로더입니다. `org.devlive.grantforge.plugin.api.`와 `org.jspecify.annotations.`만 호스트에 위임하며, 플러그인은 Spring과 서비스의 클래스를 볼 수 없고 원하는 버전의 의존성을 내장할 수 있습니다.
- 읽기 실패, 버전 비호환, 중복, 생성 예외, 타임아웃은 해당 플러그인만 실패로 표시하고 이유를 기록할 뿐, 서비스는 정상적으로 계속 동작합니다.
- 플러그인을 호출할 때마다 타임아웃이 적용됩니다(`grantforge.plugins.call-timeout`, 기본 10초).

## 에이전트와 스냅샷

서비스 타입 플러그인의 소스는 `plugins/`에 있고 GrantForge 서비스가 로드합니다. 구체적인 에이전트의 소스는 `agents/`에 있으며, 패키징해 대상 시스템에 배포합니다. 예를 들어 `agents/grantforge-agent-hdfs-*`는 HDFS NameNode에 배포합니다. 공유 에이전트 인프라는 `core/grantforge-agent-core`에 있습니다.

대상 시스템 안의 에이전트는 에이전트 토큰으로 `/api/v1/agent/**`에 접근합니다:

| 인터페이스 | 역할 |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | 에이전트의 상태와 현재 스냅샷 버전을 보고합니다 |
| `GET /api/v1/agent/policies` | 정책 스냅샷을 다운로드합니다. 바뀌지 않았으면 304을 반환하고, 응답 헤더에 Ed25519 서명이 담겨 있습니다 |
| `GET /api/v1/agent/signing-key` | 서명 검증에 쓰는 공개 키 |
| `POST /api/v1/agent/access-events` | 접근 이벤트를 배치로 보고해 접근 감사에 넣습니다 |

에이전트는 `grantforge-policy-engine`(Java 8 API, 오래된 시스템에 내장할 수 있음)으로 로컬에서 평가하므로, 접근할 때마다 GrantForge를 호출할 필요가 없습니다.

에이전트가 이 프로토콜을 직접 구현할 필요는 없습니다. `core/grantforge-agent-core`(Java 8)가 이미 감싸 두었습니다:

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // 조건 평가기, 이름 기준

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- 서비스가 요구한 간격으로 하트비트를 보냅니다. 정책 버전이 바뀌면 스냅샷을 다운로드하고(ETag가 그대로면 304), 서비스의 Ed25519 공개 키로 서명을 검증합니다(설정에서 공개 키를 고정할 수 있고, 그렇지 않으면 처음에 서비스에서 받아 보관합니다). 검증을 통과해야 교체하며 캐시 디렉터리에 저장합니다. 서비스에 연결할 수 없는 상태에서 시작하면 마지막 스냅샷을 그대로 사용합니다.
- 스냅샷은 역할과 그룹을 사용자까지 펼쳐 두며, `decide`는 요청에 그 사용자의 스냅샷 안 역할과 그룹을 더합니다. 서비스가 중지되었거나 아직 스냅샷이 없으면 결과가 `NOT_DETERMINED`이며, 시스템 자체 검사로 돌아갈지 거부할지는 에이전트가 결정합니다.
- 접근 이벤트는 크기가 정해진 큐에 들어가며(가득 차면 버리고 개수만 세며, 시스템을 절대 막지 않습니다) 배치로 전송합니다. 서비스에 연결할 수 없을 때는 캐시 디렉터리 아래 `audit-spool/`에 쓰고, 복구 후 다시 보내며, 상한을 넘으면 가장 오래된 것부터 버립니다.
- Jackson 2와 Bouncy Castle에 의존합니다(JDK 15 이전에는 Ed25519가 없습니다). 대상 시스템이 이 라이브러리의 다른 버전을 이미 가지고 있을 수 있으므로, 에이전트를 패키징할 때 shade로 재배치해야 합니다.

## 예제

`plugins/grantforge-plugin-example`는 완전한 플러그인입니다. 타입 `example`(database → table → column과 path), 접근 타입 select, update, all, 열 마스킹, 테이블 행 필터, IP 범위 조건, 설정 url, timeout, password(비밀번호가 `example`이면 연결 테스트가 성공)를 갖추고 있고 예제 데이터베이스와 테이블을 조회할 수 있습니다. 풀스택 엔드투엔드 테스트는 이 플러그인으로 **서비스 추가 → 정책 작성 → 토큰 발급 → 에이전트 스냅샷 조회 → 접근 감사** 전 과정을 진행합니다.
