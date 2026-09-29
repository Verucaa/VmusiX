package com.zaaam.vmusix

import android.app.Application
import android.util.Log
import com.zaaam.vmusix.core.CrashLog
import com.zaaam.vmusix.data.youtube.OkHttpDownloader
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe

@HiltAndroidApp
class VmusiXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this, Thread.getDefaultUncaughtExceptionHandler())
        // NewPipeExtractor wajib init downloader sekali. Kalau gagal, fitur
        // YouTube mati total — karena itu app TETAP jalan untuk musik lokal
        // (cuma tab Search/Trending yang kosong, dengan pesan error).
        try {
            NewPipe.init(OkHttpDownloader())
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("VmusiXApp", "NewPipe init gagal — fitur YouTube nonaktif", e)
        }
    }
}
