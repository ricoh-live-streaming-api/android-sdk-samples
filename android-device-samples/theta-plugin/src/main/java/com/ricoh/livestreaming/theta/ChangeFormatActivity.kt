/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.theta

import android.content.Intent
import android.os.Bundle
import org.apache.commons.collections4.IteratorUtils
import org.slf4j.LoggerFactory

class ChangeFormatActivity : BaseActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ChangeFormatActivity::class.java)

        private val CAPTURE_FORMATS = IteratorUtils.loopingIterator(listOf(
                CaptureFormat(ShootingMode.RIC_MOVIE_PREVIEW_3840, StitchingMode.RIC_STATIC_STITCHING, 20),
                CaptureFormat(ShootingMode.RIC_MOVIE_PREVIEW_3840, StitchingMode.RIC_STATIC_STITCHING, 10),
                CaptureFormat(ShootingMode.RIC_MOVIE_PREVIEW_1920, StitchingMode.RIC_STATIC_STITCHING, 30)
        ))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LOGGER.debug("ChangeFormatActivity#onCreate")
        mActivityMainBinding.activityChangeButton.text = AdaptiveSendingActivity::class.java.simpleName
        mActivityMainBinding.activityChangeButton.setOnClickListener {
            setAutoClose(false)
            startActivity(Intent(applicationContext, AdaptiveSendingActivity::class.java))
            finish()
        }
        CAPTURE_FORMATS.reset()
        setResolutionToTextView(CAPTURE_FORMATS.next())
        mActivityMainBinding.currentBitrateText.text = "${BuildConfig.VIDEO_BITRATE}kbps"
    }

    override fun onMediaRecordKeyDown() {
        super.onMediaRecordKeyDown()

        try {
            updateCaptureFormat(CAPTURE_FORMATS.next())
        } catch (e: Exception) {
            LOGGER.error("Failed to updateCaptureFormat.", e)
        }
    }

    private fun updateCaptureFormat(format: CaptureFormat) {
        LOGGER.info("update capture format.")
        capturer?.updateCaptureFormat(format)
        setResolutionToTextView(format)
        notificationAudioSelf()
    }

    private fun setResolutionToTextView(format: CaptureFormat) {
        mActivityMainBinding.currentResolutionText.text = "${format.shootingMode.width}x${format.shootingMode.height} ${format.fps}fps"
    }

    override fun eventOnOpen() {
        super.eventOnOpen()
    }
}
