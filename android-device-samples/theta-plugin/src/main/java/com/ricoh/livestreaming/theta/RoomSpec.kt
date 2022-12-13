/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.theta

class RoomSpec(
        private val roomType: RoomType
) {

    enum class RoomType(val typeStr: String) {
        SFU("sfu"),
        SFU_LARGE("sfu_large"),
        P2P("p2p"),
        P2P_TURN("p2p_turn")
    }

    fun getSpec(): Map<String, Any> {
        val map = HashMap<String, Any>()
        map["type"] = roomType.typeStr
        val mediaControl = HashMap<String, Any>()
        mediaControl["bitrate_reservation_mbps"] = 25
        map["media_control"] = mediaControl

        return map
    }
}
