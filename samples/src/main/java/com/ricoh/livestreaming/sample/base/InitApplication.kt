package com.ricoh.livestreaming.sample.base

import android.app.Application

class InitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        /** ログ出力先パス(/storage/emulated/0/Android/data/com.ricoh.livestreaming.sample/files/logs)が存在しない場合はここで作成します */
        getExternalFilesDir(null)!!
                .resolve("logs").apply { this.mkdir() }
                .apply {
                    this.resolve("sample").apply { this.mkdir() }
                }
    }
}
