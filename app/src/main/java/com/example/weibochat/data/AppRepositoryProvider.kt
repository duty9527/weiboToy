package com.example.weibochat.data

import android.content.Context

object AppRepositoryProvider {
    @Volatile
    private var instance: DefaultDataRepository? = null

    fun get(context: Context): DefaultDataRepository {
        return instance ?: synchronized(this) {
            instance ?: DefaultDataRepository(context.applicationContext).also { instance = it }
        }
    }
}
