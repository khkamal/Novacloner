package com.example

import android.content.Context
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
    fun `workspace create rename associate reset export import and delete`() = runBlocking {
        val id = workspaceRepo.createWorkspace(
            name = "Client Work",
            description = "Dedicated notes",
            notes = "Check daily sync",
            accentColor = WorkspaceAccent.EMERALD,
        )

        var loaded = workspaceRepo.getWorkspace(id)!!
        assertEquals("Client Work", loaded.name)
        assertEquals(WorkspaceAccent.EMERALD, loaded.accentColor)

        workspaceRepo.renameWorkspace(id, "Engineering Hub")
        workspaceRepo.associateApp(id, "com.example.missing.app", "Missing App")
        workspaceRepo.togglePin(id)

        loaded = workspaceRepo.getWorkspace(id)!!
        assertEquals("Engineering Hub", loaded.name)
        assertEquals("com.example.missing.app", loaded.packageName)
        assertTrue(loaded.isPinned)

        val exportedJson = workspaceRepo.exportWorkspacesJson()
        assertTrue(exportedJson.contains("Engineering Hub"))

        workspaceRepo.resetWorkspace(id, clearAppAssociation = false)
        loaded = workspaceRepo.getWorkspace(id)!!
        assertEquals("", loaded.notes)
        assertEquals("com.example.missing.app", loaded.packageName)

        workspaceRepo.deleteWorkspace(id)
        assertNull(workspaceRepo.getWorkspace(id))

        val importResult = workspaceRepo.importWorkspacesJson(exportedJson)
        assertEquals(1, importResult.getOrThrow())
        assertEquals(1, workspaceRepo.observeWorkspaces().first().size)
    }

    @Test
    fun `missing app launch returns NotInstalled cleanly`() {
        val result = AppLaunchHelper.launchApp(context, "com.novaspace.nonexistent.pkg")
        assertTrue(result is LaunchResult.NotInstalled)
        assertFalse(AppLaunchHelper.isPackageInstalled(context, "com.novaspace.nonexistent.pkg"))
    }

    @Test
    fun `secret zone pin setup verify lock auto-lock and reset`() = runBlocking {
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
    fun `preferences persist theme mode and instance id`() = runBlocking {
        prefsManager.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, prefsManager.userPreferences.first().themeMode)

        val firstId = prefsManager.getOrCreateAppGeneratedInstanceId()
        assertTrue(firstId.isNotBlank())
        assertEquals(firstId, prefsManager.getOrCreateAppGeneratedInstanceId())

        val secondId = prefsManager.regenerateAppGeneratedInstanceId()
        assertTrue(secondId.isNotBlank() && secondId != firstId)
    }

    @Test
    fun `home screen empty state creates workspace and navigates to settings`() {
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
        composeRule.onNodeWithText("Focus Space").assertIsDisplayed()

        composeRule.onNodeWithTag("home_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
    }
}
