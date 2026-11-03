package com.snoah.plus

import android.app.Application
import com.snoah.plus.playback.PlayerManager

class SnoahPlusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlayerManager.init(this)
    }
}
