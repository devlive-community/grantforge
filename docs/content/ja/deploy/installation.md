---
title: リリースパッケージのインストール
description: 物理マシンまたは仮想マシンに GrantForge リリースパッケージをインストールし、起動、停止、アップグレードします。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

## 環境要件

| 項目 | 要件 |
| --- | --- |
| Java | 17 以上（リリースパッケージは Java 17 でコンパイルされており、21 を推奨） |
| メモリ | 少なくとも 1 GB、本番環境では 2 GB 以上を推奨 |
| データベース | 試用では組み込みの H2 を利用でき、本番環境では PostgreSQL、MySQL、MariaDB、Oracle、SQL Server を使用します。[データベース](/ja/deploy/databases/) を参照してください |
| ブラウザ | Chrome、Edge、Firefox、Safari の直近 2 つのメジャーバージョン |

## ディレクトリ構造

`grantforge-release.tar.gz` を解凍すると `grantforge/` ディレクトリができます。

| ディレクトリ | 内容 |
| --- | --- |
| `bin/` | `startup.sh`、`shutdown.sh`、`restart.sh`、`debug.sh`、`import-legacy.sh` |
| `configure/` | `application.properties`。既定の設定はここで上書きします |
| `lib/` | サーバーと依存ライブラリの jar |
| `drivers/` | 追加の JDBC ドライバー（MySQL 用は自分で配置する必要があります） |
| `plugins/` | サービスタイプのプラグイン。[プラグインとサービスタイプ](/ja/develop/plugins/) を参照してください |
| `agents/` | 対象システムにデプロイするエージェントの jar。例: [HDFS NameNode エージェント](/ja/external/hdfs-agent/) |
| `data/` | 組み込み H2 データベースのファイル（初回起動時に作成） |
| `logs/` | `grantforge.log`。`console.out` はログシステムが起動する前の出力を記録します |

## 起動と停止

```bash
bin/startup.sh     # バックグラウンドで起動し、プロセス ID を pid ファイルに書き込みます
bin/shutdown.sh    # pid ファイルを見て正常に停止します
bin/restart.sh     # 停止してからもう一度起動します
bin/debug.sh       # フォアグラウンドで実行し、ログをコンソールにも出力します。Ctrl+C で停止します
```

スクリプトはどのディレクトリからでも実行できます。インストールディレクトリはスクリプトがあるディレクトリの親ディレクトリです。環境変数 `GRANTFORGE_HOME` で直接指定することもできます。

## データベースの選択

既定では `data/grantforge` の下の H2 ファイルデータベースを使用します。これは試用に適しています。本番環境では `configure/application.properties` または環境変数でデータベースを指定します。

```bash
export GRANTFORGE_DB_URL=jdbc:postgresql://db.example.com:5432/grantforge
export GRANTFORGE_DB_USER=grantforge
export GRANTFORGE_DB_PASSWORD=******
bin/startup.sh
```

初回接続時に GrantForge は Liquibase ですべてのテーブルを自動作成し、その後の起動ではまだ実行されていない移行を毎回適用します。

## 初回初期化

初回起動時にログに 1 回限りの初期化トークンが出力されます。コンソールを開いてトークンを入力し、最初の管理者を作成すればよく、手順は [5 分でセットアップ](/ja/start/quick-start/) を参照してください。

## ヘルスチェックと監視

| アドレス | 用途 |
| --- | --- |
| `/actuator/health/liveness` | ライブネスプローブ |
| `/actuator/health/readiness` | レディネスプローブ。データベースが利用でき、移行が完了していれば 200 を返します |
| `/actuator/prometheus` | Prometheus のメトリクス。既定ではログインが必要で、`GRANTFORGE_PROMETHEUS_PUBLIC=true` で信頼できるネットワークに開放できます |

構造化ログが必要な場合は `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`（または `logstash`）を設定します。ログのすべての行にはリクエスト ID が含まれ、これはインターフェースのエラーレスポンスの `requestId` と対応します。

## クラスターデプロイ

複数のインスタンスが 1 つのデータベースを共有しながら同時にサービスを提供できます。セッションはデータベースに保存されるため、どのインスタンスでもどのリクエストも処理できます。各インスタンスは異なる `GRANTFORGE_ID_NODE`（0〜1023）が必要で、この値が ID 生成に使われるノード番号を決めます。ロードバランサーにセッション維持は不要です。

## アップグレード

サービスを停止し、古いバージョンの `lib/` を新しいバージョンで置き換えて（`configure/`、`data/`、`drivers/`、`plugins/` はそのまま保持します）起動すればよく、データベースの移行は自動的に実行されます。アップグレードする前にデータベースをバックアップしてください。1.x からのアップグレードは [アップグレードと旧バージョンからの移行](/ja/deploy/upgrade/) を参照してください。
