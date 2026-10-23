package io.github.krnelpan1c.droidpod

import android.app.Application
import android.content.Context

class DroidPodApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as DroidPodApp).container
