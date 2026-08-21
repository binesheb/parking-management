package com.binesheb.parking

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class SyncClient(context: Context) {
    private val appContext = context.applicationContext; private val executor = Executors.newSingleThreadExecutor(); private val store = EventStore(appContext)
    fun enqueue(event: ParkingEvent) { executor.execute { syncPending(event) } }
    fun retryPending() { executor.execute { syncPending(null) } }
    private fun syncPending(newEvent: ParkingEvent?) {
        val endpoint = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("endpoint", "") ?: return
        val events = buildList { if (newEvent != null) add(newEvent) ; addAll(store.pending().filter { e -> e.id != newEvent?.id }) }
        for (event in events) { if (post(endpoint, event)) store.markSynced(event.id) else break }
    }
    private fun post(endpoint: String, event: ParkingEvent): Boolean = runCatching {
        val c = URL(endpoint.trimEnd('/') + "/api/v1/events").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 5000; c.readTimeout = 5000; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
        val body = JSONObject().apply { put("eventId", event.id); put("plate", event.plate); put("eventType", event.eventType); put("timestamp", event.timestamp); put("deviceRole", event.deviceRole) }.toString()
        c.outputStream.use { it.write(body.toByteArray()) }; val ok = c.responseCode in 200..299; c.disconnect(); ok
    }.getOrDefault(false)
}
