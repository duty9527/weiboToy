package com.example.weibochat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, WeiboEntity::class],
    version = 8,
    exportSchema = true
)
abstract class WeiboDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun weiboDao(): WeiboDao

    companion object {
        fun create(context: Context): WeiboDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                WeiboDatabase::class.java,
                "weibo_chat.db"
            )
                .addMigrations(MIGRATION_7_8)
                .build()
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema is already compatible. Room will create its
                // room_master_table on first open for schema tracking.
                // The existing tables match the Room entity definitions.
            }
        }
    }
}
