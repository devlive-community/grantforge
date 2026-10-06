---
title: 設定參考
description: 所有設定項、預設值與對應的環境變數。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

設定可以寫在 `configure/application.properties` 裡，也可以用環境變數覆寫。Spring Boot 的寬鬆繫結規則同樣適用：`grantforge.security.mfa.step-up-window` 可以寫成環境變數 `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW`。時間長度寫成 `30m`、`12h`、`90d` 這樣的形式。

## 服務與資料庫

| 設定項 | 環境變數 | 預設值 | 說明 |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | HTTP 連接埠 |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | 內建 H2 檔案資料庫 | JDBC 位址，見 [資料庫](/zh-tw/deploy/databases/) |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | 資料庫使用者 |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | 空 | 資料庫密碼 |
| — | `GRANTFORGE_HOME` | 安裝目錄 | H2 資料與日誌所在目錄 |
| — | `GRANTFORGE_ID_NODE` | 自動 | 叢集中每個執行個體唯一的節點編號（0–1023） |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | CSV 匯入檔案大小上限 |

## 初始化與註冊

| 設定項 | 預設值 | 說明 |
| --- | --- | --- |
| `grantforge.setup.token` | 空 | 固定的初始化權杖（`GRANTFORGE_SETUP_TOKEN`），為空時隨機產生並列印在日誌中 |
| `grantforge.security.registration-enabled` | `false` | 是否允許訪客自助註冊 |
| `grantforge.security.registration-tenant` | `default` | 自助註冊的帳號所屬租戶 |

## 密碼與鎖定

| 設定項 | 預設值 | 說明 |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | 最短長度，至少 8 |
| `grantforge.security.password.max-length` | `128` | 最長長度，最多 1024 |
| `grantforge.security.password.required-character-classes` | `1` | 需要混合的字元類別數（小寫、大寫、數字、其他），1–4 |
| `grantforge.security.password.history-size` | `0` | 新密碼不能與最近幾次相同，0–24 |
| `grantforge.security.password.max-age` | 不過期 | 密碼有效期，到期後登入時必須變更 |
| `grantforge.security.lockout.max-attempts` | `5` | 連續失敗幾次後鎖定 |
| `grantforge.security.lockout.duration` | `15m` | 鎖定的時間長度 |

密碼不能包含使用者名稱。

## 工作階段與 Cookie

| 設定項 | 預設值 | 說明 |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | 工作階段閒置逾時（`GRANTFORGE_SESSION_TIMEOUT`） |
| `grantforge.security.sessions.max-per-account` | `0` | 每個帳號同時線上工作階段數上限，0 表示不限 |
| `grantforge.security.sessions.activity-interval` | `1m` | 記錄工作階段最近活動的間隔 |
| `grantforge.security.cookie-secure` | `false` | TLS 在代理處終止時設為 `true`（`GRANTFORGE_COOKIE_SECURE`） |

## 雙因素驗證

| 設定項 | 預設值 | 說明 |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | 一次雙因素驗證在多長時間內涵蓋敏感操作，1 分鐘到 12 小時 |
| `grantforge.security.mfa.required-for-sensitive` | `false` | 敏感操作是否要求帳號必須開啟雙因素驗證 |

## 加密與授權伺服器

| 設定項 | 預設值 | 說明 |
| --- | --- | --- |
| `grantforge.security.encryption-key` | 自動產生 | 加密儲存金鑰用的 32 位元組 Base64 金鑰，正式環境務必設定 |
| `grantforge.oauth.issuer` | 請求位址 | OIDC 簽發者，例如 `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | 簽章金鑰自動輪替的週期，0 表示關閉 |
| `grantforge.oauth.signing-key-retention` | `2d` | 舊金鑰繼續公開的時間長度，必須長於任何權杖的有效期 |

## 稽核、外掛程式與代理

| 設定項 | 預設值 | 說明 |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | 稽核日誌保留的時間長度 |
| `grantforge.audit.archive-directory` | 空 | 過期稽核在刪除前封存到的目錄 |
| `grantforge.access-audit.retention` | `90d` | 代理回報的存取稽核保留的時間長度 |
| `grantforge.plugins.directory` | `plugins` | 外掛程式目錄 |
| `grantforge.plugins.call-timeout` | `10s` | 呼叫外掛程式（測試連線、資源查詢）的逾時 |
| `grantforge.agents.refresh-interval` | `30s` | 建議代理取得政策的間隔 |

## 可觀測性

| 設定項 | 預設值 | 說明 |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | `/actuator/prometheus` 是否無需登入（`GRANTFORGE_PROMETHEUS_PUBLIC`） |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | 設為 `ecs` 或 `logstash` 輸出 JSON 日誌 |
