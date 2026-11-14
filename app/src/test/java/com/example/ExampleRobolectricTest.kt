package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.viewmodel.RecordingViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PhonAcq", appName)
  }

  @Test
  fun `parseImportTextToParsedWords handles diverse txt formats accurately`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = AppDatabase.getDatabase(app)
    val repo = ProjectRepository(db)
    val vm = RecordingViewModel(app, repo)

    val sampleTxt = "\uFEFF【阴平组】\n" +
        "1. 巴 (pa55)\n" +
        "02、诗 古清平\n" +
        "梯: ti55\n" +
        "风\t古清平唇音\n" +
        "【阳平组】\n" +
        "时、题、人、明\n" +
        "日头 太阳"

    val parsed = vm.parseImportTextToParsedWords(sampleTxt)
    assertEquals(9, parsed.size)
    assertEquals("巴", parsed[0].text)
    assertEquals("pa55", parsed[0].remark)
    assertEquals("阴平组", parsed[0].groupName)

    assertEquals("诗", parsed[1].text)
    assertEquals("古清平", parsed[1].remark)

    assertEquals("梯", parsed[2].text)
    assertEquals("ti55", parsed[2].remark)

    assertEquals("风", parsed[3].text)
    assertEquals("古清平唇音", parsed[3].remark)

    assertEquals("时", parsed[4].text)
    assertEquals("阳平组", parsed[4].groupName)
    assertEquals("明", parsed[7].text)

    assertEquals("日头", parsed[8].text)
    assertEquals("太阳", parsed[8].remark)
  }

  @Test
  fun `buildPhonTracerProjectJson produces 100 percent PhonTracer v1_0 compliant schema`() {
    val project = com.example.data.Project(
        id = 1L,
        name = "基本元音舌位字表",
        description = "测试元音实验"
    )
    val speaker = com.example.data.Speaker(
        id = 1L,
        name = "示例发音人",
        gender = "女",
        age = 26,
        dialect = "北京"
    )
    val recordedWord = com.example.data.Word(
        id = 10L,
        projectId = 1L,
        text = "衣",
        remark = "[i] (clothes) #单元音 #前元音",
        groupName = "高元音",
        originalIndex = 0,
        isRecorded = true,
        recordingFile = "word_10.wav",
        durationMs = 1180L
    )
    val unrecordedWord = com.example.data.Word(
        id = 11L,
        projectId = 1L,
        text = "乌",
        remark = "[u] (crow)",
        groupName = "高元音",
        originalIndex = 1,
        isRecorded = false,
        recordingFile = null,
        durationMs = 0L
    )

    val seg = com.example.utils.ZipUtility.resolveWordSegmentation(
        word = recordedWord,
        analysis = null,
        exactDurationSec = 1.18
    )
    val audioRecord = com.example.utils.ZipUtility.ExportedWordAudioRecord(
        speakerId = speaker.id,
        wordId = recordedWord.id,
        originalIndex = recordedWord.originalIndex,
        zipAudioPath = "audio/SPK01_001_衣_i.wav",
        segmentation = seg
    )

    val root = com.example.utils.ZipUtility.buildPhonTracerProjectJson(
        project = project,
        speakerWordPairs = listOf(speaker to listOf(recordedWord, unrecordedWord)),
        exportedAudioRecords = listOf(audioRecord),
        preferredActiveSpeakerId = speaker.id
    )
    val jsonStr = root.toString(2)
    org.junit.Assert.assertFalse(jsonStr.contains("NaN") || jsonStr.contains("Infinity"))

    assertEquals("1.0", root.getString("version"))
    assertEquals(BuildConfig.VERSION_NAME, root.getString("software_version"))
    assertEquals("1.0", root.getString("report_format_version"))
    assertEquals("SPK01", root.getString("active_speaker_id"))
    assertEquals("continuous", root.getString("export_numbering_rule"))
    org.junit.Assert.assertTrue(root.getBoolean("trim_silence"))

    val groups = root.getJSONArray("groups")
    assertEquals(1, groups.length())
    val grp0 = groups.getJSONObject(0)
    assertEquals("grp_01", grp0.getString("id"))
    assertEquals("高元音", grp0.getString("name"))
    val grpItems = grp0.getJSONArray("items")
    assertEquals(2, grpItems.length())
    val item0 = grpItems.getJSONObject(0)
    assertEquals("001", item0.getString("id"))
    assertEquals("衣", item0.getString("label"))
    assertEquals("i", item0.getJSONObject("meta").getString("ipa"))
    assertEquals("clothes", item0.getJSONObject("meta").getString("gloss"))

    val spkObj = root.getJSONObject("speakers").getJSONObject("SPK01")
    assertEquals("多条独立音频", spkObj.getString("tab_mode"))
    assertEquals("001", spkObj.getString("last_selected_iid"))
    val pendingPaths = spkObj.getJSONArray("pending_batch_paths")
    assertEquals(1, pendingPaths.length())
    assertEquals("audio/SPK01_001_衣_i.wav", pendingPaths.getString(0))

    val spkItems = spkObj.getJSONObject("items")
    val recItemJson = spkItems.getJSONObject("001")
    assertEquals("audio/SPK01_001_衣_i.wav", recItemJson.getString("path"))
    org.junit.Assert.assertTrue(recItemJson.getDouble("start") < recItemJson.getDouble("end"))
    assertEquals(recItemJson.getDouble("start"), recItemJson.getDouble("raw_start"), 1e-6)
    assertEquals(recItemJson.getDouble("end"), recItemJson.getDouble("raw_end"), 1e-6)
    org.junit.Assert.assertFalse(recItemJson.has("pitch_data_file"))
    org.junit.Assert.assertFalse(recItemJson.has("formant_data_file"))

    val unrecItemJson = spkItems.getJSONObject("002")
    org.junit.Assert.assertFalse(unrecItemJson.has("path"))
  }

  private fun writeDummyWav(target: java.io.File, sampleRate: Int = 44100, durationMs: Int = 200) {
    target.parentFile?.mkdirs()
    val numSamples = (sampleRate * durationMs) / 1000
    val dataSize = numSamples * 2
    val totalDataLen = 36 + dataSize
    val byteRate = sampleRate * 2
    val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
      put("RIFF".toByteArray(Charsets.US_ASCII))
      putInt(totalDataLen)
      put("WAVE".toByteArray(Charsets.US_ASCII))
      put("fmt ".toByteArray(Charsets.US_ASCII))
      putInt(16)
      putShort(1) // PCM
      putShort(1) // Mono
      putInt(sampleRate)
      putInt(byteRate)
      putShort(2) // BlockAlign
      putShort(16) // BitsPerSample
      put("data".toByteArray(Charsets.US_ASCII))
      putInt(dataSize)
    }.array()
    java.io.FileOutputStream(target).use { fos ->
      fos.write(header)
      fos.write(ByteArray(dataSize))
    }
  }

  @Test
  fun `simpleModeExport produces clean Chinese folders and UTF8 BOM CSV without json metadata`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val project = com.example.data.Project(id = 777L, name = "方言声调调查", description = "测试简洁导出")
    val projectDir = java.io.File(context.getExternalFilesDir(null), "project_${project.id}")
    projectDir.deleteRecursively()
    projectDir.mkdirs()

    writeDummyWav(java.io.File(projectDir, "spk1_w1.wav"))
    writeDummyWav(java.io.File(projectDir, "spk2_w1.wav"))

    val spk1 = com.example.data.Speaker(id = 1L, name = "张三", gender = "男", age = 52, dialect = "苏州")
    val spk2 = com.example.data.Speaker(id = 2L, name = "李四", gender = "女", age = 48, dialect = "上海")

    val wordSpk1 = com.example.data.Word(
        id = 101L,
        projectId = project.id,
        text = "巴",
        remark = "阴平例字",
        speakerNote = "白读",
        groupName = "阴平组",
        originalIndex = 0,
        isRecorded = true,
        recordingFile = "spk1_w1.wav",
        durationMs = 200L
    )
    val wordSpk2 = wordSpk1.copy(
        speakerNote = "文读",
        recordingFile = "spk2_w1.wav"
    )

    // 1. Single-speaker Simple Mode export (no TextGrid)
    val singleOut = java.io.ByteArrayOutputStream()
    val singleSummary = com.example.utils.ZipUtility.zipProjectMultiSpeaker(
        context = context,
        project = project,
        speakerWordPairs = listOf(spk1 to listOf(wordSpk1)),
        options = com.example.utils.ExportOptions(
            simpleModeExport = true,
            includePraatTextGrid = false,
            exportAllSpeakers = false
        ),
        targetStream = singleOut
    )
    org.junit.Assert.assertTrue(singleSummary.success)
    assertEquals(1, singleSummary.wavCount)
    assertEquals(0, singleSummary.textGridCount)

    val singleEntries = mutableMapOf<String, ByteArray>()
    java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(singleOut.toByteArray())).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        singleEntries[entry.name] = zis.readBytes()
        entry = zis.nextEntry
      }
    }
    org.junit.Assert.assertTrue(singleEntries.containsKey("录音文件/001_巴.wav"))
    org.junit.Assert.assertTrue(singleEntries.containsKey("录音汇总表.xlsx"))
    org.junit.Assert.assertTrue(singleEntries.keys.none { it.endsWith(".json") || it.endsWith(".txt") || it.endsWith(".csv") })

    val xlsxBytes = singleEntries.getValue("录音汇总表.xlsx")
    assertEquals(0x50.toByte(), xlsxBytes[0]) // 'P'
    assertEquals(0x4B.toByte(), xlsxBytes[1]) // 'K'
    val sheets = com.example.utils.XlsxSpreadsheetWriter.readAllSheets(xlsxBytes)
    org.junit.Assert.assertTrue(sheets.containsKey("录音汇总表"))
    org.junit.Assert.assertTrue(sheets.containsKey("完整调查字表"))
    val summaryRows = sheets.getValue("录音汇总表")
    assertEquals(
        listOf("序号", "分组", "词条", "词表提示", "记音与备注", "发音人", "方言点", "时长(秒)", "音频文件名"),
        summaryRows[0]
    )
    org.junit.Assert.assertTrue(summaryRows[1].contains("巴"))
    org.junit.Assert.assertTrue(summaryRows[1].contains("白读"))
    org.junit.Assert.assertTrue(summaryRows[1].contains("001_巴.wav"))

    // 2. Multi-speaker Simple Mode export (with optional TextGrid)
    val multiOut = java.io.ByteArrayOutputStream()
    val multiSummary = com.example.utils.ZipUtility.zipProjectMultiSpeaker(
        context = context,
        project = project,
        speakerWordPairs = listOf(spk1 to listOf(wordSpk1), spk2 to listOf(wordSpk2)),
        options = com.example.utils.ExportOptions(
            simpleModeExport = true,
            includePraatTextGrid = true,
            exportAllSpeakers = true
        ),
        targetStream = multiOut
    )
    org.junit.Assert.assertTrue(multiSummary.success)
    assertEquals(2, multiSummary.wavCount)
    assertEquals(2, multiSummary.textGridCount)

    val multiNames = mutableSetOf<String>()
    java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(multiOut.toByteArray())).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        multiNames.add(entry.name)
        entry = zis.nextEntry
      }
    }
    org.junit.Assert.assertTrue(multiNames.contains("录音文件/张三/001_巴.wav"))
    org.junit.Assert.assertTrue(multiNames.contains("录音文件/张三/001_巴.TextGrid"))
    org.junit.Assert.assertTrue(multiNames.contains("录音文件/李四/001_巴.wav"))
    org.junit.Assert.assertTrue(multiNames.contains("录音文件/李四/001_巴.TextGrid"))
    org.junit.Assert.assertTrue(multiNames.none { it.endsWith(".json") })

    projectDir.deleteRecursively()
  }

  @Test
  fun `mode switching between Simple and Advanced preserves speaker groups and recordings`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    app.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    try {
      val repo = ProjectRepository(db)
      val spkWuId = repo.createSpeaker(
          name = "王阿婆",
          gender = "女",
          age = 71,
          dialect = "宁波",
          notes = "老年组发音人",
          groupName = "浙北组"
      )
      val spkYueId = repo.createSpeaker(
          name = "陈伯",
          gender = "男",
          age = 68,
          dialect = "广州",
          notes = "对照发音人",
          groupName = "珠三角组"
      )
      val projId = repo.createProject(
          name = "古全浊声母调查",
          description = "分组测试",
          wordTexts = listOf("袍", "病"),
          speakerId = spkWuId,
          speakerGroupName = "浙北组"
      )
      val savedProject = repo.getProjectById(projId)!!
      val savedWu = repo.getSpeakerById(spkWuId)!!
      val savedYue = repo.getSpeakerById(spkYueId)!!
      val allSpeakers = listOf(savedWu, savedYue)

      // Advanced mode respects speakerGroupName binding (only 1 speaker in "浙北组")
      val advSummary = repo.getProjectMultiSpeakerSummarySync(savedProject, allSpeakers, ignoreSpeakerGroups = false)
      assertEquals(1, advSummary.speakerProgressList.size)
      assertEquals("王阿婆", advSummary.speakerProgressList[0].speaker.name)

      // Simple mode flattens speaker groups without mutating DB (shows both speakers)
      val simpleSummary = repo.getProjectMultiSpeakerSummarySync(savedProject, allSpeakers, ignoreSpeakerGroups = true)
      assertEquals(2, simpleSummary.speakerProgressList.size)

      val vm = RecordingViewModel(app, repo)
      org.junit.Assert.assertFalse(vm.hasSelectedUiMode.value)
      vm.completeInitialModeSelection(simple = true)
      org.junit.Assert.assertTrue(vm.hasSelectedUiMode.value)
      org.junit.Assert.assertTrue(vm.isSimpleMode.value)

      // Switch back to Advanced Mode and verify DB speaker groups & project binding are intact
      vm.setSimpleMode(false)
      org.junit.Assert.assertFalse(vm.isSimpleMode.value)

      assertEquals("浙北组", repo.getSpeakerById(spkWuId)!!.groupName)
      assertEquals("珠三角组", repo.getSpeakerById(spkYueId)!!.groupName)
      assertEquals("浙北组", repo.getProjectById(projId)!!.speakerGroupName)
    } finally {
      db.close()
    }
  }

  @Test
  fun `praatCollectionExport packs paired Sound2 TextGrid and Summary Table in both binary and text modes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val project = com.example.data.Project(id = 888L, name = "吴语声调调查.v1", description = "测试 Praat 工程导出")
    val projectDir = java.io.File(context.getExternalFilesDir(null), "project_${project.id}")
    projectDir.deleteRecursively()
    projectDir.mkdirs()

    try {
      writeDummyWav(java.io.File(projectDir, "spk1_w1.wav"), sampleRate = 22050, durationMs = 120)
      writeDummyWav(java.io.File(projectDir, "spk2_w1.wav"), sampleRate = 22050, durationMs = 150)

      val spk1 = com.example.data.Speaker(id = 1L, name = "张大爷", gender = "男", age = 63, dialect = "苏州")
      val spk2 = com.example.data.Speaker(id = 2L, name = "李阿姨", gender = "女", age = 58, dialect = "上海")

      val w1 = com.example.data.Word(
          id = 201L,
          projectId = project.id,
          text = "东",
          remark = "tʊŋ55",
          speakerNote = "高平 [tʊŋ˥˥]",
          groupName = "阴平",
          originalIndex = 0,
          isRecorded = true,
          recordingFile = "spk1_w1.wav",
          durationMs = 120L
      )
      val w2 = w1.copy(
          speakerNote = "高降 [tʊŋ˥˧]",
          recordingFile = "spk2_w1.wav",
          durationMs = 150L
      )

      // 1. Binary ooBinaryFile full project export
      val binOut = java.io.ByteArrayOutputStream()
      val binSummary = com.example.utils.ZipUtility.zipProjectMultiSpeaker(
          context = context,
          project = project,
          speakerWordPairs = listOf(spk1 to listOf(w1), spk2 to listOf(w2)),
          options = com.example.utils.ExportOptions(
              includeAudio = true,
              includePraatTextGrid = true,
              exportAllSpeakers = true,
              exportAsPraatCollection = true,
              praatCollectionBinary = true,
              includePraatSummaryTable = true
          ),
          targetStream = binOut
      )
      org.junit.Assert.assertTrue(binSummary.success)
      org.junit.Assert.assertTrue(binSummary.isPraatCollection)
      assertEquals(2, binSummary.wavCount)
      assertEquals(2, binSummary.textGridCount)
      assertEquals(5, binSummary.objectCount) // 2 Sound + 2 TextGrid + 1 Table

      val parsedBin = com.example.utils.PraatCollectionExporter.readBinaryCollection(binOut.toByteArray())
      assertEquals(5, parsedBin.size)
      val s1 = parsedBin[0] as com.example.utils.PraatCollectionExporter.PraatSoundObject
      val tg1 = parsedBin[1] as com.example.utils.PraatCollectionExporter.PraatTextGridObject
      val s2 = parsedBin[2] as com.example.utils.PraatCollectionExporter.PraatSoundObject
      val tg2 = parsedBin[3] as com.example.utils.PraatCollectionExporter.PraatTextGridObject
      val tbl = parsedBin[4] as com.example.utils.PraatCollectionExporter.PraatTableObject

      assertEquals(s1.objectName, tg1.objectName)
      assertEquals(s2.objectName, tg2.objectName)
      org.junit.Assert.assertNotEquals(s1.objectName, s2.objectName)
      org.junit.Assert.assertFalse(tbl.objectName.contains("."))
      assertEquals(2, tbl.rows.size)
      org.junit.Assert.assertTrue(tbl.columnHeaders.contains("Canonical_Tone_T"))

      // 2. Full Text ooTextFile full project export
      val txtOut = java.io.ByteArrayOutputStream()
      val txtSummary = com.example.utils.ZipUtility.zipProjectMultiSpeaker(
          context = context,
          project = project,
          speakerWordPairs = listOf(spk1 to listOf(w1), spk2 to listOf(w2)),
          options = com.example.utils.ExportOptions(
              includeAudio = true,
              includePraatTextGrid = true,
              exportAllSpeakers = true,
              exportAsPraatCollection = true,
              praatCollectionBinary = false,
              includePraatSummaryTable = true
          ),
          targetStream = txtOut
      )
      org.junit.Assert.assertTrue(txtSummary.success)
      assertEquals(5, txtSummary.objectCount)
      val parsedTxt = com.example.utils.PraatCollectionExporter.readTextCollection(
          String(txtOut.toByteArray(), Charsets.UTF_8)
      )
      assertEquals(5, parsedTxt.size)
      val txtTbl = parsedTxt[4] as com.example.utils.PraatCollectionExporter.PraatTableObject
      assertEquals(tbl.columnHeaders, txtTbl.columnHeaders)
      assertEquals(2, txtTbl.rows.size)
    } finally {
      projectDir.deleteRecursively()
    }
  }

  @Test
  fun `picture wordlist parses filenames stages images and roundTrips graded zip export and import`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val createdProjectIds = mutableListOf<Long>()
    try {
      val repo = ProjectRepository(db)

      // 1. Verify smart filename parsing for batch picture imports
      val p1 = com.example.utils.WordImageManager.parseWordFromImageFileName(
          rawFileName = "001_日头(太阳).jpg",
          wordAndRemarkParser = repo::parseWordAndRemark
      )
      assertEquals("日头", p1.text)
      assertEquals("太阳", p1.remark)
      assertEquals("", p1.groupName)

      val p2 = com.example.utils.WordImageManager.parseWordFromImageFileName(
          rawFileName = "【天文】02-月光.png",
          wordAndRemarkParser = repo::parseWordAndRemark
      )
      assertEquals("月光", p2.text)
      assertEquals("", p2.remark)
      assertEquals("天文", p2.groupName)

      val p3 = com.example.utils.WordImageManager.parseWordFromImageFileName(
          rawFileName = "日头_太阳.webp",
          wordAndRemarkParser = repo::parseWordAndRemark
      )
      assertEquals("日头", p3.text)
      assertEquals("太阳", p3.remark)

      val p4 = com.example.utils.WordImageManager.parseWordFromImageFileName(
          rawFileName = "板凳.jpg",
          wordAndRemarkParser = repo::parseWordAndRemark
      )
      assertEquals("板凳", p4.text)
      assertEquals("", p4.remark)

      // 2. Stage synthetic PNG bytes and create a mixed Picture + Text project
      val bmp = android.graphics.Bitmap.createBitmap(64, 48, android.graphics.Bitmap.Config.ARGB_8888)
      bmp.eraseColor(android.graphics.Color.rgb(220, 140, 60))
      val pngBytes = java.io.ByteArrayOutputStream().use { bos ->
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bos)
        bos.toByteArray()
      }
      bmp.recycle()

      val stagedToken = com.example.utils.WordImageManager.stageImageFromBytesSync(app, pngBytes)
      org.junit.Assert.assertNotNull(stagedToken)
      org.junit.Assert.assertTrue(stagedToken!!.startsWith("staged:"))

      val spkId = repo.createSpeaker("张阿公", "男", 76, "绍兴", "老年方言发音人", "越州组")
      val projId = repo.createProjectWithParsedWords(
          name = "绍兴看图词汇调查",
          description = "老年看图发音测试",
          parsedWords = listOf(
              com.example.data.ParsedWord(text = "日头", remark = "太阳", groupName = "天文", imagePath = stagedToken),
              com.example.data.ParsedWord(text = "月光", remark = "月亮", groupName = "天文", imagePath = "")
          ),
          speakerId = spkId,
          speakerGroupName = "越州组",
          enablePicturePrompts = true,
          context = app
      )
      createdProjectIds.add(projId)

      val savedProj = repo.getProjectById(projId)!!
      org.junit.Assert.assertTrue(savedProj.enablePicturePrompts)

      val wordsAfterCreate = repo.getWordsForProjectSync(projId).sortedBy { it.originalIndex }
      assertEquals(2, wordsAfterCreate.size)
      org.junit.Assert.assertTrue(wordsAfterCreate[0].imagePath.isNotBlank())
      org.junit.Assert.assertFalse(wordsAfterCreate[0].imagePath.startsWith("staged:"))
      assertEquals("", wordsAfterCreate[1].imagePath)

      val committedImgFile = com.example.utils.WordImageManager.resolveWordImageFile(app, projId, wordsAfterCreate[0].imagePath)
      org.junit.Assert.assertNotNull(committedImgFile)
      org.junit.Assert.assertTrue(committedImgFile!!.exists())

      // Verify editing word remarks preserves bound imagePath
      repo.saveEditedProjectWords(
          project = savedProj,
          name = savedProj.name,
          description = savedProj.description,
          speakerGroupName = savedProj.speakerGroupName,
          drafts = listOf(
              com.example.data.EditableWordDraft(
                  existingWordId = wordsAfterCreate[0].id,
                  text = "日头",
                  remark = "太阳（白读）",
                  groupName = "天文",
                  imagePath = wordsAfterCreate[0].imagePath
              ),
              com.example.data.EditableWordDraft(
                  existingWordId = wordsAfterCreate[1].id,
                  text = "月光",
                  remark = "月亮",
                  groupName = "天文",
                  imagePath = ""
              )
          ),
          context = app,
          enablePicturePrompts = true
      )
      val wordsAfterEdit = repo.getWordsForProjectSync(projId).sortedBy { it.originalIndex }
      assertEquals("太阳（白读）", wordsAfterEdit[0].remark)
      assertEquals(wordsAfterCreate[0].imagePath, wordsAfterEdit[0].imagePath)
      org.junit.Assert.assertTrue(committedImgFile.exists())

      // Record dummy audio for word 0, then verify resetProjectRecordings deletes WAVs but preserves images/
      val projDir = java.io.File(app.getExternalFilesDir(null), "project_$projId")
      val wavName = "word_${wordsAfterEdit[0].id}_speaker_${spkId}_test.wav"
      val wavFile = java.io.File(projDir, wavName)
      writeDummyWav(wavFile)
      repo.saveSpeakerRecording(wordsAfterEdit[0].id, spkId, wavName, 200L, "正常", 1000f, 10000)

      repo.resetProjectRecordings(projId, app)
      org.junit.Assert.assertFalse(wavFile.exists())
      org.junit.Assert.assertTrue(committedImgFile.exists())

      // Re-create WAV for Graded Export verification
      writeDummyWav(wavFile)
      repo.saveSpeakerRecording(wordsAfterEdit[0].id, spkId, wavName, 200L, "正常", 1000f, 10000)
      val spk = repo.getSpeakerById(spkId)!!
      val spkWords = repo.getWordsForProjectWithSpeakerSync(projId, spkId)

      // 3A. Simple Mode homework export MUST stay clean (zero images/)
      val simpleOut = java.io.ByteArrayOutputStream()
      val simpleSummary = com.example.utils.ZipUtility.zipProjectMultiSpeaker(
          context = app,
          project = repo.getProjectById(projId)!!,
          speakerWordPairs = listOf(spk to spkWords),
          options = com.example.utils.ExportOptions(
              simpleModeExport = true,
              includeWordImages = false
          ),
          targetStream = simpleOut
      )
      org.junit.Assert.assertTrue(simpleSummary.success)
      val simpleEntryNames = mutableSetOf<String>()
      java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(simpleOut.toByteArray())).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          simpleEntryNames.add(entry.name)
          entry = zis.nextEntry
        }
      }
      org.junit.Assert.assertTrue(simpleEntryNames.contains("录音汇总表.xlsx"))
      org.junit.Assert.assertTrue(simpleEntryNames.none { it.startsWith("images/") || it.startsWith("词卡图片/") })

      // 3A-bis. Simple Mode WITH includeWordImages = true packs 词卡图片/ AND round-trip restores full project!
      val simpleImgOut = java.io.ByteArrayOutputStream()
      val simpleImgSummary = com.example.utils.ZipUtility.zipProjectMultiSpeaker(
          context = app,
          project = repo.getProjectById(projId)!!,
          speakerWordPairs = listOf(spk to spkWords),
          options = com.example.utils.ExportOptions(
              simpleModeExport = true,
              includeWordImages = true
          ),
          targetStream = simpleImgOut
      )
      org.junit.Assert.assertTrue(simpleImgSummary.success)
      val simpleImgEntries = mutableMapOf<String, ByteArray>()
      java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(simpleImgOut.toByteArray())).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          simpleImgEntries[entry.name] = zis.readBytes()
          entry = zis.nextEntry
        }
      }
      org.junit.Assert.assertTrue(simpleImgEntries.containsKey("录音汇总表.xlsx"))
      org.junit.Assert.assertTrue(simpleImgEntries.keys.any { it.startsWith("词卡图片/001_日头.") })
      val importedSimple = com.example.utils.ZipUtility.importProjectArchiveZip(
          context = app,
          repository = repo,
          inputStream = java.io.ByteArrayInputStream(simpleImgOut.toByteArray())
      )
      org.junit.Assert.assertTrue(importedSimple.message, importedSimple.success)
      createdProjectIds.add(importedSimple.projectId)
      val restoredSimpleWords = repo.getWordsForProjectSync(importedSimple.projectId).sortedBy { it.originalIndex }
      assertEquals(2, restoredSimpleWords.size) // Both recorded 日头 AND unrecorded 月光 restored!
      assertEquals("日头", restoredSimpleWords[0].text)
      assertEquals("太阳（白读）", restoredSimpleWords[0].remark)
      org.junit.Assert.assertTrue(restoredSimpleWords[0].imagePath.isNotBlank())
      assertEquals("月光", restoredSimpleWords[1].text)

      // 3B. Advanced Mode archive export WITH includeWordImages = true packs images/ and JSON mappings
      val advOut = java.io.ByteArrayOutputStream()
      val advSummary = com.example.utils.ZipUtility.zipProjectMultiSpeaker(
          context = app,
          project = repo.getProjectById(projId)!!,
          speakerWordPairs = listOf(spk to spkWords),
          options = com.example.utils.ExportOptions(
              includeAudio = true,
              includePraatTextGrid = false,
              includeExperimentProfile = true,
              includeWordImages = true
          ),
          targetStream = advOut
      )
      org.junit.Assert.assertTrue(advSummary.success)
      val advEntries = mutableMapOf<String, ByteArray>()
      java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(advOut.toByteArray())).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          advEntries[entry.name] = zis.readBytes()
          entry = zis.nextEntry
        }
      }
      org.junit.Assert.assertTrue(advEntries.keys.any { it.startsWith("images/001_日头.") })
      val expJson = org.json.JSONObject(String(advEntries.getValue("experiment_profile.json"), Charsets.UTF_8))
      org.junit.Assert.assertTrue(expJson.getBoolean("enable_picture_prompts"))
      val teprojJson = org.json.JSONObject(String(advEntries.getValue("project.json"), Charsets.UTF_8))
      val item0Json = teprojJson.getJSONArray("groups").getJSONObject(0).getJSONArray("items").getJSONObject(0)
      org.junit.Assert.assertTrue(item0Json.getString("image_path").startsWith("images/001_日头."))

      // 3C. Round-trip import Advanced archive and verify restored Picture Wordlist + audio
      val importedAdv = com.example.utils.ZipUtility.importProjectArchiveZip(
          context = app,
          repository = repo,
          inputStream = java.io.ByteArrayInputStream(advOut.toByteArray())
      )
      org.junit.Assert.assertTrue(importedAdv.message, importedAdv.success)
      createdProjectIds.add(importedAdv.projectId)
      val restoredProj = repo.getProjectById(importedAdv.projectId)!!
      org.junit.Assert.assertTrue(restoredProj.enablePicturePrompts)
      val restoredWords = repo.getWordsForProjectSync(importedAdv.projectId).sortedBy { it.originalIndex }
      assertEquals(2, restoredWords.size)
      org.junit.Assert.assertTrue(restoredWords[0].imagePath.isNotBlank())
      val restoredImgFile = com.example.utils.WordImageManager.resolveWordImageFile(app, importedAdv.projectId, restoredWords[0].imagePath)
      org.junit.Assert.assertNotNull(restoredImgFile)
      org.junit.Assert.assertTrue(restoredImgFile!!.exists())
      assertEquals("", restoredWords[1].imagePath)

      // 3D. Import a plain image-only ZIP pack directly into a new Picture Wordlist project
      val plainImgZipBytes = java.io.ByteArrayOutputStream().also { bos ->
        java.util.zip.ZipOutputStream(bos).use { zos ->
          zos.putNextEntry(java.util.zip.ZipEntry("农具/01_板凳(木头凳).png"))
          zos.write(pngBytes)
          zos.closeEntry()
          zos.putNextEntry(java.util.zip.ZipEntry("农具/02_水桶.png"))
          zos.write(pngBytes)
          zos.closeEntry()
        }
      }.toByteArray()

      val importedImgPack = com.example.utils.ZipUtility.importProjectArchiveZip(
          context = app,
          repository = repo,
          inputStream = java.io.ByteArrayInputStream(plainImgZipBytes)
      )
      org.junit.Assert.assertTrue(importedImgPack.message, importedImgPack.success)
      createdProjectIds.add(importedImgPack.projectId)
      val imgPackProj = repo.getProjectById(importedImgPack.projectId)!!
      org.junit.Assert.assertTrue(imgPackProj.enablePicturePrompts)
      val imgPackWords = repo.getWordsForProjectSync(importedImgPack.projectId).sortedBy { it.originalIndex }
      assertEquals(2, imgPackWords.size)
      assertEquals("板凳", imgPackWords[0].text)
      assertEquals("木头凳", imgPackWords[0].remark)
      assertEquals("农具", imgPackWords[0].groupName)
      org.junit.Assert.assertNotNull(
          com.example.utils.WordImageManager.resolveWordImageFile(app, importedImgPack.projectId, imgPackWords[0].imagePath)
      )
      assertEquals("水桶", imgPackWords[1].text)
    } finally {
      createdProjectIds.forEach { pid ->
        java.io.File(app.getExternalFilesDir(null), "project_$pid").deleteRecursively()
      }
      db.close()
    }
  }

  @Test
  fun `p0AndP1Fixes verify clean filenames cursorIpaInsertion variantWords and macroReportXlsx`() = kotlinx.coroutines.runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val createdProjectIds = mutableListOf<Long>()
    try {
      val repo = ProjectRepository(db)

      // 1. Cursor-aware IPA insertion & non-duplicating tag toggle
      val initialVal = androidx.compose.ui.text.input.TextFieldValue(
          text = "tɕia55 pa31",
          selection = androidx.compose.ui.text.TextRange(2) // right after "tɕ"
      )
      val afterInsert = com.example.ui.screens.insertSymbolAtFieldCursor(initialVal, "ʰ")
      assertEquals("tɕʰia55 pa31", afterInsert.text)
      assertEquals(3, afterInsert.selection.start)

      val t1 = com.example.ui.screens.toggleQuickNoteTagInValue(
          androidx.compose.ui.text.input.TextFieldValue(""),
          "地道老派"
      )
      assertEquals("地道老派", t1.text)
      org.junit.Assert.assertTrue(com.example.ui.screens.hasQuickNoteTag(t1.text, "地道老派"))
      val t2 = com.example.ui.screens.toggleQuickNoteTagInValue(t1, "声调特别")
      assertEquals("地道老派 · 声调特别", t2.text)
      val t3 = com.example.ui.screens.toggleQuickNoteTagInValue(t2, "地道老派")
      assertEquals("声调特别", t3.text) // cleanly toggled off instead of duplicating!

      // 2. Variant word derivation + clean vs note-appended filenames + genuine acoustic_summary.xlsx
      val spkId = repo.createSpeaker("陈阿公", "男", 75, "温州", "", "")
      val projId = repo.createProject("文白异读测试", "", listOf("血", "月"), spkId)
      createdProjectIds.add(projId)
      val baseWords = repo.getWordsForProjectSync(projId).sortedBy { it.originalIndex }
      val derivedWord = repo.insertVariantWordAfter(
          baseWord = baseWords[0],
          variantText = "血·白读",
          variantRemark = "白读",
          copyFromWavFile = null,
          speakerId = spkId,
          context = app
      )
      org.junit.Assert.assertNotNull(derivedWord)
      val updatedWords = repo.getWordsForProjectSync(projId).sortedBy { it.originalIndex }
      assertEquals(3, updatedWords.size)
      assertEquals("血", updatedWords[0].text)
      assertEquals("血·白读", updatedWords[1].text)
      assertEquals("月", updatedWords[2].text)

      val projDir = java.io.File(app.getExternalFilesDir(null), "project_$projId")
      val wavFile = java.io.File(projDir, "word_${updatedWords[0].id}_speaker_${spkId}_1000.wav")
      writeDummyWav(wavFile, sampleRate = 44100, durationMs = 240)
      repo.saveSpeakerRecording(updatedWords[0].id, spkId, wavFile.name, 240L, "正常", 1200f, 15000)
      repo.saveSpeakerWordNote(updatedWords[0].id, spkId, "tɕʰiaʔ53 老派白读")

      val spk = repo.getSpeakerById(spkId)!!
      val spkWords = repo.getWordsForProjectWithSpeakerSync(projId, spkId)
      val proj = repo.getProjectById(projId)!!

      // 2A. Default appendNoteToFileName = false -> strictly SPK01_001_血.wav + acoustic_summary.xlsx
      val cleanZipOut = java.io.ByteArrayOutputStream()
      com.example.utils.ZipUtility.zipProjectMultiSpeaker(
          context = app,
          project = proj,
          speakerWordPairs = listOf(spk to spkWords),
          options = com.example.utils.ExportOptions(
              includeAudio = true,
              includeAnalysis = true,
              includePraatTextGrid = false,
              appendNoteToFileName = false
          ),
          targetStream = cleanZipOut
      )
      val cleanEntries = mutableMapOf<String, ByteArray>()
      java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(cleanZipOut.toByteArray())).use { zis ->
        var e = zis.nextEntry
        while (e != null) {
          cleanEntries[e.name] = zis.readBytes()
          e = zis.nextEntry
        }
      }
      org.junit.Assert.assertTrue(cleanEntries.keys.any { it.endsWith("/SPK01_001_血.wav") })
      org.junit.Assert.assertTrue(cleanEntries.containsKey("acoustic_summary.xlsx"))
      val advSheets = com.example.utils.XlsxSpreadsheetWriter.readAllSheets(cleanEntries.getValue("acoustic_summary.xlsx"))
      org.junit.Assert.assertTrue(advSheets.values.first().size >= 2)

      // 2B. Opt-in appendNoteToFileName = true -> preserves IPA & Chinese characters without mangling!
      val suffixNote = com.example.utils.ZipUtility.transliterateNoteForFileName("tɕʰiaʔ53 老派白读")
      assertEquals("tɕʰiaʔ53_老派白读", suffixNote)

      // 3. MacroReportExporter generates genuine multi-sheet .xlsx inside .zip
      val report = com.example.audio.CorpusPhonologyAnalyzer.buildSpeakerPhonologyReport(
          context = app,
          projectId = projId,
          speaker = spk,
          words = spkWords
      )
      val macroZipOut = java.io.ByteArrayOutputStream()
      val macroOk = com.example.utils.MacroReportExporter.exportReportZip(
          outputStream = macroZipOut,
          projectName = proj.name,
          report = report,
          useMergedToneClusters = false,
          multiSpeakerReport = null,
          textMeasurer = null
      )
      org.junit.Assert.assertTrue(macroOk)
      val macroEntries = mutableMapOf<String, ByteArray>()
      java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(macroZipOut.toByteArray())).use { zis ->
        var e = zis.nextEntry
        while (e != null) {
          macroEntries[e.name] = zis.readBytes()
          e = zis.nextEntry
        }
      }
      org.junit.Assert.assertTrue(macroEntries.containsKey("声学与音系归纳报告.xlsx"))
      val macroSheets = com.example.utils.XlsxSpreadsheetWriter.readAllSheets(macroEntries.getValue("声学与音系归纳报告.xlsx"))
      org.junit.Assert.assertTrue(macroSheets.isNotEmpty())
    } finally {
      createdProjectIds.forEach { pid ->
        java.io.File(app.getExternalFilesDir(null), "project_$pid").deleteRecursively()
      }
      db.close()
    }
  }
}



