/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.sample.base

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.slf4j.LoggerFactory

class CameraListAdapter(context: Context) : ArrayAdapter<CameraInfo>(context, android.R.layout.simple_spinner_item) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CameraListAdapter::class.java)
    }

    init {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager?
        manager?.let {
            for (cameraId in it.cameraIdList) try {
                this.add(CameraInfo(cameraId, it.getCameraCharacteristics(cameraId)))
            } catch (e: Exception) {
                LOGGER.error("Failed to add camera. ", e)
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val textView = super.getView(position, convertView, parent) as TextView
        textView.text = getItem(position)!!.getName()
        return textView
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val textView = super.getDropDownView(position, convertView, parent) as TextView
        textView.text = getItem(position)!!.getName()
        return textView
    }
}

open class CameraInfo(open val cameraId: String,
                      private val characteristics: CameraCharacteristics) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CameraListAdapter::class.java)
        const val FRONT = "Front"
        const val BACK = "Back"
        const val EXTERNAL = "External"
        const val UNKNOWN = "Unknown Camera"
    }

    fun getName(): String = try {
        getCameraName()
    } catch (e: Exception) {
        LOGGER.error("Failed to get camera name. ", e)
        UNKNOWN
    }

    private fun getCameraName(): String {
        val name = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> FRONT
            CameraCharacteristics.LENS_FACING_BACK -> BACK
            CameraCharacteristics.LENS_FACING_EXTERNAL -> EXTERNAL
            else -> UNKNOWN
        }
        val size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE).toString()
        val pixel = size.split("x")[0].toLong() * size.split("x")[1].toLong()
        return "$name(${(pixel / 1000000)}MP)"
    }
}