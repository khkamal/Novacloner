package com.example.domain.model

enum class SecretRecordCategory(val key: String, val displayLabel: String) {
    PRIVATE_WORKSPACE("workspace", "Private Workspace"),
    PRIVATE_NOTE("note", "Private Note"),
    CREDENTIAL_REF("reference", "Reference");

    companion object {
        fun fromKey(key: String?): SecretRecordCategory =
            entries.firstOrNull { it.key == key } ?: PRIVATE_NOTE
    }
}

data class SecretRecord(
    val id: Long = 0L,
    val title: String,
    val category: SecretRecordCategory = SecretRecordCategory.PRIVATE_NOTE,
    val content: String = "",
    val linkedPackageName: String? = null,
    val linkedAppName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
