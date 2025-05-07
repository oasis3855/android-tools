## Android シンプル デスク クロック<br/>Simple Desk Clock<!-- omit in toc -->

[Home](https://oasis3855.github.io/webpage/) > [Software](https://oasis3855.github.io/webpage/software/index.html) > [Software Download](https://oasis3855.github.io/webpage/software/software-download.html) > [android-tools](../README.md) > ***SimpleDeskClock*** (this page)

<br />
<br />

Last Updated : May 2025

- [ソフトウエアのダウンロード](#ソフトウエアのダウンロード)
- [概要](#概要)
- [実装されている機能](#実装されている機能)
- [動作確認済み](#動作確認済み)
- [バージョンアップ情報](#バージョンアップ情報)
- [ライセンス](#ライセンス)

<br />
<br />

## ソフトウエアのダウンロード

- ![download icon](../readme_pics/soft-ico-download-darkmode.gif) [このGitHubリポジトリを参照する（ソースコード）](./src/)
- ![download icon](../readme_pics/soft-ico-download-darkmode.gif) [このGitHubリポジトリを参照する（apkファイル）](./apk/)

<br />
<br />

## 概要

古いAndroidスマホを、置き時計として使うためのアプリケーション。机の上において、日めくりカレンダー代わりに使う目的で作成。

インストールする実機に合わせてフォントサイズ、照度センサーのしきい値を変更しapkファイルを再作成する必要があります。 → Version 1.1 で設定画面追加

![画面例](readme_pics/simpledeskclock-sampleview.jpg)

Handleを使い1秒毎に画面更新するアクティビティ、画面タップの検出、センサー読み出しのサンプルコードとして公開。

<br />
<br />

## 実装されている機能

- 画面の自動回転に対応
- ACアダプタで充電中にのみバックライト点灯（明設定）
- バックライト消灯時に、画面タップすると一時的にバックライト点灯（明設定）
- 照度センサーがしきい値以下の場合はバックライト消灯（暗設定）
- バックライト制御を無効化し、常に点灯（明設定）する時間帯

バックライトの条件適用の優先順序は次の通り
1. 画面タップONでの点灯（明設定）
2. 充電中ではない場合の消灯（暗設定）
3. バックライト制御を無効化する時間帯は点灯（明設定）
4. 環境照度Luxがしきい値以上・以下で点灯・消灯を制御

<br />
<br />

## 動作確認済み

- Android 7
- Android 10
- Android 11
- Android 13 

<br />
<br />

## バージョンアップ情報

- Version 1.0 (2023/06/12)

  - 当初 

- Version 1.1 (2025/05/06)

  - 設定AlertDialog追加
  - バックライト輝度制御の無効化時間帯の設定を追加
  - バックライト輝度明・暗の設定を追加
  - バックライト制御に使われる環境照度Luxしきい値の設定を追加
  - clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) を無効化する設定を追加
  - 日時・時計のフォントサイズ変更設定を追加

<br />
<br />

## ライセンス

このプログラムは [GNU General Public License v3ライセンスで公開する](https://gpl.mhatta.org/gpl.ja.html) フリーソフトウエア
