package com.notification.wechat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class WeChatListenerService : NotificationListenerService() {

    // 【核心修改】定义两个不同的身份证号
    private val CHANNEL_ID_SERVICE = "Channel_KeepAlive"   // 1. 专门给那个烦人的常驻通知用
    private val CHANNEL_ID_MESSAGE = "Channel_WeChat_Msg"  // 2. 专门给转发的微信消息用

    private val FOREGROUND_ID = 9999

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            createNotificationChannels() // 创建两个渠道

            // 【核心修改】这里使用 SERVICE 渠道，用户可以单独屏蔽这个
            val notification = Notification.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle("微信同步服务")
                .setContentText("服务运行中 (可长按关闭此通知)")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()

            // 针对 Android 14+ 的类型声明
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(FOREGROUND_ID, notification)
            }

        } catch (e: Exception) {
            Log.e("WeChatService", "前台服务启动失败: ${e.message}")
        }
        return Service.START_STICKY
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return
            // 过滤掉自己发的两种通知 (防止自己转发自己)
            if (sbn.packageName == packageName) return

            if (sbn.packageName == "com.tencent.mm") {
                Thread {
                    try {
                        checkAndResend(sbn)
                    } catch (e: Exception) {
                        Log.e("WeChatService", "处理通知出错: ${e.message}")
                    }
                }.start()
            }
        } catch (e: Exception) {
            Log.e("WeChatService", "onNotificationPosted 崩溃: ${e.message}")
        }
    }

    private fun checkAndResend(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences("Config", Context.MODE_PRIVATE)
        val targetMac = prefs.getString("mac", "") ?: return
        if (targetMac.isEmpty()) return

        if (!isBluetoothConnected(targetMac)) {
            Log.d("WeChatBypass", "蓝牙未连接，准备重发")
            Handler(Looper.getMainLooper()).post {
                resendNotification(sbn, prefs.getInt("delay", 3))
            }
        }
    }

    private fun isBluetoothConnected(mac: String): Boolean {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) return false
            val device = adapter.getRemoteDevice(mac)
            val method = BluetoothDevice::class.java.getMethod("isConnected")
            return method.invoke(device) as Boolean
        } catch (e: Exception) {
            return false
        }
    }

    private fun resendNotification(sbn: StatusBarNotification, delaySeconds: Int) {
        try {
            val originalNotification = sbn.notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val title = originalNotification.extras.getString(Notification.EXTRA_TITLE) ?: "微信"
            val text = originalNotification.extras.getString(Notification.EXTRA_TEXT) ?: "收到新消息"

            // 【核心修改】这里使用 MESSAGE 渠道，确保这个通知能发出来
            val newNotification = Notification.Builder(this, CHANNEL_ID_MESSAGE)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(originalNotification.contentIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(10086, newNotification)

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    notificationManager.cancel(10086)
                } catch (e: Exception) { }
            }, delaySeconds * 1000L)
        } catch (e: Exception) {
            Log.e("WeChatService", "重发失败: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val restartIntent = Intent(this, BootReceiver::class.java)
            restartIntent.action = "com.notification.wechat.RESTART_SERVICE"
            sendBroadcast(restartIntent)
        } catch (e: Exception) { }
    }

    // 【核心修改】同时创建两个渠道
    private fun createNotificationChannels() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // 1. 保活服务渠道 (建议用户关闭)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "后台保活服务 (可关闭)", // 名字写得直白点
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "此通知用于保持App在后台运行，可以在设置中关闭，不影响转发功能"
                setShowBadge(false)
            }

            // 2. 消息转发渠道 (必须开启)
            val messageChannel = NotificationChannel(
                CHANNEL_ID_MESSAGE,
                "微信消息转发",
                NotificationManager.IMPORTANCE_DEFAULT // 即使静音也需要有展示级别
            ).apply {
                description = "用于显示转发的微信消息"
                enableVibration(false)
                setSound(null, null)
            }

            notificationManager.createNotificationChannels(listOf(serviceChannel, messageChannel))
        } catch (e: Exception) { }
    }
}