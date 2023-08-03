/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */
package com.ricoh.livestreaming.wearable_glass

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Base64
import android.util.Base64.DEFAULT
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.CaptureActivity
import com.ricoh.livestreaming.wearable_glass.databinding.ActivitySettingBinding
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

class SettingActivity : AppCompatActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SettingActivity::class.java)

        private const val ROOM_ID_KEY = "ROOM_ID"
        private const val SEND_RESOLUTION_KEY = "RESOLUTION_KEY"
        private const val BITRATE_KEY = "BITRATE"
        private const val INITIAL_AUDIO_MUTE_KEY = "INITIAL_AUDIO_MUTE"
        private const val PROXY = "PROXY"

        private const val OK_DIALOG = 100
        private const val ERROR_DIALOG = 101
    }

    private var qrScanIntegrator: IntentIntegrator? = null

    /** View Binding */
    private lateinit var mActivitySettingBinding: ActivitySettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mActivitySettingBinding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(mActivitySettingBinding.root)

        qrScanIntegrator = IntentIntegrator(this)
        qrScanIntegrator?.setOrientationLocked(true)
        qrScanIntegrator?.captureActivity = CaptureActivity::class.java

        qrScanIntegrator?.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (result != null) {
            try {
                val charset = StandardCharsets.UTF_8
                val bytes = Base64.decode(result.contents, DEFAULT)

                val json = JSONObject(String(bytes, charset))

                Preference.saveRoomId(applicationContext, json.getString(ROOM_ID_KEY))
                Preference.saveSendResolution(applicationContext, json.getInt(SEND_RESOLUTION_KEY))
                Preference.saveBitrate(applicationContext, json.getInt(BITRATE_KEY))
                if (json.has(PROXY)) {
                    Preference.saveProxy(applicationContext, json.getString(PROXY))
                }
                if (json.has(INITIAL_AUDIO_MUTE_KEY)) {
                    Preference.saveInitialAudioMute(applicationContext, json.getBoolean(INITIAL_AUDIO_MUTE_KEY))
                }
                handler.sendEmptyMessage(OK_DIALOG)
            } catch (e: Exception) {
                LOGGER.error("Failed to setting.", e)
                handler.sendEmptyMessage(ERROR_DIALOG)
            }
        } else {
            finish()
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                OK_DIALOG -> {
                    val title = getString(R.string.information)
                    val message = getString(R.string.setting_completed)
                    val buttonText = getString(R.string.ok_button)
                    ConfirmDialog().apply {
                        arguments = Bundle().apply {
                            putString("title", title)
                            putString("message", message)
                            putString("positiveButtonText", buttonText)
                        }
                        onPositiveButtonClickListener =
                                DialogInterface.OnClickListener { _, _ ->
                                    finish()
                                }
                        onCancelListener = DialogInterface.OnCancelListener {
                            finish()
                        }
                        onDismissListener = DialogInterface.OnDismissListener {
                            finish()
                        }
                    }.show(supportFragmentManager, "setting")
                }
                ERROR_DIALOG -> {
                    val title = getString(R.string.error)
                    val message = getString(R.string.app_setup_failed)
                    val buttonText = getString(R.string.ok_button)

                    ConfirmDialog().apply {
                        arguments = Bundle().apply {
                            putString("title", title)
                            putString("message", message)
                            putString("positiveButtonText", buttonText)
                        }
                        onPositiveButtonClickListener = DialogInterface.OnClickListener { _, _ ->
                            finish()
                        }
                        onCancelListener = DialogInterface.OnCancelListener {
                            finish()
                        }
                        onDismissListener = DialogInterface.OnDismissListener {
                            finish()
                        }
                    }.show(supportFragmentManager, "error")
                }
            }
        }
    }
}
