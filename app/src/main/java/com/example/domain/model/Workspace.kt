package com.example.domain.model

import androidx.compose.ui.graphics.Color

enum class WorkspaceAccent(val key: String, val label: String, val colorHex: Long) {
    COBALT("cobalt", "Cobalt", 0xFF4B8DFF),
    ICE("ice", "Sky", 0xFF8BB6FF),
    EMERALD("emerald", "Emerald", 0xFF4CAF87),
    AMBER("amber", "Amber", 0xFFE8A94B),
    CORAL("coral", "Coral", 0xFFE57373),
    VIOLET("violet", "Iris", 0xFF9B8CFF);

    val color: Color
        get() = Color(colorHex)

    companion object {
        fun fromKey(key: String?): WorkspaceAccent =
            entries.firstOrNull { it.key == key } ?: COBALT
    }
}

data class Workspace(
    val id: Long = 0L,
    val generatedWorkspaceId: String = "",
    val name: String,
    val packageName: String? = null,
    val appDisplayName: String? = null,
    val description: String = "",
    val notes: String = "",
    val accentColor: WorkspaceAccent = WorkspaceAccent.COBALT,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val launchCount: Int = 0,
    val lastLaunchedAt: Long? = null,
)

enum class AppAvailability {
    NO_APP_LINKED,
    AVAILABLE,
    UNAVAILABLE
}
