---
title: 設定リファレンス
description: すべての設定項目、デフォルト値と対応する環境変数。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

設定は `configure/application.properties` に記述するか、環境変数で上書きできます。Spring Boot のリラックスしたバインディング規則も同様に適用されます。`grantforge.security.mfa.step-up-window` は環境変数 `GRANTFORGE_SECURITY_MFA_STEP_UP_WINDOW` として記述できます。期間は `30m`、`12h`、`90d` のような形式で記述します。

## サービスとデータベース

| 設定項目 | 環境変数 | デフォルト値 | 説明 |
| --- | --- | --- | --- |
| `server.port` | `GRANTFORGE_SERVER_PORT` | `9999` | HTTP ポート |
| `spring.datasource.url` | `GRANTFORGE_DB_URL` | 組み込み H2 ファイルデータベース | JDBC アドレス。[データベース](/ja/deploy/databases/) を参照 |
| `spring.datasource.username` | `GRANTFORGE_DB_USER` | `sa` | データベースユーザー |
| `spring.datasource.password` | `GRANTFORGE_DB_PASSWORD` | 空の値 | データベースパスワード |
| — | `GRANTFORGE_HOME` | インストールディレクトリ | H2 のデータとログがあるディレクトリ |
| — | `GRANTFORGE_ID_NODE` | 自動 | クラスター内で各インスタンスが一意に持つノード番号（0–1023） |
| `spring.servlet.multipart.max-file-size` | `GRANTFORGE_UPLOAD_MAX` | `2MB` | CSV インポートファイルのサイズ上限 |

## 初期化と登録

| 設定項目 | デフォルト値 | 説明 |
| --- | --- | --- |
| `grantforge.setup.token` | 空の値 | 固定の初期化トークン（`GRANTFORGE_SETUP_TOKEN`）。空の場合はランダムに生成してログに出力します |
| `grantforge.security.registration-enabled` | `false` | 訪問者が自己登録できるかどうか |
| `grantforge.security.registration-tenant` | `default` | 自己登録したアカウントが属するテナント |

## パスワードとロック

| 設定項目 | デフォルト値 | 説明 |
| --- | --- | --- |
| `grantforge.security.password.min-length` | `12` | 最短長さ。8 以上 |
| `grantforge.security.password.max-length` | `128` | 最長長さ。1024 以下 |
| `grantforge.security.password.required-character-classes` | `1` | 混在させる必要がある文字種別の数（小文字、大文字、数字、その他）。1–4 |
| `grantforge.security.password.history-size` | `0` | 新しいパスワードは直近の何件かと同じにできません。0–24 |
| `grantforge.security.password.max-age` | 無期限 | パスワードの有効期限。期限切れになるとログイン時に必ず変更する必要があります |
| `grantforge.security.lockout.max-attempts` | `5` | 連続で何回失敗するとロックするか |
| `grantforge.security.lockout.duration` | `15m` | ロックの長さ |

パスワードにユーザー名を含めることはできません。

## セッションと Cookie

| 設定項目 | デフォルト値 | 説明 |
| --- | --- | --- |
| `spring.session.timeout` | `30m` | セッションのアイドルタイムアウト（`GRANTFORGE_SESSION_TIMEOUT`） |
| `grantforge.security.sessions.max-per-account` | `0` | アカウントごとの同時オンラインセッション数の上限。0 で無制限 |
| `grantforge.security.sessions.activity-interval` | `1m` | セッションの直近の活動を記録する間隔 |
| `grantforge.security.cookie-secure` | `false` | TLS をプロキシで終端する場合は `true` に設定します（`GRANTFORGE_COOKIE_SECURE`） |

## 二段階認証

| 設定項目 | デフォルト値 | 説明 |
| --- | --- | --- |
| `grantforge.security.mfa.step-up-window` | `10m` | 1 回の二段階認証がセンシティブな操作をどのくらいの時間カバーするか。1 分から 12 時間 |
| `grantforge.security.mfa.required-for-sensitive` | `false` | センシティブな操作でアカウントに二段階認証の有効化を必須とするか |

## 暗号化と認可サーバー

| 設定項目 | デフォルト値 | 説明 |
| --- | --- | --- |
| `grantforge.security.encryption-key` | 自動生成 | 保存済みのシークレットを暗号化する 32 バイトの Base64 キー。本番環境では必ず設定してください |
| `grantforge.oauth.issuer` | リクエストアドレス | OIDC 発行者。たとえば `https://auth.example.com` |
| `grantforge.oauth.signing-key-rotation` | `90d` | 署名キーの自動ローテーション周期。0 で無効 |
| `grantforge.oauth.signing-key-retention` | `2d` | 古いキーを引き続き公開する期間。すべてのトークンの有効期限より長くする必要があります |

## 監査、プラグインとエージェント

| 設定項目 | デフォルト値 | 説明 |
| --- | --- | --- |
| `grantforge.audit.retention` | `365d` | 監査ログの保持期間 |
| `grantforge.audit.archive-directory` | 空の値 | 期限切れの監査記録を削除する前にアーカイブするディレクトリ |
| `grantforge.access-audit.retention` | `90d` | エージェントが報告するアクセス監査の保持期間 |
| `grantforge.plugins.directory` | `plugins` | プラグインディレクトリ |
| `grantforge.plugins.call-timeout` | `10s` | プラグイン呼び出し（接続テスト、リソース検索）のタイムアウト |
| `grantforge.agents.refresh-interval` | `30s` | エージェントがポリシーを取得することを推奨する間隔 |

## 観測可能性

| 設定項目 | デフォルト値 | 説明 |
| --- | --- | --- |
| `grantforge.observability.prometheus-public` | `false` | `/actuator/prometheus` をログインなしで利用できるかどうか（`GRANTFORGE_PROMETHEUS_PUBLIC`） |
| — | `LOGGING_STRUCTURED_FORMAT_CONSOLE` | `ecs` または `logstash` に設定すると JSON ログを出力します |
