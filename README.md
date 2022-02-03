# Samples for RICOH Live Streaming Client SDK for Android

株式会社リコーが提供するRICOH Live Streaming Serviceを利用するためのRICOH Live Streaming Client SDK for Android です。

RICOH Live Streaming Serviceは、映像/音声などのメディアデータやテキストデータなどを
複数の拠点間で双方向かつリアルタイムにやりとりできるプラットフォームです。

サービスのご利用には、API利用規約への同意とアカウントの登録、ソフトウェア利用許諾書への同意が必要です。
詳細は下記Webサイトをご確認ください。

* サービスサイト: https://livestreaming.ricoh/
  * ソフトウェア開発者向けサイト: https://api.livestreaming.ricoh/
* ソフトウェア使用許諾契約書 : [Software License Agreement](SoftwareLicenseAgreement.txt)

* NOTICE: This package includes SDK and sample application(s) for "RICOH Live Streaming Service".
At this moment, we provide API license agreement / software license agreement only in Japanese.

# プロジェクト構成

* [libs](libs) ライブラリ本体
* [samples](samples) SDK の API をそれぞれ用いたサンプル (以下に詳細を記載)
* [theta-plugin](android-device-samples/theta-plugin) RICOH Live Streaming API の THETA プラグインサンプル (配信専用)
* [wearable-glass](android-device-samples/wearable-glass) RICOH Live Streaming API の ウェアラブルグラス向けサンプル
* [setting-app](android-device-samples/setting-app) wearable-glass アプリ用のQRコードを作成するアプリ
* [unity-samples](unity-samples) RICOH Live Streaming API の Unity Android 向けサンプル

# samples について

### ビルド時の設定について

samples モジュールのビルドには、モジュール直下の credentials.properties ファイルへ  
`CLIENT ID` 並びに `CLIENT SECRET` を以下のように設定する必要があります。

```
client_id=xxxxx
client_secret=xxxxx
```

## meta

基本処理に加えてメタデータの更新を行うサンプルです。  
また、接続／更新／切断時に通知されるイベントにて画面表示を更新するハンドリングも行います。  
なお、トラックメタデータに関する処理は SFU 接続時にのみ有効となります。

* API
  * connect
    * onAddRemoteConnection
    * onAddRemoteTrack
  * disconnect
    * onRemoveRemoteConnection
  * updateMeta
    * onUpdateRemoteConnection
  * updateTrackMeta
    * onUpdateRemoteTrack

## mute

基本処理に加えてミュート状態の変更を行うサンプルです。  
また、ミュート状態変更時に通知されるイベントにて画面表示を更新するハンドリングも行います。  
なお、本イベント通知は SFU 接続時にのみ有効となります。

* API
  * connect
  * disconnect
  * changeMute
    * onUpdateMute

## device

基本処理に加えてカメラ／マイクの変更を行うサンプルです。  
なお、カメラ／マイクの変更によるイベント発火はありません。

* API
  * connect
  * disconnect
  * replaceMediaStreamTrack

## selective

基本処理に加えてリモート映像受信可否の変更を行うサンプルです。  
なお、映像受信可否の変更によるイベント発火はありません。

* API
  * connect
  * disconnect
  * changeMediaRequirements

## base

各サンプルに共通する処理をまとめたパッケージです。

connect, disconnect を始め、カメラ／マイクからの stream 取得やパーミッションの設定、  
`RoomSpec` や `AccessToken` の生成処理などはここにまとめています。
