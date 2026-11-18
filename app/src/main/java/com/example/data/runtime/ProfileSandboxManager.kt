package com.example.data.runtime

import android.content.Context
import android.os.Process
import android.os.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ProfileSandboxSnapshot(
    val packageName: String,
    val linuxUid: Int,
    val userSerialNumber: Long,
    val filesDirPath: String,
    val instanceToken: String,
    val probeCounter: Int,
    val lastUpdatedAt: Long,
)

/**
 * Reads and persists a real file inside the current OS user profile's private app sandbox.
 * When NovaCloner is installed in both Personal and Work Profiles, Android mounts distinct
 * /data/user/<userId>/<package> directories and assigns distinct UIDs/user serials.
 */
class ProfileSandboxManager(private val context: Context) {

    private val markerFile: File
        get() = File(context.filesDir, "profile_sandbox_marker.txt")

    suspend fun readSnapshot(): ProfileSandboxSnapshot = withContext(Dispatchers.IO) {
        val currentFile = markerFile
        val existing = parseMarker(currentFile)
        val uid = Process.myUid()
        val serial = resolveUserSerial()
        val now = System.currentTimeMillis()

        val state = if (existing == null) {
            val fresh = MarkerState(
                token = java.util.UUID.randomUUID().toString().take(12),
                counter = 1,
                updatedAt = now,
            )
            writeMarker(currentFile, fresh)
            fresh
        } else {
            existing
        }

        ProfileSandboxSnapshot(
            packageName = context.packageName,
            linuxUid = uid,
            userSerialNumber = serial,
            filesDirPath = context.filesDir.absolutePath,
            instanceToken = state.token,
            probeCounter = state.counter,
            lastUpdatedAt = state.updatedAt,
        )
    }

    suspend fun incrementProbeCounter(): ProfileSandboxSnapshot = withContext(Dispatchers.IO) {
        val currentFile = markerFile
        val existing = parseMarker(currentFile)
        val next = MarkerState(
            token = existing?.token ?: java.util.UUID.randomUUID().toString().take(12),
            counter = (existing?.counter ?: 0) + 1,
            updatedAt = System.currentTimeMillis(),
        )
        writeMarker(currentFile, next)

        ProfileSandboxSnapshot(
            packageName = context.packageName,
            linuxUid = Process.myUid(),
            userSerialNumber = resolveUserSerial(),
            filesDirPath = context.filesDir.absolutePath,
            instanceToken = next.token,
            probeCounter = next.counter,
            lastUpdatedAt = next.updatedAt,
        )
    }

    private fun resolveUserSerial(): Long {
        return runCatching {
            val um = context.getSystemService(UserManager::class.java) ?: return@runCatching 0L
            um.getSerialNumberForUser(Process.myUserHandle())
        }.getOrDefault(0L)
    }

    private data class MarkerState(
        val token: String,
        val counter: Int,
        val updatedAt: Long,
    )

    private fun parseMarker(file: File): MarkerState? {
        if (!file.exists()) return null
        return runCatching {
            val lines = file.readLines()
            val token = lines.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return@runCatching null
            val counter = lines.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
            val updatedAt = lines.getOrNull(2)?.trim()?.toLongOrNull() ?: file.lastModified()
            MarkerState(token = token, counter = counter, updatedAt = updatedAt)
        }.getOrNull()
    }

    private fun writeMarker(file: File, state: MarkerState) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText("${state.token}\n${state.counter}\n${state.updatedAt}")
        }
    }
}
