package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WorkspaceEntity::class, SecretRecordEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NovaSpaceDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun secretRecordDao(): SecretRecordDao

    companion object {
        private const val DATABASE_NAME = "nova_space.db"

        @Volatile
        private var INSTANCE: NovaSpaceDatabase? = null

        fun getInstance(context: Context): NovaSpaceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NovaSpaceDatabase::class.java,
                    DATABASE_NAME
                )
                    // Future schema versions must register explicit Migration(from, to) objects here.
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
