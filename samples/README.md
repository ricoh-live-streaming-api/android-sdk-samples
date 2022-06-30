# 各種機能をそれぞれ機能別に実装したアプリケーションサンプル

RICOH Live Streaming Client SDK for Android を使用した各種機能別サンプルアプリケーションです。

## サンプルパッケージ一覧

|サンプル|概要| 
|:--|:--|
|[meta](src/main/java/com/ricoh/livestreaming/sample/meta)|Connection/Track Metadata 更新通知の送受信|
|[mute](src/main/java/com/ricoh/livestreaming/sample/mute)|ミュート状態の変更とミュート状態更新通知の送受信|
|[device](src/main/java/com/ricoh/livestreaming/sample/device)|カメラ・マイクのデバイス検出と切り替え|
|[selective](src/main/java/com/ricoh/livestreaming/sample/selective)|相手映像の選択受信設定|
|[bitrate](src/main/java/com/ricoh/livestreaming/sample/bitrate)|Videoの最大送信ビットレートの変更|

### ビルド時の設定について

samples プロジェクトのビルドには、samples プロジェクト直下の local.properties ファイルへ  
`CLIENT ID` 並びに `CLIENT SECRET` を以下のように設定する必要があります。

```
client_id=xxxxx
client_secret=xxxxx
```

## base

各サンプルに共通する処理をまとめたパッケージです。

connect, disconnect を始め、カメラ・マイクからの stream 取得やパーミッションの設定、  
`RoomSpec` や `AccessToken` の生成処理などはここにまとめています。

## meta

基本処理に加えてメタデータの更新を行うサンプルです。  
また、接続、更新、切断時に通知されるイベントにて画面表示を更新するハンドリングも行います。  
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

基本処理に加えてカメラ・マイクの変更を行うサンプルです。  
なお、カメラ・マイクの変更によるイベント発火はありません。

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

## bitrate

基本処理に加えてVideoの最大送信ビットレートの変更を行うサンプルです。  
なお、ビットレートの変更によるイベント発火はありません。

* API
  * connect
  * disconnect
  * changeVideoSendBitrate
