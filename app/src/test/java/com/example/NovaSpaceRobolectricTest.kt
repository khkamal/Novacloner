package com.example

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
        val originalGeneratedId = loaded.generatedWorkspaceId

        workspaceRepo.renameWorkspace(id, "Engineering Hub")
        workspaceRepo.associateApp(id, "com.example.missing.app", "Missing App")
        workspaceRepo.togglePin(id)

        loaded = workspaceRepo.getWorkspace(id)!!
        assertEquals("Engineering Hub", loaded.name)
        assertEquals("com.example.missing.app", loaded.packageName)
        assertTrue(loaded.isPinned)
        assertEquals(originalGeneratedId, loaded.generatedWorkspaceId)

        val exportedJson = workspaceRepo.exportWorkspacesJson()
        assertTrue(exportedJson.contains("Engineering Hub"))
        assertTrue(exportedJson.contains(originalGeneratedId))

        workspaceRepo.resetWorkspace(id, clearAppAssociation = false)
        loaded = workspaceRepo.getWorkspace(id)!!
        assertEquals("", loaded.notes)
        assertEquals("", loaded.description)
        assertEquals(WorkspaceAccent.COBALT, loaded.accentColor)
        assertFalse(loaded.isPinned)
        assertEquals("com.example.missing.app", loaded.packageName)
        assertNotEquals(originalGeneratedId, loaded.generatedWorkspaceId)

        workspaceRepo.deleteWorkspace(id)
        assertNull(workspaceRepo.getWorkspace(id))

        val importResult = workspaceRepo.importWorkspacesJson(exportedJson)
        assertEquals(1, importResult.getOrThrow())
        val imported = workspaceRepo.observeWorkspaces().first()
        assertEquals(1, imported.size)
        assertEquals(originalGeneratedId, imported.first().generatedWorkspaceId)

        // Importing the same bundle a second time must assign a distinct generatedWorkspaceId
        workspaceRepo.importWorkspacesJson(exportedJson)
        val afterSecondImport = workspaceRepo.observeWorkspaces().first()
        assertEquals(2, afterSecondImport.size)
        assertNotEquals(
            afterSecondImport[0].generatedWorkspaceId,
            afterSecondImport[1].generatedWorkspaceId,
        )
    }

    @Test
    fun `workspace IDs are persistent per workspace and isolated across regeneration and reset`(): Unit = runBlocking {
        val dbName = "workspace_isolation_test.db"
        context.deleteDatabase(dbName)
        var persistentDb = Room.databaseBuilder(context, NovaSpaceDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(NovaSpaceDatabase.MIGRATION_1_2)
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

        // Close and reopen DB to verify persistence across sessions
        persistentDb.close()
        persistentDb = Room.databaseBuilder(context, NovaSpaceDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(NovaSpaceDatabase.MIGRATION_1_2)
            .build()
        repo = WorkspaceRepository(persistentDb.workspaceDao())

        assertEquals(wsAInitial.generatedWorkspaceId, repo.getWorkspace(idA)!!.generatedWorkspaceId)
        assertEquals(wsBInitial.generatedWorkspaceId, repo.getWorkspace(idB)!!.generatedWorkspaceId)

        // Regenerating Workspace A ID must not affect Workspace B
        val regeneratedA = repo.regenerateWorkspaceGeneratedId(idA)
        assertNotNull(regeneratedA)
        assertNotEquals(wsAInitial.generatedWorkspaceId, regeneratedA)
        assertNotEquals(wsBInitial.generatedWorkspaceId, regeneratedA)
        assertEquals(wsBInitial.generatedWorkspaceId, repo.getWorkspace(idB)!!.generatedWorkspaceId)

        // Resetting Workspace A must not modify Workspace B metadata or generatedWorkspaceId
        repo.resetWorkspace(idA, clearAppAssociation = true)
        val wsAAfterReset = repo.getWorkspace(idA)!!
        val wsBAfterResetA = repo.getWorkspace(idB)!!

        assertEquals("", wsAAfterReset.description)
        assertEquals("", wsAAfterReset.notes)
        assertEquals(WorkspaceAccent.COBALT, wsAAfterReset.accentColor)
        assertFalse(wsAAfterReset.isPinned)
        assertNotEquals(regeneratedA, wsAAfterReset.generatedWorkspaceId)

        assertEquals("Workspace B", wsBAfterResetA.name)
        assertEquals("Beta context", wsBAfterResetA.description)
        assertEquals("Beta notes", wsBAfterResetA.notes)
        assertEquals(WorkspaceAccent.AMBER, wsBAfterResetA.accentColor)
        assertTrue(wsBAfterResetA.isPinned)
        assertEquals(wsBInitial.generatedWorkspaceId, wsBAfterResetA.generatedWorkspaceId)

        // Deleting Workspace A leaves Workspace B intact
        repo.deleteWorkspace(idA)
        assertNull(repo.getWorkspace(idA))
        assertNotNull(repo.getWorkspace(idB))

        persistentDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `room migration 1 to 2 preserves existing workspaces and assigns unique workspace IDs`(): Unit = runBlocking {
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
        v1Db.version = 1
        v1Db.close()

        val migratedDb = Room.databaseBuilder(context, NovaSpaceDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .addMigrations(NovaSpaceDatabase.MIGRATION_1_2)
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
    fun `home screen creates workspace opens workspace device info and navigates to settings`() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("home_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_empty_state").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Create your first workspace.").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("add_workspace_fab").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("create_workspace_name_input").performTextInput("Focus Space")
        composeRule.onNodeWithTag("confirm_create_workspace_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Focus Space").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Focus Space").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("detail_device_info_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("detail_device_info_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("workspace_device_info_uuid").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Workspace-Specific Identifier").assertIsDisplayed()
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
