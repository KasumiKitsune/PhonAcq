package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.i18n.S
import com.example.ui.screens.MainScreen
import com.example.ui.screens.PhonAcqLogo
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RecordingViewModel
import com.example.viewmodel.RecordingViewModelFactory

class MainActivity : ComponentActivity() {
  private lateinit var viewModel: RecordingViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setupHighRefreshRate()

    val database = AppDatabase.getDatabase(this)
    val repository = ProjectRepository(database)
    val factory = RecordingViewModelFactory(application, repository)
    viewModel = ViewModelProvider(this, factory)[RecordingViewModel::class.java]

    setContent {
      val themeMode by viewModel.themeMode.collectAsState()
      val themePaletteId by viewModel.themePaletteId.collectAsState()
      val dynamicColor by viewModel.dynamicColorEnabled.collectAsState()
      val systemInDark = isSystemInDarkTheme()
      val useDarkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> systemInDark
      }

      MyApplicationTheme(
        darkTheme = useDarkTheme,
        dynamicColor = dynamicColor,
        themePaletteId = themePaletteId
      ) {
        var micPermissionGranted by remember {
          mutableStateOf(
            ContextCompat.checkSelfPermission(
              this,
              Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
          )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
          micPermissionGranted = isGranted
        }

        LaunchedEffect(Unit) {
          if (!micPermissionGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          if (micPermissionGranted) {
            MainScreen(
              viewModel = viewModel,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
              contentAlignment = Alignment.Center
            ) {
              Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(28.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  PhonAcqLogo(
                    modifier = Modifier.size(72.dp)
                  )
                  Spacer(modifier = Modifier.height(20.dp))
                  Text(
                    text = S.permissions.micRequiredTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.height(12.dp))
                  Text(
                    text = S.permissions.micRequiredMainBody,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                  )
                  Spacer(modifier = Modifier.height(28.dp))
                  Button(
                    onClick = {
                      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                  ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(S.permissions.enableMicButton, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (::viewModel.isInitialized && viewModel.volumeKeyControlEnabled.value && viewModel.currentProject.value != null) {
      val actionMode = viewModel.volumeKeyAction.value
      when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> {
          if (event?.repeatCount == 0) {
            if (actionMode == "PREV_NEXT") {
              if (!viewModel.isRecording.value) viewModel.prevWord()
            } else {
              if (viewModel.isRecording.value) {
                viewModel.triggerStopRecording()
              } else {
                viewModel.triggerStartRecording()
              }
            }
          }
          return true
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> {
          if (event?.repeatCount == 0) {
            if (actionMode == "RECORD_ONLY") {
              if (viewModel.isRecording.value) {
                viewModel.triggerStopRecording()
              } else {
                viewModel.triggerStartRecording()
              }
            } else {
              if (!viewModel.isRecording.value) {
                viewModel.nextWord()
              }
            }
          }
          return true
        }
      }
    }
    return super.onKeyDown(keyCode, event)
  }

  private fun setupHighRefreshRate() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        val display = display
        val modes = display?.supportedModes
        if (modes != null && modes.isNotEmpty()) {
          val highestMode = modes.maxByOrNull { it.refreshRate }
          highestMode?.let { mode ->
            val layoutParams = window.attributes
            layoutParams.preferredDisplayModeId = mode.modeId
            window.attributes = layoutParams
          }
        }
      } catch (e: Exception) {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val modes = display?.supportedModes
        if (modes != null && modes.isNotEmpty()) {
          val highestMode = modes.maxByOrNull { it.refreshRate }
          highestMode?.let { mode ->
            val layoutParams = window.attributes
            layoutParams.preferredDisplayModeId = mode.modeId
            window.attributes = layoutParams
          }
        }
      }
    } else {
      @Suppress("DEPRECATION")
      val display = windowManager.defaultDisplay
      val modes = display?.supportedModes
      if (modes != null && modes.isNotEmpty()) {
        val highestMode = modes.maxByOrNull { it.refreshRate }
        highestMode?.let { mode ->
          val layoutParams = window.attributes
          layoutParams.preferredDisplayModeId = mode.modeId
          window.attributes = layoutParams
        }
      }
    }
  }
}
