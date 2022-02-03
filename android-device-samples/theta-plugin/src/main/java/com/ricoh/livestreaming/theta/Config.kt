/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.theta

import android.content.Context
import org.webrtc.Logging

/**
 * Configuration class. Settings are as follows:
 * <li>Default Room ID</ili>
 * <li>Log Level</li>
 */
object Config {
    private const val CONFIG_FILE = "config"
    private const val KEY_ROOM_ID = "room_id"
    private const val KEY_LOGGING_SEVERITY = "logging_severity"
    private const val KEY_ROOM_TYPE = "room_type"

    enum class LoggingSeverity(val level: String, val position: Int, val severity: Logging.Severity) {
        VERBOSE("VERBOSE", 0, Logging.Severity.LS_VERBOSE),
        INFO("INFO", 1, Logging.Severity.LS_INFO),
        WARNING("WARNING", 2, Logging.Severity.LS_WARNING),
        ERROR("ERROR", 3, Logging.Severity.LS_ERROR),
        NONE("NONE", 4, Logging.Severity.LS_NONE)
    }

    private val DEFAULT_LOGGING_SEVERITY = LoggingSeverity.INFO

    enum class RoomType(val position: Int, val id: Int, val type: RoomSpec.RoomType) {
        SFU(0, R.id.room_type_sfu, RoomSpec.RoomType.SFU),
        P2P(1, R.id.room_type_p2p, RoomSpec.RoomType.P2P),
        P2P_TURN(2, R.id.room_type_p2p_turn, RoomSpec.RoomType.P2P_TURN),
    }

    private val DEFAULT_ROOM_TYPE = RoomType.SFU

    /** Configuration values    */
    private var roomId = ""
    private var loggingSeverity = "INFO"
    private var roomType = RoomType.SFU.position

    fun load(context: Context) {
        val preferences = context.getSharedPreferences(CONFIG_FILE, Context.MODE_PRIVATE)
        this.roomId = preferences.getString(KEY_ROOM_ID, "").toString()
        if (this.roomId.isEmpty()) this.setRoomId(context, BuildConfig.ROOM_ID)
        this.loggingSeverity = preferences.getString(KEY_LOGGING_SEVERITY, "").toString()
        if (this.loggingSeverity.isEmpty()) this.setLoggingSeverity(context, "INFO")
        this.roomType = preferences.getInt(KEY_ROOM_TYPE, DEFAULT_ROOM_TYPE.position)
    }

    fun getRoomId(): String {
        return this.roomId
    }

    fun setRoomId(context: Context, roomId: String) {
        this.roomId = roomId.let { if (it.isEmpty()) "" else it }
        this.saveString(context, KEY_ROOM_ID, this.roomId)
    }

    fun getSelectedLoggingSeverity(): Int {
        return when (this.loggingSeverity) {
            LoggingSeverity.VERBOSE.level -> LoggingSeverity.VERBOSE.position
            LoggingSeverity.INFO.level -> LoggingSeverity.INFO.position
            LoggingSeverity.WARNING.level -> LoggingSeverity.WARNING.position
            LoggingSeverity.ERROR.level -> LoggingSeverity.ERROR.position
            LoggingSeverity.NONE.level -> LoggingSeverity.NONE.position
            else -> this.DEFAULT_LOGGING_SEVERITY.position
        }
    }

    fun getLoggingSeverity(): Logging.Severity {
        return when (this.loggingSeverity) {
            LoggingSeverity.VERBOSE.level -> LoggingSeverity.VERBOSE.severity
            LoggingSeverity.INFO.level -> LoggingSeverity.INFO.severity
            LoggingSeverity.WARNING.level -> LoggingSeverity.WARNING.severity
            LoggingSeverity.ERROR.level -> LoggingSeverity.ERROR.severity
            LoggingSeverity.NONE.level -> LoggingSeverity.NONE.severity
            else -> this.DEFAULT_LOGGING_SEVERITY.severity
        }
    }

    fun setLoggingSeverity(context: Context, loggingSeverity: String) {
        this.loggingSeverity = loggingSeverity.let { if (it.isEmpty()) this.DEFAULT_LOGGING_SEVERITY.level else it }
        this.saveString(context, KEY_LOGGING_SEVERITY, this.loggingSeverity)
    }

    fun getSelectedRoomTypeID(): Int {
        return when (this.roomType) {
            RoomType.SFU.position -> RoomType.SFU.id
            RoomType.P2P.position -> RoomType.P2P.id
            RoomType.P2P_TURN.position -> RoomType.P2P_TURN.id
            else -> this.DEFAULT_ROOM_TYPE.id
        }
    }

    fun getRoomType(): RoomSpec.RoomType {
        return when (this.roomType) {
            RoomType.SFU.position -> RoomType.SFU.type
            RoomType.P2P.position -> RoomType.P2P.type
            RoomType.P2P_TURN.position -> RoomType.P2P_TURN.type
            else -> this.DEFAULT_ROOM_TYPE.type
        }
    }

    fun setRoomType(context: Context, id: Int) {
        this.roomType = when (id) {
            RoomType.SFU.id -> RoomType.SFU.position
            RoomType.P2P.id -> RoomType.P2P.position
            RoomType.P2P_TURN.id -> RoomType.P2P_TURN.position
            else -> RoomType.SFU.id
        }
        this.saveInt(context, KEY_ROOM_TYPE, roomType)
    }

    private fun saveString(context: Context, key: String, value: String) {
        val preferences = context.getSharedPreferences(CONFIG_FILE, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putString(key, value)
        editor.apply()
    }

    private fun saveInt(context: Context, key: String, value: Int) {
        val preferences = context.getSharedPreferences(CONFIG_FILE, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putInt(key, value)
        editor.apply()
    }
}
