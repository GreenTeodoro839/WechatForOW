package com.notification.wechat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.content.pm.ServiceInfo
import android.os.Build

class WeChatListenerService : NotificationListenerService() {

    private val CHANNEL_ID = "WeChat_Bypass_Channel"
    private val FOREGROUND_ID = 9999

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. 开始 try 块
        try {
            createNotificationChannel()
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("微信同步服务运行中")
                .setContentText("正在后台运行")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()

            // 2. 针对 Android 14 的前台服务类型检查
            if (Build.VERSION.SDK_INT >= 34) {
                // 如果你还在用 SDK 33 编译但想保留这行，可能会报红，建议先改 build.gradle 到 34
                // 如果暂时改不了 SDK，可以把下面这个 if 块删掉，只保留 else 里的内容
                startForeground(
                    FOREGROUND_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(FOREGROUND_ID, notification)
            }

        } catch (e: Exception) { // 3. 这里的 catch 必须紧跟在上面的 } 后面
            // 4. 修复了 Log 报错：去掉了 tag= 和 msg=
            Log.e("WeChatService", "前台服务启动失败: ${e.message}")
        }

        return Service.START_STICKY
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName

        // 核心过滤：只看微信，且排除自己发的重发通知（防止死循环）
        if (packageName == "com.tencent.mm" && sbn.id != 10086) {
            checkAndResend(sbn)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 发送广播尝试重启自己
        val restartIntent = Intent(this, BootReceiver::class.java)
        restartIntent.action = "com.notification.wechat.RESTART_SERVICE"
        sendBroadcast(restartIntent)
    }

    private fun checkAndResend(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences("Config", Context.MODE_PRIVATE)
        val targetMac = prefs.getString("mac", "") ?: return
        if (targetMac.isEmpty()) return

        // 蓝牙反射检测
        if (!isBluetoothConnected(targetMac)) {
            Log.d("WeChatBypass", "蓝牙未连接，准备重发")
            resendNotification(sbn, prefs.getInt("delay", 3))
        } else {
            Log.d("WeChatBypass", "蓝牙已连接，忽略")
        }
    }

    private fun isBluetoothConnected(mac: String): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) return false

        try {
            val device = adapter.getRemoteDevice(mac)
            // ColorOS 专用的反射大法，检测隐藏的 API
            val method = BluetoothDevice::class.java.getMethod("isConnected")
            return method.invoke(device) as Boolean
        } catch (e: Exception) {
            // 如果反射失败，尝试备用方案：检查是否Bonded（虽然不代表Connected，但在某些rom上是唯一能获取的状态）
            // 这里为了稳妥，如果报错默认当做未连接，或者你可以根据情况调整
            Log.e("WeChatBypass", "反射检测失败: ${e.message}")
            return false
        }
    }

    private fun resendNotification(sbn: StatusBarNotification, delaySeconds: Int) {
        val originalNotification = sbn.notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 克隆通知
        val newNotification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(originalNotification.extras.getString(Notification.EXTRA_TITLE))
            .setContentText(originalNotification.extras.getString(Notification.EXTRA_TEXT))
            .setSmallIcon(R.mipmap.ic_launcher)
            // 关键：复制原始的 PendingIntent，这样点击就能跳回微信
            .setContentIntent(originalNotification.contentIntent)
            .setAutoCancel(true)
            .build()

        // 发送 ID 为 10086 的通知
        notificationManager.notify(10086, newNotification)

        // N秒后自动删除
        Handler(Looper.getMainLooper()).postDelayed({
            notificationManager.cancel(10086)
            Log.d("WeChatBypass", "通知已自动移除")
        }, delaySeconds * 1000L)
    }

    private fun createNotificationChannel() {
        val name = "微信同步转发" // 确保名字一致

        // 【关键修改】改为 IMPORTANCE_LOW
        val importance = NotificationManager.IMPORTANCE_LOW

        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        channel.description = "用于绕过限制转发微信通知"

        // 显式关闭震动和声音
        channel.enableVibration(false)
        channel.setSound(null, null)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}