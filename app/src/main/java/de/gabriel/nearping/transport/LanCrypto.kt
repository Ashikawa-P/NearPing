package de.gabriel.nearping.transport

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal const val LAN_TRANSPORT_VERSION = 1
internal const val LAN_GCM_TAG_BYTES = 16

internal data class LanSessionKeys(
    val sendKey: SecretKeySpec,
    val receiveKey: SecretKeySpec,
)

internal object LanCrypto {
    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    fun decodePublicKey(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    fun deriveSessionKeys(
        localPrivateKey: PrivateKey,
        remotePublicKey: PublicKey,
        localSessionId: String,
        remoteSessionId: String,
    ): LanSessionKeys {
        val sharedSecret = KeyAgreement.getInstance("ECDH").run {
            init(localPrivateKey)
            doPhase(remotePublicKey, true)
            generateSecret()
        }
        val lowerSession = minOf(localSessionId, remoteSessionId)
        val higherSession = maxOf(localSessionId, remoteSessionId)
        val info = "$HKDF_INFO|$lowerSession|$higherSession".toByteArray(StandardCharsets.UTF_8)
        val keyMaterial = hkdfSha256(sharedSecret, HKDF_SALT, info, DERIVED_KEY_BYTES * 2)
        val lowerToHigher = SecretKeySpec(keyMaterial.copyOfRange(0, DERIVED_KEY_BYTES), "AES")
        val higherToLower = SecretKeySpec(
            keyMaterial.copyOfRange(DERIVED_KEY_BYTES, DERIVED_KEY_BYTES * 2),
            "AES",
        )
        sharedSecret.fill(0)
        keyMaterial.fill(0)
        return if (localSessionId == lowerSession) {
            LanSessionKeys(lowerToHigher, higherToLower)
        } else {
            LanSessionKeys(higherToLower, lowerToHigher)
        }
    }

    fun encrypt(
        key: SecretKeySpec,
        senderSessionId: String,
        targetSessionId: String,
        counter: Long,
        plaintext: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce(counter)))
        cipher.updateAAD(frameAad(senderSessionId, targetSessionId, counter))
        return cipher.doFinal(plaintext)
    }

    fun decrypt(
        key: SecretKeySpec,
        senderSessionId: String,
        targetSessionId: String,
        counter: Long,
        ciphertext: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce(counter)))
        cipher.updateAAD(frameAad(senderSessionId, targetSessionId, counter))
        return cipher.doFinal(ciphertext)
    }

    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudorandomKey = extract.doFinal(inputKeyMaterial)
        val output = ByteArray(outputLength)
        var previous = ByteArray(0)
        var written = 0
        var blockIndex = 1
        while (written < outputLength) {
            val expand = Mac.getInstance("HmacSHA256")
            expand.init(SecretKeySpec(pseudorandomKey, "HmacSHA256"))
            expand.update(previous)
            expand.update(info)
            expand.update(blockIndex.toByte())
            previous = expand.doFinal()
            val copied = minOf(previous.size, outputLength - written)
            previous.copyInto(output, written, 0, copied)
            written += copied
            blockIndex += 1
        }
        pseudorandomKey.fill(0)
        previous.fill(0)
        return output
    }

    private fun nonce(counter: Long): ByteArray =
        ByteBuffer.allocate(GCM_NONCE_BYTES).putInt(0).putLong(counter).array()

    private fun frameAad(senderSessionId: String, targetSessionId: String, counter: Long): ByteArray =
        "$LAN_TRANSPORT_VERSION|$senderSessionId|$targetSessionId|$counter"
            .toByteArray(StandardCharsets.UTF_8)

    private const val GCM_TAG_BITS = 128
    private const val GCM_NONCE_BYTES = 12
    private const val DERIVED_KEY_BYTES = 32
    private const val HKDF_INFO = "NearPing-LAN-Transport-v1"
    private val HKDF_SALT = MessageDigest.getInstance("SHA-256")
        .digest("NearPing LAN v1 salt".toByteArray(StandardCharsets.UTF_8))
}
