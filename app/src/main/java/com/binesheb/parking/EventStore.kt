package com.binesheb.parking

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class EventStore(context: Context) : SQLiteOpenHelper(context, "parking.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, plate TEXT NOT NULL, event_type TEXT NOT NULL, timestamp INTEGER NOT NULL, device_role TEXT NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { }
    fun insert(event: ParkingEvent) { writableDatabase.insert("events", null, ContentValues().apply { put("plate", event.plate); put("event_type", event.eventType); put("timestamp", event.timestamp); put("device_role", event.deviceRole) }) }
    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM events", null).use { if (it.moveToFirst()) it.getLong(0) else 0 }
}
