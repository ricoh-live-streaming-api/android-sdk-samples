/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */
package com.ricoh.livestreaming.setting_app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.ricoh.livestreaming.setting_app.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity(), TextWatcher {
    companion object {
        const val SSID_KEY = "SSID"
        const val SECURITY_KEY = "SECURITY"
        const val PASSWORD_KEY = "PASSWORD"
        const val ROOM_ID_KEY = "ROOM_ID"
        const val SEND_RESOLUTION_KEY = "RESOLUTION_KEY"
        const val RESOLUTION_4K = 0
        const val RESOLUTION_2K = 1
        const val BITRATE_KEY = "BITRATE"
        const val INITIAL_AUDIO_MUTE_KEY = "INITIAL_AUDIO_MUTE"

        const val INTENT_PARAM = "param"

        const val BITRATE_DEFAULT_VALUE = "7000"    // 7Mbps
        const val RESOLUTION_DEFAULT_VALUE = RESOLUTION_4K
        const val INITIAL_AUDIO_MUTE_DEFAULT_VALUE = false
    }
    
    /** View Binding */
    private lateinit var mActivityMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mActivityMainBinding.root)

        mActivityMainBinding.ssidEdit.text = Editable.Factory.getInstance().newEditable(getSavedStringData(SSID_KEY))
        mActivityMainBinding.passwordEdit.text = Editable.Factory.getInstance().newEditable(getSavedStringData(PASSWORD_KEY))
        mActivityMainBinding.roomIdEdit.text = Editable.Factory.getInstance().newEditable(getSavedStringData(ROOM_ID_KEY))
        mActivityMainBinding.bitrateEdit.text = Editable.Factory.getInstance().newEditable(getSavedStringData(BITRATE_KEY, BITRATE_DEFAULT_VALUE))
        mActivityMainBinding.ssidEdit.addTextChangedListener(this)
        mActivityMainBinding.passwordEdit.addTextChangedListener(this)
        mActivityMainBinding.roomIdEdit.addTextChangedListener(this)
        mActivityMainBinding.bitrateEdit.addTextChangedListener(this)

        mActivityMainBinding.securitySpinner.setSelection(getSavedIntData(SECURITY_KEY))
        mActivityMainBinding.securitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
            ) {
                if (position == 0) {
                    mActivityMainBinding.passwordLayout.visibility = GONE
                } else {
                    mActivityMainBinding.passwordLayout.visibility = VISIBLE
                }
                mActivityMainBinding.createButton.isEnabled = isButtonEnabled()
            }
        }

        mActivityMainBinding.createButton.setOnClickListener {
            saveData(SSID_KEY, mActivityMainBinding.ssidEdit.text.toString())
            saveData(PASSWORD_KEY, mActivityMainBinding.passwordEdit.text.toString())
            saveData(SECURITY_KEY, mActivityMainBinding.securitySpinner.selectedItemId.toInt())
            saveData(ROOM_ID_KEY, mActivityMainBinding.roomIdEdit.text.toString())
            val resolution = if (mActivityMainBinding.sendResolutionGroup.checkedRadioButtonId == R.id.send_2k_radio) {
                RESOLUTION_2K
            } else {
                RESOLUTION_4K
            }
            saveData(SEND_RESOLUTION_KEY, resolution)
            saveData(BITRATE_KEY, mActivityMainBinding.bitrateEdit.text.toString())
            saveData(INITIAL_AUDIO_MUTE_KEY, mActivityMainBinding.initialAudioMute.isChecked)

            val json = JSONObject().apply {
                put(SSID_KEY, mActivityMainBinding.ssidEdit.text.toString())
                put(PASSWORD_KEY, mActivityMainBinding.passwordEdit.text.toString())
                put(SECURITY_KEY, mActivityMainBinding.securitySpinner.selectedItemId.toInt())
                put(ROOM_ID_KEY, mActivityMainBinding.roomIdEdit.text.toString())
                put(SEND_RESOLUTION_KEY, resolution)
                put(BITRATE_KEY, Integer.parseInt(mActivityMainBinding.bitrateEdit.text.toString()))
                put(INITIAL_AUDIO_MUTE_KEY, mActivityMainBinding.initialAudioMute.isChecked)
            }
            val intent = Intent(applicationContext, QRCodeActivity::class.java)
            intent.putExtra(INTENT_PARAM, json.toString())
            startActivity(intent)
        }

        mActivityMainBinding.showPassword.setOnCheckedChangeListener { button, isChecked ->
            val pos = mActivityMainBinding.passwordEdit.selectionEnd
            if (isChecked) {
                mActivityMainBinding.passwordEdit.inputType = InputType.TYPE_CLASS_TEXT +
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                mActivityMainBinding.passwordEdit.inputType = InputType.TYPE_CLASS_TEXT +
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            if (pos > 0) {
                mActivityMainBinding.passwordEdit.setSelection(pos)
            }
        }

        when (getSavedIntData(SEND_RESOLUTION_KEY, RESOLUTION_DEFAULT_VALUE)) {
            RESOLUTION_4K -> {
                mActivityMainBinding.sendResolutionGroup.check(R.id.send_4k_radio)
            }
            RESOLUTION_2K -> {
                mActivityMainBinding.sendResolutionGroup.check(R.id.send_2k_radio)
            }
        }

        mActivityMainBinding.initialAudioMute.isChecked =
                getSavedBooleanData(INITIAL_AUDIO_MUTE_KEY, INITIAL_AUDIO_MUTE_DEFAULT_VALUE)
    }

    override fun onResume() {
        super.onResume()
        mActivityMainBinding.createButton.isEnabled = isButtonEnabled()
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        Log.d("MainActivity", "onTextChanged")
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        Log.d("MainActivity", "beforeTextChanged")
    }

    override fun afterTextChanged(s: Editable?) {
        Log.d("MainActivity", "afterTextChanged")
        mActivityMainBinding.createButton.isEnabled = isButtonEnabled()
    }


    private fun isButtonEnabled(): Boolean {
        if (mActivityMainBinding.ssidEdit.text.isEmpty() || mActivityMainBinding.roomIdEdit.text.isEmpty() || mActivityMainBinding.bitrateEdit.text.isEmpty()) {
            return false
        }

        if (mActivityMainBinding.securitySpinner.selectedItemId != 0L && mActivityMainBinding.passwordEdit.text.isEmpty()) {
            return false
        }

        return true
    }

    private fun getSavedStringData(key: String, defaultValue: String = ""): String {
        val pref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return pref.getString(key, defaultValue)!!
    }

    private fun saveData(key: String, data: String) {
        val edit = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        edit.putString(key, data)
        edit.apply()
    }

    private fun getSavedIntData(key: String, defaultValue: Int = 0): Int {
        val pref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return pref.getInt(key, defaultValue)
    }

    private fun saveData(key: String, data: Int) {
        val edit = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        edit.putInt(key, data)
        edit.apply()
    }

    private fun getSavedBooleanData(key: String, defaultValue: Boolean = false): Boolean {
        val pref = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return pref.getBoolean(key, defaultValue)
    }

    private fun saveData(key: String, data: Boolean) {
        val edit = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        edit.putBoolean(key, data)
        edit.apply()
    }
}
