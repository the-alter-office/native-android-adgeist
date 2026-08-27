package com.adgeistkit.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

internal class AnalyticsRetryQueueStore(context: Context) {

    companion object {
        const val MAX_QUEUED = 200
    }

    private val dbHelper = AnalyticsRetryQueueDbHelper(context)

    fun insertAndTrim(url: String, body: String, nextAttemptAt: Long = 0L): Long {
        val db = dbHelper.writableDatabase
        db.beginTransaction()

        try {
            val values = ContentValues().apply {
                put(AnalyticsRetryQueueDbHelper.COL_URL, url)
                put(AnalyticsRetryQueueDbHelper.COL_BODY, body)
                put(AnalyticsRetryQueueDbHelper.COL_CREATED_AT, System.currentTimeMillis())
                put(AnalyticsRetryQueueDbHelper.COL_NEXT_ATTEMPT_AT, nextAttemptAt)
            }

            val id = db.insertOrThrow(AnalyticsRetryQueueDbHelper.TABLE, null, values)

            db.execSQL(
                """
                DELETE FROM ${AnalyticsRetryQueueDbHelper.TABLE}
                WHERE ${AnalyticsRetryQueueDbHelper.COL_ID} IN (
                    SELECT ${AnalyticsRetryQueueDbHelper.COL_ID} FROM ${AnalyticsRetryQueueDbHelper.TABLE}
                    ORDER BY ${AnalyticsRetryQueueDbHelper.COL_ID} ASC
                    LIMIT MAX(0, (SELECT COUNT(*) FROM ${AnalyticsRetryQueueDbHelper.TABLE}) - $MAX_QUEUED)
                )
                """.trimIndent()
            )
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun getAll(): List<QueuedAnalyticsRequest> = read(null, null)

    fun getDue(nowMillis: Long): List<QueuedAnalyticsRequest> = read(
        "${AnalyticsRetryQueueDbHelper.COL_NEXT_ATTEMPT_AT} <= ?",
        arrayOf(nowMillis.toString())
    )

    fun markRetry(id: Long, nextAttemptAtMillis: Long) {
        dbHelper.writableDatabase.execSQL(
            """
            UPDATE ${AnalyticsRetryQueueDbHelper.TABLE}
            SET ${AnalyticsRetryQueueDbHelper.COL_REATTEMPTS} = ${AnalyticsRetryQueueDbHelper.COL_REATTEMPTS} + 1,
                ${AnalyticsRetryQueueDbHelper.COL_NEXT_ATTEMPT_AT} = ?
            WHERE ${AnalyticsRetryQueueDbHelper.COL_ID} = ?
            """.trimIndent(),
            arrayOf<Any>(nextAttemptAtMillis, id)
        )
    }

    fun earliestNextAttempt(): Long? =
        dbHelper.readableDatabase.rawQuery(
            "SELECT MIN(${AnalyticsRetryQueueDbHelper.COL_NEXT_ATTEMPT_AT}) FROM ${AnalyticsRetryQueueDbHelper.TABLE}",
            null
        ).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
        }

    fun deleteById(id: Long) {
        dbHelper.writableDatabase.delete(
            AnalyticsRetryQueueDbHelper.TABLE, "${AnalyticsRetryQueueDbHelper.COL_ID} = ?", arrayOf(id.toString())
        )
    }

    fun count(): Int =
        dbHelper.readableDatabase.rawQuery("SELECT COUNT(*) FROM ${AnalyticsRetryQueueDbHelper.TABLE}", null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun read(selection: String?, args: Array<String>?): List<QueuedAnalyticsRequest> {
        dbHelper.readableDatabase.query(
            AnalyticsRetryQueueDbHelper.TABLE,
            arrayOf(
                AnalyticsRetryQueueDbHelper.COL_ID,
                AnalyticsRetryQueueDbHelper.COL_URL,
                AnalyticsRetryQueueDbHelper.COL_BODY,
                AnalyticsRetryQueueDbHelper.COL_REATTEMPTS,
            ),
            selection, args, null, null,
            "${AnalyticsRetryQueueDbHelper.COL_ID} ASC"
        ).use { cursor ->
            return cursor.toRows()
        }
    }

    private fun Cursor.toRows(): List<QueuedAnalyticsRequest> {
        val idIdx = getColumnIndexOrThrow(AnalyticsRetryQueueDbHelper.COL_ID)
        val urlIdx = getColumnIndexOrThrow(AnalyticsRetryQueueDbHelper.COL_URL)
        val bodyIdx = getColumnIndexOrThrow(AnalyticsRetryQueueDbHelper.COL_BODY)
        val reattemptsIdx = getColumnIndexOrThrow(AnalyticsRetryQueueDbHelper.COL_REATTEMPTS)

        val result = ArrayList<QueuedAnalyticsRequest>(count)
        while (moveToNext()) {
            result.add(
                QueuedAnalyticsRequest(
                    getLong(idIdx),
                    getString(urlIdx),
                    getString(bodyIdx),
                    getInt(reattemptsIdx),
                )
            )
        }
        return result
    }
}
