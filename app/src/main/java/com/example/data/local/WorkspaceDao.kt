package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY isPinned DESC, updatedAt DESC")
    fun observeWorkspaces(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    fun observeWorkspaceById(id: Long): Flow<WorkspaceEntity?>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getWorkspaceById(id: Long): WorkspaceEntity?

    @Query("SELECT * FROM workspaces ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllWorkspacesOnce(): List<WorkspaceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkspace(entity: WorkspaceEntity): Long

    @Update
    suspend fun updateWorkspace(entity: WorkspaceEntity)

    @Query("SELECT generatedWorkspaceId FROM workspaces")
    suspend fun getAllGeneratedWorkspaceIds(): List<String>

    @Query("UPDATE workspaces SET generatedWorkspaceId = :generatedWorkspaceId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateGeneratedWorkspaceId(id: Long, generatedWorkspaceId: String, updatedAt: Long)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteWorkspaceById(id: Long)

    @Query("DELETE FROM workspaces")
    suspend fun deleteAllWorkspaces()
}
