package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ExecutionMode
import com.example.domain.model.Workspace
import com.example.domain.model.WorkspaceAccent

@Entity(
    tableName = "workspaces",
    indices = [
        Index(value = ["generatedWorkspaceId"], unique = true),
        Index(value = ["simulatedDeviceId"], unique = true),
        Index(value = ["simulatedAndroidId"], unique = true),
        Index(value = ["simulatedWifiMac"], unique = true),
        Index(value = ["simulatedBluetoothMac"], unique = true),
        Index(value = ["simulatedIccSerial"], unique = true),
    ],
)
data class WorkspaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val generatedWorkspaceId: String = "",
    val simulatedDeviceId: String = "",
    val simulatedAndroidId: String = "",
    val simulatedWifiMac: String = "",
    val simulatedBluetoothMac: String = "",
    val simulatedIccSerial: String = "",
    val name: String,
    val packageName: String? = null,
    val appDisplayName: String? = null,
    val description: String = "",
    val notes: String = "",
    val accentColorKey: String = WorkspaceAccent.COBALT.key,
    val executionModeKey: String = ExecutionMode.HOST_DIRECT.key,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val launchCount: Int = 0,
    val lastLaunchedAt: Long? = null,
)

fun WorkspaceEntity.toDomain(): Workspace = Workspace(
    id = id,
    generatedWorkspaceId = generatedWorkspaceId,
    simulatedDeviceId = simulatedDeviceId,
    simulatedAndroidId = simulatedAndroidId,
    simulatedWifiMac = simulatedWifiMac,
    simulatedBluetoothMac = simulatedBluetoothMac,
    simulatedIccSerial = simulatedIccSerial,
    name = name,
    packageName = packageName,
    appDisplayName = appDisplayName,
    description = description,
    notes = notes,
    accentColor = WorkspaceAccent.fromKey(accentColorKey),
    executionMode = ExecutionMode.fromKey(executionModeKey),
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    launchCount = launchCount,
    lastLaunchedAt = lastLaunchedAt,
)

fun Workspace.toEntity(): WorkspaceEntity = WorkspaceEntity(
    id = id,
    generatedWorkspaceId = generatedWorkspaceId,
    simulatedDeviceId = simulatedDeviceId,
    simulatedAndroidId = simulatedAndroidId,
    simulatedWifiMac = simulatedWifiMac,
    simulatedBluetoothMac = simulatedBluetoothMac,
    simulatedIccSerial = simulatedIccSerial,
    name = name,
    packageName = packageName,
    appDisplayName = appDisplayName,
    description = description,
    notes = notes,
    accentColorKey = accentColor.key,
    executionModeKey = executionMode.key,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    launchCount = launchCount,
    lastLaunchedAt = lastLaunchedAt,
)
