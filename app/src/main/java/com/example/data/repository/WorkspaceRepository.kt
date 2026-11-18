package com.example.data.repository

import com.example.data.local.WorkspaceDao
import com.example.data.local.WorkspaceEntity
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceAccent
import com.example.domain.model.WorkspaceExportBundle
import com.example.domain.model.WorkspaceExportItem
import com.example.utils.WorkspaceIdentityGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkspaceRepository(private val workspaceDao: WorkspaceDao) {

    private val writeMutex = Mutex()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private class UsedIdentityPool(
        existing: List<WorkspaceEntity>,
        excludeId: Long? = null,
        includeExcludedValues: Boolean = false,
    ) {
        val uuids = HashSet<String>(existing.size)
        val deviceIds = HashSet<String>(existing.size)
        val androidIds = HashSet<String>(existing.size)
        val allMacs = HashSet<String>(existing.size * 2)
        val iccSerials = HashSet<String>(existing.size)

        init {
            for (row in existing) {
                if (!includeExcludedValues && excludeId != null && row.id == excludeId) continue
                row.generatedWorkspaceId.trim().takeIf { it.isNotEmpty() }?.let { uuids.add(it) }
                row.simulatedDeviceId.trim().takeIf { it.isNotEmpty() }?.let { deviceIds.add(it) }
                WorkspaceIdentityGenerator.normalizeHex(row.simulatedAndroidId)
                    .takeIf { it.isNotEmpty() }?.let { androidIds.add(it) }
                WorkspaceIdentityGenerator.normalizeHex(row.simulatedWifiMac)
                    .takeIf { it.isNotEmpty() }?.let { allMacs.add(it) }
                WorkspaceIdentityGenerator.normalizeHex(row.simulatedBluetoothMac)
                    .takeIf { it.isNotEmpty() }?.let { allMacs.add(it) }
                row.simulatedIccSerial.trim().takeIf { it.isNotEmpty() }?.let { iccSerials.add(it) }
            }
        }
    }

    fun observeWorkspaces(): Flow<List<Workspace>> =
        workspaceDao.observeWorkspaces().map { list -> list.map { it.toDomain() } }

    fun observeWorkspace(id: Long): Flow<Workspace?> =
        workspaceDao.observeWorkspaceById(id).map { it?.toDomain() }

    suspend fun getWorkspace(id: Long): Workspace? = withContext(Dispatchers.IO) {
        workspaceDao.getWorkspaceById(id)?.toDomain()
    }

    suspend fun createWorkspace(
        name: String,
        packageName: String? = null,
        appDisplayName: String? = null,
        description: String = "",
        notes: String = "",
        accentColor: WorkspaceAccent = WorkspaceAccent.COBALT,
        isPinned: Boolean = false,
    ): Long = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val now = System.currentTimeMillis()
            val trimmedName = name.trim().ifBlank { "Untitled Workspace" }
            val pool = UsedIdentityPool(workspaceDao.getAllWorkspacesOnce())
            val generatedId = WorkspaceIdentityGenerator.generateUuid(pool.uuids)
            val profile = WorkspaceIdentityGenerator.generateProfile(
                usedDeviceIds = pool.deviceIds,
                usedAndroidIds = pool.androidIds,
                usedAllMacs = pool.allMacs,
                usedIccSerials = pool.iccSerials,
            )

            val entity = Workspace(
                generatedWorkspaceId = generatedId,
                simulatedDeviceId = profile.deviceId,
                simulatedAndroidId = profile.androidId,
                simulatedWifiMac = profile.wifiMac,
                simulatedBluetoothMac = profile.bluetoothMac,
                simulatedIccSerial = profile.iccSerial,
                name = trimmedName,
                packageName = packageName?.takeIf { it.isNotBlank() },
                appDisplayName = appDisplayName?.takeIf { it.isNotBlank() },
                description = description.trim(),
                notes = notes.trim(),
                accentColor = accentColor,
                isPinned = isPinned,
                createdAt = now,
                updatedAt = now,
            ).toEntity()
            workspaceDao.insertWorkspace(entity)
        }
    }

    suspend fun updateWorkspace(workspace: Workspace) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val allRows = workspaceDao.getAllWorkspacesOnce()
            val currentRow = allRows.firstOrNull { it.id == workspace.id }
            val pool = UsedIdentityPool(allRows, excludeId = workspace.id)

            val candidateUuid = workspace.generatedWorkspaceId.trim().ifEmpty {
                currentRow?.generatedWorkspaceId?.trim().orEmpty()
            }
            val ensuredUuid = if (candidateUuid.isNotEmpty() && pool.uuids.add(candidateUuid)) {
                candidateUuid
            } else {
                WorkspaceIdentityGenerator.generateUuid(pool.uuids)
            }

            val candidateDeviceId = workspace.simulatedDeviceId.trim().ifEmpty {
                currentRow?.simulatedDeviceId?.trim().orEmpty()
            }
            val ensuredDeviceId = if (
                WorkspaceIdentityGenerator.isValidDeviceId(candidateDeviceId) &&
                pool.deviceIds.add(candidateDeviceId)
            ) {
                candidateDeviceId
            } else {
                WorkspaceIdentityGenerator.generateNumericDeviceId(pool.deviceIds)
            }

            val candidateAndroidId = WorkspaceIdentityGenerator.normalizeHex(
                workspace.simulatedAndroidId.ifBlank { currentRow?.simulatedAndroidId }
            )
            val ensuredAndroidId = if (
                WorkspaceIdentityGenerator.isValidAndroidId(candidateAndroidId) &&
                pool.androidIds.add(candidateAndroidId)
            ) {
                candidateAndroidId
            } else {
                WorkspaceIdentityGenerator.generateAndroidId(pool.androidIds)
            }

            val candidateWifiMac = WorkspaceIdentityGenerator.normalizeHex(
                workspace.simulatedWifiMac.ifBlank { currentRow?.simulatedWifiMac }
            )
            val ensuredWifiMac = if (
                WorkspaceIdentityGenerator.isValidMac(candidateWifiMac) &&
                pool.allMacs.add(candidateWifiMac)
            ) {
                candidateWifiMac
            } else {
                WorkspaceIdentityGenerator.generateMacAddress(pool.allMacs)
            }

            val candidateBtMac = WorkspaceIdentityGenerator.normalizeHex(
                workspace.simulatedBluetoothMac.ifBlank { currentRow?.simulatedBluetoothMac }
            )
            val ensuredBtMac = if (
                WorkspaceIdentityGenerator.isValidMac(candidateBtMac) &&
                pool.allMacs.add(candidateBtMac)
            ) {
                candidateBtMac
            } else {
                WorkspaceIdentityGenerator.generateMacAddress(pool.allMacs)
            }

            val candidateIccSerial = workspace.simulatedIccSerial.trim().ifEmpty {
                currentRow?.simulatedIccSerial?.trim().orEmpty()
            }
            val ensuredIccSerial = if (
                WorkspaceIdentityGenerator.isValidIccSerial(candidateIccSerial) &&
                pool.iccSerials.add(candidateIccSerial)
            ) {
                candidateIccSerial
            } else {
                WorkspaceIdentityGenerator.generateIccSerial(pool.iccSerials)
            }

            val sanitized = workspace.copy(
                generatedWorkspaceId = ensuredUuid,
                simulatedDeviceId = ensuredDeviceId,
                simulatedAndroidId = ensuredAndroidId,
                simulatedWifiMac = ensuredWifiMac,
                simulatedBluetoothMac = ensuredBtMac,
                simulatedIccSerial = ensuredIccSerial,
                name = workspace.name.trim().ifBlank { "Untitled Workspace" },
                description = workspace.description.trim(),
                notes = workspace.notes.trim(),
                updatedAt = System.currentTimeMillis(),
            )
            workspaceDao.updateWorkspace(sanitized.toEntity())
        }
    }

    suspend fun renameWorkspace(id: Long, newName: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = workspaceDao.getWorkspaceById(id) ?: return@withLock
            val cleanName = newName.trim()
            if (cleanName.isNotEmpty()) {
                workspaceDao.updateWorkspace(
                    existing.copy(
                        name = cleanName,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    suspend fun associateApp(
        id: Long,
        packageName: String?,
        appDisplayName: String?,
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = workspaceDao.getWorkspaceById(id) ?: return@withLock
            workspaceDao.updateWorkspace(
                existing.copy(
                    packageName = packageName?.takeIf { it.isNotBlank() },
                    appDisplayName = appDisplayName?.takeIf { it.isNotBlank() },
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun togglePin(id: Long) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = workspaceDao.getWorkspaceById(id) ?: return@withLock
            workspaceDao.updateWorkspace(
                existing.copy(
                    isPinned = !existing.isPinned,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun recordAppLaunch(id: Long) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = workspaceDao.getWorkspaceById(id) ?: return@withLock
            val now = System.currentTimeMillis()
            workspaceDao.updateWorkspace(
                existing.copy(
                    launchCount = existing.launchCount + 1,
                    lastLaunchedAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    suspend fun regenerateWorkspaceGeneratedId(id: Long): String? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = workspaceDao.getWorkspaceById(id) ?: return@withLock null
            val usedIds = workspaceDao.getAllGeneratedWorkspaceIds()
                .filter { it.isNotBlank() }
                .toMutableSet()
            if (existing.generatedWorkspaceId.isNotBlank()) {
                usedIds.add(existing.generatedWorkspaceId)
            }
            val newId = WorkspaceIdentityGenerator.generateUuid(usedIds)
            workspaceDao.updateGeneratedWorkspaceId(
                id = existing.id,
                generatedWorkspaceId = newId,
                updatedAt = System.currentTimeMillis(),
            )
            newId
        }
    }

    suspend fun regenerateWorkspaceIdentityProfile(id: Long): Workspace? = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = workspaceDao.getWorkspaceById(id) ?: return@withLock null
            val pool = UsedIdentityPool(
                existing = workspaceDao.getAllWorkspacesOnce(),
                excludeId = id,
                includeExcludedValues = true,
            )
            val newUuid = WorkspaceIdentityGenerator.generateUuid(pool.uuids)
            val newProfile = WorkspaceIdentityGenerator.generateProfile(
                usedDeviceIds = pool.deviceIds,
                usedAndroidIds = pool.androidIds,
                usedAllMacs = pool.allMacs,
                usedIccSerials = pool.iccSerials,
            )
            val updated = existing.copy(
                generatedWorkspaceId = newUuid,
                simulatedDeviceId = newProfile.deviceId,
                simulatedAndroidId = newProfile.androidId,
                simulatedWifiMac = newProfile.wifiMac,
                simulatedBluetoothMac = newProfile.bluetoothMac,
                simulatedIccSerial = newProfile.iccSerial,
                updatedAt = System.currentTimeMillis(),
            )
            workspaceDao.updateWorkspace(updated)
            updated.toDomain()
        }
    }

    suspend fun resetWorkspace(id: Long, clearAppAssociation: Boolean) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val existing = workspaceDao.getWorkspaceById(id) ?: return@withLock
            val pool = UsedIdentityPool(
                existing = workspaceDao.getAllWorkspacesOnce(),
                excludeId = id,
                includeExcludedValues = true,
            )
            val freshUuid = WorkspaceIdentityGenerator.generateUuid(pool.uuids)
            val freshProfile = WorkspaceIdentityGenerator.generateProfile(
                usedDeviceIds = pool.deviceIds,
                usedAndroidIds = pool.androidIds,
                usedAllMacs = pool.allMacs,
                usedIccSerials = pool.iccSerials,
            )

            workspaceDao.updateWorkspace(
                existing.copy(
                    generatedWorkspaceId = freshUuid,
                    simulatedDeviceId = freshProfile.deviceId,
                    simulatedAndroidId = freshProfile.androidId,
                    simulatedWifiMac = freshProfile.wifiMac,
                    simulatedBluetoothMac = freshProfile.bluetoothMac,
                    simulatedIccSerial = freshProfile.iccSerial,
                    description = "",
                    notes = "",
                    accentColorKey = WorkspaceAccent.COBALT.key,
                    isPinned = false,
                    launchCount = 0,
                    lastLaunchedAt = null,
                    packageName = if (clearAppAssociation) null else existing.packageName,
                    appDisplayName = if (clearAppAssociation) null else existing.appDisplayName,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun deleteWorkspace(id: Long) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            workspaceDao.deleteWorkspaceById(id)
        }
    }

    suspend fun deleteAllWorkspaces() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            workspaceDao.deleteAllWorkspaces()
        }
    }

    suspend fun exportWorkspacesJson(): String = withContext(Dispatchers.IO) {
        val items = workspaceDao.getAllWorkspacesOnce().map { entity ->
            WorkspaceExportItem(
                name = entity.name,
                generatedWorkspaceId = entity.generatedWorkspaceId,
                simulatedDeviceId = entity.simulatedDeviceId,
                simulatedAndroidId = entity.simulatedAndroidId,
                simulatedWifiMac = entity.simulatedWifiMac,
                simulatedBluetoothMac = entity.simulatedBluetoothMac,
                simulatedIccSerial = entity.simulatedIccSerial,
                packageName = entity.packageName,
                appDisplayName = entity.appDisplayName,
                description = entity.description,
                notes = entity.notes,
                accentColorKey = entity.accentColorKey,
                isPinned = entity.isPinned,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
            )
        }
        val bundle = WorkspaceExportBundle(
            exportedAt = System.currentTimeMillis(),
            workspaces = items,
        )
        json.encodeToString(bundle)
    }

    suspend fun importWorkspacesJson(rawJson: String): Result<Int> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            runCatching {
                val bundle = json.decodeFromString<WorkspaceExportBundle>(rawJson)
                val now = System.currentTimeMillis()
                val pool = UsedIdentityPool(workspaceDao.getAllWorkspacesOnce())
                var importedCount = 0

                bundle.workspaces.forEach { item ->
                    val cleanName = item.name.trim()
                    if (cleanName.isNotEmpty()) {
                        val candidateUuid = item.generatedWorkspaceId?.trim().orEmpty()
                        val assignedUuid = if (candidateUuid.isNotEmpty() && pool.uuids.add(candidateUuid)) {
                            candidateUuid
                        } else {
                            WorkspaceIdentityGenerator.generateUuid(pool.uuids)
                        }

                        val candidateDeviceId = item.simulatedDeviceId?.trim().orEmpty()
                        val assignedDeviceId = if (
                            WorkspaceIdentityGenerator.isValidDeviceId(candidateDeviceId) &&
                            pool.deviceIds.add(candidateDeviceId)
                        ) {
                            candidateDeviceId
                        } else {
                            WorkspaceIdentityGenerator.generateNumericDeviceId(pool.deviceIds)
                        }

                        val candidateAndroidId = WorkspaceIdentityGenerator.normalizeHex(item.simulatedAndroidId)
                        val assignedAndroidId = if (
                            WorkspaceIdentityGenerator.isValidAndroidId(candidateAndroidId) &&
                            pool.androidIds.add(candidateAndroidId)
                        ) {
                            candidateAndroidId
                        } else {
                            WorkspaceIdentityGenerator.generateAndroidId(pool.androidIds)
                        }

                        val candidateWifiMac = WorkspaceIdentityGenerator.normalizeHex(item.simulatedWifiMac)
                        val assignedWifiMac = if (
                            WorkspaceIdentityGenerator.isValidMac(candidateWifiMac) &&
                            pool.allMacs.add(candidateWifiMac)
                        ) {
                            candidateWifiMac
                        } else {
                            WorkspaceIdentityGenerator.generateMacAddress(pool.allMacs)
                        }

                        val candidateBtMac = WorkspaceIdentityGenerator.normalizeHex(item.simulatedBluetoothMac)
                        val assignedBtMac = if (
                            WorkspaceIdentityGenerator.isValidMac(candidateBtMac) &&
                            pool.allMacs.add(candidateBtMac)
                        ) {
                            candidateBtMac
                        } else {
                            WorkspaceIdentityGenerator.generateMacAddress(pool.allMacs)
                        }

                        val candidateIccSerial = item.simulatedIccSerial?.trim().orEmpty()
                        val assignedIccSerial = if (
                            WorkspaceIdentityGenerator.isValidIccSerial(candidateIccSerial) &&
                            pool.iccSerials.add(candidateIccSerial)
                        ) {
                            candidateIccSerial
                        } else {
                            WorkspaceIdentityGenerator.generateIccSerial(pool.iccSerials)
                        }

                        val entity = Workspace(
                            generatedWorkspaceId = assignedUuid,
                            simulatedDeviceId = assignedDeviceId,
                            simulatedAndroidId = assignedAndroidId,
                            simulatedWifiMac = assignedWifiMac,
                            simulatedBluetoothMac = assignedBtMac,
                            simulatedIccSerial = assignedIccSerial,
                            name = cleanName,
                            packageName = item.packageName?.takeIf { it.isNotBlank() },
                            appDisplayName = item.appDisplayName?.takeIf { it.isNotBlank() },
                            description = item.description.trim(),
                            notes = item.notes.trim(),
                            accentColor = WorkspaceAccent.fromKey(item.accentColorKey),
                            isPinned = item.isPinned,
                            createdAt = item.createdAt.takeIf { it > 0L } ?: now,
                            updatedAt = now,
                        ).toEntity()
                        workspaceDao.insertWorkspace(entity)
                        importedCount++
                    }
                }
                importedCount
            }
        }
    }
}
