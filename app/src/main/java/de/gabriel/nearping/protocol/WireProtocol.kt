package de.gabriel.nearping.protocol

import java.util.Base64
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val CURRENT_PROTOCOL_VERSION = 3
const val MAX_WIRE_BYTES = 30_000

@Serializable
enum class MessageType {
    HELLO,
    PING,
    PHOTO_REQUEST,
    PHOTO_DATA,
    COORDINATION_SIGNAL,
}

@Serializable
data class HelloPayload(
    val displayName: String,
)

@Serializable
data class PingPayload(
    val active: Boolean = true,
)

@Serializable
data class PhotoPayload(
    val requestMessageId: String,
    val jpegBase64: String,
)

@Serializable
data class CoordinationSignalPayload(
    val code: String,
    val optionCode: String? = null,
)

@Serializable
data class WireMessage(
    val protocolVersion: Int = CURRENT_PROTOCOL_VERSION,
    val type: MessageType,
    val messageId: String = UUID.randomUUID().toString(),
    val senderSessionId: String,
    val targetSessionId: String? = null,
    val sentAtEpochMs: Long = System.currentTimeMillis(),
    val hello: HelloPayload? = null,
    val ping: PingPayload? = null,
    val photo: PhotoPayload? = null,
    val coordinationSignal: CoordinationSignalPayload? = null,
) {
    companion object {
        fun hello(
            senderSessionId: String,
            displayName: String,
        ) = WireMessage(
            type = MessageType.HELLO,
            senderSessionId = senderSessionId,
            hello = HelloPayload(displayName = displayName),
        )

        fun ping(senderSessionId: String, targetSessionId: String) = WireMessage(
            type = MessageType.PING,
            senderSessionId = senderSessionId,
            targetSessionId = targetSessionId,
            ping = PingPayload(active = true),
        )

        fun photoRequest(senderSessionId: String, targetSessionId: String) = WireMessage(
            type = MessageType.PHOTO_REQUEST,
            senderSessionId = senderSessionId,
            targetSessionId = targetSessionId,
        )

        fun photoData(
            senderSessionId: String,
            targetSessionId: String,
            requestMessageId: String,
            jpegBytes: ByteArray,
        ) = WireMessage(
            type = MessageType.PHOTO_DATA,
            senderSessionId = senderSessionId,
            targetSessionId = targetSessionId,
            photo = PhotoPayload(
                requestMessageId = requestMessageId,
                jpegBase64 = Base64.getEncoder().encodeToString(jpegBytes),
            ),
        )

        fun coordinationSignal(
            senderSessionId: String,
            targetSessionId: String,
            code: String,
            optionCode: String?,
        ) = WireMessage(
            type = MessageType.COORDINATION_SIGNAL,
            senderSessionId = senderSessionId,
            targetSessionId = targetSessionId,
            coordinationSignal = CoordinationSignalPayload(
                code = code,
                optionCode = optionCode,
            ),
        )
    }
}

object ProtocolCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(message: WireMessage): ByteArray {
        val bytes = json.encodeToString(WireMessage.serializer(), message).encodeToByteArray()
        require(bytes.size <= MAX_WIRE_BYTES) {
            "Wire message is ${bytes.size} bytes; maximum is $MAX_WIRE_BYTES"
        }
        return bytes
    }

    fun decode(bytes: ByteArray): WireMessage {
        require(bytes.size <= MAX_WIRE_BYTES) { "Wire message exceeds the allowed size" }
        return json.decodeFromString(WireMessage.serializer(), bytes.decodeToString())
    }

    fun decodePhoto(payload: PhotoPayload): ByteArray =
        Base64.getDecoder().decode(payload.jpegBase64)
}
