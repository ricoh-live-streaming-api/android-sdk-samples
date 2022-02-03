/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.wearable_glass

import android.content.Context
import androidx.preference.PreferenceManager

class Preference {
    companion object {
        private const val ROOM_ID_KEY = "room_id"
        private const val SEND_RESOLUTION_KEY = "send_resolution"
        private const val BITRATE_KEY = "bitrate"
        private const val BITRATE_DEFAULT_VALUE = 7_000 // 7Mbps
        private const val INITIAL_AUDIO_MUTE_KEY = "audio_mute"
        private const val INITIAL_AUDIO_MUTE_DEFAULT_VALUE = false

        fun saveRoomId(context: Context, roomId: String) {
            val preference = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = preference.edit()
            editor.putString(ROOM_ID_KEY, roomId)
            editor.apply()
        }

        fun getRoomId(context: Context): String {
            val preference = PreferenceManager.getDefaultSharedPreferences(context)
            return preference.getString(ROOM_ID_KEY, "")!!
        }

        fun saveSendResolution(context: Context, resolution: Int) {
            val preference = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = preference.edit()
            editor.putInt(SEND_RESOLUTION_KEY, resolution)
            editor.apply()
        }

        fun getSendResolution(context: Context): Int {
            val preference = PreferenceManager.getDefaultSharedPreferences(context)
            return preference.getInt(SEND_RESOLUTION_KEY, 0)
        }

        fun saveBitrate(context: Context, bitrate: Int) {
            val preference = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = preference.edit()
            editor.putInt(BITRATE_KEY, bitrate)
            editor.apply()
        }

        fun getVideoBitrate(context: Context): Int {
            val preference = PreferenceManager.getDefaultSharedPreferences(context)
            return preference.getInt(BITRATE_KEY, BITRATE_DEFAULT_VALUE)
        }

        fun saveInitialAudioMute(context: Context, audioMute: Boolean) {
            val preference = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = preference.edit()
            editor.putBoolean(INITIAL_AUDIO_MUTE_KEY, audioMute)
            editor.apply()
        }

        fun isInitialAudioMute(context: Context): Boolean {
            val preference = PreferenceManager.getDefaultSharedPreferences(context)
            return preference.getBoolean(INITIAL_AUDIO_MUTE_KEY, INITIAL_AUDIO_MUTE_DEFAULT_VALUE)
        }
    }
}
