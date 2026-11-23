
package com.poolaim

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import android.view.Gravity
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.rgb(20, 24, 32))
        }

        val title = TextView(this).apply {
            text = "🎱 POOL AIM"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(10, 30, 10, 30)
        }

        val permissionButton = Button(this).apply {
            text = "GRANT OVERLAY PERMISSION"

            setOnClickListener {
                openOverlayPermission()
            }
        }

        val startButton = Button(this).apply {
            text = "START POOL AIM"

            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startOverlay()
                } else {
                    openOverlayPermission()
                }
            }
        }

        val stopButton = Button(this).apply {
            text = "STOP OVERLAY"

            setOnClickListener {
                stopService(
                    Intent(
                        this@MainActivity,
                        OverlayService::class.java
                    )
                )

                updateStatus()
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(permissionButton)
        layout.addView(startButton)
        layout.addView(stopButton)

        setContentView(layout)

        updateStatus()
    }

    private fun requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= 33) {

            if (checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ),
                    100
                )
            }
        }
    }

    private fun openOverlayPermission() {

        if (!Settings.canDrawOverlays(this)) {

            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )

            startActivity(intent)

        } else {
            updateStatus()
        }
    }

    private fun startOverlay() {

        val intent = Intent(
            this,
            OverlayService::class.java
        ).apply {
            action = OverlayService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        status.text = "Pool Aim overlay is running"
    }

    private fun updateStatus() {

        val overlayGranted =
            Settings.canDrawOverlays(this)

        status.text = if (overlayGranted) {
            "Overlay Permission: GRANTED\nReady to start"
        } else {
            "Overlay Permission: NOT GRANTED\nPlease allow permission"
        }
    }

    override fun onResume() {
        super.onResume()

        if (::status.isInitialized) {
            updateStatus()
        }
    }
}
