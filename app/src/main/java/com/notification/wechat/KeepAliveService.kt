package com.notification.wechat

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class KeepAliveService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理任何事件，空着就行
    }

    override fun onInterrupt() {
        // 服务被打断时的回调
    }
}