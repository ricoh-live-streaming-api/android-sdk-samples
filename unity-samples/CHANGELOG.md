# CHANGELOG

## v1.4.0
* Client SDKを1.5.0 にアップデート
  * UnityのライフサイクルのStart()でUnityPlugin#setUnityContext()を呼び出していたのをGL.IssuePluginEvent()の呼び出しに変更
* Oculus向けサンプルアプリの `Multithreaded Rendering` 設定を OFF->ONに変更

## v1.3.0
* Client SDKを1.3.0 にアップデート

## v1.2.0
* Client SDKを1.2.0 にアップデート
* Idealens向けサンプルアプリを追加
* 表記ゆれの統一
  * `LiveStreaming`、`Live Streaming`を`RICOH Live Streaming`に修正
  * `Ricoh`を`RICOH`に修正
* ソフトウェア使用許諾契約書を追加

## v1.1.0
* Client SDKを1.1.0にアップデート
* 画質改善
  * 画質改善には以下の操作が必要になります
    * UnityのライフサイクルのStart()でUnityPlugin#setUnityContext()を呼び出す
    * UnityのPlayer Settingsの `Multithreaded Rendering` を OFFに設定

## v1.0.0
* Client SDKを1.0.1にアップデート
* マイク入力に対応
* bitrate_reservation_mbps の設定例を記載

## v0.4.0

* Client SDKを0.5.1にアップデート
* OculusIntegrationを削除
  * OculusIntegrationはアプリ開発者自身でインストールが必要です
  * インストール方法は README.md を参照ください

## v0.3.0

* Client SDKを0.5.0にアップデート
* JwtAccessTokenの有効期限を1時間に変更

## v0.2.0

* Client SDKを0.4.0にアップデート
* RoomSpecからUseTurnを削除
* JwtAccessTokenに設定するnbf、expの値を修正

## v0.1.0

* 初版
