/*
 * Copyright 2021 Ricoh Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.theta

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.slf4j.LoggerFactory

class Attitude(private val mSensorManager: SensorManager) : SensorEventListener  {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(Attitude::class.java)

        private const val RAD2DEG = (180/Math.PI)
    }

    private val rotationMatrix = FloatArray(9)
    private val curAttitudeVal = FloatArray(3)
    private var isStarted = false

    @Synchronized
    fun start() {
        if (isStarted) {
            // already started
            return
        }
        LOGGER.debug("start")

        val sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        isStarted = true
    }

    @Synchronized
    fun stop() {
        if (!isStarted) {
            // already stopped
            return
        }

        LOGGER.debug("stop")
        val sensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        mSensorManager.unregisterListener(this, sensor)
        isStarted = false
    }

    @Synchronized
    fun getDegYaw(): Float {
        return (curAttitudeVal[0] * RAD2DEG).toFloat()
    }

    @Synchronized
    fun getDegPitch(): Float {
        return (curAttitudeVal[1] * RAD2DEG).toFloat()
    }

    @Synchronized
    fun getDegRoll(): Float {
        return (curAttitudeVal[2] * RAD2DEG).toFloat()
    }

    @Synchronized
    private fun getOrientation(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, curAttitudeVal)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            getOrientation(event)
        }
    }
}
