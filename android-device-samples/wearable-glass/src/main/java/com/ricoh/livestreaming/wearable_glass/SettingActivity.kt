/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */
package com.ricoh.livestreaming.wearable_glass

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiConfiguration.AuthAlgorithm
import android.net.wifi.WifiConfiguration.KeyMgmt
import android.net.wifi.WifiManager
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
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

class SettingActivity : AppCompatActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SettingActivity::class.java)

        private const val SSID_KEY = "SSID"
        private const val SECURITY_KEY = "SECURITY"
        private const val PASSWORD_KEY = "PASSWORD"
        private const val ROOM_ID_KEY = "ROOM_ID"
        private const val SEND_RESOLUTION_KEY = "RESOLUTION_KEY"
        private const val BITRATE_KEY = "BITRATE"
        private const val INITIAL_AUDIO_MUTE_KEY = "INITIAL_AUDIO_MUTE"
        private const val PROXY = "PROXY"

        private const val OK_DIALOG = 100
        private const val ERROR_DIALOG = 101

        private fun convertToQuotedString(string: String): String {
            return "\"" + string + "\""
        }
    }

    private var qrScanIntegrator: IntentIntegrator? = null

    /** View Binding */
    private lateinit var mActivitySettingBinding: ActivitySettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mActivitySettingBinding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(mActivitySettingBinding.root)

        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
        }

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
                val config = getWifiConfig(json)

                val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
                var networkId = wifiManager.addNetwork(config)
                if (networkId == -1) {
                    val savedNetworks = wifiManager.configuredNetworks
                    for (network in savedNetworks) {
                        if (config.SSID.equals(network.SSID)) {
                            // already saved another application
                            networkId = network.networkId
                            break;
                        }
                    }
                    if (networkId == -1) {
                        LOGGER.error("Failed to addNetwork()")
                        handler.sendEmptyMessage(ERROR_DIALOG)
                        return
                    }
                }
                if (!wifiManager.enableNetwork(networkId, true)) {
                    LOGGER.error("Failed to enableNetwork()")
                    handler.sendEmptyMessage(ERROR_DIALOG)
                    return
                }
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
                LOGGER.error("Failed to setting.")
                handler.sendEmptyMessage(ERROR_DIALOG)
            }
        } else {
            finish()
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun getWifiConfig(json: JSONObject): WifiConfiguration {
        val ssid = json.getString(SSID_KEY)

        var config = WifiConfiguration()
        config.SSID = convertToQuotedString(ssid)
        config.hiddenSSID = true

        var security = json.getInt(SECURITY_KEY)
        when (security) {
            0 -> {
                // NONE
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
            1 -> {
                // WEP
                val password = json.getString(PASSWORD_KEY)
                var length = password.length

                config.allowedKeyManagement.set(KeyMgmt.NONE)
                config.allowedAuthAlgorithms.set(AuthAlgorithm.OPEN)
                config.allowedAuthAlgorithms.set(AuthAlgorithm.SHARED)
                // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                if ((length == 10 || length == 26 || length == 58) && password.matches("[0-9A-Fa-f]*".toRegex())) {
                    config.wepKeys[0] = password
                } else {
                    config.wepKeys[0] = '"'.toString() + password + '"'.toString()
                }
            }
            2 -> {
                // WPA/WPA2 PSK
                val password = json.getString(PASSWORD_KEY)

                config.allowedKeyManagement.set(KeyMgmt.WPA_PSK)
                if (password.matches("[0-9A-Fa-f]{64}".toRegex())) {
                    config.preSharedKey = password
                } else {
                    config.preSharedKey = '"'.toString() + password + '"'.toString()
                }
            }
            else -> {
                throw JSONException("Invalid security key. security=${security}")
            }
        }
        return config
    }

    val handler = object : Handler(Looper.getMainLooper()) {
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
                                DialogInterface.OnClickListener { dialog, which ->
                                    finish()
                                }
                        onCancelListener = DialogInterface.OnCancelListener { dialog ->
                            finish()
                        }
                        onDismissListener = DialogInterface.OnDismissListener { dialog ->
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
                        onPositiveButtonClickListener = DialogInterface.OnClickListener { dialog, which ->
                            finish()
                        }
                        onCancelListener = DialogInterface.OnCancelListener { dialog ->
                            finish()
                        }
                        onDismissListener = DialogInterface.OnDismissListener { dialog ->
                            finish()
                        }
                    }.show(supportFragmentManager, "error")
                }
            }
        }
    }

}
