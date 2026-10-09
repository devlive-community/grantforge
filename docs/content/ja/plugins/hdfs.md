---
title: Apache Hadoop HDFS
description: HDFS プラグインを導入・設定し、ディレクトリーの参照とパスへのアクセスポリシーを管理します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Apache Hadoop HDFS プラグインは、GrantForge でクラスターへの接続、パス検索、ポリシー管理を提供します。プラグイン ID とサービスタイプはどちらも `hdfs` です。

## インストール

配布パッケージには `plugins/hdfs` にプラグインが含まれます。**プラットフォーム管理 → プラグイン** で `hdfs` が有効であることを確認し、更新後はプラグインディレクトリーを再スキャンします。

## データサービスの追加

1. **データ権限 → データサービス** でサービスを追加し、HDFS（`hdfs`）を選択します。
2. クラスター URI と検索ユーザーを入力します。Hadoop 2.x は `webhdfs://namenode:50070`、3.x は `hdfs://namenode:8020` または `webhdfs://namenode:9870` を使用できます。HTTPS では `swebhdfs://` とクラスターの実際のポートを指定します。
3. 検索ディレクトリーを設定し、接続をテストします。ディレクトリーの存在と一覧取得を確認してから保存します。

| 設定 | 用途 |
| --- | --- |
| `fs.default.name` | 必須のクラスター URI。サブディレクトリー、資格情報、クエリは含めません。HA では `hdfs://nameservice1` と対応する追加プロパティを使用できます |
| `username` | 必須の検索ユーザー。Kerberos では `grantforge@EXAMPLE.COM` などの principal |
| `hadoop.security.authentication` | 既定値は `simple`。Kerberos クラスターでは `kerberos` を選択 |
| `hadoop.security.authorization` | Hadoop が権限をチェックするか。既定値 `false`。クラスターの core-site.xml に合わせます |
| `hadoop.security.auth_to_local` | Kerberos principal をユーザー名に変換する規則。クラスターの core-site.xml に合わせます |
| `password` / `keytab` | Kerberos パスワード、または GrantForge サーバー上の keytab ファイルのパス |
| `dfs.namenode.kerberos.principal`、`dfs.datanode.kerberos.principal`、`dfs.secondary.namenode.kerberos.principal` | Kerberos クラスターの各コンポーネントの principal。`nn/_HOST@EXAMPLE.COM` など、クラスターの設定に合わせます |
| `lookup.path` | 検索・参照の開始ディレクトリー。既定値は `/`、例は `/data`。参照はこの配下に制限 |
| `lookup.max.entries` | ディレクトリー全体の走査上限。既定値 `10000`、範囲 `1..100000` |
| `hadoop.config` | HA などの Hadoop プロパティを 1 行に 1 つの `key=value` で指定。同名の接続設定を上書き |
| `hadoop.rpc.protection` | クラスターに合わせて `authentication`、`integrity`、`privacy` を選択 |

Hadoop 2.7.7 で NameNode 属性拡張を有効にすると、一般ユーザーによるルートパス `/` の照会で上流の `NullPointerException` が発生することが確認されているため、`lookup.path` は `/data` などの実在するディレクトリーに設定してください（[エージェントガイド](/ja/external/hdfs-agent/)参照）。

Kerberos には到達可能な KDC、サーバーの `krb5.conf`、クラスターに対応する `hadoop.security.auth_to_local` とサービス principal も必要です。検索アカウントはディレクトリーのメタデータを取得します。

設定は保存時に検証されます：`hadoop.config` の `fs.defaultFS` と `fs.default.name` はエイリアスのため、設定するのは一方だけにします。クラスター URI に資格情報、パス、クエリ、フラグメントを含めてはいけません。`kerberos` を選ぶ場合は `password` または `keytab` が必要です。`lookup.path` は `..` を含まない絶対パスである必要があります。`lookup.max.entries` は `1` から `100000` の間である必要があります。追加プロパティは同名の接続設定を上書きし、検証とログインの両方が上書き後の値を使用します。

## パスの参照

**データ権限 → ポリシー** でサービスを選び、`path` の横の **参照** を使用します。

- ディレクトリーを開き、パスから移動したり親に戻ったりして、必要に応じて後続ページを読み込みます。
- ファイル・ディレクトリーの表示、所有者、グループ、権限、ファイルサイズ、更新日時を確認します。
- 複数のファイルやディレクトリー、または現在のディレクトリーをポリシーに追加します。パス入力による候補表示も利用できます。

RPC と一括一覧をサポートする WebHDFS はネイティブなページ分割を使用します。古いエンドポイントは走査上限内でディレクトリーを読み取り、超過時はエラーを返します。権限、認証、接続の失敗は理由を表示し、再試行できます。

入力では先頭の `/` を省略でき、連続した `/` と `.` は許可されますが、`..` と `lookup.path` の範囲外の絶対パスは拒否されます。存在しないディレクトリーは候補を返しません。参照は HDFS 自身のアクセス制御を置き換えるものではなく、シンボリックリンクと ViewFS マウントはクラスターの設定に従います。

## ポリシーの適用

リソース階層は `path` の 1 段で、アクセスタイプは `read`、`write`、`execute` です。パスポリシーは再帰と除外をサポートします。

サーバープラグインは管理と検索を担当します。アクセスを制御するには Hadoop バージョンに合った NameNode エージェントも必要で、ユーザーは HDFS のネイティブ権限と GrantForge ポリシーの両方を満たす必要があります。番号付きエージェントは 2.7、2.10、3.2、3.3、3.4、3.5 向けです。検証済みの組み合わせ、認証・HA の検証範囲、スーパーユーザーの制限はエージェントガイドを参照してください。

## 関連ガイド

- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
- [Apache Hadoop HDFS NameNode エージェント](/ja/external/hdfs-agent/)
- [プラグインとサービスタイプの開発](/ja/develop/plugins/)
