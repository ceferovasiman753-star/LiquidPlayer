package com.liquid.player

import android.app.Application
import com.liquid.player.download.YouTubeDownloader
import kotlin.concurrent.thread

class LiquidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        thread(name = "ytdlp-init") {
            YouTubeDownloader.init(this)
            YouTubeDownloader.updateYtDlp(this)
        }
    }
}
