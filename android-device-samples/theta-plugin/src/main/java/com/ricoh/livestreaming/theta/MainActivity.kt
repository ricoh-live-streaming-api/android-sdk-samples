/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.theta

import android.annotation.SuppressLint
import android.media.AudioManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import com.ricoh.livestreaming.*
import com.ricoh.livestreaming.theta.databinding.ActivityMainBinding
import com.ricoh.livestreaming.theta.webapi.ThetaWebApiClient
import com.ricoh.livestreaming.webrtc.CodecUtils
import com.ricoh.livestreaming.webrtc.ThetaCameraCapturer
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
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import java.io.File
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
//                15_000_000, // 15Mbps
                10_000_000, // 10Mbps
                5_000_000,  // 5Mbps
                1_000_000,  // 1Mbps
                0           // Auto
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

    private var capturer: ThetaCameraCapturer? = null

    private var mEgl: EglBase? = null

    private var mThetaVideoEncoderFactory: ThetaVideoEncoderFactory? = null

    private var mClient: Client? = null

    private var mRtcStatsLogger: RTCStatsLogger? = null

    private var mStatsTimer: Timer? = null

    private var audioMute = MuteType.HARD_MUTE

    private var lsTracks = arrayListOf<LSTrack>()

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
        capturer = ThetaCameraCapturer(CAPTURE_FORMATS.next(), applicationContext)

        // Load configurations.
        Config.load(this.applicationContext)

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
        mActivityMainBinding.roomTypeRadio.check(Config.getSelectedRoomTypeID())
        mActivityMainBinding.roomTypeRadio.setOnCheckedChangeListener { _, checkedId ->
            Config.setRoomType(applicationContext, checkedId)
        }

        // Mute Type
        val confAudioMute = BuildConfig.INITIAL_AUDIO_MUTE
        audioMute = if (confAudioMute == "unmute" || confAudioMute == "null") MuteType.UNMUTE else MuteType.HARD_MUTE
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
            } else {
                updateText("audio: unmute")
            }
            LOGGER.debug("audio mute change to {}", muteType)
        } catch (e: SDKError) {
            LOGGER.error(e.toReportString())
        }
    }

    override fun onResume() {
        super.onResume()

        notificationLedHide(LedTarget.LED4)
        notificationLedHide(LedTarget.LED5)
        notificationLedHide(LedTarget.LED6)

        setKeyCallback(object : KeyCallback {
            override fun onKeyDown(keyCode: Int, keyEvent: KeyEvent) {
                if (keyCode == KeyReceiver.KEYCODE_MEDIA_RECORD &&
                        !keyEvent.isLongPress &&
                        mClient?.state == Client.State.OPEN) {
                    try {
                        LOGGER.info("update capture format.")
                        capturer?.updateCaptureFormat(CAPTURE_FORMATS.next())
                        notificationAudioSelf()
                    } catch (e: Exception) {
                        LOGGER.error("Failed to updateCaptureFormat.", e)
                    }
                } else if (keyCode == KeyReceiver.KEYCODE_WLAN_ON_OFF) {
                    val bitrate = BITRATES.next()
                    mThetaVideoEncoderFactory!!.setTargetBitrate(bitrate)
                    LOGGER.debug("Target bitrate set to {}", bitrate)
                    notificationAudioSelf()
                } else if (keyCode == KeyReceiver.KEYCODE_FUNCTION) {
                    // toggle mute
                    val muteType = if (audioMute == MuteType.UNMUTE)  MuteType.HARD_MUTE else MuteType.UNMUTE
                    changeAudioMute(muteType)
                }
            }

            override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent) {
                if (keyCode == KeyReceiver.KEYCODE_CAMERA &&
                        !keyEvent.isCanceled &&
                        mClient?.state == Client.State.OPEN) {
                    try {
                        LOGGER.info("try to take a picture.")
                        capturer?.takePicture { data ->
                            notificationAudioShutter()
                            val path = createFilePath() + ".jpg"
                            LOGGER.info("Save Still Image to {}", path)
                            File(path).writeBytes(data)
                        }
                    } catch (e: IllegalStateException) {
                        LOGGER.error("Failed to takePicture.", e)
                    }
                }
            }

            @SuppressLint("SimpleDateFormat")
            override fun onKeyLongPress(keyCode: Int, keyEvent: KeyEvent) {
                if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
                    if (mClient == null) {
                        executor.submit {
                            LOGGER.info("Try to connect. RoomType={}", Config.getRoomType())
                            disableBFormat()

                            val roomSpec = RoomSpec(Config.getRoomType())
                            val rand = Random()
                            val connectionId = "THETA" + rand.nextInt(1000)
                            val dateTimeZone = ThetaWebApiClient.getDateTimeZone()
                            val dateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ssZZ")
                            val date = dateFormat.parse(dateTimeZone)
                            val accessToken = JwtAccessToken.createAccessToken(
                                    BuildConfig.CLIENT_SECRET, mActivityMainBinding.roomId.text.toString(), roomSpec, date!!, connectionId)
                            val eglContext = mEgl!!.eglBaseContext as EglBase14.Context
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
                                        .meta(mapOf("isTheta" to true, "thetaVideoFormat" to "eq", "mediaType" to "VIDEO_AUDIO"))
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
                                    .build()
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
    }

    override fun onDestroy() {
        super.onDestroy()

        mEgl?.release()
        mEgl = null
    }

    inner class ClientListener : Client.Listener {
        override fun onConnecting() {
            LOGGER.debug("Client#onConnecting")
            notificationCameraClose()
            notificationLedBlink(LedTarget.LED6, LedColor.BLUE, 250)
            notificationAudioMovStart()
        }

        override fun onOpen() {
            LOGGER.debug("Client#onOpen")
            val path = createFilePath() + ".log"
            LOGGER.info("create log file: $path")
            mRtcStatsLogger = RTCStatsLogger(File(path))
            capturer?.start()

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
        }

        override fun onClosing() {
            LOGGER.debug("Client#onClosing")
            notificationLedBlink(LedTarget.LED6, LedColor.BLUE, 250)
            notificationAudioClose()
        }

        override fun onClosed() {
            LOGGER.debug("Client#onClosed")
            mStatsTimer?.cancel()
            mStatsTimer = null

            synchronized(LOCK) {
                mRtcStatsLogger?.close()
                mRtcStatsLogger = null
                capturer?.stop()
                capturer?.release()
                mClient?.setEventListener(null)
                mClient = null
            }

            notificationLedHide(LedTarget.LED6)
            notificationAudioMovStop()
            notificationCameraOpen()
        }

        override fun onAddLocalTrack(track: MediaStreamTrack, stream: MediaStream) {
            LOGGER.debug("Client#onAddLocalTrack({})", track.id())
        }

        override fun onAddRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onAddRemoteConnection(connectionId = ${connectionId})")

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
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
            LOGGER.debug("Client#onAddRemoteTrack({}, {}, {}, {})", connectionId, stream.id, track.id(), muteType)

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onUpdateRemoteConnection(connectionId = ${connectionId})")

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateRemoteTrack(connectionId: String, stream: MediaStream, track: MediaStreamTrack, metadata: Map<String, Any>) {
            LOGGER.debug("Client#onUpdateRemoteTrack({} {}, {})", connectionId, stream.id, track.id())

            for ((key, value) in metadata) {
                LOGGER.debug("metadata key=${key} : value=${value}")
            }
        }

        override fun onUpdateMute(connectionId: String, stream: MediaStream, track: MediaStreamTrack, muteType: MuteType) {
            LOGGER.debug("Client#onUpdateMute({} {}, {}, {})", connectionId, stream.id, track.id(), muteType)
        }

        override fun onChangeStability(connectionId: String, stability: Stability) {
            LOGGER.debug("Client#onChangeStability({}, {})", connectionId, stability)
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
    private fun createFilePath(): String {
        val df = SimpleDateFormat("yyyyMMdd'T'HHmm")
        val timestamp = df.format(Date())
        return getExternalFilesDir(null)!!
                .resolve("logs")
                .apply { mkdir() }
                .resolve(timestamp)
                .absolutePath
    }

    private fun disableBFormat() {
        getSystemService(AudioManager::class.java)!!.setParameters("RicUseBFormat=false")
    }
}
