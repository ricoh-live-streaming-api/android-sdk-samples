/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.base

import com.ricoh.livestreaming.IceTransportPolicy

/**
 * Configuration class. Settings are as follows:
 */
object Config {
    var roomId: String = ""
    var videoBitrate: Int = 2000
    var roomType: RoomType = RoomType("SFU")
    var iceTransportPolicy: IceTransportPolicy = if (roomType.value == RoomSpec.RoomType.P2P_TURN) IceTransportPolicy.RELAY else IceTransportPolicy.ALL
    var videoResolution: VideoResolution = VideoResolution(1920, 1080)

    class RoomType(type: String) {
        val value = when (type) {
            "P2P" -> RoomSpec.RoomType.P2P
            "P2P_TURN" -> RoomSpec.RoomType.P2P_TURN
            "SFU" -> RoomSpec.RoomType.SFU
            else -> RoomSpec.RoomType.SFU
        }
    }
    class VideoResolution(var width: Int, var height: Int)
}
