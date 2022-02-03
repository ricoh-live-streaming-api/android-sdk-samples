/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.selective

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import com.ricoh.livestreaming.SDKError
import com.ricoh.livestreaming.VideoRequirement
import com.ricoh.livestreaming.sample.base.BaseActivity
import com.ricoh.livestreaming.sample.base.BaseViewBinding
import com.ricoh.livestreaming.sample.databinding.SelectiveBinding
import org.slf4j.LoggerFactory

class SelectiveActivity : BaseActivity() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(SelectiveActivity::class.java)
    }
    private lateinit var mViewBinding: SelectiveBinding

    private val mConnectionIdList = arrayListOf<String>()

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewBinding = SelectiveBinding.inflate(layoutInflater)
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
         * ここから Selective サンプル
         */
        mViewBinding.firstInboundVideoSelectiveCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isClientOpen()) {
                try {
                    /** SDK で定義している Enum VideoRequirement を用いて videoRequirement を決定します。 */
                    val videoRequirement = if (isChecked) {
                        VideoRequirement.REQUIRED
                    } else {
                        VideoRequirement.UNREQUIRED
                    }
                    if (mConnectionIdList.isNotEmpty()) {
                        /** 最初に接続できたリモートクライアントを対象とし videoRequirement を指定して changeMediaRequirements API を利用します。 */
                        mClient?.changeMediaRequirements(mConnectionIdList[0], videoRequirement)
                    }
                } catch (e: SDKError) {
                    LOGGER.error("Failed to change media requirement.{}", e.toReportString())
                }
            }
        }
    }

    /** ここから Client.Listener へのイベントハンドラを記載します。 */

    /** 相手クライアントの接続が完了したときに発火するイベント処理です。 */
    override fun eventOnAddRemoteConnection(connectionId: String, metadata: Map<String, Any>) {
        /**
         * 接続できた相手クライアントの Connection ID はこのイベントで通知されます。
         * この ConnectionId を管理すれば接続中の相手クライアントを識別できます。
         */
        mConnectionIdList.add(connectionId)
    }
}
