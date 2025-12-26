package com.notification.wechat

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    // 这一步是为了定义频道ID，要和服务里保持一致
    private val CHANNEL_ID = "WeChat_Bypass_Channel"

    private val PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(Manifest.permission.BLUETOOTH)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. App一启动立刻创建通知渠道！这是解决“设置里打不开”的关键
        createNotificationChannel()

        // 2. 申请权限
        ActivityCompat.requestPermissions(this, PERMISSIONS, 1)

        val etMac = findViewById<EditText>(R.id.etMacAddress)
        val etDelay = findViewById<EditText>(R.id.etDelay)

        val prefs = getSharedPreferences("Config", Context.MODE_PRIVATE)
        etMac.setText(prefs.getString("mac", ""))
        etDelay.setText(prefs.getInt("delay", 3).toString())

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val mac = etMac.text.toString().uppercase().trim()
            val delayStr = etDelay.text.toString().trim()
            val delay = if (delayStr.isEmpty()) 3 else delayStr.toInt()

            prefs.edit().putString("mac", mac).putInt("delay", delay).apply()

            if (mac.isNotEmpty()) {
                val isConnected = checkBluetoothReflect(mac)
                val statusMsg = if (isConnected) "蓝牙已连接 ✅" else "蓝牙未连接 ❌ (通知将转发)"
                Toast.makeText(this, statusMsg, Toast.LENGTH_LONG).show()
            }

            // 引导开启监听权限
            it.postDelayed({
                if (!isNotificationListenerEnabled()) {
                    try {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this, "请手动开启通知权限", Toast.LENGTH_LONG).show()
                    }
                }
            }, 1000)
        }
    }

    private fun createNotificationChannel() {
        // 只有 Android 8.0+ 需要创建渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "微信同步转发" // 统一名字
            val descriptionText = "用于绕过限制转发微信通知"

            // 【关键修改】改为 IMPORTANCE_LOW，即为“静默”
            // 原来可能是 HIGH 或 DEFAULT，改成 LOW 就不会响铃震动了
            val importance = NotificationManager.IMPORTANCE_LOW

            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                // 显式关闭震动和声音（双重保险）
                enableVibration(false)
                setSound(null, null)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun checkBluetoothReflect(mac: String): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) return false
        try {
            val device = adapter.getRemoteDevice(mac)
            val method = BluetoothDevice::class.java.getMethod("isConnected")
            return method.invoke(device) as Boolean
        } catch (e: Exception) {
            return false
        }
    }
}