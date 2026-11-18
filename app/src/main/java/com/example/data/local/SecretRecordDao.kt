package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SecretRecordDao {
    @Query("SELECT * FROM secret_records ORDER BY updatedAt DESC")
    fun observeAllRecords(): Flow<List<SecretRecordEntity>>

    @Query("SELECT * FROM secret_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): SecretRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(entity: SecretRecordEntity): Long

    @Update
    suspend fun updateRecord(entity: SecretRecordEntity)

    @Query("DELETE FROM secret_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM secret_records")
    suspend fun deleteAllRecords()
}
