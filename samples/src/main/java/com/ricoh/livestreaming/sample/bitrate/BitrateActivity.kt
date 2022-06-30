/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.bitrate

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.ricoh.livestreaming.SDKError
import com.ricoh.livestreaming.sample.base.BaseActivity
import com.ricoh.livestreaming.sample.base.BaseViewBinding
import com.ricoh.livestreaming.sample.databinding.BitrateBinding
import org.slf4j.LoggerFactory

class BitrateActivity : BaseActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(BitrateActivity::class.java)
    }
    private lateinit var mViewBinding: BitrateBinding

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewBinding = BitrateBinding.inflate(layoutInflater)
        setContentView(mViewBinding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        supportActionBar!!.hide()

        setBaseView(BaseViewBinding(
                mViewBinding.viewsLayout.viewLayout,
                mViewBinding.controlsLayout.roomIdText,
                mViewBinding.controlsLayout.audioListSpinner,
                mViewBinding.controlsLayout.cameraListSpinner,
                mViewBinding.controlsLayout.connectButton
        ))

        /**
         * ここから Bitrate サンプル
         */
        mViewBinding.bitrateSpinner.adapter = BitrateAdapter(this)
        mViewBinding.bitrateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) { /** このサンプルでは何もしません。 */ }
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val bitrate = mViewBinding.bitrateSpinner.selectedItem.toString().toInt()
                LOGGER.info("onItemSelected: $bitrate")
                if (isClientOpen()) {
                    try {
                        /** 選択された bitrate を指定して changeVideoSendBitrate API を利用します。 */
                        mClient?.changeVideoSendBitrate(bitrate)
                    } catch (e: SDKError) {
                        /**
                         * connect 時に指定している bitrate よりも大きい値、
                         * もしくは 99 以下および 20001 以上を指定するとエラーとなります。
                         */
                        LOGGER.error("Failed to change video send bitrate.{}", e.toReportString())
                    }
                } else {
                    /** Open 以外の State では次回の接続向け maxBitrateKbps として選択値を反映します。 */
                    mConnectOption.maxBitrateKbps = bitrate
                }
            }
        }
        /** 初期接続設定の maxBitrateKbps に bitrate スピナーの選択値を反映します。 */
        mConnectOption.maxBitrateKbps = mViewBinding.bitrateSpinner.selectedItem.toString().toInt()
    }

    /**
     * bitrate スピナー用アダプタークラスです。
     */
    class BitrateAdapter(context: Context) : ArrayAdapter<Int>(context, android.R.layout.simple_spinner_item) {
        init {
            /** bitrate 選択肢を設定します。 */
            add(2000)
            add(20001)
            add(20000)
            add(100)
            add(99)
        }
    }
}
