package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AutoClickService : AccessibilityService() {

    companion object {
        var instance: AutoClickService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AutoClickService", "Accessibility Service Connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /**
     * اجرای کلیک‌های متوالی (Burst Click)
     * @param x مختصات افقی
     * @param y مختصات عمودی
     * @param totalClicks تعداد دفعات کلیک (پیش‌فرض 5)
     * @param intervalMs فاصله بین کلیک‌ها بر حسب میلی‌ثانیه (10 میلی‌ثانیه = 0.01 ثانیه)
     */
    fun performBurstClick(x: Float, y: Float, totalClicks: Int = 5, intervalMs: Long = 10L, onFinished: (() -> Unit)? = null) {
        var completedClicks = 0

        fun dispatchSingleClick() {
            if (completedClicks >= totalClicks) {
                onFinished?.invoke()
                return
            }

            val path = Path().apply { moveTo(x, y) }
            // مدت زمان نگه‌داشتن لمس: حداقل 1 میلی‌ثانیه برای ثبت سریع
            val stroke = GestureDescription.StrokeDescription(path, 0, 1)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    completedClicks++
                    if (completedClicks < totalClicks) {
                        handler.postDelayed({ dispatchSingleClick() }, intervalMs)
                    } else {
                        onFinished?.invoke()
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w("AutoClickService", "Gesture cancelled at index: $completedClicks")
                    completedClicks++
                    if (completedClicks < totalClicks) {
                        handler.postDelayed({ dispatchSingleClick() }, intervalMs)
                    } else {
                        onFinished?.invoke()
                    }
                }
            }, null)
        }

        dispatchSingleClick()
    }
}
