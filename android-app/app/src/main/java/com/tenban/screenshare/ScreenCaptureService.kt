package com.tenban.screenshare

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class ScreenCaptureService : Service() {

    private var webrtcClient: WebRTCClient? = null
    private val CHANNEL_ID = "ScreenShareChannel"
    private val NOTIF_ID = 1001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Đang chia sẻ màn hình")
            .setContentText("Màn hình đang được chia sẻ theo thời gian thực")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        // 1. BẮT BUỘC GỌI STARTFOREGROUND ĐẦU TIÊN (Nếu không Android 14 sẽ kill app)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        // 2. Lấy dữ liệu từ Intent
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        val serverUrl = intent?.getStringExtra("serverUrl") ?: "http://10.42.0.214:3000"
        val roomId = intent?.getStringExtra("roomId") ?: "child-01"

        if (resultCode == Activity.RESULT_OK && data != null) {
            // 3. Chạy WebRTC trên Main Thread (Bắt buộc)
            Handler(Looper.getMainLooper()).post {
                try {
                    Log.d("ScreenCapture", "Bắt đầu khởi tạo WebRTC...")
                    webrtcClient = WebRTCClient(this, serverUrl, roomId)
                    webrtcClient?.start(data)
                    Log.d("ScreenCapture", "WebRTC đã khởi tạo xong.")
                } catch (e: Exception) {
                    Log.e("ScreenCapture", "Lỗi khởi tạo WebRTC: ${e.message}")
                }
            }
        } else {
            Log.e("ScreenCapture", "Không lấy được dữ liệu MediaProjection (resultCode=$resultCode)")
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chia sẻ màn hình",
                NotificationManager.IMPORTANCE_LOW
            )
           getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        webrtcClient?.stop()
        super.onDestroy()
    }
}