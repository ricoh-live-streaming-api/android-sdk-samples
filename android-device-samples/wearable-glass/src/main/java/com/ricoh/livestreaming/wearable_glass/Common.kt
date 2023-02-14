/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.wearable_glass

import android.annotation.SuppressLint
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Create log file.
 * Example path: /storage/emulated/0/Android/data/{app_package_name}/files/logs/{date}T{time}.log
 */
@SuppressLint("SimpleDateFormat")
fun Context.createLogFile(): File {
    val df = SimpleDateFormat("yyyyMMdd'T'HHmm")
    val timestamp = df.format(Date())
    return getExternalFilesDir(null)!!
            .resolve("logs")
            .resolve("stats")
            .resolve("stats_$timestamp.log")
}
