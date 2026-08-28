package com.binesheb.parking

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class EventStore(context: Context) : SQLiteOpenHelper(context, "parking.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT UNIQUE NOT NULL, plate TEXT NOT NULL, event_type TEXT NOT NULL, timestamp INTEGER NOT NULL, device_role TEXT NOT NULL, synced INTEGER NOT NULL DEFAULT 0)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion < 2) { db.execSQL("ALTER TABLE events ADD COLUMN event_id TEXT"); db.execSQL("ALTER TABLE events ADD COLUMN synced INTEGER NOT NULL DEFAULT 0"); db.execSQL("UPDATE events SET event_id=id WHERE event_id IS NULL") } }
    fun insert(event: ParkingEvent): ParkingEvent { val id = event.id.ifBlank { UUID.randomUUID().toString() }; val saved = event.copy(id = id); writableDatabase.insertWithOnConflict("events", null, ContentValues().apply { put("event_id", id); put("plate", saved.plate); put("event_type", saved.eventType); put("timestamp", saved.timestamp); put("device_role", saved.deviceRole); put("synced", 0) }, SQLiteDatabase.CONFLICT_IGNORE); return saved }
    fun markSynced(eventId: String) { writableDatabase.update("events", ContentValues().apply { put("synced", 1) }, "event_id=?", arrayOf(eventId)) }
    fun pending(): List<ParkingEvent> = readableDatabase.rawQuery("SELECT event_id,plate,event_type,timestamp,device_role FROM events WHERE synced=0 ORDER BY timestamp ASC LIMIT 100", null).use { c -> buildList { while (c.moveToNext()) add(ParkingEvent(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4))) } }
    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM events", null).use { if (it.moveToFirst()) it.getLong(0) else 0 }
    fun currentParkingCount(): Int {
        val states = mutableMapOf<String, Boolean>()
        readableDatabase.rawQuery("SELECT plate,event_type,timestamp FROM events ORDER BY timestamp ASC,id ASC", null).use { c -> while (c.moveToNext()) { val plate = c.getString(0); when (c.getString(1)) { "PARKING_IN" -> states[plate] = true; "PARKING_OUT", "COMPOUND_OUT" -> states[plate] = false } } }
        return states.values.count { it }
    }
}
