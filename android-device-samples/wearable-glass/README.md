# RICOH Live Streaming Client APP for Wearable Glass

ウェアラブルグラス上でWebRTCを使用してライブストリーミングするアプリ

## プロジェクト構成

## 動かし方

1. Android Studio で ClientSDK のルートディレクトリごとインポートする。
2. Client ID, Secret, Room ID を取得する
3. 設定ファイルを作成する。
4. ビルドして 端末 上で起動する。

### 設定ファイル

* 以下の書式で `wearable-glass/local.properties` を作成する。
  * `client_id` と `client_secret` は実際の値を入れる

```
client_id=3341e140-0290-43f5-95a0-bd9f98d8ecdc
client_secret=kxiFVi6lzf14dffq3fg46ghg7dip1ash74ioisudsensJ9fe89f4fjijoiafDVcNmg
```

### Room帯域幅予約値

* Room帯域幅予約値は`com.ricoh.livestreaming.wearable_glass.RoomSpec#getSpec()`の "bitrate_reservation_mbps" の値を変更することにより変更可能
* 設定値の決め方は以下を参照ください
  * https://api.livestreaming.ricoh/document/%e6%83%b3%e5%ae%9a%e5%88%a9%e7%94%a8%e3%82%b7%e3%83%bc%e3%83%b3%e5%88%a5%e6%96%99%e9%87%91/

### RICOH Live Streaming Conferenceと組み合わせでの動作
* RICOH Live Streaming Conferenceと組み合わせで動作させるときの注意事項は以下を参照ください
  * https://api.livestreaming.ricoh/document/ricoh-live-streaming-client-app-for-theta-sender%e3%81%a8ricoh-live-streaming-conference%e3%82%92%e7%b5%84%e3%81%bf%e5%90%88%e3%82%8f%e3%81%9b%e3%81%a6%e5%8b%95%e4%bd%9c%e7%a2%ba%e8%aa%8d%e3%81%97/

## アプリの操作方法

### 初めての起動
* ウェアラブルグラスで `RICOH Live Streaming Client App` アプリ起動後、カメラとマイクのパーミッションが要求されるため許可する
* パーミッション許可後、QRコードの読み込み画面が表示される
* Androidスマートフォン端末で[setting-app](../setting-app)を起動し、アプリ設定のためのQRコードを作成する
* QRコードを読み込むとWi-Fiの接続および、アプリケーションで使用するRoomIDが保存される
  * RoomIDを途中で変更するためには、ウェアラブルグラスで `Setting RICOH Live Streaming Client App`を起動する
    * QRコードの読み込み画面が表示されるので、新しいRoomIDを埋め込んだQRコードを読み込むことで変更が可能
* 設定が完了するとアプリが終了する

### 2回目以降の起動
* ウェアラブルグラスで `RICOH Live Streaming Client App` アプリを起動すると、保存されたRoomIDで自動的に配信が開始される
* M400ではナビゲーションの前ボタンで表示拠点の切り替えを行う
  * 相手1拠点の表示(複数ある場合はトグルで切り替え) → 自拠点のみの表示の切り替え
* M400ではナビゲーションの前ボタン長押しでマイクのミュート状態の切り替えを行う
* M400ではナビゲーションの中央ボタンで4Kと2Kの切り替えを行う
  * 最初に接続したときの解像度はQRコードから読み取った解像度とする
* M400では決定ボタン(ナビゲーションの後ボタン)を2回連続で短押しすることで、配信を切断する
* 切断が完了するとアプリが終了する
* スピーカの音量は配信開始時にOSのRingのボリュームを読み取り、その値を音量に反映する

## ログ出力
### Clientログ

ログ出力には SLF4J を使用する。つまりアプリはSLF4Jに対応したログ実装を指定する必要がある。

実装としては [logback-android](https://github.com/tony19/logback-android) を推奨する。logback-android は XML ファイルによりログ出力を細やかに設定可能であり、ファイル等への出力もできる。
ログ出力の設定は `wearable-glass/src/main/assets/logback.xml` で指定可能。　

ログ出力レベルは libwebrtc のログ出力仕様 に倣って `ERROR` / `WARNING` / `INFO` / `TRACE` の 4 段階で設定可能。

設定レベルより上位レベルのログも出力される。つまり TRACE レベルに設定するとすべてのログが出力される。そして最上位の ERROR レベルのログは常に出力される。

### libwebrtcログ

`Option.Builder#loggingSeverity()` でログ出力レベルの指定が可能。

### 統計情報ログ出力機能

`RTCStats` を端末のディスク上に書き込む機能がある。

`/storage/emulated/0/Android/data/com.ricoh.livestreaming.wearable_glass/files/logs/20190129T1629.log` という名前で出力される。
ファイル名は実際の日時で `yyyyMMdd'T'HHmm` の形式となる。
接続する度に新しいファイルが生成される。

ファイル形式は [LTSV](http://ltsv.org/) となっている。

すべての情報を出力しているのではなく `candidate-pair`, `outbound-rtp`, `inbound-rtp`, `remote-inbound-rtp`, `track`, `sender`, `media-source` の情報だけ出力している。

その他の情報を出力したい場合は `RTCStatsLogger.kt` を修正する。
出力可能な情報の一覧は https://www.w3.org/TR/webrtc-stats/ で確認できるが、
libwebrtc の実装に依存するため、記載されているすべての情報が出力できるとは限らない。

以下のコマンドで本体ディスク上のログファイルを取得できる。

```sh
$ adb pull /storage/emulated/0/Android/data/com.ricoh.livestreaming.wearable_glass/files/logs
```
