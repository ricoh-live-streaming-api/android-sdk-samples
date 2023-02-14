/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.theta

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.theta.databinding.ActivityMainBinding
import com.ricoh.livestreaming.theta.webapi.ThetaWebApiClient
import com.ricoh.livestreaming.webrtc.CodecUtils
import com.ricoh.livestreaming.webrtc.ThetaCameraCapturer
import com.ricoh.livestreaming.webrtc.ThetaVideoCapturer
import com.ricoh.livestreaming.webrtc.ThetaXCameraCapturer
import com.theta360.pluginlibrary.activity.PluginActivity
import com.theta360.pluginlibrary.callback.KeyCallback
import com.theta360.pluginlibrary.receiver.KeyReceiver
import com.theta360.pluginlibrary.values.LedColor
import com.theta360.pluginlibrary.values.LedTarget
import com.theta360.pluginlibrary.values.TextArea
import org.apache.commons.collections4.IteratorUtils
import org.slf4j.LoggerFactory
import org.webrtc.EglBase
import org.webrtc.EglBase14
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : PluginActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MainActivity::class.java)

        private val BITRATES = IteratorUtils.loopingIterator(listOf(
                // 15Mbpsを利用するためには、connect optionsのmaxBitrateKbpsも15Mbpsに設定する必要があります
                // またRoom帯域の予約値もそれにあわせて変更が必要になります
                // 詳細は https://api.livestreaming.ricoh/document/%e6%83%b3%e5%ae%9a%e5%88%a9%e7%94%a8%e3%82%b7%e3%83%bc%e3%83%b3%e5%88%a5%e6%96%99%e9%87%91/ を参照ください
                // 15_000, // 15Mbps
                10_000, // 10Mbps
                5_000,  // 5Mbps
                1_000,  // 1Mbps
        ))

        private val CAPTURE_FORMATS = IteratorUtils.loopingIterator(listOf(
                CaptureFormat(ShootingMode.RIC_MOVIE_PREVIEW_3840, StitchingMode.RIC_STATIC_STITCHING, 20),
                CaptureFormat(ShootingMode.RIC_MOVIE_PREVIEW_3840, StitchingMode.RIC_STATIC_STITCHING, 30),
                CaptureFormat(ShootingMode.RIC_MOVIE_PREVIEW_3840, StitchingMode.RIC_STATIC_STITCHING, 10),
                CaptureFormat(ShootingMode.RIC_MOVIE_PREVIEW_1920, StitchingMode.RIC_STATIC_STITCHING, 30)
        ))

        private val LOCK = Object()
    }

    private val executor = Executors.newSingleThreadExecutor()

    private var capturer: ThetaVideoCapturer? = null

    private var mEgl: EglBase? = null

    private var mThetaVideoEncoderFactory: ThetaVideoEncoderFactory? = null

    private var mClient: Client? = null

    private var mRtcStatsLogger: RTCStatsLogger? = null

    private var mStatsTimer: Timer? = null

    private var mAttitude: Attitude? = null
    private val mVideoMetaSendTimerTask = TimerTaskRunner()

    private var audioMute = MuteType.HARD_MUTE

    private var lsTracks = arrayListOf<LSTrack>()

    private var isCameraKeyLongPressed = false

    private var savedAudioMode = AudioManager.MODE_NORMAL
    private var savedIsSpeakerPhoneOn = false
    private var savedStreamVolume = 0
    private var isDisplayOff = false
    private var prevDisplayBrightness = 0

    /** View Binding */
    private lateinit var mActivityMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mActivityMainBinding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mEgl = EglBase.create()

        val eglContext = mEgl!!.eglBaseContext as EglBase14.Context

        mThetaVideoEncoderFactory = ThetaVideoEncoderFactory(eglContext, listOf(
                CodecUtils.VIDEO_CODEC_INFO_H264,
                CodecUtils.VIDEO_CODEC_INFO_H264_HIGH_PROFILE
        ))

        CAPTURE_FORMATS.reset()
        val captureFormat = CAPTURE_FORMATS.next()
        capturer = if (("RICOH THETA V" == Build.MODEL) || ("RICOH THETA Z1" == Build.MODEL)) {
            ThetaCameraCapturer(captureFormat, applicationContext)
        } else {
            ThetaXCameraCapturer(captureFormat, applicationContext)
        }
        setResolutionToTextView(captureFormat)
        mActivityMainBinding.resolutionChangeButton.setOnClickListener {
            if (mClient?.state == Client.State.OPEN) {
                updateCaptureFormat(CAPTURE_FORMATS.next())
            }
        }

        BITRATES.reset()
        // 初期状態での最大bitrate表示
        setBitrateToTextView(BuildConfig.VIDEO_BITRATE)

        // Bitrate setting button
        mActivityMainBinding.bitrateChangeButton.setOnClickListener {
            if (mClient?.state == Client.State.OPEN) {
                val bitrateKbps = BITRATES.next()
                mClient?.changeVideoSendBitrate(bitrateKbps)
                LOGGER.debug("Target bitrate set to {}kbps", bitrateKbps)
                notificationAudioSelf()
                setBitrateToTextView(bitrateKbps)
            }
        }

        // Save button
        mActivityMainBinding.saveConfig.setOnClickListener {
            Config.setRoomId(this@MainActivity.applicationContext, mActivityMainBinding.roomId.text.toString())
            mActivityMainBinding.layoutGuide.visibility = View.INVISIBLE
        }

        // Room ID text box
        mActivityMainBinding.roomId.setText(Config.getRoomId())
        mActivityMainBinding.roomId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString() != Config.getRoomId()) mActivityMainBinding.layoutGuide.visibility = View.VISIBLE
                else mActivityMainBinding.layoutGuide.visibility = View.INVISIBLE
            }
        })

        // Log level spinner
        mActivityMainBinding.logLevel.setSelection(Config.getSelectedLoggingSeverity())
        mActivityMainBinding.logLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Config.setLoggingSeverity(this@MainActivity.applicationContext, mActivityMainBinding.logLevel.selectedItem.toString())
            }
        }

        // Room Type
        mActivityMainBinding.roomType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, RoomSpec.RoomType.values())
        mActivityMainBinding.roomType.setSelection(Config.getSelectedRoomType())
        mActivityMainBinding.roomType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                Config.setRoomType(this@MainActivity.applicationContext, position, mActivityMainBinding.roomType.selectedItem as RoomSpec.RoomType)
            }
        }

        // Mute Type
        val confAudioMute = BuildConfig.INITIAL_AUDIO_MUTE
        audioMute = if (confAudioMute == "unmute" || confAudioMute == "null") MuteType.UNMUTE else MuteType.HARD_MUTE
        mActivityMainBinding.audioMuteRadio.check(
            if (audioMute == MuteType.UNMUTE) {
                R.id.audio_unmute
            } else {
                R.id.audio_mute
            }
        )
        mActivityMainBinding.audioMuteRadio.setOnCheckedChangeListener { _, checkedId ->
            val muteType = if (checkedId == R.id.audio_unmute) {
                MuteType.UNMUTE
            } else {
                MuteType.HARD_MUTE
            }
            if (audioMute != muteType) {
                changeAudioMute(muteType)
            }
        }

        // Display off button
        if (isThetaX()) {
            mActivityMainBinding.displayOffButton.setOnClickListener {
                prevDisplayBrightness = getDisplayBrightness()
                setDisplayBrightness(0)
                isDisplayOff = true
            }
        } else {
            mActivityMainBinding.displayOffButton.visibility = View.GONE
        }
        mActivityMainBinding.displayOffButton.isEnabled = false

        // Zenith correction
        if (isThetaX()) {
            mActivityMainBinding.zenithCorrectionRadio.setOnCheckedChangeListener { _, checkedId ->
                val c = capturer
                if (c is ThetaXCameraCapturer) {
                    c.setZenithCorrection(checkedId == R.id.zenith_correction_on)
                }
            }
        } else {
            mActivityMainBinding.zenithCorrectionLayout.visibility = View.GONE
        }

        if (isSFU() && !isThetaX()) {
            val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            mAttitude = Attitude(sensorManager)
        }
    }

    private fun setDisplayBrightness(brightness: Int) {
        val c = capturer
        if (c is ThetaXCameraCapturer) {
            c.lcdBrightness = brightness
        }
    }

    private fun getDisplayBrightness(): Int {
        val c = capturer
        return if (c is ThetaXCameraCapturer) {
            return c.lcdBrightness
        } else {
            0
        }
    }

    private fun updateText(text: String) {
        val output: MutableMap<TextArea, String> = EnumMap(com.theta360.pluginlibrary.values.TextArea::class.java)
        output[TextArea.BOTTOM] = text
        notificationOledTextShow(output)
    }

    private fun changeAudioMute(muteType: MuteType) {
        if (mClient?.state != Client.State.OPEN) return

        val track = lsTracks.find { it.mediaStreamTrack.kind() == "audio" }
        if (track == null) {
            LOGGER.info("Not found audio track")
            return
        }
        try {
            mClient?.changeMute(track, muteType)
            audioMute = muteType
            if (audioMute == MuteType.HARD_MUTE) {
                updateText("audio: mute")
                mActivityMainBinding.audioMuteRadio.check(R.id.audio_mute)
            } else {
                updateText("audio: unmute")
                mActivityMainBinding.audioMuteRadio.check(R.id.audio_unmute)
            }
            LOGGER.debug("audio mute change to {}", muteType)
        } catch (e: SDKError) {
            LOGGER.error(e.toReportString())
        }
    }

    /**
     * libwebrtcログはConnectするごとにログを削除して再作成する仕組みのため
     * 過去実行時のログを残すには退避処理が必要になります。
     */
    @SuppressLint("SimpleDateFormat")
    private fun saveLibWebrtcLog() {
        getExternalFilesDir(null)!!.resolve("logs").resolve("libwebrtc").let { libWebrtcLogDir ->
            libWebrtcLogDir.listFiles()?.forEach {
                // libwebrtcログファイル名は"webrtc_log_{連番}"として作成されるため、
                // 過去実行時ログを見つけるために"webrtc"で始まるログを検索します。
                if (it.isFile && it.name.startsWith("webrtc")) {
                    it.renameTo(File("${libWebrtcLogDir.absolutePath}/${SimpleDateFormat("yyyyMMdd'T'HHmmss").format(it.lastModified())}_${it.name}"))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (isThetaX()) {
            val audioManager: AudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            savedAudioMode = audioManager.mode
            savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn
            savedStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)

            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.setStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                    AudioManager.FX_KEY_CLICK
            )
            audioManager.isSpeakerphoneOn = true
        }

        notificationLedHide(LedTarget.LED4)
        notificationLedHide(LedTarget.LED5)
        notificationLedHide(LedTarget.LED6)

        setKeyCallback(object : KeyCallback {
            override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent) {
                if (keyCode == KeyReceiver.KEYCODE_MEDIA_RECORD &&
                        !keyEvent.isLongPress &&
                        mClient?.state == Client.State.OPEN) {
                    try {
                        updateCaptureFormat(CAPTURE_FORMATS.next())
                    } catch (e: Exception) {
                        LOGGER.error("Failed to updateCaptureFormat.", e)
                    }
                } else if (keyCode == KeyReceiver.KEYCODE_WLAN_ON_OFF &&
                        mClient?.state == Client.State.OPEN) {
                    val bitrateKbps = BITRATES.next()
                    mClient?.changeVideoSendBitrate(bitrateKbps)
                    LOGGER.debug("Target bitrate set to {}kbps", bitrateKbps)
                    setBitrateToTextView(bitrateKbps)
                    notificationAudioSelf()
                } else if (keyCode == KeyReceiver.KEYCODE_FUNCTION) {
                    // toggle mute
                    val muteType = if (audioMute == MuteType.UNMUTE)  MuteType.HARD_MUTE else MuteType.UNMUTE
                    changeAudioMute(muteType)
                }
            }

            override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent) {
                if (isDisplayOff) {
                    isDisplayOff = false
                    setDisplayBrightness(prevDisplayBrightness)
                } else if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
                    val isCanceled = if (("RICOH THETA V" == Build.MODEL) || ("RICOH THETA Z1" == Build.MODEL)) {
                        keyEvent.isCanceled
                    } else {
                        isCameraKeyLongPressed
                    }
                    isCameraKeyLongPressed = false
                    if (!isCanceled && mClient?.state == Client.State.OPEN) {
                        try {
                            LOGGER.info("try to take a picture.")
                            capturer?.takePicture { data ->
                                notificationAudioShutter()
                                val path = createImgFilePath()
                                LOGGER.info("Save Still Image to {}", path)
                                File(path).writeBytes(data)
                            }
                        } catch (e: IllegalStateException) {
                            LOGGER.error("Failed to takePicture.", e)
                        }
                    }
                }
            }

            @SuppressLint("SimpleDateFormat")
            override fun onKeyLongPress(keyCode: Int, keyEvent: KeyEvent) {
                if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
                    isCameraKeyLongPressed = true
                    if (mClient == null) {
                        executor.submit {
                            LOGGER.info("Try to connect. RoomType={}", Config.getRoomType())
                            disableBFormat()

                            // 過去実行時のlibwebrtcログがある場合は退避します。
                            saveLibWebrtcLog()

                            if (isSFU() && !isThetaX()) {
                                mAttitude!!.start()
                            }

                            // 過去実行時のlibwebrtcログがある場合は退避します。
                            saveLibWebrtcLog()

                            val roomSpec = RoomSpec(Config.getRoomType())
                            val rand = Random()
                            val connectionId = "AndroidThetaPlugIn" + rand.nextInt(1000)
                            val dateTimeZone = ThetaWebApiClient.getDateTimeZone()
                            val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ssZZ")
                            val date = dateFormat.parse(dateTimeZone)
                            val accessToken = JwtAccessToken.createAccessToken(
                                    BuildConfig.CLIENT_SECRET, mActivityMainBinding.roomId.text.toString(), roomSpec, date!!, connectionId)
                            val eglContext = mEgl!!.eglBaseContext as EglBase14.Context
                            val proxy: String = BuildConfig.PROXY
                            mClient = Client(applicationContext, eglContext, mThetaVideoEncoderFactory).apply {
                                setEventListener(ClientListener())
                            }
                            val constraints = MediaStreamConstraints.Builder()
                                    .videoCapturer(capturer!!)
                                    .audio(true)
                                    .build()

                            val stream = mClient!!.getUserMedia(constraints)
                            lsTracks = arrayListOf()
                            // Track Metaデータに関しては以下を参照ください
                            // https://api.livestreaming.ricoh/document/ricoh-live-streaming-conference-%e3%82%a2%e3%83%97%e3%83%aa%e3%82%b1%e3%83%bc%e3%82%b7%e3%83%a7%e3%83%b3%e9%96%8b%e7%99%ba%e8%80%85%e3%82%ac%e3%82%a4%e3%83%89/#Track_Metadata
                            for (track in stream.audioTracks) {
                                val trackOption = LSTrackOption.Builder()
                                        .meta(mapOf("mediaType" to "VIDEO_AUDIO"))
                                        .muteType(audioMute)
                                        .build()
                                lsTracks.add(LSTrack(track, stream, trackOption))
                            }
                            for (track in stream.videoTracks) {
                                val trackOption = LSTrackOption.Builder()
                                        .meta(mutableMapOf<String, Any>().apply {
                                            this["isTheta"] = true
                                            this["thetaVideoFormat"] = "eq"
                                            this["mediaType"] = "VIDEO_AUDIO"
                                            if (!isThetaX()) {
                                                this["yaw"] = mAttitude!!.getDegYaw()
                                                this["pitch"] = mAttitude!!.getDegPitch()
                                                this["roll"] =  mAttitude!!.getDegRoll()
                                            }
                                        }.toMap())
                                        .build()
                                lsTracks.add(LSTrack(track, stream, trackOption))
                            }
                            val option = Option.Builder()
                                    .loggingSeverity(Config.getLoggingSeverity())
                                    .localLSTracks(lsTracks)
                                    .meta(mapOf("username" to connectionId, "mediaType" to "VIDEO_AUDIO", "enableVideo" to true, "enableAudio" to true))
                                    .sending(SendingOption(
                                            SendingVideoOption.Builder()
                                                    .videoCodecType(SendingVideoOption.VideoCodecType.H264)
                                                    .sendingPriority(SendingVideoOption.SendingPriority.HIGH)
                                                    .maxBitrateKbps(BuildConfig.VIDEO_BITRATE)
                                                    .build()))
                                    .receiving(ReceivingOption(false))
                                    .apply {
                                        if (isProxy(proxy)) {
                                            this.proxy(proxy)
                                        }
                                    }
                                    .build()

                            val libWebrtcLogFilePath = getExternalFilesDir(null)!!
                                    .resolve("logs")
                                    .resolve("libwebrtc")
                                    .absolutePath
                            val libWebrtcLogOptions = LibWebrtcLogOption.Builder(libWebrtcLogFilePath)
                                    .maxTotalFileSize(4)
                                    .logLevel(LibWebrtcLogLevel.INFO)
                                    .build()
                            try {
                                mClient!!.setLibWebrtcLogOption(libWebrtcLogOptions)
                            } catch (e: SDKError) {
                                LOGGER.error(e.toReportString())
                                return@submit
                            }

                            mClient!!.connect(
                                    BuildConfig.CLIENT_ID,
                                    accessToken,
                                    option)
                            if (audioMute == MuteType.HARD_MUTE) {
                                updateText("audio: mute")
                            } else {
                                updateText("audio: unmute")
                            }
                        }
                    } else {
                        executor.submit {
                            mClient!!.disconnect()
                        }
                    }
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()

        setKeyCallback(null)

        executor.submit {
            mClient?.disconnect()
        }.get()

        if (isThetaX()) {
            val audioManager: AudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioManager.mode = savedAudioMode
            audioManager.isSpeakerphoneOn = savedIsSpeakerPhoneOn
            audioManager.setStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    savedStreamVolume,
                    AudioManager.FX_KEY_CLICK
            )
            audioManager.isSpeakerphoneOn = true
            if (isDisplayOff) {
                setDisplayBrightness(prevDisplayBrightness)
                isDisplayOff = false
            }
        } else {
            if (isSFU()) {
                mAttitude!!.stop()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mEgl?.release()
        mEgl = null
    }

    inner class ClientListener : Client.Listener {
        override fun onConnecting(event: LSConnectingEvent) {
            LOGGER.debug("Client#onConnecting")
            notificationCameraClose()
            notificationLedBlink(LedTarget.LED6, LedColor.BLUE, 250)
            notificationAudioMovStart()

            runOnUiThread {
                mActivityMainBinding.liveStreamingStateText.text = mClient?.state.toString()
            }
        }

        override fun onOpen(event: LSOpenEvent) {
            LOGGER.debug("Client#onOpen")
            val file = createLogFile()
            LOGGER.info("create log file: ${file.absolutePath}")
            mRtcStatsLogger = RTCStatsLogger(file)
            capturer?.start()

            if (isSFU()) {
                if (!isThetaX()) {
                    mVideoMetaSendTimerTask.add(AttitudeSendTimerTask(), 10 * 1000)
                }
                mVideoMetaSendTimerTask.start()
            }

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

            notificationLedShow(LedTarget.LED6)
            notificationAudioOpen()
            runOnUiThread {
                mActivityMainBinding.liveStreamingStateText.text = mClient?.state.toString()
                mActivityMainBinding.resolutionChangeButton.isEnabled = true
                mActivityMainBinding.bitrateChangeButton.isEnabled = true
                mActivityMainBinding.audioMute.isEnabled = true
                mActivityMainBinding.audioUnmute.isEnabled = true
                mActivityMainBinding.displayOffButton.isEnabled = true
            }
        }

        override fun onClosing(event: LSClosingEvent) {
            LOGGER.debug("Client#onClosing")
            notificationLedBlink(LedTarget.LED6, LedColor.BLUE, 250)
            notificationAudioClose()
            runOnUiThread {
                mActivityMainBinding.liveStreamingStateText.text = mClient?.state.toString()
                mActivityMainBinding.resolutionChangeButton.isEnabled = false
                mActivityMainBinding.bitrateChangeButton.isEnabled = false
                mActivityMainBinding.audioMute.isEnabled = false
                mActivityMainBinding.audioUnmute.isEnabled = false
                mActivityMainBinding.displayOffButton.isEnabled = false
            }
        }

        override fun onClosed(event: LSClosedEvent) {
            LOGGER.debug("Client#onClosed")
            mStatsTimer?.cancel()
            mStatsTimer = null

            runOnUiThread {
                mActivityMainBinding.liveStreamingStateText.text = mClient?.state.toString()
            }

            synchronized(LOCK) {
                mRtcStatsLogger?.close()
                mRtcStatsLogger = null

                if (isSFU()) {
                    if (!isThetaX()) {
                        mAttitude!!.stop()
                    }
                    mVideoMetaSendTimerTask.stop()
                    mVideoMetaSendTimerTask.clear()
                }

                capturer?.stop()
                capturer?.release()
                mClient?.setEventListener(null)
                mClient = null
            }

            notificationLedHide(LedTarget.LED6)
            notificationAudioMovStop()
            notificationCameraOpen()
        }

        override fun onAddLocalTrack(event: LSAddLocalTrackEvent) {
            LOGGER.debug("Client#onAddLocalTrack({})", event.mediaStreamTrack.id())
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
        }

        override fun onAddRemoteTrack(event: LSAddRemoteTrackEvent) {
            LOGGER.debug("Client#onAddRemoteTrack({}, {}, {}, {})", event.connectionId, event.stream.id, event.mediaStreamTrack.id(), event.mute)

            for ((key, value) in event.meta) {
                LOGGER.debug("metadata key=${key} : value=${value}")
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
            LOGGER.debug("Client#onError({})", error.toReportString())
        }
    }

    /**
     * Create log file.
     * Example path: /storage/emulated/0/Android/data/{app_package_name}/files/logs/{date}T{time}.log
     */
    @SuppressLint("SimpleDateFormat")
    private fun getTimestamp(): String {
        val df = SimpleDateFormat("yyyyMMdd'T'HHmm")
        return df.format(Date())
    }
    private fun createLogFile(): File {
        return getExternalFilesDir(null)!!
                .resolve("logs")
                .resolve("stats")
                .resolve("stats_${getTimestamp()}.log")
    }
    private fun createImgFilePath(): String {
        return getExternalFilesDir(null)!!
                .resolve("logs")
                .resolve("${getTimestamp()}.jpg")
                .absolutePath
    }

    private fun disableBFormat() {
        getSystemService(AudioManager::class.java)!!.setParameters("RicUseBFormat=false")
    }

    private fun updateCaptureFormat(format: CaptureFormat) {
        try {
            LOGGER.info("update capture format.")
            capturer?.updateCaptureFormat(format)
            setResolutionToTextView(format)
            notificationAudioSelf()
        } catch (e: Exception) {
            LOGGER.error("Failed to updateCaptureFormat.", e)
        }
    }

    private fun setResolutionToTextView(format: CaptureFormat) {
        mActivityMainBinding.currentResolutionText.text = getString(
                R.string.current_resolution,
                format.shootingMode.width,
                format.shootingMode.height,
                format.fps)
    }

    private fun setBitrateToTextView(bitrateKbps: Int) {
        mActivityMainBinding.currentBitrateText.text = getString(
                R.string.current_bitrate,
                bitrateKbps / 1_000)
    }

    private fun isThetaX(): Boolean {
        return "RICOH THETA X" == Build.MODEL
    }

    private fun isSFU(): Boolean {
        return "sfu" == Config.getRoomType().typeStr
    }

    private fun isProxy(proxy: String?): Boolean {
        return proxy != "null" && proxy != ""
    }

    inner class AttitudeSendTimerTask : TimerTask() {
        override fun run() {
            val track = lsTracks.find { it.mediaStreamTrack.kind() == "video" }
            if (track == null) {
                LOGGER.info("Not found video track")
                return
            }

            val yaw = mAttitude!!.getDegYaw()
            val pitch = mAttitude!!.getDegPitch()
            val roll = mAttitude!!.getDegRoll()
            synchronized(LOCK) {
                if (mClient != null && mClient!!.state == Client.State.OPEN) {
                    try {
                        LOGGER.debug("attitude(yaw={} pitch={} roll={})", yaw, pitch, roll)
                        mClient!!.updateTrackMeta(
                                track,
                                mapOf(
                                        "isTheta" to true,
                                        "thetaVideoFormat" to "eq",
                                        "mediaType" to "VIDEO_AUDIO",
                                        "yaw" to yaw,
                                        "pitch" to pitch,
                                        "roll" to roll
                                ))
                    } catch (e: SDKError) {
                        LOGGER.error(e.toReportString())
                    }
                }
            }
        }
    }
}
