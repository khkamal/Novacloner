package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.utils.WorkspaceIdentityGenerator
import java.util.UUID

@Database(
    entities = [WorkspaceEntity::class, SecretRecordEntity::class],
    version = 5,
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
                val assigned = HashSet<String>(ids.size)
                for (rowId in ids) {
                    var candidate: String
                    do {
                        candidate = UUID.randomUUID().toString()
                    } while (!assigned.add(candidate))
                    db.execSQL(
                        "UPDATE workspaces SET generatedWorkspaceId = ? WHERE id = ?",
                        arrayOf<Any>(candidate, rowId),
                    )
                }
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workspaces_generatedWorkspaceId` ON `workspaces` (`generatedWorkspaceId`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingRows = mutableListOf<Pair<Long, String>>()
                db.query("SELECT id, generatedWorkspaceId FROM workspaces ORDER BY id ASC").use { cursor ->
                    val idIndex = cursor.getColumnIndex("id")
                    val genIdIndex = cursor.getColumnIndex("generatedWorkspaceId")
                    while (cursor.moveToNext()) {
                        if (idIndex >= 0 && genIdIndex >= 0) {
                            existingRows.add(
                                cursor.getLong(idIndex) to (cursor.getString(genIdIndex) ?: "")
                            )
                        }
                    }
                }
                val seen = HashSet<String>(existingRows.size)
                for ((rowId, currentGenId) in existingRows) {
                    val trimmed = currentGenId.trim()
                    if (trimmed.isEmpty() || !seen.add(trimmed)) {
                        var candidate: String
                        do {
                            candidate = UUID.randomUUID().toString()
                        } while (!seen.add(candidate))
                        db.execSQL(
                            "UPDATE workspaces SET generatedWorkspaceId = ? WHERE id = ?",
                            arrayOf<Any>(candidate, rowId),
                        )
                    } else if (trimmed != currentGenId) {
                        db.execSQL(
                            "UPDATE workspaces SET generatedWorkspaceId = ? WHERE id = ?",
                            arrayOf<Any>(trimmed, rowId),
                        )
                    }
                }
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workspaces_generatedWorkspaceId` ON `workspaces` (`generatedWorkspaceId`)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN simulatedDeviceId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN simulatedAndroidId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN simulatedWifiMac TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN simulatedBluetoothMac TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN simulatedIccSerial TEXT NOT NULL DEFAULT ''"
                )

                val rowIds = mutableListOf<Long>()
                db.query("SELECT id FROM workspaces ORDER BY id ASC").use { cursor ->
                    val idIndex = cursor.getColumnIndex("id")
                    while (cursor.moveToNext()) {
                        if (idIndex >= 0) {
                            rowIds.add(cursor.getLong(idIndex))
                        }
                    }
                }

                val usedDeviceIds = HashSet<String>(rowIds.size)
                val usedAndroidIds = HashSet<String>(rowIds.size)
                val usedAllMacs = HashSet<String>(rowIds.size * 2)
                val usedIccSerials = HashSet<String>(rowIds.size)

                for (rowId in rowIds) {
                    val profile = WorkspaceIdentityGenerator.generateProfile(
                        usedDeviceIds = usedDeviceIds,
                        usedAndroidIds = usedAndroidIds,
                        usedAllMacs = usedAllMacs,
                        usedIccSerials = usedIccSerials,
                    )
                    db.execSQL(
                        """
                        UPDATE workspaces
                        SET simulatedDeviceId = ?,
                            simulatedAndroidId = ?,
                            simulatedWifiMac = ?,
                            simulatedBluetoothMac = ?,
                            simulatedIccSerial = ?
                        WHERE id = ?
                        """.trimIndent(),
                        arrayOf<Any>(
                            profile.deviceId,
                            profile.androidId,
                            profile.wifiMac,
                            profile.bluetoothMac,
                            profile.iccSerial,
                            rowId,
                        ),
                    )
                }

                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workspaces_simulatedDeviceId` ON `workspaces` (`simulatedDeviceId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workspaces_simulatedAndroidId` ON `workspaces` (`simulatedAndroidId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workspaces_simulatedWifiMac` ON `workspaces` (`simulatedWifiMac`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workspaces_simulatedBluetoothMac` ON `workspaces` (`simulatedBluetoothMac`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workspaces_simulatedIccSerial` ON `workspaces` (`simulatedIccSerial`)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE workspaces ADD COLUMN executionModeKey TEXT NOT NULL DEFAULT 'host_direct'"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        @Volatile
        private var INSTANCE: NovaSpaceDatabase? = null

        fun getInstance(context: Context): NovaSpaceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaSpaceDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
