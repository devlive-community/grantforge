---
title: 外掛程式與服務類型
description: 用服務類型外掛程式把 GrantForge 的策略管理擴展到外部資料系統：契約、打包、隔離與分發。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

服務類型外掛程式描述一種外部系統：它有哪些資源層級、哪些存取類型、能否脫敏和列過濾、連線需要哪些設定。GrantForge 據此為這類系統提供資料服務、通用的策略編輯器、策略快照與存取稽核（見 [資料服務與策略](/zh-tw/external/data-services/)）。

## 依賴

外掛程式僅依賴 `grantforge-plugin-api`（僅依賴 JDK 與 JSpecify），以 `provided` 範圍引入：

```xml
<dependency>
  <groupId>org.devlive.grantforge</groupId>
  <artifactId>grantforge-plugin-api</artifactId>
  <version>${grantforge.version}</version>
  <scope>provided</scope>
</dependency>
```

## 實作 ServiceTypeProvider

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
        // 傳回 request.resource() 層級下以 request.userInput() 開頭的候選值，最多 request.limit() 個
        return List.of();
    }
}
```

以上摘自範例外掛程式 `plugins/grantforge-plugin-example`，可以直接複製作為範本。

定義在建構時一次性驗證並回報全部問題：父層級未知或成環、重名、參照了未宣告的存取類型或資源等。名稱必須符合 `[a-z][a-z0-9_-]{0,63}`。

| 元件 | 說明 |
| --- | --- |
| 資源 | 層級、比對方式（精確、萬用字元、路徑、正規表示式）、是否區分大小寫、是否必填、是否支援排除與遞迴（僅路徑）、是否支援查詢、是否可作為葉節點 |
| 存取類型 | 名稱、顯示名稱、隱含的其他存取類型（如 `all` 隱含 `select`），可限定資源 |
| 脫敏、列過濾 | 宣告哪些資源支援、有哪些脫敏方式；執行在目標系統 |
| 條件 | 策略可附加的條件（如 IP 範圍），由策略引擎的條件 SPI 求值 |
| 設定欄位 | 字串、長文字、整數、布林、金鑰、列舉；金鑰欄位加密儲存，不能有預設值 |

提供者需要公開無參數建構子並且執行緒安全。`validateConfig`、`testConnection`、`lookup` 都有預設實作，依需求覆寫。

### 查詢失敗時

`lookup` 失敗時拋出 `LookupException` 並給出原因，主控台會顯示原因並允許使用者重試，而不是顯示成空結果。訊息只寫一行，不要包含密鑰；伺服器還會把服務的密鑰設定從訊息中抹去。

- `NOT_FOUND`：要查詢的位置不存在（例如設定的查詢目錄）
- `ACCESS_DENIED`：目標系統拒絕了查詢使用者
- `UNREACHABLE`：無法連線目標系統
- `AUTHENTICATION_FAILED`：登入目標系統失敗
- `LIMIT_EXCEEDED`：值太多，需要縮小範圍
- `INVALID_INPUT`：輸入無法查詢，例如超出允許的目錄
- `FAILED`：其他失敗

外掛程式拋出的其他任何例外都按 `FAILED` 處理，因此基於 API 1.0 的外掛程式無需修改。`LookupException` 自 API 1.1.0 起提供。

### 目錄瀏覽

路徑類資源可以讓管理員逐級瀏覽目錄來選擇值：在層級上宣告 `browseSupported(true)`（只允許 `PATH` 比對方式），並實作 `browse(BrowseRequest)`，傳回一頁 `BrowsePage`：起始目錄 `root`、目前目錄、項目，以及下一頁的游標 `nextCursor`（最後一頁為 `null`）。每個 `BrowseEntry` 包含名稱、策略中使用的值、是否為目錄，以及選用的擁有者、群組、權限、大小與修改時間。

游標由外掛程式自行定義，伺服器原樣傳回；各頁的順序必須保持一致，不得遺漏或重複。每頁最多 500 筆，傳回超過請求數量的項目會被伺服器視為外掛程式出錯。目標系統不能分頁且目錄超過掃描上限時，應拋出 `LIMIT_EXCEEDED`，而不是只傳回一部分。瀏覽失敗與查詢失敗一樣使用 `LookupException`。

## 描述元與打包

外掛程式根目錄放 `grantforge-plugin.yaml`：

```yaml
id: example
version: 1.0.0
name: Example warehouse
description: A sample service type that shows what a plugin can declare
apiVersion: "1.1"
providers:
  - org.devlive.grantforge.example.ExampleProvider
```

`version` 是外掛程式自己的版本，由作者決定；GrantForge 內建的外掛程式（如 HDFS 與範例外掛程式）隨產品一起發布，版本與產品版本相同，由建置自動填入。`apiVersion` 則是外掛程式需要的契約版本，見下文「相容性」。

外掛程式可以是：

- 一個 jar，描述元在 jar 根目錄；
- 一個目錄或 zip：`grantforge-plugin.yaml`、`classes/` 與 `lib/*.jar`。

把它放進 `grantforge.plugins.directory`（預設 `plugins`），在主控台的「外掛程式」頁點擊重新掃描即可，無需重啟。

外掛程式帶有依賴時，像 `plugins/grantforge-plugin-hdfs` 那樣用 assembly 打成 `plugin` 分類的 zip（描述元在頂層、`classes/`、`lib/`），並在 `generate-resources` 階段把執行期依賴複製到 `target/plugin-lib`：

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

## 從原始碼啟動時

在 IDE 裡直接啟動 `org.devlive.grantforge.server.GrantForge` 時，服務端的類別來自各模組的 `target/classes`，此時如果沒有設定 `grantforge.plugins.directory`、工作目錄下也沒有 `plugins` 目錄，就使用儲存庫的 `plugins/` 目錄：其中建置過的外掛程式模組（`target/classes` 裡有描述元，且建置產生了 `target/plugin-lib`）直接作為外掛程式載入，類別來自 `target/classes`，依賴來自 `target/plugin-lib`；不產生 `plugin-lib` 的模組（如測試用的範例外掛程式）不會載入。修改外掛程式程式碼後由 IDE 重新編譯，在主控台「外掛程式」頁重新掃描即可生效。外掛程式模組第一次使用前，用 Maven 建置一次以複製依賴：

```bash
./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests
```

## 相容性

`apiVersion` 宣告外掛程式需要的契約版本。宿主目前提供 `1.1.0`，主版本相同且不低於所需版本的外掛程式才會載入，否則標記為「不相容」。契約的每次變化都會提升版本，CI 用 japicmp 與上一個發行版比較（`script/ci/check_plugin_api_compat.py`），不相容的改動必須提升主版本。

GrantForge 2026.1.0 提供外掛程式 API 1.1.0。宣告 `apiVersion: "1.1"` 的外掛程式需要 2026.1.0 或更新的宿主；宣告 `1.0` 的外掛程式無需修改即可在新宿主上執行。產品版本與外掛程式 API 版本彼此獨立：只有契約變化時 API 版本才會提升。

## 隔離

- 每個外掛程式有自己的類別載入器，父載入器是平台類別載入器，僅有 `org.devlive.grantforge.plugin.api.` 與 `org.jspecify.annotations.` 委派給宿主；外掛程式看不到 Spring 和服務端的類別，可以自帶任意版本的依賴。
- 讀取失敗、版本不相容、重複、建構例外或逾時僅會讓這個外掛程式被標記為失敗並記錄原因，服務照常執行。
- 對外掛程式的每次呼叫都有逾時（`grantforge.plugins.call-timeout`，預設 10 秒）。

## 代理與快照

服務類型外掛程式的原始碼位於 `plugins/`，由 GrantForge 服務端載入；具體代理的原始碼位於 `agents/`，打包後部署到目標系統中，例如 `agents/grantforge-agent-hdfs-*` 部署到 HDFS NameNode。共享的代理基礎設施位於 `core/grantforge-agent-core`。

目標系統裡的代理用代理權杖存取 `/api/v1/agent/**`：

| 介面 | 作用 |
| --- | --- |
| `POST /api/v1/agent/heartbeat` | 回報代理的狀態與目前快照版本 |
| `GET /api/v1/agent/policies` | 下載策略快照；未變化時傳回 304；回應標頭帶 Ed25519 簽章 |
| `GET /api/v1/agent/signing-key` | 驗證簽章用的公開金鑰 |
| `POST /api/v1/agent/access-events` | 批次上傳存取事件，進入存取稽核 |

代理用 `grantforge-policy-engine`（Java 8 API，可以嵌入較舊的系統）在本機求值，不必每次存取都呼叫 GrantForge。

代理不必自己實作這些協定，`core/grantforge-agent-core`（Java 8）已經封裝好：

```java
AgentSettings settings = AgentSettings.builder()
        .server(URI.create("https://grantforge.example.com"))
        .token(System.getenv("GRANTFORGE_AGENT_TOKEN"))
        .instance("namenode-1:8020")
        .cacheDirectory(Paths.get("/var/lib/grantforge-agent"))
        .build();
GrantForgeAgent agent = GrantForgeAgent.start(settings, evaluators);   // 條件求值器，依名稱

AgentDecision decision = agent.decide(AccessRequest.builder(user, "read").groups(groups).resource("path", path).build());
agent.record(AccessEvent.builder(user, path, "read", allowed).decidedBy(decision).action("open").build());
```

- 依服務端要求的間隔傳送心跳；策略版本變化時下載快照（ETag 未變則 304），用服務端的 Ed25519 公開金鑰驗證簽章（可在設定中固定公開金鑰，否則首次從服務端取得並保留），驗證通過才替換，並儲存到快取目錄；服務端不可連線時啟動沿用最後一份快照。
- 快照把角色與群組展開到使用者，`decide` 會把使用者在快照中的角色和群組加到請求上。服務停用或尚無快照時結果為 `NOT_DETERMINED`，由代理決定退回系統自身的檢查或拒絕。
- 存取事件進入有界佇列（滿了就丟棄並計數，絕不阻塞系統），依批次傳送；服務端不可連線時寫入快取目錄下的 `audit-spool/`，恢復後補傳，超過上限丟棄最舊的。
- 依賴 Jackson 2 與 Bouncy Castle（JDK 15 之前沒有 Ed25519）；目標系統自帶這些程式庫的其他版本，代理打包時需要用 shade 重定位。

## 範例

`plugins/grantforge-plugin-example` 是一個完整的外掛程式：型別 `example`（資料庫 → 資料表 → 欄 與路徑），存取類型 select、update、all，欄脫敏、資料表列過濾、IP 範圍條件，設定 url、timeout、password（密碼為 `example` 時測試連線成功），並能查詢範例資料庫資料表。全棧端對端測試用它走完「新增服務 → 寫策略 → 簽發權杖 → 代理拉取 → 存取稽核」。
