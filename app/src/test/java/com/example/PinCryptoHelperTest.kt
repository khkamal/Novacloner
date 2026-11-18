package com.example

import com.example.utils.PinCryptoHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinCryptoHelperTest {

    @Test
    fun `validates 4 to 8 digit numeric PIN format`() {
        assertFalse(PinCryptoHelper.isValidPinFormat("123"))
        assertFalse(PinCryptoHelper.isValidPinFormat("123456789"))
        assertFalse(PinCryptoHelper.isValidPinFormat("12a4"))
        assertTrue(PinCryptoHelper.isValidPinFormat("2580"))
        assertTrue(PinCryptoHelper.isValidPinFormat("12345678"))
    }

    @Test
    fun `derives salted PBKDF2 hash and verifies without plaintext`() {
        val cred1 = PinCryptoHelper.derivePinCredential("4829")
        val cred2 = PinCryptoHelper.derivePinCredential("4829")

        assertNotEquals("4829", cred1.hashBase64)
        assertNotEquals(cred1.saltBase64, cred2.saltBase64)
        assertNotEquals(cred1.hashBase64, cred2.hashBase64)

        assertTrue(PinCryptoHelper.verifyPin("4829", cred1.hashBase64, cred1.saltBase64))
        assertTrue(PinCryptoHelper.verifyPin("4829", cred2.hashBase64, cred2.saltBase64))
        assertFalse(PinCryptoHelper.verifyPin("4820", cred1.hashBase64, cred1.saltBase64))
        assertFalse(PinCryptoHelper.verifyPin("", cred1.hashBase64, cred1.saltBase64))
    }
}
