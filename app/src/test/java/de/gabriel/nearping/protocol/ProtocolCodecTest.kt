package de.gabriel.nearping.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCodecTest {
    @Test
    fun helloRoundTripPreservesCrossPlatformFields() {
        val message = WireMessage.hello(
            senderSessionId = "session-a",
            displayName = "Gabriel",
        )

        val decoded = ProtocolCodec.decode(ProtocolCodec.encode(message))

        assertEquals(CURRENT_PROTOCOL_VERSION, decoded.protocolVersion)
        assertEquals(MessageType.HELLO, decoded.type)
        assertEquals("session-a", decoded.senderSessionId)
        assertEquals("Gabriel", decoded.hello?.displayName)
    }

    @Test
    fun coordinationSignalRoundTripPreservesWhitelistedFields() {
        val message = WireMessage.coordinationSignal(
            senderSessionId = "session-a",
            targetSessionId = "session-b",
            code = "floor",
            optionCode = "OG3",
        )

        val decoded = ProtocolCodec.decode(ProtocolCodec.encode(message))

        assertEquals(MessageType.COORDINATION_SIGNAL, decoded.type)
        assertEquals("session-b", decoded.targetSessionId)
        assertEquals("floor", decoded.coordinationSignal?.code)
        assertEquals("OG3", decoded.coordinationSignal?.optionCode)
    }

    @Test
    fun compressedPhotoFitsNearbyBytePayload() {
        val photo = ByteArray(20_000) { index -> (index % 251).toByte() }
        val encoded = ProtocolCodec.encode(
            WireMessage.photoData(
                senderSessionId = "session-a",
                targetSessionId = "session-b",
                requestMessageId = "photo-request-1",
                jpegBytes = photo,
            ),
        )

        assertTrue(encoded.size <= MAX_WIRE_BYTES)
        val payload = ProtocolCodec.decode(encoded).photo!!
        assertEquals("photo-request-1", payload.requestMessageId)
        val decodedPhoto = ProtocolCodec.decodePhoto(payload)
        assertTrue(photo.contentEquals(decodedPhoto))
    }
}
