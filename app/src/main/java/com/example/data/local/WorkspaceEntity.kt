package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceAccent

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val generatedWorkspaceId: String = "",
    val name: String,
    val packageName: String? = null,
    val appDisplayName: String? = null,
    val description: String = "",
    val notes: String = "",
    val accentColorKey: String = WorkspaceAccent.COBALT.key,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val launchCount: Int = 0,
    val lastLaunchedAt: Long? = null,
)

fun WorkspaceEntity.toDomain(): Workspace = Workspace(
    id = id,
    generatedWorkspaceId = generatedWorkspaceId,
    name = name,
    packageName = packageName,
    appDisplayName = appDisplayName,
    description = description,
    notes = notes,
    accentColor = WorkspaceAccent.fromKey(accentColorKey),
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    launchCount = launchCount,
    lastLaunchedAt = lastLaunchedAt,
)

fun Workspace.toEntity(): WorkspaceEntity = WorkspaceEntity(
    id = id,
    generatedWorkspaceId = generatedWorkspaceId,
    name = name,
    packageName = packageName,
    appDisplayName = appDisplayName,
    description = description,
    notes = notes,
    accentColorKey = accentColor.key,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    launchCount = launchCount,
    lastLaunchedAt = lastLaunchedAt,
)
