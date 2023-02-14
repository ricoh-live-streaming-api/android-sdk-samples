package com.ricoh.livestreaming.wearable_glass

import android.app.Application

class InitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        /** ログ出力先パス(/storage/emulated/0/Android/data/com.ricoh.livestreaming.wearable_glass/files/logs)が存在しない場合はここで作成します */
        getExternalFilesDir(null)!!
                .resolve("logs").apply { this.mkdir() }
                .apply {
                    this.resolve("libwebrtc").apply { this.mkdir() }
                    this.resolve("wearable_glass").apply { this.mkdir() }
                    this.resolve("stats").apply { this.mkdir() }
                }
    }
}
