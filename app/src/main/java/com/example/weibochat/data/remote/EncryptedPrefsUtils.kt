package com.example.weibochat.data

import android.content.Context

fun migrateToEncryptedPrefs(
    context: Context,
    oldPrefsName: String,
    newPrefsName: String
): android.content.SharedPreferences {
    val masterKey = androidx.security.crypto.MasterKey.Builder(context)
        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
        .build()

    val encryptedPrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
        context,
        newPrefsName,
        masterKey,
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Migrate data from old unencrypted prefs if present
    val oldPrefs = context.getSharedPreferences(oldPrefsName, Context.MODE_PRIVATE)
    if (oldPrefs.all.isNotEmpty()) {
        val editor = encryptedPrefs.edit()
        for ((key, value) in oldPrefs.all) {
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
            }
        }
        editor.apply()
        oldPrefs.edit().clear().apply()
    }

    return encryptedPrefs
}
