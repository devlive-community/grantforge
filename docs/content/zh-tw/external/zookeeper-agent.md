---
title: Apache ZooKeeper 代理
description: 在每個 ZooKeeper 節點部署代理，透過 marker ACL 執行 GrantForge 策略。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

服務端外掛 `grantforge-plugin-zookeeper` 統一提供 `zookeeper` 服務類型、資源與連線設定。讓策略真正生效還需要另一部分：執行於 ZooKeeper 內部的 agent，它作為 SASL authentication provider 參與伺服器的 ACL 判斷。agent 位於 `agents/grantforge-agent-zookeeper`，建置命令 `mvn -pl agents/grantforge-agent-zookeeper -am package` 會產生單一相容 Java 8、內含相依套件的 shaded JAR；發行包路徑為 `agents/zookeeper/grantforge-agent-zookeeper-<version>.jar`。實作以 ZooKeeper 3.6–3.9 API 為目標，實際版本相容性仍待驗證，不代表完整版本矩陣已通過或已完成正式環境驗收。

## 部署

1. 將 JAR 安裝到 **每個 ZooKeeper server** 的 `lib` 目錄，包括所有可接收用戶端請求的節點。在 GrantForge 對應的 `zookeeper` 資料服務中建立 **Agent 憑證**，將 token 存入獨立檔案，僅允許 ZooKeeper 執行帳號讀取；不要將 token 內容寫入 `zoo.cfg`。
2. 在每台伺服器的 `zoo.cfg` 中加入以下設定，替換範例位址、檔案路徑與實例識別碼。`grantforge.server.url`（`http[s]://GrantForge`）、`grantforge.token.file`、本機 `grantforge.cache.dir` 與 `grantforge.instance` 均為必填；快取目錄必須可由 ZooKeeper 執行帳號寫入。

   ```properties
   authProvider.1=org.devlive.grantforge.zookeeper.agent.GrantForgeSaslAuthenticationProvider
   enableEagerACLCheck=false
   grantforge.server.url=https://GrantForge
   grantforge.token.file=/etc/zookeeper/grantforge.token
   grantforge.cache.dir=/var/lib/zookeeper/grantforge-cache
   grantforge.instance=zk-1
   grantforge.principal.mapping=short
   ```

3. 此 provider **取代原生 SASL provider**；不可在其他 `authProvider.N` 中保留另一個註冊相同 `sasl` scheme 的 provider。必須設定 `enableEagerACLCheck=false`：啟用時會拒絕 provider 註冊，marker 也不允許存取。保留並驗證正常的 ZooKeeper SASL、JAAS 與 Kerberos 認證設定；agent 不取代身分認證。依 ensemble 的維護流程重新啟動伺服器。
4. 可選設定 `grantforge.signing.key.file`，指定用於固定信任的簽章公鑰檔案。`grantforge.principal.mapping=short` 為預設值，將 `kafka/host@REALM` 對應為 `kafka`；`full` 保留完整 principal。策略中的使用者必須與對應後的身分一致。

## ACL 與限制

- 僅 `sasl:grantforge` marker ACL 交由 GrantForge 策略判斷；例如 `sasl:grantforge:cdrwa`，其中 `c=create`、`d=delete`、`r=read`、`w=write`、`a=admin`。其他原生 ACL（包括其他 SASL 身分）照常判斷。
- agent 未執行、沒有可用的策略 snapshot、snapshot 的 `serviceType` 不是 `zookeeper`，或策略不符合／不允許時，marker 拒絕存取。**這不是全域 deny**：其他 ACL、`world:anyone` 或 `super` 身分仍可能放行請求。
- 策略比對伺服器上的絕對目標路徑。`create`／`delete` 比對要建立／刪除的 znode，而非提供 ACL 的父節點；使用 chroot 的請求必須加上實際 chroot 前綴，例如 chroot `/kafka` 下的 `/brokers` 對應策略路徑 `/kafka/brokers`。sequential `create` 檢查附加序號前的伺服器絕對請求前綴；後續 `read`／`delete` 檢查產生的完整節點名稱。
- 原生 ACL 不會自動遷移，ACL 也不會遞迴繼承；新建 znode 必須明確設定 marker ACL。使用 `setACL` 前，先在小範圍驗證允許與拒絕，並保留獨立管理管道；不要貿然將 marker 套用到根節點，以免鎖死存取。
- 未安裝 agent 的伺服器會將 `sasl:grantforge` 視為原生 SASL 身分 `grantforge` 進行比對，而不會執行策略，因此必須在所有節點部署，不能依靠部分部署保護 ensemble。
- follower/observer 轉送至 leader 的寫入請求在此 provider 介面可能沒有用戶端連線，因此 `clientAddress` context 可能為空，無法保證依據用戶端 IP 的 ensemble 全鏈路策略。
- 此 provider 無法得知請求的最終結果，因此不傳送最終 `AccessEvent` 稽核事件，也不提供完整存取稽核。
