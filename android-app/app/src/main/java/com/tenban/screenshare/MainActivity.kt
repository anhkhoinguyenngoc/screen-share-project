package com.tenban.screenshare

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.ActivityResultLauncher

class MainActivity : AppCompatActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val startCaptureLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        val resultCode = result.resultCode
        val data = result.data
        if (resultCode == android.app.Activity.RESULT_OK && data != null) {
            startScreenCaptureService(resultCode, data)
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val btnStart: Button = findViewById(R.id.btn_start_share)
        btnStart.setOnClickListener {
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            startCaptureLauncher.launch(intent)
        }
    }

    private fun startScreenCaptureService(resultCode: Int, data: Intent?) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("resultCode", resultCode)
            data?.let { putExtra("data", it) }
            putExtra("serverUrl", "http://10.42.0.214:3000")
            putExtra("roomId", "child-01")
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
