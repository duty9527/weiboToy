package com.example.weibochat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MessageDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "weibo_chat.db"
        const val DATABASE_VERSION = 5

        const val TABLE_NAME = "messages"
        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_SENDER_NAME = "sender_name"
        const val COLUMN_GROUP_SUFFIX = "group_suffix"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_CONTEXT_ID = "context_id"
        const val COLUMN_IMAGE_URL = "image_url"
        const val COLUMN_LINK_TITLE = "link_title"
        const val COLUMN_LINK_DESC = "link_desc"
        const val COLUMN_LINK_IMG = "link_img"
        const val COLUMN_LINK_URL = "link_url"
        const val COLUMN_FILE_URL = "file_url"
        const val COLUMN_FILE_NAME = "file_name"
        const val COLUMN_GROUP_ID = "group_id"
        const val COLUMN_PARENT_MSG_ID = "parent_msg_id"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_TIMESTAMP TEXT,
                $COLUMN_SENDER_NAME TEXT,
                $COLUMN_GROUP_SUFFIX TEXT,
                $COLUMN_CONTENT TEXT,
                $COLUMN_CONTEXT_ID INTEGER,
                $COLUMN_IMAGE_URL TEXT,
                $COLUMN_LINK_TITLE TEXT,
                $COLUMN_LINK_DESC TEXT,
                $COLUMN_LINK_IMG TEXT,
                $COLUMN_LINK_URL TEXT,
                $COLUMN_FILE_URL TEXT,
                $COLUMN_FILE_NAME TEXT,
                $COLUMN_GROUP_ID TEXT DEFAULT '4761715839862414',
                $COLUMN_PARENT_MSG_ID INTEGER
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var currentVersion = oldVersion
        if (currentVersion < 4) {
            try {
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_GROUP_ID TEXT DEFAULT '4761715839862414'")
                currentVersion = 4
            } catch (e: Exception) {
                e.printStackTrace()
                db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
                onCreate(db)
                return
            }
        }
        if (currentVersion < 5) {
            try {
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_PARENT_MSG_ID INTEGER")
                currentVersion = 5
            } catch (e: Exception) {
                e.printStackTrace()
                db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
                onCreate(db)
                return
            }
        }
    }
}
