package x.vladgba.strips.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Entry(
    val id: Long,
    val format: String,
    val text: String,
    val source: Int,
    val engine: String?,
    val time: Long,
) {
    companion object {
        const val SCANNED = 0
        const val GENERATED = 1
    }
}

/** "Last" list, stored in a local SQLite database (no backup, no network). */
class History private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE codes (id INTEGER PRIMARY KEY AUTOINCREMENT, format TEXT NOT NULL, " +
                "text TEXT NOT NULL, source INTEGER NOT NULL, engine TEXT, time INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX codes_time ON codes(time DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Adds a code; an identical existing one (same format + text) moves to the top instead. */
    fun add(format: String, text: String, source: Int, engine: String?): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("codes", "format = ? AND text = ?", arrayOf(format, text))
            val id = db.insert("codes", null, ContentValues().apply {
                put("format", format); put("text", text); put("source", source)
                put("engine", engine); put("time", System.currentTimeMillis())
            })
            db.execSQL("DELETE FROM codes WHERE id NOT IN (SELECT id FROM codes ORDER BY time DESC LIMIT $LIMIT)")
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun all(): List<Entry> =
        readableDatabase.query("codes", null, null, null, null, null, "time DESC").use { c ->
            buildList { while (c.moveToNext()) add(c.entry()) }
        }

    fun get(id: Long): Entry? =
        readableDatabase.query("codes", null, "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) c.entry() else null
        }

    fun delete(id: Long) {
        writableDatabase.delete("codes", "id = ?", arrayOf(id.toString()))
    }

    fun clear() {
        writableDatabase.delete("codes", null, null)
    }

    private fun Cursor.entry() = Entry(
        getLong(getColumnIndexOrThrow("id")),
        getString(getColumnIndexOrThrow("format")),
        getString(getColumnIndexOrThrow("text")),
        getInt(getColumnIndexOrThrow("source")),
        getString(getColumnIndexOrThrow("engine")),
        getLong(getColumnIndexOrThrow("time")),
    )

    companion object {
        private const val LIMIT = 1000
        @Volatile private var instance: History? = null
        fun get(ctx: Context): History = instance ?: synchronized(this) {
            instance ?: History(ctx).also { instance = it }
        }
    }
}
