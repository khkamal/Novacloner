package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.SecretRecord
import com.example.domain.model.SecretRecordCategory

@Entity(tableName = "secret_records")
data class SecretRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val categoryKey: String = SecretRecordCategory.PRIVATE_NOTE.key,
    val content: String = "",
    val linkedPackageName: String? = null,
    val linkedAppName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

fun SecretRecordEntity.toDomain(): SecretRecord = SecretRecord(
    id = id,
    title = title,
    category = SecretRecordCategory.fromKey(categoryKey),
    content = content,
    linkedPackageName = linkedPackageName,
    linkedAppName = linkedAppName,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SecretRecord.toEntity(): SecretRecordEntity = SecretRecordEntity(
    id = id,
    title = title,
    categoryKey = category.key,
    content = content,
    linkedPackageName = linkedPackageName,
    linkedAppName = linkedAppName,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
