@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.ui.platform

import android.app.Application
import android.content.Context
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

val LocalContext = staticCompositionLocalOf<Context> { Application.instance }
val LocalView = staticCompositionLocalOf<View> { View() }
