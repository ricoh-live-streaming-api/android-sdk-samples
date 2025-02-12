/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.theta

import android.content.Intent
import android.os.Bundle
import com.ricoh.livestreaming.adaptivetransfer.AdaptiveSendingMode
import com.ricoh.livestreaming.adaptivetransfer.AdaptiveSendingOption
import org.slf4j.LoggerFactory

class AdaptiveSendingActivity : BaseActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AdaptiveSendingActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LOGGER.debug("AdaptiveSendingActivity#onCreate")

        mActivityMainBinding.activityChangeButton.text = ChangeFormatActivity::class.java.simpleName
        mActivityMainBinding.activityChangeButton.setOnClickListener {
            setAutoClose(false)
            startActivity(Intent(applicationContext, ChangeFormatActivity::class.java))
            finish()
        }
    }

    override fun eventOnOpen() {
        super.eventOnOpen()

        //DMC有効（解像度優先モード）
        mClient?.setAdaptiveSendingCapturer(capturer)
        mClient?.changeAdaptiveSendingMode(AdaptiveSendingOption.Builder(AdaptiveSendingMode.BEST_RESOLUTION).build())
        LOGGER.info("changeAdaptiveSendingMode(mode={})", AdaptiveSendingMode.BEST_RESOLUTION)
    }
}
