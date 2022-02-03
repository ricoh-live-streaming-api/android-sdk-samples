/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.base

import android.Manifest
import android.content.pm.PackageManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.sample.BuildConfig
import com.ricoh.livestreaming.sample.R
import com.ricoh.livestreaming.theta.ThetaVideoEncoderFactory
import com.ricoh.livestreaming.webrtc.Camera2VideoCapturer
import com.ricoh.livestreaming.webrtc.CodecUtils
import org.slf4j.LoggerFactory
import org.webrtc.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class BaseViewBinding(
        val viewLayout: FrameLayout,
        val roomId: EditText,
        val audioListSpinner: Spinner,
        val cameraListSpinner: Spinner,
        val connectButton: Button
)

abstract class BaseActivity : AppCompatActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(BaseActivity::class.java)
        private val LOCK = Object()
        private const val PERMISSION_REQUEST_CODE = 1813480588
        private val REQUIRED_PERMISSIONS = listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)

    }

    private lateinit var mEgl: EglBase
    private lateinit var mBaseViewBinding: BaseViewBinding
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    var mClient: Client? = null
    val localLSTracks = arrayListOf<LSTrack>()
    var mAppAudioManager: AppAudioManager? = null
    var mAudioListAdapter: AudioListAdapter? = null
    var mVideoCapturer: Camera2VideoCapturer? = null
    var mViewLayoutManager: ViewLayoutManager? = null

    override fun onResume() {
        super.onResume()

        // Grant permission(s)
        val notGrantedPermissions = REQUIRED_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (notGrantedPermissions.isNotEmpty()) {
            requestPermissions(notGrantedPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = REQUIRED_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (deniedPermissions.isNotEmpty()) {
                Toast.makeText(applicationContext, "Please grant all permission.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    fun setBaseView(baseViewBinding: BaseViewBinding) {
        mBaseViewBinding = baseViewBinding

        mEgl = EglBase.create()
        mViewLayoutManager = ViewLayoutManager(
                applicationContext,
                window,
                mEgl,
                baseViewBinding.viewLayout
        )

        mAudioListAdapter = AudioListAdapter(this)
        mAppAudioManager = AppAudioManager(applicationContext)
        mAppAudioManager!!.start(object : AppAudioManager.AudioManagerEvents {
            override fun onAudioDeviceChanged(
                    selectedAudioDevice: AppAudioManager.AudioDevice?,
                    availableAudioDevices: Set<AppAudioManager.AudioDevice>) {
                mAudioListAdapter!!.clear()
                mAudioListAdapter!!.addAll(availableAudioDevices)
            }
        })
        baseViewBinding.audioListSpinner.adapter = mAudioListAdapter
        baseViewBinding.cameraListSpinner.adapter = CameraListAdapter(this)

        mBaseViewBinding.connectButton.setOnClickListener {
            /** Client インスタンスを生成して connect / disconnect します */
            if (mClient == null) {
                createClient(ClientListener())
                connect()
            } else {
                disconnect()
            }
        }
    }

    /** Client インスタンスの生成を行います */
    private fun createClient(listener: Client.Listener) {
        val eglContext = mEgl.eglBaseContext as EglBase14.Context
        mClient = Client(
                applicationContext,
                eglContext,
                ThetaVideoEncoderFactory(
                        eglContext,
                        listOf(
                                CodecUtils.VIDEO_CODEC_INFO_VP8,
                                CodecUtils.VIDEO_CODEC_INFO_H264,
                                CodecUtils.VIDEO_CODEC_INFO_H264_HIGH_PROFILE
                        )
                )
        ).apply {
            /** この関数でイベントリスナーを設定します */
            setEventListener(listener)
        }
    }

    fun createVideoCapturer(cameraInfo: CameraInfo) {
        val resolution = Config.videoResolution
        mVideoCapturer = Camera2VideoCapturer(applicationContext, cameraInfo.cameraId, resolution.width, resolution.height, 30)
    }

    fun getAudioStream(): MediaStream {
        val constraints = MediaStreamConstraints.Builder()
                .audio(true)
                .build()
        return mClient!!.getUserMedia(constraints)
    }

    fun getVideoStream(): MediaStream {
        val constraints = MediaStreamConstraints.Builder()
            .videoCapturer(mVideoCapturer!!)
            .audio(true)
            .build()
        return mClient!!.getUserMedia(constraints)
    }

    fun isClientOpen(): Boolean {
        return mClient?.state == Client.State.OPEN
    }

    /** connect API を用いてサーバーに接続します */
    private fun connect() = executor.safeSubmit {
        LOGGER.info("Try to connect. RoomType={}", Config.roomType.value.typeStr)

        val roomSpec = RoomSpec(Config.roomType.value)
        val accessToken = JwtAccessToken.createAccessToken(BuildConfig.CLIENT_SECRET, mBaseViewBinding.roomId.text.toString(), roomSpec)

        createVideoCapturer(mBaseViewBinding.cameraListSpinner.selectedItem as CameraInfo)
        val stream = getVideoStream()
        localLSTracks.clear()
        for (track in stream.audioTracks) {
            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("audio_meta_key" to "audio_meta_value"))
                    .build()
            localLSTracks.add(LSTrack(track, stream, trackOption))
        }
        for (track in stream.videoTracks) {
            val trackOption = LSTrackOption.Builder()
                    .meta(mapOf("video_meta_key" to "video_meta_value"))
                    .build()
            localLSTracks.add(LSTrack(track, stream, trackOption))
        }

        val option = Option.Builder()
                .loggingSeverity(Logging.Severity.LS_VERBOSE)
                .meta(mapOf("conn_meta_key" to "conn_meta_value"))
                .signalingURL("wss://signaling.livestreaming.mw.smart-integration.ricoh.com/v1/room")
                .localLSTracks(localLSTracks)
                .sending(SendingOption(
                        SendingVideoOption.Builder()
                                .videoCodecType(SendingVideoOption.VideoCodecType.VP8)
                                .sendingPriority(SendingVideoOption.SendingPriority.HIGH)
                                .maxBitrateKbps(Config.videoBitrate)
                                .build()))
                .iceTransportPolicy(Config.iceTransportPolicy)
                .build()

        mClient!!.connect(
                BuildConfig.CLIENT_ID,
                accessToken,
                option)
    }

    /** disconnect API を利用してサーバーから切断します */
    private fun disconnect() = executor.safeSubmit {
        mClient!!.disconnect()
    }

    private fun ExecutorService.safeSubmit(action: () -> Unit): Future<*> {
        return submit {
            try {
                action()
            } catch (e: Exception) {
                LOGGER.error("Uncaught Exception in Executor", e)
            }
        }
    }

    /**
     * ここから Client インスタンスへのリスナー設定サンプル
     */
    open fun eventOnConnecting() {
        runOnUiThread {
            mBaseViewBinding.connectButton.text = getString(R.string.connecting)
            mBaseViewBinding.connectButton.isEnabled = false
            mBaseViewBinding.audioListSpinner.isEnabled = false
            mBaseViewBinding.cameraListSpinner.isEnabled = false
        }
    }
    open fun eventOnOpen() {
        runOnUiThread {
            mBaseViewBinding.connectButton.text = getString(R.string.disconnect)
            mBaseViewBinding.connectButton.isEnabled = true
        }
    }
    open fun eventOnClosing() {
        runOnUiThread {
            mBaseViewBinding.connectButton.text = getString(R.string.disconnecting)
            mBaseViewBinding.connectButton.isEnabled = false
        }
    }
    open fun eventOnClosed() {
        runOnUiThread {
            mBaseViewBinding.connectButton.text = getString(R.string.connect)
            mBaseViewBinding.connectButton.isEnabled = true
            mBaseViewBinding.audioListSpinner.isEnabled = true
            mBaseViewBinding.cameraListSpinner.isEnabled = true
        }
    }
    open fun eventOnAddRemoteConnection(connectionId: String, metadata: Map<String, Any>) { /* Nothing to do. */ }
    open fun eventOnUpdateRemoteConnection(connectionId: String, metadata: Map<String, Any>) { /* Nothing to do. */ }
    open fun eventOnUpdateRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>) { /* Nothing to do. */ }
    open fun eventOnUpdateMute(connectionId: String, stream: MediaStream, track: MediaStreamTrack, muteType: MuteType) { /* Nothing to do. */ }

    inner class ClientListener : Client.Listener {

        override fun onConnecting() {
            LOGGER.debug("Client#onConnecting")

            eventOnConnecting()
        }

        override fun onOpen() {
            LOGGER.debug("Client#onOpen")

            mVideoCapturer!!.start()
            eventOnOpen()
        }

        override fun onClosing() {
            LOGGER.debug("Client#onClosing")

            eventOnClosing()
        }

        override fun onClosed() {
            LOGGER.debug("Client#onClosed")

            synchronized(LOCK) {
                mAppAudioManager!!.stop()

                mVideoCapturer!!.stop()
                mVideoCapturer!!.release()
                mVideoCapturer = null

                mClient!!.setEventListener(null)
                mClient = null
            }

            eventOnClosed()
        }

        override fun onAddLocalTrack(track: MediaStreamTrack, stream: MediaStream) {
            LOGGER.debug("Client#onAddLocalTrack({})", track.id())

            if (track is VideoTrack) {
                runOnUiThread {
                    mViewLayoutManager!!.addLocalTrack(track)
                }
            }
        }

        override fun onAddRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onAddRemoteConnection(connectionId = ${connectionId})")

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            eventOnAddRemoteConnection(connectionId, metadata)
        }

        override fun onRemoveRemoteConnection(connectionId: String, metadata: Map<String, Any>, mediaStreamTracks: List<MediaStreamTrack>) {
            LOGGER.debug("Client#onRemoveRemoteConnection(connectionId = ${connectionId})")

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            for (mediaStreamTrack in mediaStreamTracks) {
                LOGGER.debug("mediaStreamTrack={}", mediaStreamTrack)
            }

        }

        override fun onAddRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>, muteType: MuteType) {
            LOGGER.debug("Client#onAddRemoteTrack({} {}, {}, {})", connectionId, stream.id, track.id(), muteType)

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            if (track is VideoTrack) {
                runOnUiThread {
                    mViewLayoutManager!!.addRemoteTrack(connectionId, track)
                }
            }
        }

        override fun onUpdateRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onUpdateRemoteConnection(connectionId = ${connectionId})")

            eventOnUpdateRemoteConnection(connectionId, metadata)
        }

        override fun onUpdateRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onUpdateRemoteTrack({} {}, {})", connectionId, stream.id, track.id())

            eventOnUpdateRemoteTrack(connectionId, stream, track, metadata)
        }

        override fun onUpdateMute(connectionId: String, stream: MediaStream, track: MediaStreamTrack, muteType: MuteType) {
            LOGGER.debug("Client#onUpdateMute({} {}, {}, {})", connectionId, stream.id, track.id(), muteType)

            eventOnUpdateMute(connectionId, stream, track, muteType)
        }

        override fun onChangeStability(connectionId: String, stability: Stability) {
            LOGGER.debug("Client#onChangeStability({}, {})", connectionId, stability)
        }

        override fun onError(error: SDKErrorEvent) {
            LOGGER.error("Client#onError({}:{}:{}:{})", error.detail.type, error.detail.code, error.detail.error, error.toReportString())
        }
    }
}
