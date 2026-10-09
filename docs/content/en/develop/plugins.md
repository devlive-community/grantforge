---
title: Plugins and service types
description: "Extending GrantForge's policy management to external data systems with service type plugins: contracts, packaging, isolation, and distribution."
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

A service type plugin describes an external system: which resource levels it has, which access types it supports, whether masking and row filtering are possible, and which configuration a connection needs. Based on that description, GrantForge provides data services, a general policy editor, policy snapshots, and access auditing for such systems (see [Data services, policies, and agents](/en/external/data-services/)).

## Dependencies

A plugin depends only on `grantforge-plugin-api` (which itself depends only on the JDK and JSpecify), introduced with `provided` scope:

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## Implementing ServiceTypeProvider

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
        // 返回 request.resource() 层级下以 request.userInput() 开头的候选值，最多 request.limit() 个
        return List.of();
    }
}
```

The above is excerpted from the sample plugin `plugins/grantforge-plugin-example`; copy it directly as a template.

The definition is validated once at construction time and reports all problems at once: unknown or cyclic parents, duplicate names, references to undeclared access types or resources, and so on. Names must match `[a-z][a-z0-9_-]{0,63}`.

| Part | Description |
| --- | --- |
| Resources | Hierarchy, matching (exact, wildcard, path, regex), case sensitivity, whether required, exclusion and recursion support (path only), lookup support, and whether it can be a leaf |
| Access types | Name, display name, implied access types (for example `all` implies `select`), optionally restricted to resources |
| Masking, row filter | Which resources support them and which masking types exist; enforcement happens in the target system |
| Conditions | Conditions a policy can attach (for example an IP range), evaluated by the policy engine's condition SPI |
| Configuration fields | String, long text, integer, boolean, secret, enum; secret fields are stored encrypted and cannot have default values |

A provider must expose a no-argument constructor and be thread-safe. `validateConfig`, `testConnection`, and `lookup` all have default implementations; override them as needed.

### When a lookup fails

When `lookup` fails, throw a `LookupException` with a reason: the console shows the reason and lets the user retry, instead of showing no values. Keep the message to one line without secrets; the server also blanks out the service's secret settings in it.

- `NOT_FOUND`: the place to look in does not exist, such as the configured lookup directory
- `ACCESS_DENIED`: the target system refused the lookup user
- `UNREACHABLE`: the target system cannot be reached
- `AUTHENTICATION_FAILED`: signing in to the target system failed
- `LIMIT_EXCEEDED`: there are too many values; the lookup must be narrowed
- `INVALID_INPUT`: the input cannot be looked up, such as a path outside the allowed directory
- `FAILED`: any other failure

Any other exception a plugin throws is treated as `FAILED`, so plugins built against API 1.0 need no change. `LookupException` is available since API 1.1.0.

## Descriptor and packaging

Place `grantforge-plugin.yaml` at the plugin's root:

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.1"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

A plugin can be:

- a jar with the descriptor at the jar root;
- a directory or a zip: `grantforge-plugin.yaml`, `classes/`, and `lib/*.jar`.

Drop it into `grantforge.plugins.directory` (default `plugins`) and click Rescan on the console's "Plugins" page; no restart is needed.

When a plugin carries dependencies, package it like `plugins/grantforge-plugin-hdfs`: use assembly to build a zip with the `plugin` classifier (descriptor at the top level, plus `classes/` and `lib/`), and copy the runtime dependencies to `target/plugin-lib` in the `generate-resources` phase:

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

## When starting from source

When `org.devlive.grantforge.server.GrantForge` is started directly in an IDE, the server's classes come from each module's `target/classes`. In that case, if `grantforge.plugins.directory` is not configured and there is no `plugins` directory in the working directory, the repository's `plugins/` directory is used: plugin modules built there (a descriptor present in `target/classes` and a `target/plugin-lib` produced by the build) are loaded directly as plugins, with classes from `target/classes` and dependencies from `target/plugin-lib`; modules that do not produce `plugin-lib` (such as the sample plugin used for testing) are not loaded. After changing plugin code, let the IDE recompile and click Rescan on the console's "Plugins" page for the change to take effect. Before using a plugin module for the first time, build it once with Maven to copy its dependencies:

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## Compatibility

`apiVersion` declares the contract version a plugin needs. The host currently provides `1.1.0`; a plugin is loaded only when the major version matches and the provided version is not lower than the required one, otherwise it is marked "incompatible". Every change to the contract bumps the version; CI compares against the previous release with japicmp (`script/ci/check_plugin_api_compat.py`), and incompatible changes must bump the major version.

GrantForge 2026.1.0 provides plugin API 1.1.0. A plugin declaring `apiVersion: "1.1"` needs a 2026.1.0 or newer host; a plugin declaring `1.0` runs unchanged on the new host. The product version and the plugin API version are independent: the API version rises only when the contract changes.

## Isolation

- Each plugin gets its own class loader whose parent is the platform class loader; only `org.devlive.grantforge.plugin.api.` and `org.jspecify.annotations.` are delegated to the host. A plugin cannot see Spring or server classes and may bundle any versions of its dependencies.
- A read failure, an incompatible version, a duplicate, a construction error, or a timeout only marks that plugin as failed and records the reason; the server keeps running.
- Every call into a plugin has a timeout (`grantforge.plugins.call-timeout`, 10 seconds by default).

## Agents and snapshots

Service type plugins live in `plugins/` and are loaded by the GrantForge server; concrete agents live in `agents/` and are deployed into target systems after packaging, for example `agents/grantforge-agent-hdfs-*` deployed into the HDFS NameNode. The shared agent infrastructure lives in `core/grantforge-agent-core`.

Agents inside target systems use an agent token to access `/api/v1/agent/**`:

| Endpoint | Purpose |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | Reports the agent's status and current snapshot version |
| `GET /api/v1/agent/policies` | Downloads the policy snapshot; returns 304 when unchanged; the response carries an Ed25519 signature |
| `GET /api/v1/agent/signing-key` | The public key for verifying signatures |
| `POST /api/v1/agent/access-events` | Reports access events in batches, feeding the access audit |

Agents evaluate locally with `grantforge-policy-engine` (a Java 8 API that can be embedded in older systems) and do not need to call GrantForge on every access.

Agents do not have to implement these protocols themselves; `core/grantforge-agent-core` (Java 8) already wraps them:

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // 条件求值器，按名称

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- Sends heartbeats at the interval the server requires; downloads a snapshot when the policy version changes (304 if the ETag is unchanged), verifies it with the server's Ed25519 public key (pin the key in the settings, or fetch and keep it from the server on first contact), replaces the local copy only after verification passes, and saves it to the cache directory; when starting while the server is unreachable, it reuses the last snapshot.
- The snapshot expands roles and groups down to users, and `decide` adds the user's roles and groups from the snapshot to the request. When the server is disabled or there is no snapshot yet, the result is `NOT_DETERMINED`, and the agent decides whether to fall back to the system's own checks or to deny.
- Access events enter a bounded queue (dropped and counted when full, never blocking the system) and are sent in batches; when the server is unreachable they are written to `audit-spool/` under the cache directory and re-sent after recovery, with the oldest dropped beyond the cap.
- It depends on Jackson 2 and Bouncy Castle (Ed25519 does not exist before JDK 15); target systems ship other versions of these libraries, so the agent package must relocate them with shade.

## Example

`plugins/grantforge-plugin-example` is a complete plugin: type `example` (database → table → column, plus path), access types select, update, and all, column masking, table row filtering, an IP range condition, and configuration of url, timeout, and password (the connection test succeeds when the password is `example`); it can also look up the sample databases and tables. The full-stack end-to-end test uses it to walk the whole path "add a service → write a policy → issue a token → agent pulls the snapshot → access audit".
