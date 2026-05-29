package com.example.weibochat

import android.app.Application
import com.example.weibochat.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class WeiboChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WeiboChatApplication)
            modules(appModule)
        }
    }
}
