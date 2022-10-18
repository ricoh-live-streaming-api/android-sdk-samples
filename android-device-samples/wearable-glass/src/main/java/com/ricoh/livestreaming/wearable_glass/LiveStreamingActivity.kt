/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.wearable_glass

import android.content.Context
import android.content.DialogInterface
import android.media.AudioManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.KeyEvent.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.theta.ThetaVideoEncoderFactory
import com.ricoh.livestreaming.wearable_glass.databinding.ActivityLiveStreamingBinding
import com.ricoh.livestreaming.webrtc.Camera2VideoCapturer
import com.ricoh.livestreaming.webrtc.CodecUtils
import org.slf4j.LoggerFactory
import org.webrtc.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class LiveStreamingActivity : AppCompatActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(LiveStreamingActivity::class.java)
        private val LOCK = Object()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var mEgl: EglBase? = null
    private var mClient: Client? = null
    private var mRtcStatsLogger: RTCStatsLogger? = null
    private var isDialogShowing: Boolean = false
    private var isFinishKeyPressed: Boolean = false
    private var mKeyEventTimer: CountDownTimer? = null
    private var mCapturer: Camera2VideoCapturer? = null
    private var mStatsTimer: Timer? = null

    private enum class CaptureFormat(val textResourceId: Int, val width: Int, val height: Int, val framerate: Int) {
        CAPTURE_4K(R.string.send_4k, 3840, 2160, 15),
        CAPTURE_2K(R.string.send_2k, 1920, 1080, 30)
    }

    private var mCurrentCaptureFormat: CaptureFormat = CaptureFormat.CAPTURE_4K

    private var mVideoRenderManager: VideoRenderManager? = null

    private var audioMute = MuteType.HARD_MUTE
    private var localLsTracks = arrayListOf<LSTrack>()

    /** View Binding */
    private lateinit var mActivityLiveStreamingBinding: ActivityLiveStreamingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mActivityLiveStreamingBinding = ActivityLiveStreamingBinding.inflate(layoutInflater)
        setContentView(mActivityLiveStreamingBinding.root)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var volume = audioManager.getStreamVolume(AudioManager.STREAM_RING) * audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) / audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        if (volume < audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)) {
            // STREAM_VOICE_CALL minimum volume is 1.
            volume++
        }
        LOGGER.info("volume=$volume")
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volume, 0)

        mCurrentCaptureFormat = if (Preference.getSendResolution(applicationContext) == 1) {
            CaptureFormat.CAPTURE_2K
        } else {
            CaptureFormat.CAPTURE_4K
        }
        mVideoRenderManager = VideoRenderManager(mActivityLiveStreamingBinding.localView)

        mActivityLiveStreamingBinding.captureCapability.text = getString(mCurrentCaptureFormat.textResourceId)
        mActivityLiveStreamingBinding.progressLayout.visibility = GONE
        mActivityLiveStreamingBinding.localView.visibility = GONE
        mActivityLiveStreamingBinding.captureCapability.visibility = GONE

        if (Preference.isInitialAudioMute(applicationContext)) {
            audioMute = MuteType.HARD_MUTE
            mActivityLiveStreamingBinding.micIcon.setImageResource(R.drawable.baseline_mic_off_white_24)
        } else {
            audioMute = MuteType.UNMUTE
            mActivityLiveStreamingBinding.micIcon.setImageResource(R.drawable.baseline_mic_white_24)
        }

        mEgl = EglBase.create()
        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context
        mActivityLiveStreamingBinding.localView.init(eglContext, null)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        connect()
    }

    override fun onStop() {
        super.onStop()

        executor.safeSubmit {
            mClient?.disconnect()
        }.get()
    }

    override fun onDestroy() {
        super.onDestroy()

        mEgl?.release()
        mEgl = null
        mKeyEventTimer?.cancel()
        mKeyEventTimer = null

        mVideoRenderManager!!.clear()
    }

    private fun connect() = executor.safeSubmit {
        runOnUiThread {
            mActivityLiveStreamingBinding.progressMessage.text = getString(R.string.connecting)
            mActivityLiveStreamingBinding.progressLayout.visibility = VISIBLE
        }

        try {
            val roomId = Preference.getRoomId(applicationContext)
            val videoBitrate = Preference.getVideoBitrate(applicationContext)
            LOGGER.debug("videoBitrate=$videoBitrate")

            mCapturer = Camera2VideoCapturer(applicationContext, "0", mCurrentCaptureFormat.width, mCurrentCaptureFormat.height, mCurrentCaptureFormat.framerate)
            val roomSpec = RoomSpec(RoomSpec.RoomType.SFU)
            val accessToken = JwtAccessToken.createAccessToken(
                    BuildConfig.CLIENT_SECRET, roomId, roomSpec)

            val eglContext = mEgl!!.eglBaseContext as EglBase14.Context
            mClient = Client(
                    applicationContext,
                    eglContext,
                    ThetaVideoEncoderFactory(
                            eglContext,
                            listOf(
                                    CodecUtils.VIDEO_CODEC_INFO_H264,
                                    CodecUtils.VIDEO_CODEC_INFO_H264_HIGH_PROFILE
                            )
                    )
            ).apply {
                setEventListener(ClientListener())
            }

            val constraints = MediaStreamConstraints.Builder()
                    .videoCapturer(mCapturer!!)
                    .audio(true)
                    .build()

            val stream = mClient!!.getUserMedia(constraints)
            localLsTracks = arrayListOf<LSTrack>()
            // Track Metaデータに関しては以下を参照ください
            // https://api.livestreaming.ricoh/document/ricoh-live-streaming-conference-%e3%82%a2%e3%83%97%e3%83%aa%e3%82%b1%e3%83%bc%e3%82%b7%e3%83%a7%e3%83%b3%e9%96%8b%e7%99%ba%e8%80%85%e3%82%ac%e3%82%a4%e3%83%89/#Track_Metadata
            for (track in stream.audioTracks) {
                val trackOption = LSTrackOption.Builder()
                        .meta(mapOf("mediaType" to "VIDEO_AUDIO"))
                        .muteType(audioMute)
                        .build()
                localLsTracks.add(LSTrack(track, stream, trackOption))
            }
            for (track in stream.videoTracks) {
                val trackOption = LSTrackOption.Builder()
                        .meta(mapOf("mediaType" to "VIDEO_AUDIO"))
                        .build()
                localLsTracks.add(LSTrack(track, stream, trackOption))
            }

            val meta = Option.Builder()
                    .localLSTracks(localLsTracks)
                    .meta(mapOf("username" to "Wearable"))
                    .sending(SendingOption(
                            SendingVideoOption.Builder()
                                    .videoCodecType(SendingVideoOption.VideoCodecType.H264)
                                    .sendingPriority(SendingVideoOption.SendingPriority.HIGH)
                                    .maxBitrateKbps(videoBitrate)
                                    .build()))
                    .build()

            mClient!!.connect(
                    BuildConfig.CLIENT_ID,
                    accessToken,
                    meta)
        } catch (e: Exception) {
            LOGGER.error("Failed to connect to server.", e)
            runOnUiThread {
                showErrorDialog(getString(R.string.connect_error))
                mActivityLiveStreamingBinding.progressLayout.visibility = GONE
            }
        }
    }

    private fun showErrorDialog(message: String) {
        isDialogShowing = true
        val title = getString(R.string.error)
        val buttonText = getString(R.string.ok_button)
        ConfirmDialog().apply {
            arguments = Bundle().apply {
                putString("title", title)
                putString("message", message)
                putString("positiveButtonText", buttonText)
            }
            onPositiveButtonClickListener = DialogInterface.OnClickListener { dialog, which ->
                isDialogShowing = false
                connect()
            }
            onCancelListener = DialogInterface.OnCancelListener { dialog ->
                isDialogShowing = false
                connect()
            }
            onDismissListener = DialogInterface.OnDismissListener { dialog ->
                isDialogShowing = false
                connect()
            }
        }.show(supportFragmentManager, "error")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KEYCODE_DPAD_LEFT) {
            mCurrentCaptureFormat = when (mCurrentCaptureFormat) {
                CaptureFormat.CAPTURE_4K -> CaptureFormat.CAPTURE_2K
                CaptureFormat.CAPTURE_2K -> CaptureFormat.CAPTURE_4K
            }

            mCapturer!!.changeCaptureFormat(mCurrentCaptureFormat.width, mCurrentCaptureFormat.height, mCurrentCaptureFormat.framerate)
            mActivityLiveStreamingBinding.captureCapability.text = getString(mCurrentCaptureFormat.textResourceId)

            return true
        } else if (keyCode == KEYCODE_DPAD_RIGHT) {
            mVideoRenderManager!!.toggleDisplay()
            return true
        } else if (!isDialogShowing && keyCode == KEYCODE_DPAD_CENTER) {
            mKeyEventTimer?.cancel()
            if (isFinishKeyPressed) {
                mActivityLiveStreamingBinding.localView.visibility = GONE
                mActivityLiveStreamingBinding.captureCapability.visibility = GONE

                executor.safeSubmit {
                    mClient!!.disconnect()
                }
            } else {
                mKeyEventTimer = object : CountDownTimer(600, 100) {
                    override fun onTick(p0: Long) {
                    }

                    override fun onFinish() {
                        isFinishKeyPressed = false
                    }
                }
                mKeyEventTimer?.start()
                isFinishKeyPressed = true
            }
            return true
        } else if (keyCode == KEYCODE_MENU) {
            // toggle mute
            val muteType = if (audioMute == MuteType.UNMUTE) {
                MuteType.HARD_MUTE
            } else {
                MuteType.UNMUTE
            }
            changeAudioMute(muteType)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun changeAudioMute(muteType: MuteType) {
        if (mClient?.state != Client.State.OPEN) {
            return
        }

        val track = localLsTracks.find { it.mediaStreamTrack is AudioTrack }
        if (track == null) {
            LOGGER.info("Not found audio track")
            return
        }

        mClient?.changeMute(track, muteType)
        audioMute = muteType
        val iconResourceId = if (audioMute == MuteType.HARD_MUTE) {
            R.drawable.baseline_mic_off_white_24
        } else {
            R.drawable.baseline_mic_white_24
        }
        mActivityLiveStreamingBinding.micIcon.setImageResource(iconResourceId)
        LOGGER.debug("audio mute change to {}", muteType)
    }

    inner class ClientListener : Client.Listener {

        override fun onConnecting(event: LSConnectingEvent) {
            LOGGER.debug("Client#onConnecting")

            runOnUiThread {
                mActivityLiveStreamingBinding.progressMessage.text = getString(R.string.connecting)
                mActivityLiveStreamingBinding.progressLayout.visibility = VISIBLE
            }
        }

        override fun onOpen(event: LSOpenEvent) {
            LOGGER.debug("Client#onOpen")

            // == For WebRTC Internal Tracing Capture.
            // == "/sdcard/{LOGS_DIR}/{date}T{time}.log.json" will be created.
            //
            // if (!PeerConnectionFactory.startInternalTracingCapture(createLogFile().absolutePath + ".json")) {
            //     LOGGER.error("failed to start internal tracing capture")
            // }

            val file = createLogFile()
            LOGGER.info("create log file: ${file.path}")
            mRtcStatsLogger = RTCStatsLogger(file)
            mCapturer?.start()

            mStatsTimer = Timer(true)
            mStatsTimer?.schedule(object : TimerTask() {
                override fun run() {
                    synchronized(LOCK) {
                        if (mClient != null) {
                            val stats = mClient!!.stats
                            for ((key, value) in stats) {
                                mRtcStatsLogger?.log(key, value)
                            }
                        }
                    }
                }
            }, 0, 1000)

            runOnUiThread {
                mActivityLiveStreamingBinding.captureCapability.visibility = VISIBLE
                mActivityLiveStreamingBinding.progressLayout.visibility = GONE
                mActivityLiveStreamingBinding.localView.visibility = VISIBLE
            }
        }

        override fun onClosing(event: LSClosingEvent) {
            LOGGER.debug("Client#onClosing")

            runOnUiThread {
                mActivityLiveStreamingBinding.progressMessage.text = getString(R.string.disconnecting)
                mActivityLiveStreamingBinding.progressLayout.visibility = VISIBLE
            }
        }

        override fun onClosed(event: LSClosedEvent) {
            LOGGER.debug("Client#onClosed")

            // == For WebRTC Internal Tracing
            //
            // PeerConnectionFactory.stopInternalTracingCapture()

            mStatsTimer?.cancel()
            mStatsTimer = null

            synchronized(LOCK) {
                mRtcStatsLogger?.close()
                mRtcStatsLogger = null
                mCapturer?.stop()
                mCapturer?.release()
                mClient?.setEventListener(null)
                mClient = null
            }

            mVideoRenderManager!!.clear()

            finish()
        }

        override fun onAddLocalTrack(event: LSAddLocalTrackEvent) {
            LOGGER.debug("Client#onAddLocalTrack({})", event.mediaStreamTrack.id())

            if (event.mediaStreamTrack is VideoTrack) {
                mVideoRenderManager!!.addLocalTrack(event.mediaStreamTrack as VideoTrack)
            }
        }

        override fun onAddRemoteConnection(event: LSAddRemoteConnectionEvent) {
            LOGGER.debug("Client#onAddRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onRemoveRemoteConnection(event: LSRemoveRemoteConnectionEvent) {
            LOGGER.debug("Client#onRemoveRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            for (mediaStreamTrack in event.mediaStreamTracks) {
                LOGGER.debug("mediaStreamTrack={}", mediaStreamTrack)
            }

            mVideoRenderManager!!.removeRemoteTrack(event.connectionId)
        }

        override fun onAddRemoteTrack(event: LSAddRemoteTrackEvent) {
            LOGGER.debug("Client#onAddRemoteTrack({}, {}, {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id(), event.mute)

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }

            if (event.mediaStreamTrack is VideoTrack) {
                mVideoRenderManager!!.addRemoteTrack(event.connectionId, event.mediaStreamTrack as VideoTrack)
            }

        }

        override fun onUpdateRemoteConnection(event: LSUpdateRemoteConnectionEvent) {
            LOGGER.debug("Client#onUpdateRemoteConnection(connectionId = ${event.connectionId})")

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateRemoteTrack(event: LSUpdateRemoteTrackEvent) {
            LOGGER.debug("Client#onUpdateRemoteTrack({} {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id())

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateMute(event: LSUpdateMuteEvent) {
            LOGGER.debug("Client#onUpdateMute({} {}, {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id(), event.mute)
        }

        override fun onChangeStability(event: LSChangeStabilityEvent) {
            LOGGER.debug("Client#onChangeStability({}, {})", event.connectionId, event.stability)
        }

        override fun onError(error: SDKErrorEvent) {
            LOGGER.error("Client#onError({})", error.toReportString())
        }
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
}
