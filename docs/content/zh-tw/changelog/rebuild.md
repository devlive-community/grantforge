---
title: 2026.0.0（重構版）
description: 從零重寫的 GrantForge：多租戶身分、資源與角色授權、資料與欄位權限、標準協定接入、企業治理與外掛化的外部系統權限。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

2026.0.0 是一次完整的重寫，取代 1.x（AuthX）。它不再是一個後台範本，而是一個獨立部署的身分與權限平台。1.x 的帳號、角色與選單可以匯入，見 [升級與舊版本遷移](/zh-tw/deploy/upgrade/)。

## 平台

- Spring Boot 4、Java 17 執行時期；一個發行包包含服務端與主控台，另提供 Docker 映像、Compose 與 Helm Chart。
- 支援 H2、PostgreSQL、MySQL、MariaDB、Oracle、SQL Server，遷移由 Liquibase 管理，每次提交在九個資料庫版本上測試。
- 首次啟動的初始化精靈，用服務日誌中的一次性權杖建立平台管理員。
- 叢集部署：工作階段儲存在資料庫，ID 為時間有序的 TSID。
- 全新的主控台：Vue 3、Tailwind CSS，亮色與暗色主題，中文與英文。

## 身分與組織

- 多租戶：每個租戶的帳號、組織與授權完全隔離。
- 使用者、部門樹、使用者群組、職位，CSV 批次匯入匯出。
- Argon2id 密碼、可設定的密碼策略與鎖定、工作階段管理、TOTP 兩步驟驗證與還原碼、敏感操作的再次驗證。
- LDAP / Active Directory 與 OIDC 身分來源，支援同步與聯合登入。

## 授權

- 資源目錄：模組、選單、頁面、標籤、按鈕、API、資料實體與欄位，以及它們之間的依賴。主控台自身的頁面、按鈕與 API 也在目錄中，並接受同樣的授權。
- 角色與授權：允許與拒絕、繼承、按使用者/使用者群組/部門/職位分配、有效期。
- 資料權限：按組織範圍或結構化條件限定可見的資料列。
- 欄位權限：按角色隱藏、遮罩或唯讀欄位。
- 權限解釋、按使用者模擬、完整的稽核日誌，以及失效設定的檢查。

## 治理

- 職責分離：互斥角色在分配、繼承與申請時被拒絕，並能發現已存在的衝突。
- 權限申請：使用者申請可申請的角色，核准人核准後限時生效、到期自動收回。
- 定期權限覆核。

## 應用程式接入

- 內建 OAuth 2.1 / OpenID Connect 授權伺服器：授權碼 + PKCE、客戶端憑證、更新權杖輪換、簽章金鑰輪換。
- 權限查詢開放 API，帶版本與 ETag。
- Spring Boot Starter 與 JavaScript SDK，附帶可執行的範例應用程式。

## 外部系統權限

- 外掛化的服務類型：外掛定義資源層級、存取類型、遮罩與資料列過濾；每個外掛獨立載入。
- 通用策略編輯器、Ed25519 簽章的策略快照、代理心跳與存取稽核。
- 範例外掛、HDFS 服務類型和 Hadoop 3.5.0 NameNode 代理；Hive 外掛與其他 Hadoop 版本的代理正在開發中。

## 品質

- 百萬帳號規模的效能基準每晚執行，超過上限即失敗。
- 靜態分析（NullAway、Error Prone、Checkstyle、PMD、SpotBugs、ArchUnit）、覆蓋率門檻與全棧瀏覽器測試。
- 文件站改用 Next.js 與 Tailwind CSS 重建。
