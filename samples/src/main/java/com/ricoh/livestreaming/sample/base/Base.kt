/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.base

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
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
        val connectButton: Button,
        val roomTypeSpinner: Spinner
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

    val mConnectOption: ConnectOption = ConnectOption()

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

    fun setBaseView(baseViewBinding: BaseViewBinding, activity: String, roomTypeArray: Array<RoomSpec.RoomType> = RoomSpec.RoomType.values()) {
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
                connect(mConnectOption, activity)
            } else {
                disconnect()
            }
        }

        // Room Type
        baseViewBinding.roomTypeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roomTypeArray)
        mBaseViewBinding.roomTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Config.roomType = mBaseViewBinding.roomTypeSpinner.selectedItem as RoomSpec.RoomType
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
                        CodecUtils.getSupportedEncoderCodecInfo(applicationContext)
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
    private fun connect(connectOption: ConnectOption, prefix: String) = executor.safeSubmit {
        LOGGER.info("Try to connect. RoomType={}", Config.roomType.typeStr)
        
        val roomSpec = RoomSpec(Config.roomType)
        val accessToken = JwtAccessToken.createAccessToken(BuildConfig.CLIENT_SECRET, mBaseViewBinding.roomId.text.toString(), roomSpec, prefix)

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
                                .maxBitrateKbps(connectOption.maxBitrateKbps ?: Config.videoBitrate)
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
            mBaseViewBinding.roomTypeSpinner.isEnabled = false
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
            mBaseViewBinding.roomTypeSpinner.isEnabled = true
        }
    }
    open fun eventOnAddRemoteConnection(connectionId: String, metadata: Map<String, Any>) { /* Nothing to do. */ }
    open fun eventOnUpdateRemoteConnection(connectionId: String, metadata: Map<String, Any>) { /* Nothing to do. */ }
    open fun eventOnUpdateRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>) { /* Nothing to do. */ }
    open fun eventOnUpdateMute(connectionId: String, stream: MediaStream, track: MediaStreamTrack, muteType: MuteType) { /* Nothing to do. */ }

    inner class ClientListener : Client.Listener {

        override fun onConnecting(event: LSConnectingEvent) {
            LOGGER.debug("Client#onConnecting")

            eventOnConnecting()
        }

        override fun onOpen(event: LSOpenEvent) {
            LOGGER.debug("Client#onOpen(accessTokenJson={}, connectionsStatus.video.receiver_existence={})", event.accessTokenJson, event.connectionsStatus.video.receiverExistence)

            mVideoCapturer!!.start()
            eventOnOpen()
        }

        override fun onClosing(event: LSClosingEvent) {
            LOGGER.debug("Client#onClosing")

            eventOnClosing()
        }

        override fun onClosed(event: LSClosedEvent) {
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

        override fun onAddLocalTrack(event: LSAddLocalTrackEvent) {
            LOGGER.debug("Client#onAddLocalTrack({})", event.mediaStreamTrack.id())

            if (event.mediaStreamTrack is VideoTrack) {
                runOnUiThread {
                    mViewLayoutManager!!.addLocalTrack(event.mediaStreamTrack as VideoTrack)
                }
            }
        }

        override fun onAddRemoteConnection(event: LSAddRemoteConnectionEvent) {
            LOGGER.debug("Client#onAddRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            eventOnAddRemoteConnection(event.connectionId, event.meta)
        }

        override fun onRemoveRemoteConnection(event: LSRemoveRemoteConnectionEvent) {
            LOGGER.debug("Client#onRemoveRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            for (mediaStreamTrack in event.mediaStreamTracks) {
                LOGGER.debug("mediaStreamTrack={}", mediaStreamTrack)
            }

            runOnUiThread {
                mViewLayoutManager!!.removeRemoteTrack(event.connectionId)
            }
        }

        override fun onAddRemoteTrack(event: LSAddRemoteTrackEvent) {
            LOGGER.debug("Client#onAddRemoteTrack({} {}, {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id(), event.mute)

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            if (event.mediaStreamTrack is VideoTrack) {
                runOnUiThread {
                    mViewLayoutManager!!.addRemoteTrack(event.connectionId, event.mediaStreamTrack as VideoTrack)
                }
            }
        }

        override fun onUpdateRemoteConnection(event: LSUpdateRemoteConnectionEvent) {
            LOGGER.debug("Client#onUpdateRemoteConnection(connectionId = ${event.connectionId})")

            eventOnUpdateRemoteConnection(event.connectionId, event.meta)
        }

        override fun onUpdateRemoteTrack(event: LSUpdateRemoteTrackEvent) {
            LOGGER.debug("Client#onUpdateRemoteTrack({} {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id())

            eventOnUpdateRemoteTrack(event.connectionId, event.stream, event.mediaStreamTrack, event.meta)
        }

        override fun onUpdateConnectionsStatus(event: LSUpdateConnectionsStatusEvent) {
            LOGGER.debug("Client#onUpdateConnectionsStatus(connectionsStatus.video.receiver_existence={})", event.connectionsStatus.video.receiverExistence)
        }

        override fun onUpdateMute(event: LSUpdateMuteEvent) {
            LOGGER.debug("Client#onUpdateMute({} {}, {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id(), event.mute)

            eventOnUpdateMute(event.connectionId, event.stream, event.mediaStreamTrack, event.mute)
        }

        override fun onChangeStability(event: LSChangeStabilityEvent) {
            LOGGER.debug("Client#onChangeStability({}, {})", event.connectionId, event.stability)
        }

        override fun onError(error: SDKErrorEvent) {
            LOGGER.error("Client#onError({}:{}:{}:{})", error.detail.type, error.detail.code, error.detail.error, error.toReportString())
        }
    }
}
