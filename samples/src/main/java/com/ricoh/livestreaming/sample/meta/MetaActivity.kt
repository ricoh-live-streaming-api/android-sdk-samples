/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.meta

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.EditText
import com.google.gson.Gson
import com.ricoh.livestreaming.SDKError
import com.ricoh.livestreaming.sample.R
import com.ricoh.livestreaming.sample.base.BaseActivity
import com.ricoh.livestreaming.sample.base.BaseViewBinding
import com.ricoh.livestreaming.sample.base.RoomSpec
import com.ricoh.livestreaming.sample.databinding.MetaBinding
import org.slf4j.LoggerFactory
import org.webrtc.AudioTrack
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.VideoTrack

class MetaActivity : BaseActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MetaActivity::class.java)
    }
    private lateinit var mViewBinding: MetaBinding

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewBinding = MetaBinding.inflate(layoutInflater)
        setContentView(mViewBinding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        supportActionBar!!.hide()

        setBaseView(BaseViewBinding(
                mViewBinding.viewsLayout.viewLayout,
                mViewBinding.controlsLayout.roomIdText,
                mViewBinding.controlsLayout.audioListSpinner,
                mViewBinding.controlsLayout.cameraListSpinner,
                mViewBinding.controlsLayout.connectButton,
                mViewBinding.controlsLayout.roomTypeSpinner
        ), "AndroidAPISamplesMeta")

        /**
         * ここから Meta サンプル
         */
        mViewBinding.updateConnectionMetaButton.setOnClickListener {
            if (isClientOpen()) {
                try {
                    /** 新しいメタデータの Map を指定して updateMeta API を利用します。 */
                    this.mClient?.updateMeta(getMetadata(mViewBinding.updateConnectionMetaEdit)!!)
                } catch (e: SDKError) {
                    LOGGER.error("Failed to update connection metadata.{}", e.toReportString())
                }
            }
        }
        mViewBinding.updateAudioTrackMetaButton.setOnClickListener {
            if (isClientOpen()) {
                try {
                    /** 対象の Audio トラックと、新しいメタデータの Map を指定して updateTrackMeta API を利用します。 */
                    this.mClient?.updateTrackMeta(
                            this.localLSTracks.find { it.mediaStreamTrack is AudioTrack }!!,
                            getMetadata(mViewBinding.updateAudioTrackMetaEdit)!!
                    )
                } catch (e: SDKError) {
                    LOGGER.error("Failed to update track metadata.{}", e.toReportString())
                }
            }
        }
        mViewBinding.updateVideoTrackMetaButton.setOnClickListener {
            if (isClientOpen()) {
                try {
                    /** 対象の Video トラックと、新しいメタデータの Map を指定して updateTrackMeta API を利用します。 */
                    this.mClient?.updateTrackMeta(
                            this.localLSTracks.find { it.mediaStreamTrack is VideoTrack }!!,
                            getMetadata(mViewBinding.updateVideoTrackMetaEdit)!!
                    )
                } catch (e: SDKError) {
                    LOGGER.error("Failed to update track metadata.{}", e.toReportString())
                }
            }
        }
    }

    /** メタデータは Map で API に渡す必要があるため、変換処理が必要です。 */
    private fun getMetadata(targetEditText: EditText): Map<String, Any>? {
        return Gson().fromJson<Map<String, Any>>(targetEditText.text.toString(), Map::class.java)
    }

    /** ここから Client.Listener へのイベントハンドラを記載します。 */

    /** 接続中の相手クライアントが updateMeta したときに発火するイベント処理です。 */
    @SuppressLint("SetTextI18n")
    override fun eventOnUpdateRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
        LOGGER.info("Remote client updated connection metadata.")
        for ((key, value) in metadata) {
            LOGGER.info("({}: {})", key, value)
            runOnUiThread { mViewBinding.updateConnectionMetaText.text = "{$key:$value}" }
        }
    }
    /** 【SFU接続時のみ】接続中の相手クライアントが updateTrackMeta したときに発火するイベント処理です。 */
    @SuppressLint("SetTextI18n")
    override fun eventOnUpdateRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>) {
        LOGGER.info("Remote client updated track metadata.")
        for ((key, value) in metadata) {
            LOGGER.info("({}: {})", key, value)
            val targetTextView = if (track is AudioTrack) mViewBinding.updateAudioTrackMetaText else mViewBinding.updateVideoTrackMetaText
            runOnUiThread { targetTextView.text = "{$key:$value}" }
        }
    }

    override fun eventOnConnecting() {
        super.eventOnConnecting()
        runOnUiThread {
            if (mViewBinding.controlsLayout.roomTypeSpinner.selectedItem == RoomSpec.RoomType.P2P ||
                    mViewBinding.controlsLayout.roomTypeSpinner.selectedItem == RoomSpec.RoomType.P2P_TURN) {
                mViewBinding.updateAudioTrackMetaButton.isEnabled = false
                mViewBinding.updateVideoTrackMetaButton.isEnabled = false
            }
        }
    }

    override fun eventOnClosed() {
        super.eventOnClosed()
        runOnUiThread {
            mViewBinding.updateAudioTrackMetaButton.isEnabled = true
            mViewBinding.updateVideoTrackMetaButton.isEnabled = true
        }
    }
}
