/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.base

import android.content.Context
import com.ricoh.livestreaming.IceTransportPolicy

/**
 * Configuration class. Settings are as follows:
 */
object Config {
    var roomId: String = ""
    var videoBitrate: Int = 2000
    var roomType: RoomSpec.RoomType = RoomSpec.RoomType.SFU
    var iceTransportPolicy: IceTransportPolicy = if (roomType == RoomSpec.RoomType.P2P_TURN) IceTransportPolicy.RELAY else IceTransportPolicy.ALL
    var videoResolution: VideoResolution = VideoResolution(1920, 1080)

    class VideoResolution(var width: Int, var height: Int)
}
