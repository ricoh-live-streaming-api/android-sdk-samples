/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.theta

import org.webrtc.RTCStats
import org.webrtc.RTCStatsReport
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

//
// Please refer the following page for more details about RTCStatsReport.
// https://www.w3.org/TR/webrtc-stats/
//
class RTCStatsLogger(
        file: File,
        private val statsFilter: (RTCStats) -> Boolean = { stats ->
            stats.type == "candidate-pair" || stats.type == "outbound-rtp" ||
                    stats.type == "inbound-rtp" || stats.type == "remote-inbound-rtp" || stats.type == "track" || stats.type == "sender" || stats.type == "media-source"
        }
) : Closeable {
    private val out = BufferedOutputStream(file.outputStream()).writer()

    fun log(connectionId: String, report: RTCStatsReport) {
        report.statsMap.values.stream()
                .filter(statsFilter)
                .forEach { stats -> out.append(stats.toLTSV(connectionId)) }
    }

    override fun close() {
        out.flush()
        out.close()
    }
}

fun RTCStats.toLTSV(connectionId: String): String {
    val sb = StringBuilder()
    val df = SimpleDateFormat("MM-dd HH:mm:ss.SSS")
    sb.append(df.format(Date(timestampUs.toLong() / 1000))).append("\t")
    sb.append("connectionId:$connectionId").append("\t")
    sb.append("type:$type")
    members.entries.forEach {
        if (sb.isNotEmpty()) {
            sb.append("\t")
        }
        sb.append(it.key).append(":").append(it.value)
    }
    sb.append('\n')
    return sb.toString()
}
