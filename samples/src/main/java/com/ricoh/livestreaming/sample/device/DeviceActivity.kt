/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.device

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import com.ricoh.livestreaming.SDKError
import com.ricoh.livestreaming.sample.R
import com.ricoh.livestreaming.sample.base.AppAudioManager
import com.ricoh.livestreaming.sample.base.BaseActivity
import com.ricoh.livestreaming.sample.base.BaseViewBinding
import com.ricoh.livestreaming.sample.base.CameraInfo
import com.ricoh.livestreaming.sample.databinding.DeviceBinding
import org.slf4j.LoggerFactory
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack

class DeviceActivity : BaseActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeviceActivity::class.java)
    }
    private lateinit var mViewBinding: DeviceBinding

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewBinding = DeviceBinding.inflate(layoutInflater)
        setContentView(mViewBinding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        supportActionBar!!.hide()

        setBaseView(BaseViewBinding(
                mViewBinding.viewsLayout.viewLayout,
                mViewBinding.controlsLayout.roomIdText,
                mViewBinding.controlsLayout.audioListSpinner,
                mViewBinding.controlsLayout.cameraListSpinner,
                mViewBinding.controlsLayout.connectButton
        ), "AndroidAPISamplesDevice")

        /**
         * ここから Device サンプル
         */
        mViewBinding.controlsLayout.audioListSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { /** このサンプルでは何もしません。 */ }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val audioDevice = mViewBinding.controlsLayout.audioListSpinner.selectedItem as AppAudioManager.AudioDevice
                LOGGER.info("onItemSelected: ${audioDevice.deviceName}")
                if (isClientOpen()) {
                    try {
                        /** 使用するマイクをスピナーで指定したものに更新します。 */
                        mAppAudioManager!!.selectAudioDevice(audioDevice)
                        /** Audio stream を取得します。 */
                        val stream = getAudioStream()
                        /** 入れ替え対象の Track と新しい Track を指定して replaceMediaStreamTrack API を利用します。 */
                        mClient!!.replaceMediaStreamTrack(localLSTracks.find { it.mediaStreamTrack is AudioTrack }!!, stream.audioTracks[0])
                    } catch (e: SDKError) {
                        LOGGER.error("Failed to replace media stream.{}", e.toReportString())
                    }
                }
            }
        }
        mViewBinding.controlsLayout.cameraListSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { /** このサンプルでは何もしません。 */ }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val cameraInfo: CameraInfo = mViewBinding.controlsLayout.cameraListSpinner.selectedItem as CameraInfo
                LOGGER.info("onItemSelected: ${cameraInfo.getName()}")
                if (isClientOpen()) {
                    try {
                        /** これまで使用してきたカメラの映像キャプチャを停止して開放します。 */
                        mVideoCapturer!!.stop()
                        mVideoCapturer!!.release()
                        /** 使用するカメラをスピナーで指定したものに更新してキャプチャーインスタンスを再生成します。 */
                        createVideoCapturer(cameraInfo)
                        /** Video stream を取得します。 */
                        val stream = getVideoStream()
                        /** 入れ替え対象の Track と新しい Track を指定して replaceMediaStreamTrack API を利用します。 */
                        mClient!!.replaceMediaStreamTrack(localLSTracks.find { it.mediaStreamTrack is VideoTrack }!!, stream.videoTracks[0])
                        /** 入れ替え後にキャプチャーを開始してシンクします。 */
                        mVideoCapturer!!.start()
                        stream.videoTracks[0].addSink(mViewLayoutManager!!.getLocalView())
                    } catch (e: SDKError) {
                        LOGGER.error("Failed to replace media stream.{}", e.toReportString())
                    }
                }
            }
        }
    }

    /** ここから Client.Listener へのイベントハンドラを記載します。 */

    /** 接続中・接続完了・切断中・切断完了のそれぞれへの状態変化時に発火するイベント処理です。 */
    override fun eventOnConnecting() {
        runOnUiThread {
            mViewBinding.controlsLayout.connectButton.text = getString(R.string.connecting)
            mViewBinding.controlsLayout.connectButton.isEnabled = false
            mViewBinding.controlsLayout.audioListSpinner.isEnabled = false
            mViewBinding.controlsLayout.cameraListSpinner.isEnabled = false
        }
    }
    override fun eventOnOpen() {
        runOnUiThread {
            mViewBinding.controlsLayout.connectButton.text = getString(R.string.disconnect)
            mViewBinding.controlsLayout.connectButton.isEnabled = true
            mViewBinding.controlsLayout.audioListSpinner.isEnabled = true
            mViewBinding.controlsLayout.cameraListSpinner.isEnabled = true
        }
    }
    override fun eventOnClosing() {
        runOnUiThread {
            mViewBinding.controlsLayout.connectButton.text = getString(R.string.disconnecting)
            mViewBinding.controlsLayout.connectButton.isEnabled = false
            mViewBinding.controlsLayout.audioListSpinner.isEnabled = false
            mViewBinding.controlsLayout.cameraListSpinner.isEnabled = false
        }
    }
    override fun eventOnClosed() {
        runOnUiThread {
            mViewBinding.controlsLayout.connectButton.text = getString(R.string.connect)
            mViewBinding.controlsLayout.connectButton.isEnabled = true
            mViewBinding.controlsLayout.audioListSpinner.isEnabled = true
            mViewBinding.controlsLayout.cameraListSpinner.isEnabled = true
        }
    }
}
