package com.binesheb.parking

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

class SyncClient(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    fun send(event: ParkingEvent) {
        val endpoint = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("endpoint", "") ?: ""
        if (endpoint.isBlank()) return
        executor.execute {
            runCatching {
                val connection = URL(endpoint.trimEnd('/') + "/api/v1/events").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"; connection.connectTimeout = 5000; connection.readTimeout = 5000; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().apply { put("plate", event.plate); put("eventType", event.eventType); put("timestamp", event.timestamp); put("deviceRole", event.deviceRole) }.toString()
                connection.outputStream.use { it.write(body.toByteArray()) }
                connection.inputStream.close(); connection.disconnect()
            }
        }
    }
}
