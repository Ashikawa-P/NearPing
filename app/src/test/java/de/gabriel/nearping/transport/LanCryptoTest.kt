package de.gabriel.nearping.transport

import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LanCryptoTest {
    @Test
    fun bothSidesDeriveMatchingDirectionalKeysAndDecryptFrames() {
        val sessionA = "0123456789abcdef"
        val sessionB = "fedcba9876543210"
        val pairA = LanCrypto.generateKeyPair()
        val pairB = LanCrypto.generateKeyPair()

        val keysA = LanCrypto.deriveSessionKeys(pairA.private, pairB.public, sessionA, sessionB)
        val keysB = LanCrypto.deriveSessionKeys(pairB.private, pairA.public, sessionB, sessionA)

        assertArrayEquals(keysA.sendKey.encoded, keysB.receiveKey.encoded)
        assertArrayEquals(keysB.sendKey.encoded, keysA.receiveKey.encoded)
        assertFalse(keysA.sendKey.encoded.contentEquals(keysA.receiveKey.encoded))

        val plaintext = "NearPing LAN payload".encodeToByteArray()
        val ciphertext = LanCrypto.encrypt(keysA.sendKey, sessionA, sessionB, 0L, plaintext)
        val decrypted = LanCrypto.decrypt(keysB.receiveKey, sessionA, sessionB, 0L, ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test(expected = AEADBadTagException::class)
    fun modifiedCiphertextIsRejected() {
        val sessionA = "0123456789abcdef"
        val sessionB = "fedcba9876543210"
        val pairA = LanCrypto.generateKeyPair()
        val pairB = LanCrypto.generateKeyPair()
        val keysA = LanCrypto.deriveSessionKeys(pairA.private, pairB.public, sessionA, sessionB)
        val keysB = LanCrypto.deriveSessionKeys(pairB.private, pairA.public, sessionB, sessionA)
        val ciphertext = LanCrypto.encrypt(
            keysA.sendKey,
            sessionA,
            sessionB,
            4L,
            "protected".encodeToByteArray(),
        )
        ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 1).toByte()

        LanCrypto.decrypt(keysB.receiveKey, sessionA, sessionB, 4L, ciphertext)
    }
}
