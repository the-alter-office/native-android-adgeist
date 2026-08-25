package com.adgeistkit.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class AnalyticsRetryQueueDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "adgeist_analytics_retry_queue.db"
        private const val DB_VERSION = 1
        const val TABLE = "queued_requests"
        const val COL_ID = "id"
        const val COL_URL = "url"
        const val COL_BODY = "body"
        const val COL_CREATED_AT = "created_at"
        const val COL_REATTEMPTS = "reattempts"
        const val COL_NEXT_ATTEMPT_AT = "next_attempt_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_URL TEXT NOT NULL,
                $COL_BODY TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_REATTEMPTS INTEGER NOT NULL DEFAULT 0,
                $COL_NEXT_ATTEMPT_AT INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No prior versions exist yet.
    }
}
