---
title: 開發、測試與 CI
description: 本機建置、測試、程式碼規範與 CI 檢查。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 環境

- JDK 21（產出為 Java 17 位元組碼；策略引擎為 Java 8）
- Node.js 22 與 pnpm 8.10.2
- Docker（資料庫整合測試、效能基準與映像）
- Python 3.12（CI 指令碼）

## 常用命令

```bash
./mvnw verify                          # 建置並測試全部 Java 模組（含主控台）
./mvnw verify -DskipFrontend           # 跳過主控台建置
bash script/ci/web.sh test             # 主控台單元測試
bash script/ci/web.sh e2e              # 主控台瀏覽器測試（模擬後端）
bash script/ci/e2e_fullstack.sh        # 打包、啟動真實服務並執行全棧測試
bash script/ci/db_integration.sh postgres:17   # 在指定資料庫上執行整合測試
bash script/ci/perf_benchmark.sh smoke # 小規模效能基準
```

開發主控台時執行 `pnpm dev`（`core/grantforge-web`），Vite 把 `/api` 等請求代理到 `localhost:9999` 的服務。

## 在 IDE 中啟動

直接執行 `org.devlive.grantforge.server.GrantForge`（模組 `grantforge-server`），預設使用 H2 資料庫。服務端會自動載入儲存庫 `plugins/` 下建置過的外掛程式模組（見 [外掛程式與服務類型](/zh-tw/develop/plugins/)）；外掛程式模組第一次使用前執行一次 `./mvnw -pl plugins/grantforge-plugin-hdfs -am install -DskipTests` 複製它的依賴。

## 程式碼規範

- 後端：Error Prone + NullAway（預設非空，可空處用 JSpecify `@Nullable`）、Checkstyle、PMD、SpotBugs；ArchUnit 守護共同的約定（不用欄位注入、不寫原生 SQL、實體不出現在 API 中、不允許 `Optional.get()` 等）。
- 前端：TypeScript 嚴格模式、ESLint 零警告；文案全部走 i18n，鍵必須是字面量，中英文鍵完全一致。
- 每個原始檔都有 MIT 授權條款標頭；每個主程式碼類別都要有對應的測試類別（例外登記在 `script/ci/test_mapping_exclusions.txt`）。
- 覆蓋率按模組設定門檻（`script/ci/coverage_thresholds.txt`）。
- 資料庫遷移是 Liquibase YAML，一個變更一個檔案，僅能新增不能修改；型別使用 `${text}` 等跨庫屬性。
- 提交訊息遵循 Conventional Commits，標題不超過 72 個字元。

## CI

| 作業 | 內容 |
| --- | --- |
| Repository hygiene | 授權條款標頭、禁止的路徑、測試對應、i18n、權限清單、檔案格式、指令碼與工作流檢查 |
| Commit messages | 提交訊息格式 |
| CI script unit tests | CI 指令碼自身的測試 |
| Java 17 / 21 / 25 / latest | 全部 Java 模組的建置與測試，位元組碼版本檢查 |
| Java static analysis | 覆蓋率、Checkstyle、SpotBugs、PMD |
| Frontend | API 型別與契約一致、型別檢查與建置、ESLint、單元測試、瀏覽器測試 |
| JavaScript SDK | 型別檢查、建置、ESLint、單元測試 |
| Database | H2、PostgreSQL 14/17、MySQL 8.0/8.4、MariaDB 10.11/11.4、Oracle 23、SQL Server 2022 上的遷移與整合測試 |
| Plugin API compatibility | 與上一個發行版比較外掛程式契約 |
| Full-stack acceptance | 在 PostgreSQL 上打包、啟動服務並執行全棧瀏覽器測試 |
| Docs | 文件站的檢查、測試與建置 |

每晚另外執行效能基準；安全工作流掃描依賴與金鑰。

## 發布

版本號為 `年.次版本.修訂`（如 `2026.0.0`），候選版加 `-rc.N`。所有 pom、npm 套件、Helm Chart 的 appVersion、主控台側邊欄與 README 中的版本必須一致，CI 用 `check_versions.py` 檢查。

在 `dev` 分支上用一條命令發布：

```bash
bash script/release/tag.sh 2026.1.0 --dry-run          # 僅檢查並預覽發布說明，不做任何修改
bash script/release/tag.sh 2026.1.0 --next 2026.2.0    # 設定版本、打標籤 v2026.1.0 並推送，再把 dev 切到下一個版本
```

指令碼要求工作區乾淨、本機分支不落後於遠端、標籤不存在，確認後提交 `chore(release): prepare <版本>`、建立帶註解的標籤並推送。標籤觸發 `release.yml`：

- `script/ci/release.sh` 建置發行包、僅含發行依賴的 CycloneDX SBOM 與 `SHA256SUMS`；
- 多架構映像推送到 `ghcr.io/devlive-community/grantforge`；
- Maven 構件（含原始碼套件與 Javadoc）發布到 GitHub Packages；儲存庫設定了 `CENTRAL_USERNAME`、`CENTRAL_PASSWORD`（Central Portal 權杖）、`GPG_PRIVATE_KEY` 與 `GPG_PASSPHRASE` 時，簽章後發布到 Maven Central；
- 建立 GitHub Release，內文是上一個發布版本（`v*` 或 `1.0.6` 這樣的數字標籤）以來的全部提交，依新功能、問題修復、效能等分組，附提交連結。

候選版標記為預發布，不更新映像的 `latest`。本機啟用 `central` profile 預設不會發布（`central.skip=true`），僅有發布工作流顯式傳入 `-Dcentral.skip=false`。

## 權限清單

主控台的頁面、按鈕與它們需要的 API 在 `core/grantforge-web/src/permissions/` 中宣告。`check_permission_manifest.py` 確保每個宣告的 API 都存在、每個需要權限的介面都被某個按鈕或頁面覆蓋（直接呼叫的例外登記在 `script/ci/permission_direct_apis.txt`）。

## 文件

本站位於 `docs/`，使用 Next.js 靜態匯出與 Tailwind CSS：

```bash
cd docs
pnpm install
pnpm dev        # http://localhost:3100
pnpm check      # 頁面、連結與圖片檢查
pnpm build      # 輸出到 docs/out
```

頁面是 `docs/content/` 下的 Markdown，導覽在 `docs/lib/navigation.ts`。API 參考與錯誤碼在建置時從契約與原始碼產生。截圖由 `script/docs/screenshots.sh` 啟動真實服務、寫入範例資料後用 Playwright 產生。
