/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */
package com.ricoh.livestreaming.setting_app

import android.os.Bundle
import android.util.AndroidRuntimeException
import android.util.Base64
import android.util.Base64.DEFAULT
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.ricoh.livestreaming.setting_app.databinding.ActivityQrcodeBinding
import java.nio.charset.StandardCharsets


class QRCodeActivity : AppCompatActivity() {
    /** View Binding */
    private lateinit var mActivityQRCodeBinding: ActivityQrcodeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mActivityQRCodeBinding = ActivityQrcodeBinding.inflate(layoutInflater)
        setContentView(mActivityQRCodeBinding.root)

        val param = intent.getStringExtra(MainActivity.INTENT_PARAM)
        createQRCode(param!!, 500)
    }

    private fun createQRCode(data: String, size: Int) {
        val charset = StandardCharsets.UTF_8
        val bytes = Base64.encode(data.toByteArray(), DEFAULT)

        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q)

        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(String(bytes, charset), BarcodeFormat.QR_CODE, size, size, hints)
            mActivityQRCodeBinding.qrcodeImage.setImageBitmap(bitmap)

        } catch (e: WriterException) {
            throw AndroidRuntimeException("Barcode Error.", e)
        }
    }
}
