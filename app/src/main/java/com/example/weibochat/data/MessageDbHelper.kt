package com.example.weibochat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MessageDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "weibo_chat.db"
        const val DATABASE_VERSION = 6

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

        // Weibos table constants
        const val TABLE_WEIBO = "weibos"
        const val COLUMN_WEIBO_ID = "id"
        const val COLUMN_WEIBO_CREATED_AT_LONG = "created_at_long"
        const val COLUMN_WEIBO_JSON = "content_json"
        const val COLUMN_WEIBO_IS_READ = "is_read"
        const val COLUMN_WEIBO_IS_GAP = "is_gap"
        const val COLUMN_WEIBO_GAP_SINCE_ID = "gap_since_id"
        const val COLUMN_WEIBO_GAP_MAX_ID = "gap_max_id"
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

        val createWeiboTableQuery = """
            CREATE TABLE $TABLE_WEIBO (
                $COLUMN_WEIBO_ID INTEGER PRIMARY KEY,
                $COLUMN_WEIBO_CREATED_AT_LONG INTEGER,
                $COLUMN_WEIBO_JSON TEXT,
                $COLUMN_WEIBO_IS_READ INTEGER DEFAULT 0,
                $COLUMN_WEIBO_IS_GAP INTEGER DEFAULT 0,
                $COLUMN_WEIBO_GAP_SINCE_ID INTEGER,
                $COLUMN_WEIBO_GAP_MAX_ID INTEGER
            )
        """.trimIndent()
        db.execSQL(createWeiboTableQuery)
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
        if (currentVersion < 6) {
            try {
                db.execSQL("""
                    CREATE TABLE $TABLE_WEIBO (
                        $COLUMN_WEIBO_ID INTEGER PRIMARY KEY,
                        $COLUMN_WEIBO_CREATED_AT_LONG INTEGER,
                        $COLUMN_WEIBO_JSON TEXT,
                        $COLUMN_WEIBO_IS_READ INTEGER DEFAULT 0,
                        $COLUMN_WEIBO_IS_GAP INTEGER DEFAULT 0,
                        $COLUMN_WEIBO_GAP_SINCE_ID INTEGER,
                        $COLUMN_WEIBO_GAP_MAX_ID INTEGER
                    )
                """.trimIndent())
                currentVersion = 6
            } catch (e: Exception) {
                e.printStackTrace()
                db.execSQL("DROP TABLE IF EXISTS $TABLE_WEIBO")
                db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
                onCreate(db)
                return
            }
        }
    }
}
