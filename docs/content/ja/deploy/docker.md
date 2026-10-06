---
title: Docker、Compose と Helm
description: コンテナイメージで GrantForge を実行し、Compose で各種データベースと組み合わせて試用し、Helm で Kubernetes にデプロイします。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## イメージ

すべてのリリースでイメージ `ghcr.io/devlive-community/grantforge:<バージョン>`（linux/amd64 と linux/arm64）を公開し、正式版では `latest` も同時に更新します。

```bash
docker run -p 9999:9999 ghcr.io/devlive-community/grantforge:2026.0.0
```

イメージはリリースパッケージからビルドされ、`eclipse-temurin:21-jre` をベースにしており、非特権ユーザー（UID 10001）で実行され、ログはコンソールに出力され、データベースは含まれません。ソースから自分でビルドすることもできます。

```bash
./mvnw -DskipTests package
docker build -f deploy/docker/Dockerfile -t grantforge dist
```

イメージの規約は次のとおりです。

| パス / 変数 | 説明 |
| --- | --- |
| `/opt/grantforge/data` | ボリューム。組み込み H2 のデータファイル |
| `/opt/grantforge/plugins` | ボリューム。サービスタイプのプラグイン |
| `/opt/grantforge/drivers` | 追加の JDBC ドライバー（MySQL Connector/J はここに置きます） |
| `9999` | サービスのポート |
| `HEALTHCHECK` | `/actuator/health/readiness` を呼び出します |

## Compose の例

`deploy/compose/` にはデータベースごとに例が 1 つずつ用意されています。`h2`、`postgres`、`mariadb`、`mysql`、`sqlserver`、`oracle` です。

```bash
docker compose -f deploy/compose/postgres.yml up -d
docker compose -f deploy/compose/postgres.yml logs grantforge | grep "setup token"
```

その後 http://127.0.0.1:9999/ を開きます。例で使っている既定のデータベースパスワードは試用にしか適さないため、実際に使う前に `GRANTFORGE_DB_PASSWORD` で変更してください。MySQL の例では、まず `mysql-connector-j-<バージョン>.jar` を `deploy/compose/drivers/` に入れる必要があります。

## Helm

`deploy/helm/grantforge` は Helm Chart です。1 つの StatefulSet と外部データベースで構成され、各レプリカは Pod の順序に応じて自分の ID ノード番号を取得します（Kubernetes 1.28 以上が必要です）。

```bash
helm install grantforge deploy/helm/grantforge \
  --set database.url=jdbc:postgresql://postgresql:5432/grantforge \
  --set database.username=grantforge \
  --set database.existingSecret=grantforge-db \
  --set encryptionKey=$(openssl rand -base64 32)
```

主なパラメーターは次のとおりです。

| パラメーター | 既定値 | 説明 |
| --- | --- | --- |
| `image.repository` / `image.tag` | `ghcr.io/devlive-community/grantforge` / Chart の appVersion | イメージ |
| `replicaCount` | `1` | レプリカ数。1 より大きくできます |
| `database.url` / `username` / `password` | — | データベース接続。パスワードは `existingSecret` に置くことを推奨します |
| `setupToken` | 空 | 初期化トークンを事前に指定します。空の場合はログに出力されます |
| `encryptionKey` | 空 | 保存済みのシークレット（アイデンティティソースのパスワード、認証器のキー、署名用の秘密鍵など）を暗号化する 32 バイトの Base64 キー。空の場合は自動生成してデータベースに保存します |
| `cookieSecure` | `false` | TLS が入口で終了する場合は `true` に設定します。セッション Cookie には常に Secure が付きます |
| `ingress.*` | 無効 | コンソールと API を公開します |
| `plugins.persistence.enabled` | `false` | プラグインディレクトリに永続ボリュームをマウントします |
| `podDisruptionBudget.enabled` | `false` | レプリカが複数ある場合は有効化を推奨します |

> [!IMPORTANT]
> 本番環境では必ず `encryptionKey` を設定してください。設定しない場合、キーはデータベースに保存されるため、データベースのバックアップを入手した人なら誰でも、その中に暗号化して保存されているシークレットを復号できます。
