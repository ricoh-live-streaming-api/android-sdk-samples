# For Android Unity

Unity AndroidでWebRTCを使用してライブストリーミングするアプリ

## 動かし方

1. Unity Hubでunity-appをリストに追加し起動する
2. `File > Build Settings` を選択し、PlatformをAndroidに変更する
3. Client ID, Secret, Room ID を取得する
4. 設定ファイルを作成する。
5. `File > Build And Run` を選択し、端末にアプリをインストールする

### 設定ファイル

* 以下の書式で `unity-app/Assets/Scripts/Secrets.cs` を作成する。
  * `client_id` と `client_secret` は実際の値を入れる

```
public static class Secrets
{
    public static readonly string CLIENT_ID = "xxxxxx";
    public static readonly string CLIENT_SECRET = "xxxxxx";
    public static readonly string ROOM_ID = "sample-room";
    public static readonly int VIDEO_BITRATE = 5000;
}
```

## ログ出力
### Clientログ

ログ出力には SLF4J を使用する。つまりアプリはSLF4Jに対応したログ実装を指定する必要がある。

実装としては [logback-android](https://github.com/tony19/logback-android) を推奨する。logback-android は XML ファイルによりログ出力を細やかに設定可能であり、ファイル等への出力もできる。
ログ出力の設定は `unity-app/Assets/Plugins/Android/assets/logback.xml` で指定可能。　

ログ出力レベルは libwebrtc のログ出力仕様 に倣って `ERROR` / `WARNING` / `INFO` / `TRACE` の 4 段階で設定可能。

設定レベルより上位レベルのログも出力される。つまり TRACE レベルに設定するとすべてのログが出力される。そして最上位の ERROR レベルのログは常に出力される。

### libwebrtcログ

`Option.Builder#loggingSeverity()` でログ出力レベルの指定が可能。

### 統計情報ログ出力機能

`RTCStats` を端末のディスク上に書き込む機能がある。

`/storage/emulated/0/Android/data/com.ricoh.livestreaming.unity_app/files/logs/20190129T1629.log` という名前で出力される。
ファイル名は実際の日時で `yyyyMMdd'T'HHmm` の形式となる。
接続する度に新しいファイルが生成される。

ファイル形式は [LTSV](http://ltsv.org/) となっている。

すべての情報を出力しているのではなく `candidate-pair`, `outbound-rtp`, `inbound-rtp`, `remote-inbound-rtp`, `track`, `sender`, `media-source` の情報だけ出力している。

その他の情報を出力したい場合は `RTCStatsLogger.cs` を修正する。
出力可能な情報の一覧は https://www.w3.org/TR/webrtc-stats/ で確認できるが、
libwebrtc の実装に依存するため、記載されているすべての情報が出力できるとは限らない。

以下のコマンドで本体ディスク上のログファイルを取得できる。

```sh
$ adb pull /storage/emulated/0/Android/data/com.ricoh.livestreaming.unity_app/files/logs
```
