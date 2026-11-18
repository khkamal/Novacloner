package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [WorkspaceEntity::class, SecretRecordEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NovaSpaceDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun secretRecordDao(): SecretRecordDao

    companion object {
        private const val DATABASE_NAME = "nova_space.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN generatedWorkspaceId TEXT NOT NULL DEFAULT ''"
                )
                val ids = mutableListOf<Long>()
                db.query("SELECT id FROM workspaces").use { cursor ->
                    val idIndex = cursor.getColumnIndex("id")
                    while (cursor.moveToNext()) {
                        if (idIndex >= 0) {
                            ids.add(cursor.getLong(idIndex))
                        }
                    }
                }
                for (rowId in ids) {
                    db.execSQL(
                        "UPDATE workspaces SET generatedWorkspaceId = ? WHERE id = ?",
                        arrayOf<Any>(UUID.randomUUID().toString(), rowId),
                    )
                }
            }
        }

        @Volatile
        private var INSTANCE: NovaSpaceDatabase? = null

        fun getInstance(context: Context): NovaSpaceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaSpaceDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
