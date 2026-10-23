package dev.aezochka.glimpse

import android.app.Application

class GlimpseApp : Application() {
    lateinit var repository: MediaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        repository = MediaRepository(this)
    }
}
