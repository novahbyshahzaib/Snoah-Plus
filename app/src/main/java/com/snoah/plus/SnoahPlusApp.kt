package com.snoah.plus

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.snoah.plus.playback.DynamicBarManager
import com.snoah.plus.playback.PlayerManager

class SnoahPlusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlayerManager.init(this)
        // Track whether any Snoah Plus screen is in the foreground:
        // the floating dynamic bar only shows over other apps, never
        // on top of the app's own player screens.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumedCount = 0

            override fun onActivityResumed(activity: Activity) {
                resumedCount++
                DynamicBarManager.setAppInForeground(resumedCount > 0)
            }

            override fun onActivityPaused(activity: Activity) {
                resumedCount = (resumedCount - 1).coerceAtLeast(0)
                DynamicBarManager.setAppInForeground(resumedCount > 0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
