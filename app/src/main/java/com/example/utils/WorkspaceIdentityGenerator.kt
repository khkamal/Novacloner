package com.example.utils

import java.security.SecureRandom
import java.util.UUID

data class SimulatedIdentityProfile(
    val deviceId: String,
    val androidId: String,
    val wifiMac: String,
    val bluetoothMac: String,
    val iccSerial: String,
)

object WorkspaceIdentityGenerator {
    private val secureRandom = SecureRandom()
    private val hexChars = "0123456789abcdef"

    private val numericDeviceIdRegex = Regex("^[0-9]{10,20}$")
    private val androidIdRegex = Regex("^[0-9a-fA-F]{16}$")
    private val macRegex = Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")
    private val iccSerialRegex = Regex("^[0-9]{15,22}$")

    fun generateUuid(used: MutableSet<String>): String {
        var candidate: String
        do {
            candidate = UUID.randomUUID().toString()
        } while (!used.add(candidate))
        return candidate
    }

    fun generateNumericDeviceId(used: MutableSet<String>): String {
        var candidate: String
        do {
            val sb = StringBuilder(15)
            sb.append(1 + secureRandom.nextInt(9))
            repeat(14) {
                sb.append(secureRandom.nextInt(10))
            }
            candidate = sb.toString()
        } while (!used.add(candidate))
        return candidate
    }

    fun generateAndroidId(used: MutableSet<String>): String {
        var candidate: String
        do {
            val sb = StringBuilder(16)
            repeat(16) {
                sb.append(hexChars[secureRandom.nextInt(16)])
            }
            candidate = sb.toString()
        } while (!used.add(candidate))
        return candidate
    }

    fun generateMacAddress(usedMacs: MutableSet<String>): String {
        var candidate: String
        do {
            val bytes = ByteArray(6)
            secureRandom.nextBytes(bytes)
            // Enforce locally administered (bit 1) + unicast (bit 0 = 0) on first octet
            val first = (bytes[0].toInt() and 0xFE) or 0x02
            candidate = buildString(17) {
                append("%02x".format(first and 0xFF))
                for (i in 1..5) {
                    append(':')
                    append("%02x".format(bytes[i].toInt() and 0xFF))
                }
            }
        } while (!usedMacs.add(candidate))
        return candidate
    }

    fun generateIccSerial(used: MutableSet<String>): String {
        var candidate: String
        do {
            val sb = StringBuilder(19)
            sb.append("89")
            repeat(17) {
                sb.append(secureRandom.nextInt(10))
            }
            candidate = sb.toString()
        } while (!used.add(candidate))
        return candidate
    }

    fun generateProfile(
        usedDeviceIds: MutableSet<String>,
        usedAndroidIds: MutableSet<String>,
        usedAllMacs: MutableSet<String>,
        usedIccSerials: MutableSet<String>,
    ): SimulatedIdentityProfile = SimulatedIdentityProfile(
        deviceId = generateNumericDeviceId(usedDeviceIds),
        androidId = generateAndroidId(usedAndroidIds),
        wifiMac = generateMacAddress(usedAllMacs),
        bluetoothMac = generateMacAddress(usedAllMacs),
        iccSerial = generateIccSerial(usedIccSerials),
    )

    fun isValidDeviceId(value: String): Boolean = numericDeviceIdRegex.matches(value.trim())

    fun isValidAndroidId(value: String): Boolean = androidIdRegex.matches(value.trim())

    fun isValidMac(value: String): Boolean = macRegex.matches(value.trim())

    fun isValidIccSerial(value: String): Boolean = iccSerialRegex.matches(value.trim())

    fun normalizeHex(value: String?): String = value?.trim()?.lowercase().orEmpty()
}
