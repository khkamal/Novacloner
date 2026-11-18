package com.example.utils

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class DerivedPinCredential(
    val hashBase64: String,
    val saltBase64: String,
)

object PinCryptoHelper {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    private val secureRandom = SecureRandom()

    fun isValidPinFormat(pin: String): Boolean {
        return pin.length in 4..8 && pin.all { it.isDigit() }
    }

    fun derivePinCredential(pin: String): DerivedPinCredential {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val hash = pbkdf2(pin.toCharArray(), salt)
        return DerivedPinCredential(
            hashBase64 = hash.toHexString(),
            saltBase64 = salt.toHexString(),
        )
    }

    fun verifyPin(pin: String, storedHashBase64: String, storedSaltBase64: String): Boolean {
        if (storedHashBase64.isBlank() || storedSaltBase64.isBlank()) return false
        return try {
            val salt = storedSaltBase64.hexToBytes()
            val expectedHash = storedHashBase64.hexToBytes()
            val actualHash = pbkdf2(pin.toCharArray(), salt)
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun pbkdf2(pinChars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pinChars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            pinChars.fill('\u0000')
        }
    }

    private fun ByteArray.toHexString(): String {
        val chars = CharArray(size * 2)
        val hexDigits = "0123456789abcdef"
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            chars[i * 2] = hexDigits[v ushr 4]
            chars[i * 2 + 1] = hexDigits[v and 0x0F]
        }
        return String(chars)
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex length" }
        return ByteArray(length / 2) { i ->
            val hi = Character.digit(this[i * 2], 16)
            val lo = Character.digit(this[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex character" }
            ((hi shl 4) or lo).toByte()
        }
    }
}
