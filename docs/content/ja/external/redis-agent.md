---
title: Redis エージェント
description: Redis または Valkey のデプロイメントの隣で Redis エージェントを実行し、すべてのノードで各ユーザーの ACL セレクターを GrantForge のポリシーに一致させて 1 つのコマンドで置き換え、GrantForge の外の権限が何を許すかを ACL DRYRUN で Redis に尋ね、ノードの ACL ログから拒否を報告します。
---
<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

Redis エージェントは、デプロイメントの隣で動くサービスです。Redis はノードごとに ACL を持つため、エージェントはレプリカやクラスターのすべてのノードも含め、各ノードで Redis 自身の ACL を `redis` データサービスの署名済みポリシーに一致させます。あとは Redis がすべてのコマンドを自分で確認し、エージェントはどのクライアントの前にも入りません。配布物は実行可能 JAR `agents/redis/grantforge-agent-redis-<version>.jar` として提供し、Java 17 以降が必要です。

リポジトリの Testcontainers 統合テストは、エージェントと配布される JAR を Redis `7.2`、`7.4`、`8.2`、`8.10` と Valkey `8.1`、`9.1` のレプリカ 1 台付きプライマリ、および Redis `8.10` のクラスターで実行します。これは他のすべてのバージョンを認定するものではなく、エージェントは ACL ログの項目に番号がない `7.2` より古い Redis を拒否します。サービスプラグインは [Redis](/ja/plugins/redis/) を参照してください。

## 仕組み

- **セレクターは GrantForge のもの、ルート権限は運用者のものです。** ユーザーのルート権限かセレクターのどれかが許せば、Redis はコマンドを許可します。エージェントは管理するユーザーのすべてのセレクターを維持し、ルート権限とパスワードには決して触れません。セレクターの 1 つはユーザーが読む、書く、または両方できるキーを、1 つは購読できるチャンネルを、1 つは発行できるチャンネルを持ち、監視できるノードではさらに 1 つ持ちます。
- **変更は 1 つのコマンドで行います。** `ACL SETUSER user clearselectors (…)` はノード上のユーザーのすべてのセレクターを一度に置き換えるため、ユーザーが新旧の権限を同時に持つことも、どちらも持たないこともありません。手作業で変えられたセレクターも、次の同期で同じように正します。
- **パターンは成り立つところにだけ付与します。** ポリシーのキーやチャンネルのパターンは、ポリシーがそのパターン全体でユーザーを同じに決める場合に限り、Redis 自身のパターンとして付与します。エージェントは重なるすべてのポリシーに沿ってパターンを切り分けて各部分を決めるため、他のユーザーのポリシーが妨げになりません。ポリシーが中で異なる決定をするパターンは保留し、1 度ログに残します。
- **危険なものは与えず、他人のキーを一覧にさせません。** セレクターは Redis が危険とみなすものを決して与えず、`SCAN` と `RANDOMKEY` はすべてのキーを読めるユーザーにだけ与えます。
- **すべてのノードに、可能なら保存します。** エージェントは指定されたノードからレプリカとクラスターのノードを見つけ、それぞれを一致させます。ACL をファイルに持つノードは `ACL SAVE` で保存し、持たないノードは再起動でセレクターを失い、次の同期で再び受け取ります。
- **何を許すかを Redis に尋ねます。** ルート権限はエージェントのセレクターと並んで決定します。エージェントはそのルールを読む代わりに、ポリシーが拒否するキー、チャンネル、ノードに対してユーザーがコマンドを実行できるかを `ACL DRYRUN` で Redis に尋ね、ユーザー、アクセス、ポリシーを挙げて 1 度ずつ警告します。どのセレクターにも縛られない、すべてのキーにすべてのコマンドを持つユーザーと、誰でもパスワードなしでサインインできるユーザーも警告します。
- **拒否は各ノードの ACL ログから得ます。** 下の拒否の報告を参照してください。
- **現時点のポリシー。** 各同期はその時点で適用されるポリシーと、Redis にあるユーザーとノードから計画するため、有効期間は時間どおりに始まって終わります。

## 配置

1. `redis` データサービスを追加し、ポリシーを設定します。**データ権限 → エージェント** を開いてサービスを選び、エージェントトークンを発行します。トークンは一度しか表示されません。エージェントのアカウントだけが読めるファイルに保存してください。
2. すべてのノードにエージェントの ACL ユーザーを作成します。このユーザーはユーザーを読み、セレクターを設定し、ACL ファイルを保存し、ACL ログを読み、ドライランを行い、クラスターのノードを見つけます。`+acl|setuser` は自分に何でも与えられるので、このユーザーは管理者と同じように守ってください。

   ```text
   ACL SETUSER grantforge on >… -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun +acl|save +config|get +info +cluster|nodes +command|info
   ```

3. エージェントが管理するユーザーのルート権限は、サインインのようにどのユーザーにも必要なものに限ってください。残りはエージェントが与えます。それ以上を与えるルート権限は見つけて警告します。

   ```text
   ACL SETUSER alice on >… resetkeys resetchannels -@all +@connection
   ```

4. 例のアドレス、パス、名前を置き換えて設定ファイルを書きます。

   ```properties
   grantforge.server.url=https://grantforge.example.com
   grantforge.token.file=/etc/grantforge/redis-agent.token
   grantforge.instance=redis-agent-1
   grantforge.cache.dir=/var/lib/grantforge/redis-agent
   grantforge.redis.nodes=redis1.internal:6379,redis2.internal:6379
   grantforge.redis.user=grantforge
   grantforge.redis.password.file=/etc/grantforge/redis-agent.password
   ```

   これらのパスはエージェントのマシン上にあり、キャッシュディレクトリは書き込み可能でなければなりません。パスワードファイルは UTF-8 で読み、前後の空白を除きます。
5. 例えば systemd サービスとしてエージェントを起動します。

   ```bash
   java -jar agents/redis/grantforge-agent-redis-<version>.jar /etc/grantforge/redis-agent.properties
   ```

   `<version>` を配布物のバージョンに置き換えてください。`java -Dgrantforge.…=… -jar …` で与えた設定はファイルより優先されます。エージェントは SIGTERM できれいに停止し、保留中のイベントを送信またはスプールします。Redis は与えられたものを保ちます。

| 設定 | 用途 |
| --- | --- |
| `grantforge.server.url`、`grantforge.token.file`、`grantforge.instance`、`grantforge.cache.dir` | 必須：GrantForge サーバー、エージェントトークンのファイル、このエージェントの名前、署名付きスナップショット、スプールしたイベント、監査ログの読み取り位置を置く書き込み可能なディレクトリ |
| `grantforge.signing.key.file` | スナップショットの署名鍵を固定する任意のファイル。エージェントページから Base64 エンコードの X.509 Ed25519 公開鍵をコピーします。省略すると最初の接続時にサーバーから取得します |
| `grantforge.redis.nodes` | 必須：ノードのアドレス `host:port`。ポートを書かなければ `6379` |
| `grantforge.redis.discover` | 既定は `true`：指定されたノードからクラスターの残りのノードとプライマリのレプリカを見つけます。ノードが公開するアドレスにエージェントが届かない場合は `false` にして、指定されたノードだけを使います |
| `grantforge.redis.user`、`grantforge.redis.password.file` | エージェントの ACL ユーザー（空ならデフォルトユーザー）とそのパスワード |
| `grantforge.redis.tls.mode`、`grantforge.redis.tls.ca.file`、`grantforge.redis.tls.certificate.file`、`grantforge.redis.tls.key.file`、`grantforge.redis.tls.key.password.file` | `disable`（既定）、`verify-ca`、`verify-full`。サーバーを検証する PEM CA 証明書、空なら Java が信頼するもの。PEM クライアント証明書、別ファイルの場合はその鍵、鍵のパスワード |
| `grantforge.redis.timeout.ms` | 接続と 1 つのコマンドにかけてよい時間。既定 `10000`、範囲 `1..600000` ミリ秒 |
| `grantforge.redis.unmanaged.users` | エージェントが触らないユーザー。カンマ区切り。例：サービスアカウント。自分のユーザーと、すべてのキーにすべてのコマンドを持つユーザーには常に触りません |
| `grantforge.sync.interval.ms` | エージェントが Redis を合わせる間隔。既定 `10000`、範囲 `1000..3600000` ミリ秒 |
| `grantforge.audit.log`、`grantforge.audit.log.interval.ms` | 既定は `true`。`false` なら ACL ログを読みません。ACL ログを読む間隔、既定 `5000` ミリ秒 |
| `grantforge.connect.timeout.ms`、`grantforge.read.timeout.ms` | GrantForge への接続と読み取りのタイムアウト。既定 `5000` / `8000` ミリ秒 |
| `grantforge.refresh.interval.ms` | エージェントが新しいポリシーを求める間隔。既定 `30000`、最小 `1000` ミリ秒 |
| `grantforge.audit.batch.size`、`grantforge.audit.queue.capacity`、`grantforge.audit.flush.interval.ms`、`grantforge.audit.spool.limit.bytes` | アクセスイベント：バッチサイズ（既定 `500`）、キューサイズ（既定 `10000`）、送信間隔（既定 `5000` ミリ秒）、ディスク上のスプール上限（既定 64 MiB） |
| `grantforge.audit.fold.window.ms` | 同じ許可されたアクセスを 1 件のイベントにまとめる時間。既定 `10000`、最大 `3600000` ミリ秒。`0` は 1 件ずつ送ります |

## 拒否の報告

Redis は何を許可したかを記録しないため、エージェントは各ノードの ACL ログから Redis が拒否したものを報告します。誰が何をいつどこから拒否されたか、そしてポリシーが Redis と同じ決定をするかです。Redis は 1 分以内に繰り返された拒否を前の項目にまとめ、エージェントは項目が更新されるたびにもう一度報告します。

| ログの理由 | アクセスイベント |
| --- | --- |
| `key` | Redis がコマンドに付けた印に従い、キーの `read` または `write` |
| `channel` | チャンネルの `subscribe` または `publish` |
| `command` | 監視のコマンドはノードの `monitor`。他のコマンドはキーなしで記録されるため報告しません |
| `auth` | 報告しません：拒否されたサインインはどのアクセスも指しません |

## ユーザー、グループ、ロール

ポリシーは Redis の ACL ユーザーを名前で指定します。Redis には独自のロールやグループがないため、グループは GrantForge がユーザーを入れたグループです。

## 決定と制限

- スナップショットがなければエージェントは何も変更しません。使われていないサービス、またはタイプが `redis` ではないサービスに結び付いたトークンは何も付与しません。エージェントは管理するユーザーのセレクターを空にします。
- エージェントが動いていない間、Redis は最後に受け取ったものを保持し、ポリシーの変更は待ちます。変更は更新と同期の間隔内に Redis に届きます。
- エージェントは管理するユーザーのすべてのセレクターを維持します。運用者が手作業で追加したセレクターは次の同期で削除されます。追加の権限はルート権限に与えるか、そのユーザーを運用者に任せてください。
- Redis の ACL はデータベースではなくキーとチャンネルを指定するため、パターンはノードのすべてのデータベースでそのキーを含みます。
- ユーザーがまったく実行できないコマンドが拒否された場合、Redis はキーなしで記録するため、報告しません。
- Redis は値の中をフィルターせず、マスクもしません。

## 関連ガイド

- [Redis](/ja/plugins/redis/)
- [データサービス、ポリシーとエージェント](/ja/external/data-services/)
