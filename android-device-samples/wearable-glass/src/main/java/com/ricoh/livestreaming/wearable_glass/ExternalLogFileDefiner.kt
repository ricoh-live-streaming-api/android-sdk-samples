/*
 * Copyright 2023 Ricoh Company, Ltd. All rights reserved.
 */
package com.ricoh.livestreaming.wearable_glass

import android.os.Build
import ch.qos.logback.core.PropertyDefinerBase
import ch.qos.logback.core.android.AndroidContextUtil

class ExternalLogFileDefiner : PropertyDefinerBase() {
    override fun getPropertyValue(): String {
        val androidContextUtil = AndroidContextUtil()
        return if (Build.VERSION.SDK_INT >= 29) {
            androidContextUtil.externalStorageDirectoryPath + "/logs/wearable_glass"
        } else {
            androidContextUtil.externalStorageDirectoryPath + "/Android/data/${androidContextUtil.packageName}/files/logs/wearable_glass"
        }

    }
}