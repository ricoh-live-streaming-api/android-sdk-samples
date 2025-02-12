/*
 * Copyright 2023 Ricoh Company, Ltd. All rights reserved.
 */
package com.ricoh.livestreaming.sample.base

import android.os.Build
import ch.qos.logback.core.PropertyDefinerBase
import ch.qos.logback.core.android.AndroidContextUtil

class ExternalLogFileDefiner : PropertyDefinerBase() {
    override fun getPropertyValue(): String {
        val androidContextUtil = AndroidContextUtil()
        return if (Build.VERSION.SDK_INT >= 29) {
            androidContextUtil.externalStorageDirectoryPath + "/logs/sample"
        } else {
            androidContextUtil.externalStorageDirectoryPath + "/Android/data/${androidContextUtil.packageName}/files/logs/sample"
        }

    }
}