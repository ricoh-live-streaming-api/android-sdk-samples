/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.base

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.slf4j.LoggerFactory


class AppAudioManager(private val context: Context) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AppAudioManager::class.java)
    }

    interface AudioManagerEvents {
        // Callback fired once audio device is changed or list of available audio devices changed.
        fun onAudioDeviceChanged(
                selectedAudioDevice: AudioDevice?, availableAudioDevices: Set<AudioDevice>)
    }

    enum class AudioDevice(val deviceName: String) {
        SPEAKER_PHONE("Speaker"),
        WIRED_HEADSET("Headset"),
    }

    private enum class AudioManagerState {
        UNINITIALIZED,
        RUNNING
    }

    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioManagerEvents: AudioManagerEvents? = null
    private var hasWiredHeadset: Boolean
    private var savedIsSpeakerPhoneOn: Boolean
    private var state: AudioManagerState
    private var audioDevices: MutableSet<AudioDevice> = mutableSetOf()
    private var userSelectedAudioDevice: AudioDevice
    private var selectedAudioDevice: AudioDevice

    init {
        hasWiredHeadset = hasWiredHeadset()
        savedIsSpeakerPhoneOn = audioManager.isMicrophoneMute
        state = AudioManagerState.UNINITIALIZED
        userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE
        selectedAudioDevice = AudioDevice.SPEAKER_PHONE
    }

    fun start(audioManagerEvents: AudioManagerEvents) {
        if (state == AudioManagerState.RUNNING) {
            LOGGER.info("AudioManager is already active.")
            return
        }

        this.audioManagerEvents = audioManagerEvents
        state = AudioManagerState.RUNNING
        audioDevices.clear()
        hasWiredHeadset = hasWiredHeadset()
        savedIsSpeakerPhoneOn = audioManager.isMicrophoneMute
        context.registerReceiver(wiredHeadSetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        updateAudioDeviceState()
    }

    fun stop() {
        if (state == AudioManagerState.UNINITIALIZED) {
            LOGGER.info("AudioManager is already stop.")
            return
        }
        audioManagerEvents = null
        state = AudioManagerState.UNINITIALIZED
        context.unregisterReceiver(wiredHeadSetReceiver)
        setSpeakerphoneOn(savedIsSpeakerPhoneOn)
    }

    fun selectAudioDevice(device: AudioDevice) {
        userSelectedAudioDevice = device
        updateAudioDeviceState()
    }

    private val wiredHeadSetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    hasWiredHeadset = intent.getIntExtra("state", 0) > 0
                    LOGGER.debug("ACTION_HEADSET_PLUG: {}", hasWiredHeadset)
                    updateAudioDeviceState()
                }
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun hasWiredHeadset(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        for (device in devices) {
            val type = device.type
            if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                LOGGER.debug("hasWiredHeadset: found wired headset")
                return true
            } else if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
                LOGGER.debug("hasWiredHeadset: found USB audio device")
                return true
            }
        }
        return false
    }

    private fun setSpeakerphoneOn(on: Boolean) {
        if (audioManager.isSpeakerphoneOn == on) {
            return
        }

        audioManager.isSpeakerphoneOn = on
    }

    private fun updateAudioDeviceState() {
        val newAudioDevices = mutableSetOf<AudioDevice>()

        newAudioDevices.add(AudioDevice.SPEAKER_PHONE)
        if (hasWiredHeadset) {
            newAudioDevices.add(AudioDevice.WIRED_HEADSET)
        }

        val audioDeviceSetUpdated = audioDevices != newAudioDevices
        audioDevices = newAudioDevices

        val newAudioDevice = if (userSelectedAudioDevice == AudioDevice.WIRED_HEADSET && hasWiredHeadset) {
            AudioDevice.WIRED_HEADSET
        } else {
            AudioDevice.SPEAKER_PHONE
        }

        if (newAudioDevice != selectedAudioDevice || audioDeviceSetUpdated) {
            selectedAudioDevice = newAudioDevice
            setSpeakerphoneOn(selectedAudioDevice == AudioDevice.SPEAKER_PHONE)

            audioManagerEvents?.onAudioDeviceChanged(newAudioDevice, audioDevices)
        }
    }
}
