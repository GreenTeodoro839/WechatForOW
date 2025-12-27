package com.notification.wechat

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

class KeepAliveService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 因为下面把事件设为了 0，这里永远不会被调用
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // 【核心骚操作】
        // 获取当前的配置
        val info = serviceInfo

        // 直接把事件类型设为 0（代表什么都不监听）
        // 这样 Binder 连一个字节的数据都不会发过来，真正的“零开销”
        info.eventTypes = 0

        // 只要反馈类型还在，服务就是合法的
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

        // 应用修改后的配置
        serviceInfo = info

        // 顺手拉起前台服务
        // try { startService(...) } catch (e) {}
    }
}