package com.binesheb.parking

enum class DeviceRole(val code: String, val label: String, val eventType: String) {
    ENTRY_GATE("ENTRY_GATE", "Compound Entry", "COMPOUND_IN"),
    EXIT_GATE("EXIT_GATE", "Compound Exit", "COMPOUND_OUT"),
    PARKING_ENTRY("PARKING_ENTRY", "Parking Entry", "PARKING_IN"),
    PARKING_EXIT("PARKING_EXIT", "Parking Exit", "PARKING_OUT");
    companion object { fun fromCode(code: String) = entries.firstOrNull { it.code == code } ?: ENTRY_GATE }
}

data class ParkingEvent(val plate: String, val eventType: String, val timestamp: Long, val deviceRole: String)

object PlateParser {
    private val compact = Regex("[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}")
    fun normalize(raw: String): String = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
    fun find(text: String): String? {
        val candidates = text.uppercase().split(Regex("\\s+|[|,;]"))
        return candidates.asSequence().map(::normalize).firstOrNull { it.length in 7..11 && compact.matches(it) }
            ?: compact.find(text.uppercase().replace(" ", ""))?.value
    }
}
