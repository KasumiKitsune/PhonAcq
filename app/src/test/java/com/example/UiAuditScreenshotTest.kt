package com.example

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.data.Speaker
import com.example.data.SpeakerRecording
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.screens.MainScreen
import com.example.ui.screens.RecordingTipsDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WordListEditorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RecordingViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class UiAuditScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var vm: RecordingViewModel
    private val outDir = "build/outputs/roborazzi"

    private fun SemanticsNodeInteraction.snapshotOrAssert(fileName: String) {
        val isRoborazziActive = System.getProperty("roborazzi.test.record") == "true" ||
            System.getProperty("roborazzi.test.verify") == "true" ||
            System.getProperty("roborazzi.test.compare") == "true"
        if (isRoborazziActive) {
            this.captureRoboImage("$outDir/$fileName")
        } else {
            this.assertExists()
        }
    }

    @Before
    fun setUp() = runBlocking {
        File(outDir).mkdirs()
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("app_settings", Application.MODE_PRIVATE)
            .edit()
            .putBoolean("has_seen_recording_tips_v2", true)
            .commit()

        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ProjectRepository(db)
        repo.bootstrapDefaultProjectIfNeeded()

        val projects = db.projectDao().getAllProjects().first()
        val project = projects.first()
        val speakers = db.speakerDao().getAllSpeakers().first()
        val speaker = speakers.firstOrNull() ?: run {
            val newId = db.speakerDao().insertSpeaker(
                Speaker(name = "林阿婆", gender = "女", age = 68, dialect = "苏州城区老派")
            )
            Speaker(id = newId, name = "林阿婆", gender = "女", age = 68, dialect = "苏州城区老派")
        }

        val projDir = File(app.getExternalFilesDir(null), "project_${project.id}").apply { mkdirs() }
        val words = db.wordDao().getWordsForProjectSync(project.id)
        if (words.size >= 3) {
            val wav1 = File(projDir, "word_${words[0].id}_speaker_${speaker.id}_1000.wav")
            val wav1b = File(projDir, "word_${words[0].id}_speaker_${speaker.id}_2000.wav")
            val wav2 = File(projDir, "word_${words[1].id}_speaker_${speaker.id}_1000.wav")
            wav1.writeBytes(ByteArray(4096))
            wav1b.writeBytes(ByteArray(4096))
            wav2.writeBytes(ByteArray(4096))

            db.speakerRecordingDao().insertRecording(
                SpeakerRecording(
                    wordId = words[0].id,
                    speakerId = speaker.id,
                    recordingFile = wav1b.name,
                    durationMs = 1280L,
                    qualityStatus = "声音偏小"
                )
            )
            db.speakerRecordingDao().insertRecording(
                SpeakerRecording(
                    wordId = words[1].id,
                    speakerId = speaker.id,
                    recordingFile = wav2.name,
                    durationMs = 960L,
                    qualityStatus = "正常"
                )
            )
        }

        vm = RecordingViewModel(app, repo)
        vm.markRecordingTipsSeen()
        vm.selectProjectAndSpeaker(project, speaker)
        Thread.sleep(150)
    }

    private fun clearViewModel(target: RecordingViewModel) {
        target.wirelessLink.cancelOrDisconnect()
        runCatching {
            androidx.lifecycle.ViewModel::class.java.declaredMethods
                .firstOrNull { it.name == "clear" || it.name.startsWith("clear$") }
                ?.apply { isAccessible = true }
                ?.invoke(target)
        }
    }

    @After
    fun tearDown() {
        DesktopAdaptiveState.isDesktopPlatform = false
        DesktopAdaptiveState.isProjectSidebarCollapsed = false
        DesktopAdaptiveState.desktopTopBarSlot = null
        if (::vm.isInitialized) {
            clearViewModel(vm)
        }
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Test
    fun captureDesktop960AndSettings() {
        DesktopAdaptiveState.isDesktopPlatform = true
        DesktopAdaptiveState.isProjectSidebarCollapsed = false

        RuntimeEnvironment.setQualifiers("w960dp-h640dp-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(viewModel = vm)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().snapshotOrAssert("audit_desktop_960_main.png")

        composeTestRule.onNodeWithTag("rail_settings").performClick()
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.onRoot().snapshotOrAssert("audit_desktop_960_settings.png")
    }

    @Test
    fun captureDesktop1280DockedPhoneticPane() {
        DesktopAdaptiveState.isDesktopPlatform = true
        DesktopAdaptiveState.isProjectSidebarCollapsed = false
        RuntimeEnvironment.setQualifiers("w1280dp-h800dp-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(viewModel = vm)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        val firstWord = vm.currentWords.value.firstOrNull()
        vm.showPhoneticWordDetail(firstWord)
        composeTestRule.mainClock.advanceTimeBy(450)
        composeTestRule.onRoot().snapshotOrAssert("audit_desktop_1280_phonetic.png")
    }

    @Test
    fun captureDesktopTipsDialog() {
        DesktopAdaptiveState.isDesktopPlatform = true
        RuntimeEnvironment.setQualifiers("w960dp-h640dp-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                RecordingTipsDialog(onDismiss = {}, isFirstLaunch = true)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.onNodeWithTag("tips_next_or_done_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.onAllNodes(isDialog()).onFirst()
            .snapshotOrAssert("audit_desktop_960_tips_p2.png")

        composeTestRule.onNodeWithTag("tips_next_or_done_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNodeWithTag("tips_next_or_done_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(1200)
        composeTestRule.onAllNodes(isDialog()).onFirst()
            .snapshotOrAssert("audit_desktop_960_tips_p4.png")
    }

    @Test
    fun captureMobileLandscape800x360() {
        DesktopAdaptiveState.isDesktopPlatform = false
        RuntimeEnvironment.setQualifiers("w800dp-h360dp-land-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(viewModel = vm)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().snapshotOrAssert("audit_landscape_800x360_main.png")

        composeTestRule.onNodeWithTag("tab_recording_workspace").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().snapshotOrAssert("audit_landscape_800x360_recording.png")
    }

    @Test
    fun captureMobileLandscapeTipsDialog() {
        DesktopAdaptiveState.isDesktopPlatform = false
        RuntimeEnvironment.setQualifiers("w800dp-h360dp-land-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                RecordingTipsDialog(onDismiss = {}, isFirstLaunch = true)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.onNodeWithTag("tips_next_or_done_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.onAllNodes(isDialog()).onFirst()
            .snapshotOrAssert("audit_landscape_800x360_tips.png")
    }

    @Test
    fun captureMobilePortrait360x800() {
        DesktopAdaptiveState.isDesktopPlatform = false
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(viewModel = vm)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().snapshotOrAssert("audit_portrait_360_projects.png")

        composeTestRule.onNodeWithTag("tab_recording_workspace").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().snapshotOrAssert("audit_portrait_360_recording.png")

        composeTestRule.onNodeWithTag("tab_archive_explorer").performClick()
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().snapshotOrAssert("audit_portrait_360_archive.png")
    }

    @Test
    fun captureMobilePortraitTipsDialog() {
        DesktopAdaptiveState.isDesktopPlatform = false
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                RecordingTipsDialog(onDismiss = {}, isFirstLaunch = true)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onNodeWithTag("tips_next_or_done_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.onAllNodes(isDialog()).onFirst()
            .snapshotOrAssert("audit_portrait_360_tips_p2.png")
    }

    @Test
    fun capturePortraitWordEditorAndGlobalMgmt() {
        DesktopAdaptiveState.isDesktopPlatform = false
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                WordListEditorScreen(
                    viewModel = vm,
                    initialProject = vm.currentProject.value,
                    onBack = {},
                    onSavedAndOpenRecording = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().snapshotOrAssert("audit_portrait_360_editor.png")
    }

    @Test
    fun verifyProjectExportScreenSingleRowOnMobileAndDesktop() {
        DesktopAdaptiveState.isDesktopPlatform = false
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        vm.completeInitialModeSelection(simple = false)

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(viewModel = vm)
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.onNodeWithTag("tab_archive_explorer").performClick()
        composeTestRule.mainClock.advanceTimeBy(400)
        composeTestRule.onNodeWithTag("export_zip_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(400)

        composeTestRule.onNodeWithTag("project_export_screen").assertExists()
        val zipBounds = composeTestRule.onNodeWithTag("export_format_zip_chip").fetchSemanticsNode().boundsInRoot
        val praatBounds = composeTestRule.onNodeWithTag("export_format_praat_collection_chip").fetchSemanticsNode().boundsInRoot
        val teprojBounds = composeTestRule.onNodeWithTag("export_format_teproj_chip").fetchSemanticsNode().boundsInRoot

        // Verify all 3 cards sit strictly in ONE single horizontal row on 360dp portrait
        org.junit.Assert.assertEquals(zipBounds.top, praatBounds.top, 2f)
        org.junit.Assert.assertEquals(praatBounds.top, teprojBounds.top, 2f)
        org.junit.Assert.assertTrue(zipBounds.left < praatBounds.left && praatBounds.left < teprojBounds.left)

        composeTestRule.onNodeWithTag("export_format_praat_collection_chip").performClick()
        composeTestRule.mainClock.advanceTimeBy(350)
        val binaryBounds = composeTestRule.onNodeWithTag("praat_collection_binary_chip").getUnclippedBoundsInRoot()
        val textBounds = composeTestRule.onNodeWithTag("praat_collection_text_chip").getUnclippedBoundsInRoot()
        // On 360dp narrow mobile portrait, encoding cards must stack full-width rather than squeezing into 2 columns
        org.junit.Assert.assertTrue(
            "Encoding cards should stack vertically on 360dp mobile (binaryTop=${binaryBounds.top}, textTop=${textBounds.top})",
            textBounds.top > binaryBounds.top + 20.dp
        )
        org.junit.Assert.assertTrue(
            "Encoding card width (${binaryBounds.width}) should span full mobile card width",
            binaryBounds.width >= 260.dp
        )

        composeTestRule.onNodeWithTag("export_page_back_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(350)
        composeTestRule.onNodeWithTag("export_zip_button").assertExists()
    }

    @Test
    fun verifyMobileSettingsMainMenuAndSecondarySubPages() {
        DesktopAdaptiveState.isDesktopPlatform = false
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        vm.completeInitialModeSelection(simple = false)

        var rootExited = false
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = vm,
                    onBack = { rootExited = true },
                    onOpenRecordingsCleanup = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(350)

        // 1. Mobile starts on Main Menu, NOT overwhelmed by TextGrid controls
        composeTestRule.onNodeWithTag("settings_mobile_main_menu").assertExists()
        composeTestRule.onNodeWithTag("settings_desktop_sidebar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tg_syntax_long_chip").assertDoesNotExist()

        // 2. Entering Praat TextGrid secondary page
        composeTestRule.onNodeWithTag("settings_nav_praat_textgrid").performClick()
        composeTestRule.mainClock.advanceTimeBy(350)
        composeTestRule.onNodeWithTag("settings_mobile_subpage_praat_textgrid").assertExists()
        composeTestRule.onNodeWithTag("tg_syntax_long_chip").assertExists()

        // 3. Sub-page back button returns to Settings Main Menu without exiting Settings
        composeTestRule.onNodeWithTag("settings_subpage_back_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(350)
        org.junit.Assert.assertFalse("Sub-page back should return to main menu, not exit Settings", rootExited)
        composeTestRule.onNodeWithTag("settings_mobile_main_menu").assertExists()

        // 4. Main menu back button exits Settings
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(200)
        org.junit.Assert.assertTrue("Main menu back should invoke root onBack", rootExited)
    }

    @Test
    fun verifyDesktopSettingsSidebarAndMainPaneSplit() {
        DesktopAdaptiveState.isDesktopPlatform = true
        RuntimeEnvironment.setQualifiers("w960dp-h640dp-mdpi")
        composeTestRule.mainClock.autoAdvance = false
        vm.completeInitialModeSelection(simple = false)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    viewModel = vm,
                    onBack = {},
                    onOpenRecordingsCleanup = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(350)

        // Sidebar and Main Detail Pane must sit side-by-side
        val sidebarBounds = composeTestRule.onNodeWithTag("settings_desktop_sidebar").getUnclippedBoundsInRoot()
        val detailBounds = composeTestRule.onNodeWithTag("settings_desktop_detail_pane").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(
            "Desktop sidebar (${sidebarBounds.right}) must sit to the left of main pane (${detailBounds.left})",
            sidebarBounds.right <= detailBounds.left + 2.dp
        )
        org.junit.Assert.assertTrue(
            "Desktop detail pane (${detailBounds.width}) should have generous width",
            detailBounds.width >= 550.dp
        )

        // Switching sidebar category updates right main pane while keeping sidebar visible
        composeTestRule.onNodeWithTag("settings_nav_praat_textgrid").performClick()
        composeTestRule.mainClock.advanceTimeBy(350)
        composeTestRule.onNodeWithTag("settings_desktop_sidebar").assertExists()
        composeTestRule.onNodeWithTag("tg_syntax_long_chip").assertExists()
    }

    @Test
    fun verifyUserManualMobileAndDesktopLayouts() {
        // Mobile 360dp + Simple Mode: silently renders Simple Mode Android manual with zero mode/platform switcher tabs
        DesktopAdaptiveState.isDesktopPlatform = false
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        composeTestRule.mainClock.autoAdvance = false

        val isSimple = androidx.compose.runtime.mutableStateOf(true)
        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.UserManualScreen(
                    onBack = {},
                    isSimpleMode = isSimple.value
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(350)

        composeTestRule.onNodeWithTag("user_manual_screen").assertExists()
        composeTestRule.onNodeWithTag("toc_item_101").assertExists()
        composeTestRule.onNodeWithTag("toc_item_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("manual_desktop_sidebar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("manual_mode_simple_tab").assertDoesNotExist()
        composeTestRule.onNodeWithTag("manual_platform_windows_tab").assertDoesNotExist()

        // When environment switches to Advanced Mode, manual silently switches to Advanced Mode chapters
        composeTestRule.runOnUiThread {
            isSimple.value = false
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        composeTestRule.mainClock.advanceTimeBy(350)
        composeTestRule.onNodeWithTag("toc_item_1").assertExists()
        composeTestRule.onNodeWithTag("toc_item_101").assertDoesNotExist()
    }

    @Test
    fun verifyUserManualDesktopTwoPaneSplit() {
        DesktopAdaptiveState.isDesktopPlatform = true
        RuntimeEnvironment.setQualifiers("w960dp-h640dp-mdpi")
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.UserManualScreen(
                    onBack = {},
                    isSimpleMode = false
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(350)

        composeTestRule.onNodeWithTag("manual_mode_advanced_tab").assertDoesNotExist()
        composeTestRule.onNodeWithTag("manual_platform_windows_tab").assertDoesNotExist()

        val sidebarBounds = composeTestRule.onNodeWithTag("manual_desktop_sidebar").getUnclippedBoundsInRoot()
        val readerBounds = composeTestRule.onNodeWithTag("manual_desktop_reader_pane").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(
            "Manual desktop sidebar (${sidebarBounds.right}) must sit to the left of reader pane (${readerBounds.left})",
            sidebarBounds.right <= readerBounds.left + 2.dp
        )
        composeTestRule.onNodeWithTag("toc_item_6").performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithTag("manual_desktop_sidebar").assertExists()
    }

    @Test
    fun verifyFieldworkEncyclopediaMobileSearchAndDesktopSplit() {
        // 1. Mobile 360dp: search colloquial terms ("AirPods" -> bluetooth_mic_cut, "解压" -> what_is_zip)
        DesktopAdaptiveState.isDesktopPlatform = false
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.FieldworkEncyclopediaScreen(
                    onBack = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(350)

        composeTestRule.onNodeWithTag("fieldwork_encyclopedia_screen").assertExists()
        composeTestRule.onNodeWithTag("encyclopedia_desktop_sidebar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("enc_item_speaker_selection").assertExists()

        // Colloquial search: "AirPods" surfaces Bluetooth headset entry
        composeTestRule.onNodeWithTag("encyclopedia_search_input").performTextReplacement("AirPods")
        composeTestRule.mainClock.advanceTimeBy(250)
        composeTestRule.onNodeWithTag("enc_item_bluetooth_mic_cut").assertExists()

        // Colloquial search: "解压" surfaces .zip entry
        composeTestRule.onNodeWithTag("encyclopedia_search_input").performTextReplacement("解压")
        composeTestRule.mainClock.advanceTimeBy(250)
        composeTestRule.onNodeWithTag("enc_item_what_is_zip").assertExists()
    }

    @Test
    fun verifyFieldworkEncyclopediaDesktopTwoPaneSplit() {
        DesktopAdaptiveState.isDesktopPlatform = true
        RuntimeEnvironment.setQualifiers("w960dp-h640dp-mdpi")
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.FieldworkEncyclopediaScreen(
                    onBack = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(350)

        val sidebarBounds = composeTestRule.onNodeWithTag("encyclopedia_desktop_sidebar").getUnclippedBoundsInRoot()
        val readerBounds = composeTestRule.onNodeWithTag("encyclopedia_desktop_reader_pane").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(
            "Encyclopedia desktop sidebar (${sidebarBounds.right}) must sit to the left of reader pane (${readerBounds.left})",
            sidebarBounds.right <= readerBounds.left + 2.dp
        )

        // Volume filter works and updates sidebar entries cleanly
        composeTestRule.onNodeWithTag("encyclopedia_vol_AUDIO_GEAR").performClick()
        composeTestRule.mainClock.advanceTimeBy(250)
        composeTestRule.onNodeWithTag("enc_item_ai_denoise_harm").assertExists()
        composeTestRule.onNodeWithTag("enc_item_bluetooth_mic_cut").performClick()
        composeTestRule.mainClock.advanceTimeBy(250)
        composeTestRule.onNodeWithTag("encyclopedia_desktop_reader_pane").assertExists()
    }

    @Test
    fun verifyAll24EncyclopediaDiagramsRenderCleanly() {
        val entries = com.example.ui.screens.EncyclopediaRepository.allEntries
        org.junit.Assert.assertEquals("Encyclopedia must contain all 28 entries", 28, entries.size)
        entries.forEach { entry ->
            org.junit.Assert.assertNotNull("Entry ${entry.id} must have a diagramId", entry.diagramId)
            org.junit.Assert.assertFalse("Entry ${entry.id} must have a non-blank diagramCaption", entry.diagramCaption.isNullOrBlank())
        }

        RuntimeEnvironment.setQualifiers("w800dp-h1200dp-mdpi")

        val activeIndex = androidx.compose.runtime.mutableIntStateOf(0)
        val darkTheme = androidx.compose.runtime.mutableStateOf(false)

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = darkTheme.value) {
                val entry = entries[activeIndex.intValue]
                com.example.ui.screens.EncyclopediaDynamicDiagramCard(
                    diagramId = entry.diagramId!!,
                    caption = entry.diagramCaption,
                    modifier = Modifier
                )
            }
        }

        for (isDark in listOf(false, true)) {
            darkTheme.value = isDark
            for (idx in entries.indices) {
                activeIndex.intValue = idx
                composeTestRule.waitForIdle()
                composeTestRule.onRoot().getUnclippedBoundsInRoot()
            }
        }
    }

    @Test
    fun verifyMultiDeviceLinkSettingsAndPromptDisplayPresetsAndPermissions() {
        DesktopAdaptiveState.isDesktopPlatform = true
        RuntimeEnvironment.setQualifiers("w960dp-h720dp-mdpi")

        val showPromptScreen = androidx.compose.runtime.mutableStateOf(false)
        composeTestRule.setContent {
            MyApplicationTheme {
                if (showPromptScreen.value) {
                    com.example.ui.screens.PromptDisplayLinkScreen(
                        viewModel = vm,
                        onExitSession = { showPromptScreen.value = false }
                    )
                } else {
                    SettingsScreen(
                        viewModel = vm,
                        onBack = {},
                        onOpenRecordingsCleanup = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Navigate to 无线互联 in Settings sidebar
        composeTestRule.onNodeWithTag("settings_nav_multi_device_link").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify all 4 friendly presets exist and switch cleanly
        for (preset in com.example.link.PromptVisualPreset.entries) {
            composeTestRule.onNodeWithTag("preset_card_${preset.name}")
                .assertExists()
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()
            org.junit.Assert.assertEquals(preset, vm.wirelessLink.promptPolicy.value.visualPreset)
        }

        // Open Wireless Pairing Dialog and verify 看读端 / 遥控端 role options
        composeTestRule.onNodeWithTag("settings_open_wireless_link_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wireless_pairing_dialog").assertExists()
        composeTestRule.onNodeWithTag("pair_role_controller").assertExists()
        composeTestRule.onNodeWithTag("pair_role_prompt_display").assertExists()
        composeTestRule.onNodeWithTag("pair_dialog_close_button").performClick()
        composeTestRule.waitForIdle()

        // 2. Connect real loopback peer so 看读端 is in active connected state and verify permission & 中场休息 gating
        val app = ApplicationProvider.getApplicationContext<Application>()
        val peerDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val peerVm = RecordingViewModel(app, ProjectRepository(peerDb))
        try {
            vm.wirelessLink.updatePromptPolicy {
                it.copy(
                    pageTurnPolicy = com.example.link.PromptPageTurnPolicy.LOCKED_BY_CONTROLLER,
                    allowPromptStartStopRecord = false,
                    isIntermissionBreak = false,
                    visualPreset = com.example.link.PromptVisualPreset.WARM_PAPER
                )
            }
            val port = vm.wirelessLink.startHostingSession(
                hostRole = com.example.link.LinkRole.PROMPT_DISPLAY,
                preferredPort = 0
            )
            val pin = vm.wirelessLink.pairingPin.value
            runBlocking {
                val joinRes = peerVm.wirelessLink.joinPeerSession("127.0.0.1", port, pin)
                org.junit.Assert.assertTrue("Loopback join must succeed", joinRes.isSuccess)
            }

            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.runOnUiThread {
                showPromptScreen.value = true
            }
            composeTestRule.mainClock.advanceTimeBy(350)
            composeTestRule.onNodeWithTag("prompt_display_screen_root").assertExists()
            composeTestRule.onNodeWithTag("prompt_word_card").assertExists()
            // Locked by controller: neither prev nor next buttons appear on 看读端
            composeTestRule.onNodeWithTag("prompt_btn_next").assertDoesNotExist()
            composeTestRule.onNodeWithTag("prompt_btn_prev").assertDoesNotExist()

            // Grant NEXT_ONLY permission -> only 下一词 appears
            vm.wirelessLink.updatePromptPolicy {
                it.copy(pageTurnPolicy = com.example.link.PromptPageTurnPolicy.NEXT_ONLY)
            }
            composeTestRule.mainClock.advanceTimeBy(250)
            composeTestRule.onNodeWithTag("prompt_btn_next").assertExists()
            composeTestRule.onNodeWithTag("prompt_btn_prev").assertDoesNotExist()

            // Trigger 中场休息 -> word card replaced by gentle intermission break card
            vm.wirelessLink.toggleIntermissionBreak()
            composeTestRule.mainClock.advanceTimeBy(250)
            composeTestRule.onNodeWithTag("prompt_intermission_card").assertExists()
        } finally {
            clearViewModel(peerVm)
            vm.wirelessLink.cancelOrDisconnect()
            peerDb.close()
        }
    }

    @Test
    fun verifyWirelessLinkLoopbackHandshakeAndLiveSessionSync() = runBlocking {
        var initSpin = 0
        while (vm.currentWords.value.isEmpty() && initSpin < 20) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(25)
            initSpin++
        }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val peerDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val peerVm = RecordingViewModel(app, ProjectRepository(peerDb))

        try {
            val port = vm.wirelessLink.startHostingSession(
                hostRole = com.example.link.LinkRole.CONTROLLER,
                initialMicOwner = com.example.link.ActiveMicOwner.PROMPT_DISPLAY_MIC,
                preferredPort = 0
            )
            val pin = vm.wirelessLink.pairingPin.value
            org.junit.Assert.assertTrue("Port must be > 0", port > 0)
            org.junit.Assert.assertEquals(6, pin.length)

            // Wrong PIN must be rejected cleanly
            val badAttempt = peerVm.wirelessLink.joinPeerSession("127.0.0.1", port, "000000".let { if (it == pin) "999999" else it })
            org.junit.Assert.assertTrue("Mismatched PIN must fail handshake", badAttempt.isFailure)

            // Correct 6-digit PIN succeeds and assigns PROMPT_DISPLAY ("看读端") to peer
            val goodAttempt = peerVm.wirelessLink.joinPeerSession("127.0.0.1", port, pin)
            org.junit.Assert.assertTrue("Valid PIN must succeed: ${goodAttempt.exceptionOrNull()}", goodAttempt.isSuccess)
            org.junit.Assert.assertEquals(com.example.link.LinkRole.PROMPT_DISPLAY, goodAttempt.getOrNull())

            // Wait briefly for initial TCP snapshot sync
            var retries = 0
            while (peerVm.wirelessLink.sessionSnapshot.value.words.isEmpty() && retries < 30) {
                kotlinx.coroutines.delay(50)
                retries++
            }
            val initialPos = vm.currentSeqIndex.value
            val syncedWords = peerVm.wirelessLink.sessionSnapshot.value.words
            org.junit.Assert.assertTrue("Peer 看读端 must receive full wordlist from Host", syncedWords.isNotEmpty())
            org.junit.Assert.assertEquals(initialPos, peerVm.wirelessLink.sessionSnapshot.value.currentSeqPos)

            // Controller advances to next word -> Peer 看读端 updates cursor automatically
            vm.wirelessLink.requestNextWord()
            retries = 0
            while (peerVm.wirelessLink.sessionSnapshot.value.currentSeqPos != initialPos + 1 && retries < 30) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(50)
                retries++
            }
            org.junit.Assert.assertEquals(initialPos + 1, peerVm.wirelessLink.sessionSnapshot.value.currentSeqPos)

            // Controller toggles 中场休息 -> Peer 看读端 receives intermission state
            vm.wirelessLink.toggleIntermissionBreak()
            retries = 0
            while (!peerVm.wirelessLink.promptPolicy.value.isIntermissionBreak && retries < 30) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(50)
                retries++
            }
            org.junit.Assert.assertTrue(peerVm.wirelessLink.promptPolicy.value.isIntermissionBreak)
        } finally {
            clearViewModel(peerVm)
            vm.wirelessLink.cancelOrDisconnect()
            peerDb.close()
        }
    }

    @Test
    fun verifyRemoteControllerProjectSpeakerSwitchingAndUnselectedProjectState() = runBlocking {
        var initSpin = 0
        while (vm.allProjects.value.isEmpty() && initSpin < 20) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(25)
            initSpin++
        }
        val app = ApplicationProvider.getApplicationContext<Application>()
        val peerDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val peerVm = RecordingViewModel(app, ProjectRepository(peerDb))

        try {
            // 1. Host starts as PROMPT_DISPLAY ("看读端") with NO project selected (currentProject == null)
            vm.selectProject(null)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(40)
            org.junit.Assert.assertNull(vm.currentProject.value)

            val port = vm.wirelessLink.startHostingSession(
                hostRole = com.example.link.LinkRole.PROMPT_DISPLAY,
                initialMicOwner = com.example.link.ActiveMicOwner.PROMPT_DISPLAY_MIC,
                preferredPort = 0
            )
            val pin = vm.wirelessLink.pairingPin.value

            // Peer joins as CONTROLLER ("遥控端")
            val joinRes = peerVm.wirelessLink.joinPeerSession("127.0.0.1", port, pin)
            org.junit.Assert.assertTrue(joinRes.isSuccess)
            org.junit.Assert.assertEquals(com.example.link.LinkRole.CONTROLLER, joinRes.getOrNull())

            // Wait for initial snapshot arrival
            var retries = 0
            while (!peerVm.wirelessLink.sessionSnapshot.value.hasSnapshotArrived && retries < 30) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(50)
                retries++
            }
            val emptySnap = peerVm.wirelessLink.sessionSnapshot.value
            org.junit.Assert.assertTrue("Snapshot must arrive even when no project is selected", emptySnap.hasSnapshotArrived)
            org.junit.Assert.assertEquals("Unselected project must report projectId == 0L", 0L, emptySnap.projectId)
            org.junit.Assert.assertNull("currentWord must be null when projectId == 0L", emptySnap.currentWord)
            org.junit.Assert.assertTrue("Available projects list must be synced to Peer Controller", emptySnap.availableProjects.isNotEmpty())
            org.junit.Assert.assertTrue("Available speakers list must be synced to Peer Controller", emptySnap.availableSpeakers.isNotEmpty())

            // 2. Peer Controller remotely selects a Project on the Host!
            val targetProj = emptySnap.availableProjects.first()
            peerVm.wirelessLink.requestSelectProject(targetProj.id)
            retries = 0
            while (
                (peerVm.wirelessLink.sessionSnapshot.value.projectId != targetProj.id ||
                    peerVm.wirelessLink.sessionSnapshot.value.words.isEmpty()) &&
                retries < 40
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(50)
                retries++
            }
            org.junit.Assert.assertEquals(targetProj.id, vm.currentProject.value?.id)
            org.junit.Assert.assertEquals(targetProj.id, peerVm.wirelessLink.sessionSnapshot.value.projectId)
            org.junit.Assert.assertNotNull(peerVm.wirelessLink.sessionSnapshot.value.currentWord)

            // 3. Peer Controller remotely creates and switches to a new Speaker on the Host!
            peerVm.wirelessLink.requestCreateSpeaker(
                name = "遥控新建合作人",
                gender = "男",
                age = 68,
                dialect = "苏州老派"
            )
            retries = 0
            while (peerVm.wirelessLink.sessionSnapshot.value.speakerName != "遥控新建合作人" && retries < 35) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                kotlinx.coroutines.delay(50)
                retries++
            }
            org.junit.Assert.assertEquals("遥控新建合作人", vm.currentSpeaker.value?.name)
            org.junit.Assert.assertEquals("遥控新建合作人", peerVm.wirelessLink.sessionSnapshot.value.speakerName)
        } finally {
            clearViewModel(peerVm)
            vm.wirelessLink.cancelOrDisconnect()
            peerDb.close()
        }
    }

    @Test
    fun verifyStudioRecordTriggerPressAndHoldAndTapBehavior() {
        val isRecordingState = androidx.compose.runtime.mutableStateOf(false)
        val strictPttState = androidx.compose.runtime.mutableStateOf(false)
        var startCount = 0
        var stopCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.components.StudioRecordTriggerButton(
                    isRecording = isRecordingState.value,
                    enabled = true,
                    strictPushToTalk = strictPttState.value,
                    dbfs = -24f,
                    testTag = "controller_record_button",
                    onStartRecord = {
                        startCount++
                        isRecordingState.value = true
                    },
                    onStopRecord = {
                        stopCount++
                        isRecordingState.value = false
                    },
                    onToggleRecord = {
                        if (isRecordingState.value) {
                            stopCount++
                            isRecordingState.value = false
                        } else {
                            startCount++
                            isRecordingState.value = true
                        }
                    }
                )
            }
        }

        // 1. Press-and-hold (>= 220ms) -> starts immediately on down, stops automatically on release!
        composeTestRule.onNodeWithTag("controller_record_button").performTouchInput {
            down(center)
        }
        composeTestRule.waitForIdle()
        org.junit.Assert.assertTrue("Must start recording immediately on press down", isRecordingState.value)
        org.junit.Assert.assertEquals(1, startCount)
        org.junit.Assert.assertEquals(0, stopCount)

        composeTestRule.onNodeWithTag("controller_record_button").performTouchInput {
            advanceEventTime(450L)
            up()
        }
        composeTestRule.waitForIdle()
        org.junit.Assert.assertFalse("Must stop recording automatically when releasing after hold", isRecordingState.value)
        org.junit.Assert.assertEquals(1, stopCount)

        // 2. Quick tap (< 220ms) in hybrid mode -> latches recording ON; second quick tap stops it!
        composeTestRule.onNodeWithTag("controller_record_button").performTouchInput {
            down(center)
            advanceEventTime(60L)
            up()
        }
        composeTestRule.waitForIdle()
        org.junit.Assert.assertTrue("Quick tap in hybrid mode must latch recording ON", isRecordingState.value)
        org.junit.Assert.assertEquals(2, startCount)
        org.junit.Assert.assertEquals(1, stopCount)

        composeTestRule.onNodeWithTag("controller_record_button").performTouchInput {
            down(center)
            advanceEventTime(60L)
            up()
        }
        composeTestRule.waitForIdle()
        org.junit.Assert.assertFalse("Second tap must stop latched recording", isRecordingState.value)
        org.junit.Assert.assertEquals(2, stopCount)

        // 3. Strict Push-to-Talk mode -> even a brief 60ms press stops immediately on release!
        strictPttState.value = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("controller_record_button").performTouchInput {
            down(center)
            advanceEventTime(60L)
            up()
        }
        composeTestRule.waitForIdle()
        org.junit.Assert.assertFalse("Strict PTT mode must always stop on release", isRecordingState.value)
        org.junit.Assert.assertEquals(3, startCount)
        org.junit.Assert.assertEquals(3, stopCount)
    }
}








