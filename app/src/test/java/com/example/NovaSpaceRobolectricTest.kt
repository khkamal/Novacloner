package com.example

import android.content.ClipboardManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NovaSpaceDatabase
import com.example.data.local.PreferencesManager
import com.example.data.repository.SecretZoneRepository
import com.example.data.repository.WorkspaceRepository
import com.example.domain.model.DefaultWorkspaceBehavior
import com.example.domain.model.SecretRecordCategory
import com.example.domain.model.ThemeMode
import com.example.domain.model.WorkspaceAccent
import com.example.utils.AppLaunchHelper
import com.example.utils.LaunchResult
import com.example.utils.WorkspaceIdentityGenerator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = NovaSpaceApp::class)
class NovaSpaceRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var db: NovaSpaceDatabase
    private lateinit var workspaceRepo: WorkspaceRepository
    private lateinit var prefsManager: PreferencesManager
    private lateinit var secretRepo: SecretZoneRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NovaSpaceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workspaceRepo = WorkspaceRepository(db.workspaceDao())
        prefsManager = PreferencesManager(context)
        secretRepo = SecretZoneRepository(db.secretRecordDao(), prefsManager)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `workspace create rename associate reset export import and delete`(): Unit = runBlocking {
        val id = workspaceRepo.createWorkspace(
            name = "Client Work",
            description = "Dedicated notes",
            notes = "Check daily sync",
            accentColor = WorkspaceAccent.EMERALD,
        )

        var loaded = workspaceRepo.getWorkspace(id)!!
        assertEquals("Client Work", loaded.name)
        assertEquals(WorkspaceAccent.EMERALD, loaded.accentColor)
        assertTrue(loaded.generatedWorkspaceId.isNotBlank())
        assertTrue(WorkspaceIdentityGenerator.isValidDeviceId(loaded.simulatedDeviceId))
        assertTrue(WorkspaceIdentityGenerator.isValidAndroidId(loaded.simulatedAndroidId))
        assertTrue(WorkspaceIdentityGenerator.isValidMac(loaded.simulatedWifiMac))
        assertTrue(WorkspaceIdentityGenerator.isValidMac(loaded.simulatedBluetoothMac))
        assertNotEquals(loaded.simulatedWifiMac, loaded.simulatedBluetoothMac)
        assertTrue(WorkspaceIdentityGenerator.isValidIccSerial(loaded.simulatedIccSerial))
        val originalLoaded = loaded

        workspaceRepo.renameWorkspace(id, "Engineering Hub")
        workspaceRepo.associateApp(id, "com.example.missing.app", "Missing App")
        workspaceRepo.togglePin(id)

        loaded = workspaceRepo.getWorkspace(id)!!
        assertEquals("Engineering Hub", loaded.name)
        assertEquals("com.example.missing.app", loaded.packageName)
        assertTrue(loaded.isPinned)
        assertEquals(originalLoaded.generatedWorkspaceId, loaded.generatedWorkspaceId)
        assertEquals(originalLoaded.simulatedDeviceId, loaded.simulatedDeviceId)
        assertEquals(originalLoaded.simulatedAndroidId, loaded.simulatedAndroidId)

        val exportedJson = workspaceRepo.exportWorkspacesJson()
        assertTrue(exportedJson.contains("Engineering Hub"))
        assertTrue(exportedJson.contains(originalLoaded.generatedWorkspaceId))
        assertTrue(exportedJson.contains(originalLoaded.simulatedDeviceId))
        assertTrue(exportedJson.contains(originalLoaded.simulatedAndroidId))

        workspaceRepo.resetWorkspace(id, clearAppAssociation = false)
        loaded = workspaceRepo.getWorkspace(id)!!
        assertEquals("", loaded.notes)
        assertEquals("", loaded.description)
        assertEquals(WorkspaceAccent.COBALT, loaded.accentColor)
        assertFalse(loaded.isPinned)
        assertEquals("com.example.missing.app", loaded.packageName)
        assertNotEquals(originalLoaded.generatedWorkspaceId, loaded.generatedWorkspaceId)
        assertNotEquals(originalLoaded.simulatedDeviceId, loaded.simulatedDeviceId)
        assertNotEquals(originalLoaded.simulatedAndroidId, loaded.simulatedAndroidId)
        assertNotEquals(originalLoaded.simulatedWifiMac, loaded.simulatedWifiMac)
        assertNotEquals(originalLoaded.simulatedBluetoothMac, loaded.simulatedBluetoothMac)
        assertNotEquals(originalLoaded.simulatedIccSerial, loaded.simulatedIccSerial)

        workspaceRepo.deleteWorkspace(id)
        assertNull(workspaceRepo.getWorkspace(id))

        val importResult = workspaceRepo.importWorkspacesJson(exportedJson)
        assertEquals(1, importResult.getOrThrow())
        val imported = workspaceRepo.observeWorkspaces().first()
        assertEquals(1, imported.size)
        assertEquals(originalLoaded.generatedWorkspaceId, imported.first().generatedWorkspaceId)
        assertEquals(originalLoaded.simulatedDeviceId, imported.first().simulatedDeviceId)

        // Importing a bundle containing duplicate identity values must deduplicate safely across all fields
        val duplicateBundleJson = """
            {
              "formatVersion": 1,
              "appName": "Nova Space",
              "exportedAt": 1700000000000,
              "workspaces": [
                {
                  "name": "Dup One",
                  "generatedWorkspaceId": "11111111-1111-1111-1111-111111111111",
                  "simulatedDeviceId": "358472910482715",
                  "simulatedAndroidId": "4a9f0c2e8b1d73f6",
                  "simulatedWifiMac": "02:aa:bb:cc:dd:01",
                  "simulatedBluetoothMac": "02:aa:bb:cc:dd:02",
                  "simulatedIccSerial": "8901410321111851072",
                  "createdAt": 1000,
                  "updatedAt": 1000
                },
                {
                  "name": "Dup Two",
                  "generatedWorkspaceId": "11111111-1111-1111-1111-111111111111",
                  "simulatedDeviceId": "358472910482715",
                  "simulatedAndroidId": "4A9F0C2E8B1D73F6",
                  "simulatedWifiMac": "02:AA:BB:CC:DD:01",
                  "simulatedBluetoothMac": "02:aa:bb:cc:dd:02",
                  "simulatedIccSerial": "8901410321111851072",
                  "createdAt": 1001,
                  "updatedAt": 1001
                },
                {
                  "name": "Legacy Blank",
                  "createdAt": 1002,
                  "updatedAt": 1002
                }
              ]
            }
        """.trimIndent()
        val dupImportResult = workspaceRepo.importWorkspacesJson(duplicateBundleJson)
        assertEquals(3, dupImportResult.getOrThrow())
        val allWorkspaces = workspaceRepo.observeWorkspaces().first()
        assertEquals(4, allWorkspaces.size)
        assertEquals(4, allWorkspaces.map { it.generatedWorkspaceId }.toSet().size)
        assertEquals(4, allWorkspaces.map { it.simulatedDeviceId }.toSet().size)
        assertEquals(4, allWorkspaces.map { it.simulatedAndroidId }.toSet().size)
        assertEquals(4, allWorkspaces.map { it.simulatedWifiMac }.toSet().size)
        assertEquals(4, allWorkspaces.map { it.simulatedBluetoothMac }.toSet().size)
        assertEquals(4, allWorkspaces.map { it.simulatedIccSerial }.toSet().size)
        assertTrue(allWorkspaces.all { it.generatedWorkspaceId.isNotBlank() })
    }

    @Test
    fun `concurrent workspace creation and ID regeneration produce strictly unique IDs`(): Unit = runBlocking {
        val createdIds = (1..12).map { idx ->
            async {
                workspaceRepo.createWorkspace(name = "Concurrent $idx")
            }
        }.awaitAll()

        val initialWorkspaces = createdIds.map { id -> workspaceRepo.getWorkspace(id)!! }
        assertEquals(12, initialWorkspaces.map { it.generatedWorkspaceId }.toSet().size)
        assertEquals(12, initialWorkspaces.map { it.simulatedDeviceId }.toSet().size)
        assertEquals(12, initialWorkspaces.map { it.simulatedAndroidId }.toSet().size)
        assertEquals(12, initialWorkspaces.map { it.simulatedWifiMac }.toSet().size)
        assertEquals(12, initialWorkspaces.map { it.simulatedBluetoothMac }.toSet().size)
        assertEquals(12, initialWorkspaces.map { it.simulatedIccSerial }.toSet().size)

        val regeneratedProfiles = createdIds.map { id ->
            async {
                workspaceRepo.regenerateWorkspaceIdentityProfile(id)!!
            }
        }.awaitAll()

        assertEquals(12, regeneratedProfiles.map { it.generatedWorkspaceId }.toSet().size)
        assertEquals(12, regeneratedProfiles.map { it.simulatedDeviceId }.toSet().size)
        assertEquals(12, regeneratedProfiles.map { it.simulatedAndroidId }.toSet().size)
        assertEquals(12, regeneratedProfiles.map { it.simulatedWifiMac }.toSet().size)
        assertEquals(12, regeneratedProfiles.map { it.simulatedBluetoothMac }.toSet().size)
        assertEquals(12, regeneratedProfiles.map { it.simulatedIccSerial }.toSet().size)
    }

    @Test
    fun `workspace IDs are persistent per workspace and isolated across regeneration and reset`(): Unit = runBlocking {
        val dbName = "workspace_isolation_test.db"
        context.deleteDatabase(dbName)
        var persistentDb = Room.databaseBuilder(context, NovaSpaceDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*NovaSpaceDatabase.ALL_MIGRATIONS)
            .build()
        var repo = WorkspaceRepository(persistentDb.workspaceDao())

        val idA = repo.createWorkspace(
            name = "Workspace A",
            description = "Alpha context",
            notes = "Alpha notes",
            accentColor = WorkspaceAccent.EMERALD,
            isPinned = true,
        )
        val idB = repo.createWorkspace(
            name = "Workspace B",
            description = "Beta context",
            notes = "Beta notes",
            accentColor = WorkspaceAccent.AMBER,
            isPinned = true,
        )

        val wsAInitial = repo.getWorkspace(idA)!!
        val wsBInitial = repo.getWorkspace(idB)!!
        assertTrue(wsAInitial.generatedWorkspaceId.isNotBlank())
        assertTrue(wsBInitial.generatedWorkspaceId.isNotBlank())
        assertNotEquals(wsAInitial.generatedWorkspaceId, wsBInitial.generatedWorkspaceId)
        assertNotEquals(wsAInitial.simulatedDeviceId, wsBInitial.simulatedDeviceId)
        assertNotEquals(wsAInitial.simulatedAndroidId, wsBInitial.simulatedAndroidId)

        // Close and reopen DB to verify full identity persistence across sessions
        persistentDb.close()
        persistentDb = Room.databaseBuilder(context, NovaSpaceDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*NovaSpaceDatabase.ALL_MIGRATIONS)
            .build()
        repo = WorkspaceRepository(persistentDb.workspaceDao())

        val wsAReopened = repo.getWorkspace(idA)!!
        val wsBReopened = repo.getWorkspace(idB)!!
        assertEquals(wsAInitial.generatedWorkspaceId, wsAReopened.generatedWorkspaceId)
        assertEquals(wsAInitial.simulatedDeviceId, wsAReopened.simulatedDeviceId)
        assertEquals(wsAInitial.simulatedAndroidId, wsAReopened.simulatedAndroidId)
        assertEquals(wsAInitial.simulatedWifiMac, wsAReopened.simulatedWifiMac)
        assertEquals(wsAInitial.simulatedBluetoothMac, wsAReopened.simulatedBluetoothMac)
        assertEquals(wsAInitial.simulatedIccSerial, wsAReopened.simulatedIccSerial)
        assertEquals(wsBInitial.generatedWorkspaceId, wsBReopened.generatedWorkspaceId)
        assertEquals(wsBInitial.simulatedDeviceId, wsBReopened.simulatedDeviceId)

        // Regenerating Workspace A identity profile must not affect Workspace B
        val regeneratedA = repo.regenerateWorkspaceIdentityProfile(idA)!!
        assertNotEquals(wsAInitial.generatedWorkspaceId, regeneratedA.generatedWorkspaceId)
        assertNotEquals(wsAInitial.simulatedDeviceId, regeneratedA.simulatedDeviceId)
        assertNotEquals(wsAInitial.simulatedAndroidId, regeneratedA.simulatedAndroidId)
        assertNotEquals(wsAInitial.simulatedWifiMac, regeneratedA.simulatedWifiMac)
        assertNotEquals(wsAInitial.simulatedBluetoothMac, regeneratedA.simulatedBluetoothMac)
        assertNotEquals(wsAInitial.simulatedIccSerial, regeneratedA.simulatedIccSerial)

        val wsBAfterRegen = repo.getWorkspace(idB)!!
        assertEquals(wsBInitial.generatedWorkspaceId, wsBAfterRegen.generatedWorkspaceId)
        assertEquals(wsBInitial.simulatedDeviceId, wsBAfterRegen.simulatedDeviceId)
        assertEquals(wsBInitial.simulatedAndroidId, wsBAfterRegen.simulatedAndroidId)
        assertEquals(wsBInitial.simulatedWifiMac, wsBAfterRegen.simulatedWifiMac)
        assertEquals(wsBInitial.simulatedBluetoothMac, wsBAfterRegen.simulatedBluetoothMac)
        assertEquals(wsBInitial.simulatedIccSerial, wsBAfterRegen.simulatedIccSerial)

        // Resetting Workspace A regenerates A's simulated identity without modifying Workspace B
        repo.resetWorkspace(idA, clearAppAssociation = true)
        val wsAAfterReset = repo.getWorkspace(idA)!!
        val wsBAfterResetA = repo.getWorkspace(idB)!!

        assertEquals("", wsAAfterReset.description)
        assertEquals("", wsAAfterReset.notes)
        assertEquals(WorkspaceAccent.COBALT, wsAAfterReset.accentColor)
        assertFalse(wsAAfterReset.isPinned)
        assertNotEquals(regeneratedA.generatedWorkspaceId, wsAAfterReset.generatedWorkspaceId)
        assertNotEquals(regeneratedA.simulatedDeviceId, wsAAfterReset.simulatedDeviceId)
        assertNotEquals(regeneratedA.simulatedAndroidId, wsAAfterReset.simulatedAndroidId)
        assertNotEquals(regeneratedA.simulatedWifiMac, wsAAfterReset.simulatedWifiMac)
        assertNotEquals(regeneratedA.simulatedBluetoothMac, wsAAfterReset.simulatedBluetoothMac)
        assertNotEquals(regeneratedA.simulatedIccSerial, wsAAfterReset.simulatedIccSerial)

        assertEquals("Workspace B", wsBAfterResetA.name)
        assertEquals("Beta context", wsBAfterResetA.description)
        assertEquals("Beta notes", wsBAfterResetA.notes)
        assertEquals(WorkspaceAccent.AMBER, wsBAfterResetA.accentColor)
        assertTrue(wsBAfterResetA.isPinned)
        assertEquals(wsBInitial.generatedWorkspaceId, wsBAfterResetA.generatedWorkspaceId)
        assertEquals(wsBInitial.simulatedDeviceId, wsBAfterResetA.simulatedDeviceId)
        assertEquals(wsBInitial.simulatedAndroidId, wsBAfterResetA.simulatedAndroidId)
        assertEquals(wsBInitial.simulatedWifiMac, wsBAfterResetA.simulatedWifiMac)
        assertEquals(wsBInitial.simulatedBluetoothMac, wsBAfterResetA.simulatedBluetoothMac)
        assertEquals(wsBInitial.simulatedIccSerial, wsBAfterResetA.simulatedIccSerial)

        // Deleting Workspace A removes A's profile and leaves Workspace B intact
        repo.deleteWorkspace(idA)
        assertNull(repo.getWorkspace(idA))
        assertNotNull(repo.getWorkspace(idB))

        persistentDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `room migration 1 to 2 preserves existing workspaces and secret records and assigns unique workspace IDs`(): Unit = runBlocking {
        val dbName = "migration_v1_to_v2_test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create a v1 database matching NovaSpaceDatabase schema version 1
        val v1Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v1Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workspaces` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `packageName` TEXT,
                `appDisplayName` TEXT,
                `description` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `accentColorKey` TEXT NOT NULL,
                `isPinned` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `launchCount` INTEGER NOT NULL,
                `lastLaunchedAt` INTEGER
            )
            """.trimIndent()
        )
        v1Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `secret_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `categoryKey` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `linkedPackageName` TEXT,
                `linkedAppName` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        v1Db.execSQL(
            "INSERT INTO workspaces (name, packageName, appDisplayName, description, notes, accentColorKey, isPinned, createdAt, updatedAt, launchCount, lastLaunchedAt) VALUES ('Legacy One', 'com.test.one', 'One', 'Desc 1', 'Notes 1', 'emerald', 1, 1000, 2000, 3, 1500)"
        )
        v1Db.execSQL(
            "INSERT INTO workspaces (name, packageName, appDisplayName, description, notes, accentColorKey, isPinned, createdAt, updatedAt, launchCount, lastLaunchedAt) VALUES ('Legacy Two', NULL, NULL, 'Desc 2', 'Notes 2', 'amber', 0, 1100, 2100, 0, NULL)"
        )
        v1Db.execSQL(
            "INSERT INTO secret_records (title, categoryKey, content, linkedPackageName, linkedAppName, createdAt, updatedAt) VALUES ('Legacy Secret Note', 'private_note', 'Confidential text', NULL, NULL, 1200, 2200)"
        )
        v1Db.version = 1
        v1Db.close()

        val migratedDb = Room.databaseBuilder(context, NovaSpaceDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*NovaSpaceDatabase.ALL_MIGRATIONS)
            .build()
        val repo = WorkspaceRepository(migratedDb.workspaceDao())

        val workspaces = repo.observeWorkspaces().first()
        assertEquals(2, workspaces.size)

        val one = workspaces.first { it.name == "Legacy One" }
        val two = workspaces.first { it.name == "Legacy Two" }
        assertEquals("Notes 1", one.notes)
        assertEquals(3, one.launchCount)
        assertEquals(WorkspaceAccent.EMERALD, one.accentColor)
        assertTrue(one.isPinned)

        assertEquals("Notes 2", two.notes)
        assertEquals(WorkspaceAccent.AMBER, two.accentColor)

        assertTrue(one.generatedWorkspaceId.isNotBlank())
        assertTrue(two.generatedWorkspaceId.isNotBlank())
        assertNotEquals(one.generatedWorkspaceId, two.generatedWorkspaceId)
        assertTrue(WorkspaceIdentityGenerator.isValidDeviceId(one.simulatedDeviceId))
        assertTrue(WorkspaceIdentityGenerator.isValidDeviceId(two.simulatedDeviceId))
        assertNotEquals(one.simulatedDeviceId, two.simulatedDeviceId)

        // Secret Zone records must remain intact after migration 1 -> 2 -> 3 -> 4
        val secrets = migratedDb.secretRecordDao().observeAllRecords().first()
        assertEquals(1, secrets.size)
        assertEquals("Legacy Secret Note", secrets.first().title)
        assertEquals("Confidential text", secrets.first().content)

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `room migration 2 to 3 adds unique index cleanly and preserves workspaces and secret records`(): Unit = runBlocking {
        val dbName = "migration_v2_to_v3_test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Simulate pre-index v2 schema previously installed on device/emulator
        val v2Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v2Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workspaces` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `generatedWorkspaceId` TEXT NOT NULL DEFAULT '',
                `name` TEXT NOT NULL,
                `packageName` TEXT,
                `appDisplayName` TEXT,
                `description` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `accentColorKey` TEXT NOT NULL,
                `isPinned` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `launchCount` INTEGER NOT NULL,
                `lastLaunchedAt` INTEGER
            )
            """.trimIndent()
        )
        v2Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `secret_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `categoryKey` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `linkedPackageName` TEXT,
                `linkedAppName` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        v2Db.execSQL(
            "INSERT INTO workspaces (generatedWorkspaceId, name, packageName, appDisplayName, description, notes, accentColorKey, isPinned, createdAt, updatedAt, launchCount, lastLaunchedAt) VALUES ('22222222-2222-2222-2222-222222222222', 'Existing v2 Space', 'com.test.v2', 'V2 App', 'Desc', 'Notes', 'cobalt', 1, 1000, 2000, 1, 1500)"
        )
        v2Db.execSQL(
            "INSERT INTO workspaces (generatedWorkspaceId, name, packageName, appDisplayName, description, notes, accentColorKey, isPinned, createdAt, updatedAt, launchCount, lastLaunchedAt) VALUES ('22222222-2222-2222-2222-222222222222', 'Duplicate v2 Space', NULL, NULL, '', '', 'amber', 0, 1100, 2100, 0, NULL)"
        )
        v2Db.execSQL(
            "INSERT INTO secret_records (title, categoryKey, content, linkedPackageName, linkedAppName, createdAt, updatedAt) VALUES ('Existing v2 Secret', 'private_note', 'Secret payload', NULL, NULL, 1200, 2200)"
        )
        v2Db.version = 2
        v2Db.close()

        val migratedDb = Room.databaseBuilder(context, NovaSpaceDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*NovaSpaceDatabase.ALL_MIGRATIONS)
            .build()
        val repo = WorkspaceRepository(migratedDb.workspaceDao())

        val workspaces = repo.observeWorkspaces().first()
        assertEquals(2, workspaces.size)
        val first = workspaces.first { it.name == "Existing v2 Space" }
        val second = workspaces.first { it.name == "Duplicate v2 Space" }
        assertEquals("22222222-2222-2222-2222-222222222222", first.generatedWorkspaceId)
        assertTrue(second.generatedWorkspaceId.isNotBlank())
        assertNotEquals(first.generatedWorkspaceId, second.generatedWorkspaceId)
        assertTrue(WorkspaceIdentityGenerator.isValidDeviceId(first.simulatedDeviceId))
        assertTrue(WorkspaceIdentityGenerator.isValidDeviceId(second.simulatedDeviceId))
        assertNotEquals(first.simulatedDeviceId, second.simulatedDeviceId)

        val secrets = migratedDb.secretRecordDao().observeAllRecords().first()
        assertEquals(1, secrets.size)
        assertEquals("Existing v2 Secret", secrets.first().title)

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `room migration 3 to 4 backfills unique simulated identity profiles and preserves existing records`(): Unit = runBlocking {
        val dbName = "migration_v3_to_v4_test.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val v3Db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v3Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workspaces` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `generatedWorkspaceId` TEXT NOT NULL DEFAULT '',
                `name` TEXT NOT NULL,
                `packageName` TEXT,
                `appDisplayName` TEXT,
                `description` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `accentColorKey` TEXT NOT NULL,
                `isPinned` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `launchCount` INTEGER NOT NULL,
                `lastLaunchedAt` INTEGER
            )
            """.trimIndent()
        )
        v3Db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workspaces_generatedWorkspaceId` ON `workspaces` (`generatedWorkspaceId`)"
        )
        v3Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `secret_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `categoryKey` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `linkedPackageName` TEXT,
                `linkedAppName` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        v3Db.execSQL(
            "INSERT INTO workspaces (generatedWorkspaceId, name, packageName, appDisplayName, description, notes, accentColorKey, isPinned, createdAt, updatedAt, launchCount, lastLaunchedAt) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'V3 Alpha', 'com.test.a', 'App A', 'Desc A', 'Notes A', 'cobalt', 1, 1000, 2000, 2, 1800)"
        )
        v3Db.execSQL(
            "INSERT INTO workspaces (generatedWorkspaceId, name, packageName, appDisplayName, description, notes, accentColorKey, isPinned, createdAt, updatedAt, launchCount, lastLaunchedAt) VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'V3 Beta', NULL, NULL, 'Desc B', 'Notes B', 'emerald', 0, 1100, 2100, 0, NULL)"
        )
        v3Db.execSQL(
            "INSERT INTO secret_records (title, categoryKey, content, linkedPackageName, linkedAppName, createdAt, updatedAt) VALUES ('V3 Secret', 'private_note', 'Kept safely', NULL, NULL, 1300, 2300)"
        )
        v3Db.version = 3
        v3Db.close()

        val migratedDb = Room.databaseBuilder(context, NovaSpaceDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(*NovaSpaceDatabase.ALL_MIGRATIONS)
            .build()
        val repo = WorkspaceRepository(migratedDb.workspaceDao())

        val workspaces = repo.observeWorkspaces().first()
        assertEquals(2, workspaces.size)
        val alpha = workspaces.first { it.name == "V3 Alpha" }
        val beta = workspaces.first { it.name == "V3 Beta" }

        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", alpha.generatedWorkspaceId)
        assertEquals("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", beta.generatedWorkspaceId)
        assertTrue(WorkspaceIdentityGenerator.isValidDeviceId(alpha.simulatedDeviceId))
        assertTrue(WorkspaceIdentityGenerator.isValidAndroidId(alpha.simulatedAndroidId))
        assertTrue(WorkspaceIdentityGenerator.isValidMac(alpha.simulatedWifiMac))
        assertTrue(WorkspaceIdentityGenerator.isValidMac(alpha.simulatedBluetoothMac))
        assertTrue(WorkspaceIdentityGenerator.isValidIccSerial(alpha.simulatedIccSerial))
        assertNotEquals(alpha.simulatedDeviceId, beta.simulatedDeviceId)
        assertNotEquals(alpha.simulatedAndroidId, beta.simulatedAndroidId)
        assertNotEquals(alpha.simulatedWifiMac, beta.simulatedWifiMac)
        assertNotEquals(alpha.simulatedBluetoothMac, beta.simulatedBluetoothMac)
        assertNotEquals(alpha.simulatedIccSerial, beta.simulatedIccSerial)

        val secrets = migratedDb.secretRecordDao().observeAllRecords().first()
        assertEquals(1, secrets.size)
        assertEquals("V3 Secret", secrets.first().title)

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `missing app launch returns NotInstalled cleanly`() {
        val result = AppLaunchHelper.launchApp(context, "com.novaspace.nonexistent.pkg")
        assertTrue(result is LaunchResult.NotInstalled)
        assertFalse(AppLaunchHelper.isPackageInstalled(context, "com.novaspace.nonexistent.pkg"))
    }

    @Test
    fun `secret zone pin setup verify lock auto-lock and reset`(): Unit = runBlocking {
        assertFalse(secretRepo.isPinConfigured())
        assertTrue(secretRepo.setupPin("7391", enableBiometric = false))
        assertTrue(secretRepo.isPinConfigured())
        assertTrue(secretRepo.isUnlocked.value)

        secretRepo.saveRecord(
            title = "Private Key Note",
            category = SecretRecordCategory.PRIVATE_NOTE,
            content = "Local checklist",
        )
        assertEquals(1, secretRepo.observeRecords().first().size)

        secretRepo.lock()
        assertFalse(secretRepo.isUnlocked.value)

        assertFalse(secretRepo.verifyPinAndUnlock("0000"))
        assertFalse(secretRepo.isUnlocked.value)

        assertTrue(secretRepo.verifyPinAndUnlock("7391"))
        assertTrue(secretRepo.isUnlocked.value)

        assertFalse(secretRepo.changePin("0000", "2468"))
        assertTrue(secretRepo.changePin("7391", "2468"))

        secretRepo.lock()
        assertFalse(secretRepo.verifyPinAndUnlock("7391"))
        assertTrue(secretRepo.verifyPinAndUnlock("2468"))

        secretRepo.onAppMovedToBackground()
        assertFalse(secretRepo.isUnlocked.value)

        secretRepo.resetSecretZone()
        assertFalse(secretRepo.isPinConfigured())
        assertTrue(secretRepo.observeRecords().first().isEmpty())
    }

    @Test
    fun `preferences persist theme mode and default workspace behavior`(): Unit = runBlocking {
        prefsManager.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, prefsManager.userPreferences.first().themeMode)

        prefsManager.setDefaultWorkspaceBehavior(DefaultWorkspaceBehavior.LAUNCH_LINKED_APP)
        assertEquals(
            DefaultWorkspaceBehavior.LAUNCH_LINKED_APP,
            prefsManager.userPreferences.first().defaultWorkspaceBehavior,
        )
    }

    @Test
    fun `home screen creates workspaces verifies copy and regenerate per workspace and navigates to global device info`() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("home_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_empty_state").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Create your first workspace.").performScrollTo().assertIsDisplayed()

        // Create first workspace
        composeRule.onNodeWithTag("add_workspace_fab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("create_workspace_name_input").performTextInput("Focus Space")
        composeRule.onNodeWithTag("confirm_create_workspace_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Focus Space").fetchSemanticsNodes().isNotEmpty()
        }

        // Create second workspace & link a sample package on Focus Space via live AppContainer repository
        val appContainer = (context.applicationContext as NovaSpaceApp).container
        val focusId = runBlocking {
            val all = appContainer.workspaceRepository.observeWorkspaces().first()
            val focus = all.first { it.name == "Focus Space" }
            appContainer.workspaceRepository.associateApp(
                id = focus.id,
                packageName = context.packageName,
                appDisplayName = "Nova Space",
            )
            appContainer.workspaceRepository.createWorkspace(name = "Second Space")
            focus.id
        }

        val focusBefore = runBlocking { appContainer.workspaceRepository.getWorkspace(focusId)!! }
        val secondBefore = runBlocking {
            appContainer.workspaceRepository.observeWorkspaces().first().first { it.name == "Second Space" }
        }
        assertNotEquals(focusBefore.generatedWorkspaceId, secondBefore.generatedWorkspaceId)

        // Open Focus Space -> Detail -> Workspace Device Info
        composeRule.onNodeWithText("Focus Space").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("detail_device_info_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("detail_device_info_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(focusBefore.generatedWorkspaceId).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Device Identity").assertIsDisplayed()
        composeRule.onNodeWithText("Simulated Profile").assertIsDisplayed()
        composeRule.onNodeWithTag("identity_device_id_value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("identity_android_id_value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("identity_wifi_mac_value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("identity_bluetooth_mac_value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("identity_icc_serial_value").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_device_info_uuid").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("workspace_device_info_linked_package").performScrollTo().assertIsDisplayed()

        // Verify individual copy buttons copy exact values for Focus Space to ClipboardManager
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        composeRule.onNodeWithTag("copy_device_id_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            focusBefore.simulatedDeviceId,
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
        composeRule.onNodeWithTag("copy_android_id_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            focusBefore.simulatedAndroidId,
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
        composeRule.onNodeWithTag("copy_wifi_mac_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            focusBefore.simulatedWifiMac,
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
        composeRule.onNodeWithTag("copy_bluetooth_mac_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            focusBefore.simulatedBluetoothMac,
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
        composeRule.onNodeWithTag("copy_icc_serial_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            focusBefore.simulatedIccSerial,
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )
        composeRule.onNodeWithTag("workspace_copy_uuid_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            focusBefore.generatedWorkspaceId,
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )

        // Verify Regenerate dialog updates Focus Space's simulated profile & UUID without affecting Second Space
        // Scroll below the button row first so the button sits well above any active Snackbar
        composeRule.onNodeWithText("Workspace Lifecycle").performScrollTo()
        composeRule.onNodeWithTag("workspace_regenerate_uuid_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("confirm_workspace_regenerate_uuid_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("confirm_workspace_regenerate_uuid_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(focusBefore.generatedWorkspaceId).fetchSemanticsNodes().isEmpty()
        }
        val focusAfter = runBlocking { appContainer.workspaceRepository.getWorkspace(focusId)!! }
        val secondAfter = runBlocking { appContainer.workspaceRepository.getWorkspace(secondBefore.id)!! }
        assertNotEquals(focusBefore.generatedWorkspaceId, focusAfter.generatedWorkspaceId)
        assertNotEquals(focusBefore.simulatedDeviceId, focusAfter.simulatedDeviceId)
        assertNotEquals(focusBefore.simulatedAndroidId, focusAfter.simulatedAndroidId)
        assertNotEquals(focusBefore.simulatedWifiMac, focusAfter.simulatedWifiMac)
        assertNotEquals(focusBefore.simulatedBluetoothMac, focusAfter.simulatedBluetoothMac)
        assertNotEquals(focusBefore.simulatedIccSerial, focusAfter.simulatedIccSerial)

        assertEquals(secondBefore.generatedWorkspaceId, secondAfter.generatedWorkspaceId)
        assertEquals(secondBefore.simulatedDeviceId, secondAfter.simulatedDeviceId)
        assertEquals(secondBefore.simulatedAndroidId, secondAfter.simulatedAndroidId)
        assertEquals(secondBefore.simulatedWifiMac, secondAfter.simulatedWifiMac)
        assertEquals(secondBefore.simulatedBluetoothMac, secondAfter.simulatedBluetoothMac)
        assertEquals(secondBefore.simulatedIccSerial, secondAfter.simulatedIccSerial)

        composeRule.onNodeWithTag("workspace_device_info_uuid").performScrollTo().assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText(focusAfter.generatedWorkspaceId)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )

        // Navigate to Global Device Info and back (scroll footer first so button sits above any active Snackbar)
        composeRule.onNodeWithTag("workspace_device_info_footer").performScrollTo()
        composeRule.onNodeWithTag("open_global_device_info_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Global Device Info").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Global Host Device Diagnostics").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Global Host Device Diagnostics").assertIsDisplayed()
        composeRule.onNodeWithTag("device_info_back_button").performClick()

        // Navigate Back -> Detail -> Back -> Home -> Settings
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("workspace_device_info_back_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("workspace_device_info_back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("detail_back_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("detail_back_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("home_settings_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    }
}
