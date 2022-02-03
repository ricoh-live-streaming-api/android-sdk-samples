/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.wearable_glass

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ricoh.livestreaming.wearable_glass.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private val REQUIRED_PERMISSIONS = listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        )
    }

    /** View Binding */
    private lateinit var mActivityMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mActivityMainBinding.root)
    }

    override fun onResume() {
        super.onResume()

        val notGrantedPermission = REQUIRED_PERMISSIONS.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGrantedPermission.isNotEmpty()) {
            requestPermissions(notGrantedPermission.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            if (Preference.getRoomId(applicationContext).isEmpty()) {
                startActivity(Intent(applicationContext, SettingActivity::class.java))
            } else {
                startActivity(Intent(applicationContext, LiveStreamingActivity::class.java))
            }
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermission = REQUIRED_PERMISSIONS.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (deniedPermission.isNotEmpty()) {
                Toast.makeText(applicationContext, getString(R.string.permission_error_message), Toast.LENGTH_LONG).show()
                finish()
            } else {
                if (Preference.getRoomId(applicationContext).isEmpty()) {
                    startActivity(Intent(applicationContext, SettingActivity::class.java))
                } else {
                    startActivity(Intent(applicationContext, LiveStreamingActivity::class.java))
                }
                finish()
            }
        }
    }
}
