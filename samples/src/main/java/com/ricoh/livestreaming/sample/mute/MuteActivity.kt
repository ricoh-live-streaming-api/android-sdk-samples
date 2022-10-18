/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.mute

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import com.ricoh.livestreaming.MuteType
import com.ricoh.livestreaming.SDKError
import com.ricoh.livestreaming.sample.R
import com.ricoh.livestreaming.sample.base.BaseActivity
import com.ricoh.livestreaming.sample.base.BaseViewBinding
import com.ricoh.livestreaming.sample.databinding.MuteBinding
import org.slf4j.LoggerFactory
import org.webrtc.AudioTrack
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.VideoTrack

class MuteActivity : BaseActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MuteActivity::class.java)
    }
    private lateinit var mViewBinding: MuteBinding

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewBinding = MuteBinding.inflate(layoutInflater)
        setContentView(mViewBinding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        supportActionBar!!.hide()

        setBaseView(BaseViewBinding(
                mViewBinding.viewsLayout.viewLayout,
                mViewBinding.controlsLayout.roomIdText,
                mViewBinding.controlsLayout.audioListSpinner,
                mViewBinding.controlsLayout.cameraListSpinner,
                mViewBinding.controlsLayout.connectButton
        ), "AndroidAPISamplesMute")

        /**
         * ここから Mute サンプル
         */
        mViewBinding.audioMuteRadio.setOnCheckedChangeListener { _, checkedId ->
            if (isClientOpen()) {
                /** SDK で定義している Enum MuteType を用いて muteType を決定します。 */
                val muteType = when (checkedId) {
                    R.id.audio_unmute -> MuteType.UNMUTE
                    R.id.audio_soft_mute -> MuteType.SOFT_MUTE
                    else -> MuteType.HARD_MUTE
                }
                try {
                    /** ミュート対象の Track と muteType を指定して changeMute API を利用します。 */
                    this.mClient?.changeMute(this.localLSTracks.find { it.mediaStreamTrack is AudioTrack }!!, muteType)
                } catch (e: SDKError) {
                    LOGGER.error("Failed to change mute.{}", e.toReportString())
                }
            }
        }
        mViewBinding.videoMuteRadio.setOnCheckedChangeListener { _, checkedId ->
            if (isClientOpen()) {
                /** SDK で定義している Enum MuteType を用いて muteType を決定します。 */
                val muteType = when (checkedId) {
                    R.id.video_unmute -> MuteType.UNMUTE
                    R.id.video_soft_mute -> MuteType.SOFT_MUTE
                    else -> MuteType.HARD_MUTE
                }
                try {
                    /** ミュート対象の Track と muteType を指定して changeMute API を利用します。 */
                    this.mClient?.changeMute(this.localLSTracks.find { it.mediaStreamTrack is VideoTrack }!!, muteType)
                } catch (e: SDKError) {
                    LOGGER.error("Failed to change mute.{}", e.toReportString())
                }
            }
        }
    }

    /** ここから Client.Listener へのイベントハンドラを記載します。 */

    /** 【SFU接続時のみ】接続中の相手クライアントが changeMute したときに発火するイベント処理はここに実装します。 */
    override fun eventOnUpdateMute(connectionId: String, stream: MediaStream, track: MediaStreamTrack, muteType: MuteType) {
        LOGGER.info("Remote client changed mute type.({})", muteType)
        val targetTextView = if (track is AudioTrack) mViewBinding.updateAudioTrackMuteText else mViewBinding.updateVideoTrackMuteText
        runOnUiThread { targetTextView.text = "$muteType" }
    }
}
