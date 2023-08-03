# RICOH Live Streaming Client APP for THETA Sender

RICOH Live Streaming API と THETA プラグインの組み合わせで配信するサンプル

## 動かし方

1. Android Studio で ClientSDK のルートディレクトリごとインポートする。
2. Client ID, Secret, Room ID を取得して、設定ファイルを作成する。
3. ビルドして THETA 上で起動する。

### 設定ファイル

* 以下の書式で `theta-plugin/local.properties` を作成する。
  * `client_id` と `client_secret` と `room_id` は実際の値を入れる
  * `video_bitrate` は送信するビデオのビットレートの上限値を設定する
    * 未指定時は 10000 (10Mbps)
  * `initial_audio_mute` は接続開始時のオーディオミュート状態を設定する
    * 選択肢は mute|unmute
    * 未指定時は unmute
```
client_id=3341e140-0290-43f5-95a0-bd9f98d8ecdc
client_secret=kxiFVi6lzf14dffq3fg46ghg7dip1ash74ioisudsensJ9fe89f4fjijoiafDVcNmg
room_id=sample-room
video_bitrate=10000
initial_audio_mute=unmute
```

### Room帯域幅予約値

* Room帯域幅予約値は`com.ricoh.livestreaming.theta.RoomSpec#getSpec()`の "bitrate_reservation_mbps" の値を変更することにより変更可能
* 設定値の決め方は以下を参照ください
  * https://api.livestreaming.ricoh/document/%e6%83%b3%e5%ae%9a%e5%88%a9%e7%94%a8%e3%82%b7%e3%83%bc%e3%83%b3%e5%88%a5%e6%96%99%e9%87%91/

### RICOH Live Streaming Conferenceと組み合わせでの動作
* RICOH Live Streaming Conferenceと組み合わせで動作させるときの注意事項は以下を参照ください
  * https://api.livestreaming.ricoh/document/ricoh-live-streaming-client-app-for-theta-sender%e3%81%a8ricoh-live-streaming-conference%e3%82%92%e7%b5%84%e3%81%bf%e5%90%88%e3%82%8f%e3%81%9b%e3%81%a6%e5%8b%95%e4%bd%9c%e7%a2%ba%e8%aa%8d%e3%81%97/

## 操作と状態確認の方法

* シャッターボタン長押し : 接続/切断
* 配信中にシャッターボタン短押し : 静止画撮影
* 接続中にモードボタン短押し : 解像度・スティッチングモード切り替え
  * モードは以下の順で切り替わる

| 解像度 | fps | スティッチングモード |
| ------ | ------ | ------ |
| 3840x1920 | 20fps | Equirectangular |
| 3840x1920 | 30fps | Equirectangular |
| 3840x1920 | 10fps | Equirectangular |
| 1920x960 | 30fps | Equirectangular |

* 接続中に無線ボタン短押し : 送信ビットレートの変更
* THETA VではLive LEDで配信状態を示す
  * Live LED 消灯 : 未接続 or 切断完了
  * Live LED 点滅 : 接続準備中
  * Live LED 点灯 : 接続成功 (配信中)
  * Live LED すばやく点滅 : 切断準備中

* THETA Z1 でFnボタン短押し : オーディオミュート状態の変更

また、画面操作アプリケーション Vysor を用いることで、画面 UI の操作により設定できる項目もあります。

## ログ出力

本アプリでは以下のログを出力する。

* Clientログ
  * `/storage/emulated/0/Android/data/com.ricoh.livestreaming.theta/files/logs/theta/` に出力
* libwebrtcログ
  * `/storage/emulated/0/Android/data/com.ricoh.livestreaming.theta/files/logs/libwebrtc/` に出力
* statsログ
  * `/storage/emulated/0/Android/data/com.ricoh.livestreaming.theta/files/logs/stats/` に出力

以下のコマンドで本体ディスク上のログファイルをすべて取得できる。

```sh
$ adb pull /storage/emulated/0/Android/data/com.ricoh.livestreaming.theta/files/logs
```

### Clientログ

ログ出力には SLF4J を使用する。つまりアプリはSLF4Jに対応したログ実装を指定する必要がある。

実装としては [logback-android](https://github.com/tony19/logback-android) を推奨する。logback-android は XML ファイルによりログ出力を細やかに設定可能であり、ファイル等への出力もできる。
ログ出力の設定は `theta-plugin/src/main/assets/logback.xml` で指定可能。　

ログ出力レベルは libwebrtc のログ出力仕様 に倣って `ERROR` / `WARNING` / `INFO` / `TRACE` の 4 段階で設定可能。

設定レベルより上位レベルのログも出力される。つまり TRACE レベルに設定するとすべてのログが出力される。そして最上位の ERROR レベルのログは常に出力される。

アプリログは `/storage/emulated/0/Android/data/com.ricoh.livestreaming.theta/files/logs/theta/theta-plugin_20221215T143500.log` という名前で出力される。  
ファイル名は実際の日時で `theta-plugin_yyyyMMdd'T'HHmmss.log` の形式となる。

### libwebrtcログ

`Option.Builder#loggingSeverity()` で logcat に出力するログレベルの指定が可能。

また、`Client#setLibWebrtcLogOption()` を利用することで libwebrtc ログも本体のディスク上に `/storage/emulated/0/Android/data/com.ricoh.livestreaming.theta/files/logs/libwebrtc/webrtc_log_0` という名前で出力することができる。

libwebrtc ログは接続する度に "webrtc" プレフィックスのログファイルを削除して再作成する仕組みのため、  
接続時に過去実行時の webrtc ログを残すために `/storage/emulated/0/Android/data/com.ricoh.livestreaming.theta/files/logs/libwebrtc/20221215T143500_webrtc_log_0` という名前のファイルにリネームしている。  
リネーム後のファイル名は実際の日時で `yyyyMMdd'T'HHmmss_webrtc_log_{0始まりの連番}` の形式となる。

### statsログ

`RTCStats` を本体のディスク上に書き込む機能がある。

`/storage/emulated/0/Android/data/com.ricoh.livestreaming.theta/files/logs/stats/20190129T1629.log` という名前で出力される。
ファイル名は実際の日時で `yyyyMMdd'T'HHmm` の形式となる。  
このログファイルも接続する度に新しいファイルが生成される。

ファイル形式は [LTSV](http://ltsv.org/) となっている。

すべての情報を出力しているのではなく `candidate-pair`, `outbound-rtp`, `inbound-rtp`, `remote-inbound-rtp`, `track`, `sender`, `media-source`, `local-candidate`, `remote-candidate` の情報だけ出力している。

その他の情報を出力したい場合は `RTCStatsLogger.kt` を修正する。
出力可能な情報の一覧は https://www.w3.org/TR/webrtc-stats/ で確認できるが、
libwebrtc の実装に依存するため、記載されているすべての情報が出力できるとは限らない。
