package com.ricoh.livestreaming.theta

import android.app.Application

class InitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        /** ログ出力先パス(/storage/emulated/0/Android/data/com.ricoh.livestreaming.theta/files/logs)が存在しない場合はここで作成します */
        getExternalFilesDir(null)!!
                .resolve("logs").apply { this.mkdir() }
                .apply {
                    this.resolve("libwebrtc").apply { this.mkdir() }
                    this.resolve("theta").apply { this.mkdir() }
                    this.resolve("stats").apply { this.mkdir() }
                }

        // Load configurations.
        Config.load(this.applicationContext)
    }
}
