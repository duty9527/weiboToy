package com.duty.weibotoy

import android.app.Application
import com.duty.weibotoy.di.appModule
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
