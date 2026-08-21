package com.binesheb.parking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlateParserTest {
    @Test fun normalizesPlate() = assertEquals("KL07AB1234", PlateParser.normalize("KL 07 AB 1234"))
    @Test fun findsIndianPlate() = assertEquals("KL07AB1234", PlateParser.find("Vehicle KL 07 AB 1234 detected"))
    @Test fun rejectsNoise() = assertNull(PlateParser.find("PARKING 12345"))
    @Test fun rolesMapToEvents() { assertEquals("COMPOUND_IN", DeviceRole.ENTRY_GATE.eventType); assertEquals("PARKING_OUT", DeviceRole.PARKING_EXIT.eventType) }
}
