package com.example.data.repository

import com.example.data.local.WorkspaceDao
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceAccent
import com.example.domain.model.WorkspaceExportBundle
import com.example.domain.model.WorkspaceExportItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkspaceRepository(private val workspaceDao: WorkspaceDao) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
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
        val now = System.currentTimeMillis()
        val trimmedName = name.trim().ifBlank { "Untitled Workspace" }
        val entity = Workspace(
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

    suspend fun updateWorkspace(workspace: Workspace) = withContext(Dispatchers.IO) {
        val sanitized = workspace.copy(
            name = workspace.name.trim().ifBlank { "Untitled Workspace" },
            description = workspace.description.trim(),
            notes = workspace.notes.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        workspaceDao.updateWorkspace(sanitized.toEntity())
    }

    suspend fun renameWorkspace(id: Long, newName: String) = withContext(Dispatchers.IO) {
        val existing = workspaceDao.getWorkspaceById(id) ?: return@withContext
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

    suspend fun associateApp(
        id: Long,
        packageName: String?,
        appDisplayName: String?,
    ) = withContext(Dispatchers.IO) {
        val existing = workspaceDao.getWorkspaceById(id) ?: return@withContext
        workspaceDao.updateWorkspace(
            existing.copy(
                packageName = packageName?.takeIf { it.isNotBlank() },
                appDisplayName = appDisplayName?.takeIf { it.isNotBlank() },
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun togglePin(id: Long) = withContext(Dispatchers.IO) {
        val existing = workspaceDao.getWorkspaceById(id) ?: return@withContext
        workspaceDao.updateWorkspace(
            existing.copy(
                isPinned = !existing.isPinned,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun recordAppLaunch(id: Long) = withContext(Dispatchers.IO) {
        val existing = workspaceDao.getWorkspaceById(id) ?: return@withContext
        val now = System.currentTimeMillis()
        workspaceDao.updateWorkspace(
            existing.copy(
                launchCount = existing.launchCount + 1,
                lastLaunchedAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun resetWorkspace(id: Long, clearAppAssociation: Boolean) = withContext(Dispatchers.IO) {
        val existing = workspaceDao.getWorkspaceById(id) ?: return@withContext
        workspaceDao.updateWorkspace(
            existing.copy(
                description = "",
                notes = "",
                launchCount = 0,
                lastLaunchedAt = null,
                packageName = if (clearAppAssociation) null else existing.packageName,
                appDisplayName = if (clearAppAssociation) null else existing.appDisplayName,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteWorkspace(id: Long) = withContext(Dispatchers.IO) {
        workspaceDao.deleteWorkspaceById(id)
    }

    suspend fun deleteAllWorkspaces() = withContext(Dispatchers.IO) {
        workspaceDao.deleteAllWorkspaces()
    }

    suspend fun exportWorkspacesJson(): String = withContext(Dispatchers.IO) {
        val items = workspaceDao.getAllWorkspacesOnce().map { entity ->
            WorkspaceExportItem(
                name = entity.name,
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
        runCatching {
            val bundle = json.decodeFromString<WorkspaceExportBundle>(rawJson)
            val now = System.currentTimeMillis()
            var importedCount = 0
            bundle.workspaces.forEach { item ->
                val cleanName = item.name.trim()
                if (cleanName.isNotEmpty()) {
                    val entity = Workspace(
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
