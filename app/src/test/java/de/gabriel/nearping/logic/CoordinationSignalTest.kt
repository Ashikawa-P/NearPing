package de.gabriel.nearping.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinationSignalTest {
    @Test
    fun allPublishedChoicesHaveUniqueWireRepresentations() {
        val choices = CoordinationSignal.quickChoices +
            CoordinationSignal.floorChoices +
            CoordinationSignal.meetingChoices
        val wireRepresentations = choices.map {
            it.signal.code.wireCode to it.signal.optionCode
        }

        assertEquals(wireRepresentations.size, wireRepresentations.distinct().size)
        assertTrue(choices.all { it.signal.text.isNotBlank() })
    }

    @Test
    fun knownFloorAndMeetingOptionsAreAccepted() {
        assertEquals(
            "Ich bin im 3. Stock.",
            CoordinationSignal.fromWire("floor", "OG3")?.text,
        )
        assertEquals(
            "Treffen wir uns am Haupteingang?",
            CoordinationSignal.fromWire("meet_at", "main_entrance")?.text,
        )
    }

    @Test
    fun unknownOrMalformedOptionsAreRejected() {
        assertNull(CoordinationSignal.fromWire("floor", "OG99"))
        assertNull(CoordinationSignal.fromWire("yes", "unexpected"))
        assertNull(CoordinationSignal.fromWire("free_text", null))
    }
}
