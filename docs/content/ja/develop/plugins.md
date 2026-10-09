---
title: プラグインとサービスタイプ
description: サービスタイププラグインで GrantForge のポリシー管理を外部データシステムに拡張します。コントラクト、パッケージ化、分離、配布について説明します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

サービスタイププラグインは 1 つの外部システムを記述します。どのようなリソース階層を持つか、どのアクセスタイプをサポートするか、マスクと行フィルタが可能か、接続にどの設定が必要かです。GrantForge はこの記述に基づき、この種のシステムにデータサービス、汎用のポリシーエディター、ポリシースナップショット、アクセス監査を提供します（[データサービスとポリシー](/ja/external/data-services/) を参照）。

## 依存

プラグインが依存するのは `grantforge-plugin-api`（それ自体も JDK と JSpecify にのみ依存）だけで、`provided` スコープで導入します。

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## ServiceTypeProvider の実装

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
        // request.resource() 階層の下で request.userInput() で始まる候補値を最大 request.limit() 件返します
        return List.of();
    }
}
```

上記はサンプルプラグイン `plugins/grantforge-plugin-example` から抜粋したもので、そのままコピーしてテンプレートとして使えます。

定義は生成時にまとめて検証し、問題をすべて報告します。親が不明または循環している場合、名前が重複している場合、宣言していないアクセスタイプやリソースを参照している場合などです。名前は `[a-z][a-z0-9_-]{0,63}` に一致する必要があります。

| 構成要素 | 説明 |
| --- | --- |
| リソース | 階層、マッチ方式（完全一致、ワイルドカード、パス、正規表現）、大文字小文字の区別、必須かどうか、除外と再帰のサポート（パスのみ）、検索のサポート、リーフになれるか |
| アクセスタイプ | 名前、表示名、内含する他のアクセスタイプ（例：`all` は `select` を内含）、リソースで制限可能 |
| マスク、行フィルタ | どのリソースがサポートするか、どのマスク方式があるかを宣言。実行は対象システムで行われます |
| 条件 | ポリシーに付加できる条件（例：IP 範囲）。ポリシーエンジンの条件 SPI が評価します |
| 設定フィールド | 文字列、長文テキスト、整数、真偽値、シークレット、列挙。シークレットフィールドは暗号化して保存し、デフォルト値を持てません |

プロバイダーは引数なしの public コンストラクターを公開し、スレッドセーフである必要があります。`validateConfig`、`testConnection`、`lookup` にはすべてデフォルト実装があるので、必要に応じてオーバーライドします。

### 検索が失敗したとき

`lookup` が失敗したときは理由を付けて `LookupException` をスローします。コンソールは値なしと表示する代わりに理由を表示し、再試行できるようにします。メッセージは 1 行にし、秘密情報を含めないでください。サーバーもサービスの秘密設定をメッセージから伏せます。

- `NOT_FOUND`：検索する場所（設定された検索ディレクトリなど）が存在しない
- `ACCESS_DENIED`：対象システムが検索ユーザーを拒否した
- `UNREACHABLE`：対象システムに接続できない
- `AUTHENTICATION_FAILED`：対象システムへのサインインに失敗した
- `LIMIT_EXCEEDED`：値が多すぎるため範囲を絞る必要がある
- `INVALID_INPUT`：入力を検索できない（許可されたディレクトリ外のパスなど）
- `FAILED`：その他の失敗

プラグインがスローするその他の例外はすべて `FAILED` として扱われるため、API 1.0 向けのプラグインは変更不要です。`LookupException` は API 1.1.0 から利用できます。

## ディスクリプターとパッケージ化

プラグインのルートに `grantforge-plugin.yaml` を置きます。

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.1"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

プラグインは次のいずれかの形式です。

- ディスクリプターが jar のルートにある 1 つの jar
- 1 つのディレクトリーまたは zip。`grantforge-plugin.yaml`、`classes/`、`lib/*.jar` で構成

これを `grantforge.plugins.directory`（デフォルトは `plugins`）に入れ、コンソールの **プラグイン** ページで再スキャンをクリックすればよく、再起動は不要です。

プラグインが依存関係を持つ場合は、`plugins/grantforge-plugin-hdfs` のように assembly で `plugin` 分類の zip を作ります（ディスクリプターは最上位に、`classes/` と `lib/` を含む）。`generate-resources` フェーズで実行時依存を `target/plugin-lib` にコピーします。

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

## ソースから起動するとき

IDE で `org.devlive.grantforge.server.GrantForge` を直接起動すると、サービスのクラスは各モジュールの `target/classes` から読み込まれます。このとき `grantforge.plugins.directory` が設定されておらず、作業ディレクトリーに `plugins` ディレクトリーもなければ、リポジトリの `plugins/` ディレクトリーを使います。その中でビルド済みのプラグインモジュール（`target/classes` にディスクリプターがあり、ビルドが `target/plugin-lib` を生成したもの）はそのままプラグインとしてロードされ、クラスは `target/classes` から、依存関係は `target/plugin-lib` から読み込まれます。`plugin-lib` を生成しないモジュール（テスト用のサンプルプラグインなど）はロードされません。プラグインのコードを変更したら IDE に再コンパイルさせ、コンソールの **プラグイン** ページで再スキャンすれば反映されます。プラグインモジュールを初めて使う前に、Maven で一度ビルドして依存関係をコピーしてください。

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## 互換性

`apiVersion` はプラグインが必要とするコントラクトバージョンを宣言します。ホストは現在 `1.1.0` を提供しており、メジャーバージョンが同じで必要なバージョン以上のプラグインだけがロードされ、それ以外は「非対応」とマークされます。コントラクトが変わるたびにバージョンを上げ、CI は japicmp で前回リリースと比較します（`script/ci/check_plugin_api_compat.py`）。互換性のない変更は必ずメジャーバージョンを上げる必要があります。

GrantForge 2026.1.0 はプラグイン API 1.1.0 を提供します。`apiVersion: "1.1"` を宣言するプラグインには 2026.1.0 以降のホストが必要で、`1.0` を宣言するプラグインは変更なしで新しいホスト上で動作します。製品バージョンとプラグイン API バージョンは独立しており、API バージョンはコントラクトが変わったときだけ上がります。

## 分離

- 各プラグインは独自のクラスローダーを持ち、親はプラットフォームクラスローダーです。委譲されるのは `org.devlive.grantforge.plugin.api.` と `org.jspecify.annotations.` だけで、プラグインは Spring やサービスのクラスを参照できず、任意のバージョンの依存関係を同梱できます。
- 読み込みの失敗、バージョンの非対応、重複、コンストラクトの例外、タイムアウトは、そのプラグインだけが失敗とマークされて理由が記録されるだけで、サービスは正常に動作し続けます。
- プラグインを呼び出すたびにタイムアウトが適用されます（`grantforge.plugins.call-timeout`、デフォルト 10 秒）。

## エージェントとスナップショット

サービスタイププラグインのソースは `plugins/` にあり、GrantForge サービスがロードします。個別のエージェントのソースは `agents/` にあり、パッケージ化して対象システムにデプロイします。例えば `agents/grantforge-agent-hdfs-*` は HDFS NameNode にデプロイします。共有のエージェント基盤は `core/grantforge-agent-core` にあります。

対象システム内のエージェントはエージェントトークンで `/api/v1/agent/**` にアクセスします。

| エンドポイント | 役割 |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | エージェントの状態と現在のスナップショットバージョンを報告します |
| `GET /api/v1/agent/policies` | ポリシースナップショットをダウンロードします。変わっていなければ 304 を返し、レスポンスヘッダーに Ed25519 署名が入ります |
| `GET /api/v1/agent/signing-key` | 署名の検証に使う公開鍵 |
| `POST /api/v1/agent/access-events` | アクセスイベントをバッチで報告し、アクセス監査に送ります |

エージェントは `grantforge-policy-engine`（Java 8 API。古いシステムに組み込めます）でローカルに評価するため、アクセスのたびに GrantForge を呼び出す必要はありません。

エージェントがこのプロトコルを自分で実装する必要はありません。`core/grantforge-agent-core`（Java 8）がすでに実装をラップしています。

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // 条件の評価器、名前で指定

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- サービスが要求する間隔でハートビートを送ります。ポリシーバージョンが変わったらスナップショットをダウンロードし（ETag が変わっていなければ 304）、サービスの Ed25519 公開鍵で署名を検証します（設定で公開鍵を固定できます。固定しない場合は初回にサービスから取得して保持します）。検証に通ったものだけに入れ替え、キャッシュディレクトリーに保存します。サービスに到達できない状態で起動した場合は最後のスナップショットを使い続けます。
- スナップショットはロールとグループをユーザーまで展開しておき、`decide` はリクエストにそのユーザーのスナップショット内のロールとグループを追加します。サービスが停止しているかスナップショットがまだない場合、結果は `NOT_DETERMINED` となり、システム自身のチェックにフォールバックするか拒否するかはエージェントが決めます。
- アクセスイベントは有限のキューに入ります（いっぱいになると捨てて件数だけを数え、システムを決してブロックしません）。バッチで送信し、サービスに到達できないときはキャッシュディレクトリーの `audit-spool/` に書き込み、復旧後に再送します。上限を超えると最も古いものから捨てます。
- Jackson 2 と Bouncy Castle に依存します（JDK 15 より前には Ed25519 がありません）。対象システムがこれらのライブラリの別バージョンを同梱している可能性があるため、エージェントをパッケージ化するときは shade でリロケーションする必要があります。

## 例

`plugins/grantforge-plugin-example` は完全なプラグインです。型 `example`（database → table → column と path）、アクセスタイプ select、update、all、列のマスク、テーブルの行フィルタ、IP 範囲の条件、設定 url、timeout、password（パスワードが `example` のとき接続テストが成功）を持ち、サンプルのデータベースとテーブルを検索できます。フルスタックの E2E テストはこのプラグインで **サービスの追加 → ポリシーの作成 → トークンの発行 → エージェントのスナップショット取得 → アクセス監査** の全過程を実行します。
