package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhonAcqMaterialIconCache
import androidx.compose.ui.graphics.vector.ImageVector

internal fun filledIcon(name: String, spec: String): ImageVector =
    PhonAcqMaterialIconCache.getOrCreate("Filled.$name", autoMirror = false, spec = spec)

val Icons.Filled.AccountTree: ImageVector
    get() = filledIcon("AccountTree", "NZ@1@M22,11 V3 h-7 v3 H9 V3 H2 v8 h7 V8 h2 v10 h4 v3 h7 v-8 h-7 v3 h-2 V8 h2 v3 Z")

val Icons.Filled.AddCircleOutline: ImageVector
    get() = filledIcon("AddCircleOutline", "NZ@1@M13,7 h-2 v4 L7,11 v2 h4 v4 h2 v-4 h4 v-2 h-4 L13,7 Z M12,2 C6.48,2,2,6.48,2,12 s4.48,10,10,10 s10,-4.48,10,-10 S17.52,2,12,2 Z M12,20 c-4.41,0,-8,-3.59,-8,-8 s3.59,-8,8,-8 s8,3.59,8,8 s-3.59,8,-8,8 Z")

val Icons.Filled.AddPhotoAlternate: ImageVector
    get() = filledIcon("AddPhotoAlternate", "NZ@1@M19,7 v2.99 s-1.99,0.01,-2,0 L17,7 h-3 s0.01,-1.99,0,-2 h3 L17,2 h2 v3 h3 v2 h-3 Z M16,11 L16,8 h-3 L13,5 L5,5 c-1.1,0,-2,0.9,-2,2 v12 c0,1.1,0.9,2,2,2 h12 c1.1,0,2,-0.9,2,-2 v-8 h-3 Z M5,19 l3,-4 l2,3 l3,-4 l4,5 L5,19 Z")

val Icons.Filled.Analytics: ImageVector
    get() = filledIcon("Analytics", "NZ@1@M19,3 L5,3 c-1.1,0,-2,0.9,-2,2 v14 c0,1.1,0.9,2,2,2 h14 c1.1,0,2,-0.9,2,-2 L21,5 c0,-1.1,-0.9,-2,-2,-2 Z M9,17 L7,17 v-5 h2 v5 Z M13,17 h-2 v-3 h2 v3 Z M13,12 h-2 v-2 h2 v2 Z M17,17 h-2 L15,7 h2 v10 Z")

val Icons.Filled.Archive: ImageVector
    get() = filledIcon("Archive", "NZ@1@M20.54,5.23 l-1.39,-1.68 C18.88,3.21,18.47,3,18,3 H6 c-0.47,0,-0.88,0.21,-1.16,0.55 L3.46,5.23 C3.17,5.57,3,6.02,3,6.5 V19 c0,1.1,0.9,2,2,2 h14 c1.1,0,2,-0.9,2,-2 V6.5 c0,-0.48,-0.17,-0.93,-0.46,-1.27 Z M12,17.5 L6.5,12 H10 v-2 h4 v2 h3.5 L12,17.5 Z M5.12,5 l0.81,-1 h12 l0.94,1 H5.12 Z")

val Icons.Filled.ArrowDownward: ImageVector
    get() = filledIcon("ArrowDownward", "NZ@1@M20,12 l-1.41,-1.41 L13,16.17 V4 h-2 v12.17 l-5.58,-5.59 L4,12 l8,8 l8,-8 Z")

val Icons.Filled.ArrowUpward: ImageVector
    get() = filledIcon("ArrowUpward", "NZ@1@M4,12 l1.41,1.41 L11,7.83 V20 h2 V7.83 l5.58,5.59 L20,12 l-8,-8 l-8,8 Z")

val Icons.Filled.AudioFile: ImageVector
    get() = filledIcon("AudioFile", "NZ@1@M14,2 H6 C4.9,2,4.01,2.9,4.01,4 L4,20 c0,1.1,0.89,2,1.99,2 H18 c1.1,0,2,-0.9,2,-2 V8 L14,2 Z M16,13 h-3 v3.75 c0,1.24,-1.01,2.25,-2.25,2.25 S8.5,17.99,8.5,16.75 c0,-1.24,1.01,-2.25,2.25,-2.25 c0.46,0,0.89,0.14,1.25,0.38 V11 h4 V13 Z M13,9 V3.5 L18.5,9 H13 Z")

val Icons.Filled.AutoAwesome: ImageVector
    get() = filledIcon("AutoAwesome", "NZ@1@M19,9 l1.25,-2.75 L23,5 l-2.75,-1.25 L19,1 l-1.25,2.75 L15,5 l2.75,1.25 L19,9 Z M11.5,9.5 L9,4 L6.5,9.5 L1,12 l5.5,2.5 L9,20 l2.5,-5.5 L17,12 l-5.5,-2.5 Z M19,15 l-1.25,2.75 L15,19 l2.75,1.25 L19,23 l1.25,-2.75 L23,19 l-2.75,-1.25 L19,15 Z")

val Icons.Filled.BluetoothAudio: ImageVector
    get() = filledIcon("BluetoothAudio", "NZ@1@M14.24,12.01 l2.32,2.32 c0.28,-0.72,0.44,-1.51,0.44,-2.33 c0,-0.82,-0.16,-1.59,-0.43,-2.31 l-2.33,2.32 Z M19.53,6.71 l-1.26,1.26 c0.63,1.21,0.98,2.57,0.98,4.02 s-0.36,2.82,-0.98,4.02 l1.2,1.2 c0.97,-1.54,1.54,-3.36,1.54,-5.31 c-0.01,-1.89,-0.55,-3.67,-1.48,-5.19 Z M15.71,7.71 L10,2 L9,2 v7.59 L4.41,5 L3,6.41 L8.59,12 L3,17.59 L4.41,19 L9,14.41 L9,22 h1 l5.71,-5.71 l-4.3,-4.29 l4.3,-4.29 Z M11,5.83 l1.88,1.88 L11,9.59 L11,5.83 Z M12.88,16.29 L11,18.17 v-3.76 l1.88,1.88 Z")

val Icons.Filled.BrightnessAuto: ImageVector
    get() = filledIcon("BrightnessAuto", "NZ@1@M10.85,12.65 h2.3 L12,9 l-1.15,3.65 Z M20,8.69 V4 h-4.69 L12,0.69 L8.69,4 H4 v4.69 L0.69,12 L4,15.31 V20 h4.69 L12,23.31 L15.31,20 H20 v-4.69 L23.31,12 L20,8.69 Z M14.3,16 l-0.7,-2 h-3.2 l-0.7,2 H7.8 L11,7 h2 l3.2,9 h-1.9 Z")

val Icons.Filled.BubbleChart: ImageVector
    get() = filledIcon("BubbleChart", "NZ@1@M7.2,14.4 m-3.2,0 a3.2,3.2,0,1,1,6.4,0 a3.2,3.2,0,1,1,-6.4,0||NZ@1@M14.8,18 m-2,0 a2,2,0,1,1,4,0 a2,2,0,1,1,-4,0||NZ@1@M15.2,8.8 m-4.8,0 a4.8,4.8,0,1,1,9.6,0 a4.8,4.8,0,1,1,-9.6,0")

val Icons.Filled.Category: ImageVector
    get() = filledIcon("Category", "NZ@1@M12,2 l-5.5,9 h11 Z||NZ@1@M17.5,17.5 m-4.5,0 a4.5,4.5,0,1,1,9,0 a4.5,4.5,0,1,1,-9,0||NZ@1@M3,13.5 h8 v8 H3 Z")

val Icons.Filled.CenterFocusStrong: ImageVector
    get() = filledIcon("CenterFocusStrong", "NZ@1@M12,8 c-2.21,0,-4,1.79,-4,4 s1.79,4,4,4 s4,-1.79,4,-4 s-1.79,-4,-4,-4 Z M5,15 L3,15 v4 c0,1.1,0.9,2,2,2 h4 v-2 L5,19 v-4 Z M5,5 h4 L9,3 L5,3 c-1.1,0,-2,0.9,-2,2 v4 h2 L5,5 Z M19,3 h-4 v2 h4 v4 h2 L21,5 c0,-1.1,-0.9,-2,-2,-2 Z M19,19 h-4 v2 h4 c1.1,0,2,-0.9,2,-2 v-4 h-2 v4 Z")

val Icons.Filled.ChevronRight: ImageVector
    get() = filledIcon("ChevronRight", "NZ@1@M10,6 L8.59,7.41 L13.17,12 l-4.58,4.59 L10,18 l6,-6 Z")

val Icons.Filled.CleaningServices: ImageVector
    get() = filledIcon("CleaningServices", "NZ@1@M16,11 h-1 V3 c0,-1.1,-0.9,-2,-2,-2 h-2 C9.9,1,9,1.9,9,3 v8 H8 c-2.76,0,-5,2.24,-5,5 v7 h18 v-7 C21,13.24,18.76,11,16,11 Z M19,21 h-2 v-3 c0,-0.55,-0.45,-1,-1,-1 s-1,0.45,-1,1 v3 h-2 v-3 c0,-0.55,-0.45,-1,-1,-1 s-1,0.45,-1,1 v3 H9 v-3 c0,-0.55,-0.45,-1,-1,-1 s-1,0.45,-1,1 v3 H5 v-5 c0,-1.65,1.35,-3,3,-3 h8 c1.65,0,3,1.35,3,3 V21 Z")

val Icons.Filled.Code: ImageVector
    get() = filledIcon("Code", "NZ@1@M9.4,16.6 L4.8,12 l4.6,-4.6 L8,6 l-6,6 l6,6 l1.4,-1.4 Z M14.6,16.6 l4.6,-4.6 l-4.6,-4.6 L16,6 l6,6 l-6,6 l-1.4,-1.4 Z")

val Icons.Filled.ContentCopy: ImageVector
    get() = filledIcon("ContentCopy", "NZ@1@M16,1 L4,1 c-1.1,0,-2,0.9,-2,2 v14 h2 L4,3 h12 L16,1 Z M19,5 L8,5 c-1.1,0,-2,0.9,-2,2 v14 c0,1.1,0.9,2,2,2 h11 c1.1,0,2,-0.9,2,-2 L21,7 c0,-1.1,-0.9,-2,-2,-2 Z M19,21 L8,21 L8,7 h11 v14 Z")

val Icons.Filled.ContentPaste: ImageVector
    get() = filledIcon("ContentPaste", "NZ@1@M19,2 h-4.18 C14.4,0.84,13.3,0,12,0 c-1.3,0,-2.4,0.84,-2.82,2 L5,2 c-1.1,0,-2,0.9,-2,2 v16 c0,1.1,0.9,2,2,2 h14 c1.1,0,2,-0.9,2,-2 L21,4 c0,-1.1,-0.9,-2,-2,-2 Z M12,2 c0.55,0,1,0.45,1,1 s-0.45,1,-1,1 s-1,-0.45,-1,-1 s0.45,-1,1,-1 Z M19,20 L5,20 L5,4 h2 v3 h10 L17,4 h2 v16 Z")

val Icons.Filled.CreateNewFolder: ImageVector
    get() = filledIcon("CreateNewFolder", "NZ@1@M20,6 h-8 l-2,-2 L4,4 c-1.11,0,-1.99,0.89,-1.99,2 L2,18 c0,1.11,0.89,2,2,2 h16 c1.11,0,2,-0.89,2,-2 L22,8 c0,-1.11,-0.89,-2,-2,-2 Z M19,14 h-3 v3 h-2 v-3 h-3 v-2 h3 L14,9 h2 v3 h3 v2 Z")

val Icons.Filled.DarkMode: ImageVector
    get() = filledIcon("DarkMode", "NZ@1@M12,3 c-4.97,0,-9,4.03,-9,9 s4.03,9,9,9 s9,-4.03,9,-9 c0,-0.46,-0.04,-0.92,-0.1,-1.36 c-0.98,1.37,-2.58,2.26,-4.4,2.26 c-2.98,0,-5.4,-2.42,-5.4,-5.4 c0,-1.81,0.89,-3.42,2.26,-4.4 C12.92,3.04,12.46,3,12,3 L12,3 Z")

val Icons.Filled.DashboardCustomize: ImageVector
    get() = filledIcon("DashboardCustomize", "NZ@1@M3,3 h8 v8 L3,11 Z M13,3 h8 v8 h-8 Z M3,13 h8 v8 L3,21 Z M18,13 h-2 v3 h-3 v2 h3 v3 h2 v-3 h3 v-2 h-3 Z")

val Icons.Filled.DeleteOutline: ImageVector
    get() = filledIcon("DeleteOutline", "NZ@1@M6,19 c0,1.1,0.9,2,2,2 h8 c1.1,0,2,-0.9,2,-2 L18,7 L6,7 v12 Z M8,9 h8 v10 L8,19 L8,9 Z M15.5,4 l-1,-1 h-5 l-1,1 L5,4 v2 h14 L19,4 Z")

val Icons.Filled.DeleteSweep: ImageVector
    get() = filledIcon("DeleteSweep", "NZ@1@M15,16 h4 v2 h-4 Z M15,8 h7 v2 h-7 Z M15,12 h6 v2 h-6 Z M3,18 c0,1.1,0.9,2,2,2 h6 c1.1,0,2,-0.9,2,-2 L13,8 L3,8 v10 Z M14,5 h-3 l-1,-1 L6,4 L5,5 L2,5 v2 h12 Z")

val Icons.Filled.Description: ImageVector
    get() = filledIcon("Description", "NZ@1@M14,2 L6,2 c-1.1,0,-1.99,0.9,-1.99,2 L4,20 c0,1.1,0.89,2,1.99,2 L18,22 c1.1,0,2,-0.9,2,-2 L20,8 l-6,-6 Z M16,18 L8,18 v-2 h8 v2 Z M16,14 L8,14 v-2 h8 v2 Z M13,9 L13,3.5 L18.5,9 L13,9 Z")

val Icons.Filled.DesktopWindows: ImageVector
    get() = filledIcon("DesktopWindows", "NZ@1@M20,3 H4 C2.9,3,2,3.9,2,5 v10 c0,1.1,0.9,2,2,2 h6 v2 H8 v2 h8 v-2 h-2 v-2 h6 c1.1,0,2,-0.9,2,-2 V5 C22,3.9,21.1,3,20,3")

val Icons.Filled.Devices: ImageVector
    get() = filledIcon("Devices", "NZ@1@M4,6 h18 L22,4 L4,4 c-1.1,0,-2,0.9,-2,2 v11 L0,17 v3 h14 v-3 L4,17 L4,6 Z M23,8 h-6 c-0.55,0,-1,0.45,-1,1 v10 c0,0.55,0.45,1,1,1 h6 c0.55,0,1,-0.45,1,-1 L24,9 c0,-0.55,-0.45,-1,-1,-1 Z M22,17 h-4 v-7 h4 v7 Z")

val Icons.Filled.Download: ImageVector
    get() = filledIcon("Download", "NZ@1@M5,20 h14 v-2 H5 V20 Z M19,9 h-4 V3 H9 v6 H5 l7,7 L19,9 Z")

val Icons.Filled.DownloadDone: ImageVector
    get() = filledIcon("DownloadDone", "NZ@1@M20.13,5.41 l-1.41,-1.41 l-9.19,9.19 l-4.25,-4.24 l-1.41,1.41 l5.66,5.66 Z||NZ@1@M5,18 h14 v2 h-14 Z")

val Icons.Filled.EditNote: ImageVector
    get() = filledIcon("EditNote", "NZ@1@M3,10 h11 v2 H3 V10 Z M3,8 h11 V6 H3 V8 Z M3,16 h7 v-2 H3 V16 Z M18.01,12.87 l0.71,-0.71 c0.39,-0.39,1.02,-0.39,1.41,0 l0.71,0.71 c0.39,0.39,0.39,1.02,0,1.41 l-0.71,0.71 L18.01,12.87 Z M17.3,13.58 l-5.3,5.3 V21 h2.12 l5.3,-5.3 L17.3,13.58 Z")

val Icons.Filled.ErrorOutline: ImageVector
    get() = filledIcon("ErrorOutline", "NZ@1@M11,15 h2 v2 h-2 Z M11,7 h2 v6 h-2 Z M11.99,2 C6.47,2,2,6.48,2,12 s4.47,10,9.99,10 C17.52,22,22,17.52,22,12 S17.52,2,11.99,2 Z M12,20 c-4.42,0,-8,-3.58,-8,-8 s3.58,-8,8,-8 s8,3.58,8,8 s-3.58,8,-8,8 Z")

val Icons.Filled.ExpandLess: ImageVector
    get() = filledIcon("ExpandLess", "NZ@1@M12,8 l-6,6 l1.41,1.41 L12,10.83 l4.59,4.58 L18,14 Z")

val Icons.Filled.ExpandMore: ImageVector
    get() = filledIcon("ExpandMore", "NZ@1@M16.59,8.59 L12,13.17 L7.41,8.59 L6,10 l6,6 l6,-6 Z")

val Icons.Filled.FastForward: ImageVector
    get() = filledIcon("FastForward", "NZ@1@M4,18 l8.5,-6 L4,6 v12 Z M13,6 v12 l8.5,-6 L13,6 Z")

val Icons.Filled.Flag: ImageVector
    get() = filledIcon("Flag", "NZ@1@M14.4,6 L14,4 H5 v17 h2 v-7 h5.6 l0.4,2 h7 V6 Z")

val Icons.Filled.Folder: ImageVector
    get() = filledIcon("Folder", "NZ@1@M10,4 H4 c-1.1,0,-1.99,0.9,-1.99,2 L2,18 c0,1.1,0.9,2,2,2 h16 c1.1,0,2,-0.9,2,-2 V8 c0,-1.1,-0.9,-2,-2,-2 h-8 l-2,-2 Z")

val Icons.Filled.FolderOff: ImageVector
    get() = filledIcon("FolderOff", "NZ@1@M20,6 h-8 l-2,-2 H6.83 l14.93,14.93 C21.91,18.65,22,18.34,22,18 V8 C22,6.9,21.1,6,20,6 Z||NZ@1@M2.1,2.1 L0.69,3.51 l1.56,1.56 C2.1,5.35,2.01,5.66,2.01,6 L2,18 c0,1.1,0.9,2,2,2 h13.17 l3.31,3.31 l1.41,-1.41 L2.1,2.1 Z")

val Icons.Filled.FolderOpen: ImageVector
    get() = filledIcon("FolderOpen", "NZ@1@M20,6 h-8 l-2,-2 L4,4 c-1.1,0,-1.99,0.9,-1.99,2 L2,18 c0,1.1,0.9,2,2,2 h16 c1.1,0,2,-0.9,2,-2 L22,8 c0,-1.1,-0.9,-2,-2,-2 Z M20,18 L4,18 L4,8 h16 v10 Z")

val Icons.Filled.FolderShared: ImageVector
    get() = filledIcon("FolderShared", "NZ@1@M20,6 h-8 l-2,-2 L4,4 c-1.1,0,-1.99,0.9,-1.99,2 L2,18 c0,1.1,0.9,2,2,2 h16 c1.1,0,2,-0.9,2,-2 L22,8 c0,-1.1,-0.9,-2,-2,-2 Z M15,9 c1.1,0,2,0.9,2,2 s-0.9,2,-2,2 s-2,-0.9,-2,-2 s0.9,-2,2,-2 Z M19,17 h-8 v-1 c0,-1.33,2.67,-2,4,-2 s4,0.67,4,2 v1 Z")

val Icons.Filled.FolderSpecial: ImageVector
    get() = filledIcon("FolderSpecial", "NZ@1@M20,6 h-8 l-2,-2 L4,4 c-1.1,0,-2,0.9,-2,2 v12 c0,1.1,0.9,2,2,2 h16 c1.1,0,2,-0.9,2,-2 L22,8 c0,-1.1,-0.9,-2,-2,-2 Z M17.94,17 L15,15.28 L12.06,17 l0.78,-3.33 l-2.59,-2.24 l3.41,-0.29 L15,8 l1.34,3.14 l3.41,0.29 l-2.59,2.24 l0.78,3.33 Z")

val Icons.Filled.FolderZip: ImageVector
    get() = filledIcon("FolderZip", "NZ@1@M20,6 h-8 l-2,-2 H4 C2.9,4,2.01,4.9,2.01,6 L2,18 c0,1.1,0.9,2,2,2 h16 c1.1,0,2,-0.9,2,-2 V8 C22,6.9,21.1,6,20,6 Z M18,12 h-2 v2 h2 v2 h-2 v2 h-2 v-2 h2 v-2 h-2 v-2 h2 v-2 h-2 V8 h2 v2 h2 V12 Z")

val Icons.Filled.GraphicEq: ImageVector
    get() = filledIcon("GraphicEq", "NZ@1@M7,18 h2 L9,6 L7,6 v12 Z M11,22 h2 L13,2 h-2 v20 Z M3,14 h2 v-4 L3,10 v4 Z M15,18 h2 L17,6 h-2 v12 Z M19,10 v4 h2 v-4 h-2 Z")

val Icons.Filled.GridView: ImageVector
    get() = filledIcon("GridView", "EO@1@M3,3 v8 h8 L11,3 L3,3 Z M9,9 L5,9 L5,5 h4 v4 Z M3,13 v8 h8 v-8 L3,13 Z M9,19 L5,19 v-4 h4 v4 Z M13,3 v8 h8 L21,3 h-8 Z M19,9 h-4 L15,5 h4 v4 Z M13,13 v8 h8 v-8 h-8 Z M19,19 h-4 v-4 h4 v4 Z")

val Icons.Filled.Group: ImageVector
    get() = filledIcon("Group", "NZ@1@M16,11 c1.66,0,2.99,-1.34,2.99,-3 S17.66,5,16,5 c-1.66,0,-3,1.34,-3,3 s1.34,3,3,3 Z M8,11 c1.66,0,2.99,-1.34,2.99,-3 S9.66,5,8,5 C6.34,5,5,6.34,5,8 s1.34,3,3,3 Z M8,13 c-2.33,0,-7,1.17,-7,3.5 L1,19 h14 v-2.5 c0,-2.33,-4.67,-3.5,-7,-3.5 Z M16,13 c-0.29,0,-0.62,0.02,-0.97,0.05 c1.16,0.84,1.97,1.97,1.97,3.45 L17,19 h6 v-2.5 c0,-2.33,-4.67,-3.5,-7,-3.5 Z")

val Icons.Filled.GroupWork: ImageVector
    get() = filledIcon("GroupWork", "NZ@1@M12,2 C6.48,2,2,6.48,2,12 s4.48,10,10,10 s10,-4.48,10,-10 S17.52,2,12,2 Z M8,17.5 c-1.38,0,-2.5,-1.12,-2.5,-2.5 s1.12,-2.5,2.5,-2.5 s2.5,1.12,2.5,2.5 s-1.12,2.5,-2.5,2.5 Z M9.5,8 c0,-1.38,1.12,-2.5,2.5,-2.5 s2.5,1.12,2.5,2.5 s-1.12,2.5,-2.5,2.5 S9.5,9.38,9.5,8 Z M16,17.5 c-1.38,0,-2.5,-1.12,-2.5,-2.5 s1.12,-2.5,2.5,-2.5 s2.5,1.12,2.5,2.5 s-1.12,2.5,-2.5,2.5 Z")

val Icons.Filled.Groups: ImageVector
    get() = filledIcon("Groups", "NZ@1@M12,12.75 c1.63,0,3.07,0.39,4.24,0.9 c1.08,0.48,1.76,1.56,1.76,2.73 L18,18 H6 l0,-1.61 c0,-1.18,0.68,-2.26,1.76,-2.73 C8.93,13.14,10.37,12.75,12,12.75 Z M4,13 c1.1,0,2,-0.9,2,-2 c0,-1.1,-0.9,-2,-2,-2 s-2,0.9,-2,2 C2,12.1,2.9,13,4,13 Z M5.13,14.1 C4.76,14.04,4.39,14,4,14 c-0.99,0,-1.93,0.21,-2.78,0.58 C0.48,14.9,0,15.62,0,16.43 V18 l4.5,0 v-1.61 C4.5,15.56,4.73,14.78,5.13,14.1 Z M20,13 c1.1,0,2,-0.9,2,-2 c0,-1.1,-0.9,-2,-2,-2 s-2,0.9,-2,2 C18,12.1,18.9,13,20,13 Z M24,16.43 c0,-0.81,-0.48,-1.53,-1.22,-1.85 C21.93,14.21,20.99,14,20,14 c-0.39,0,-0.76,0.04,-1.13,0.1 c0.4,0.68,0.63,1.46,0.63,2.29 V18 l4.5,0 V16.43 Z M12,6 c1.66,0,3,1.34,3,3 c0,1.66,-1.34,3,-3,3 s-3,-1.34,-3,-3 C9,7.34,10.34,6,12,6 Z")

val Icons.Filled.Headphones: ImageVector
    get() = filledIcon("Headphones", "NZ@1@M12,3 c-4.97,0,-9,4.03,-9,9 v7 c0,1.1,0.9,2,2,2 h4 v-8 H5 v-1 c0,-3.87,3.13,-7,7,-7 s7,3.13,7,7 v1 h-4 v8 h4 c1.1,0,2,-0.9,2,-2 v-7 C21,7.03,16.97,3,12,3 Z")

val Icons.Filled.HeadsetMic: ImageVector
    get() = filledIcon("HeadsetMic", "NZ@1@M12,1 c-4.97,0,-9,4.03,-9,9 v7 c0,1.66,1.34,3,3,3 h3 v-8 H5 v-2 c0,-3.87,3.13,-7,7,-7 s7,3.13,7,7 v2 h-4 v8 h4 v1 h-7 v2 h6 c1.66,0,3,-1.34,3,-3 V10 c0,-4.97,-4.03,-9,-9,-9 Z")

val Icons.Filled.Hearing: ImageVector
    get() = filledIcon("Hearing", "NZ@1@M17,20 c-0.29,0,-0.56,-0.06,-0.76,-0.15 c-0.71,-0.37,-1.21,-0.88,-1.71,-2.38 c-0.51,-1.56,-1.47,-2.29,-2.39,-3 c-0.79,-0.61,-1.61,-1.24,-2.32,-2.53 C9.29,10.98,9,9.93,9,9 c0,-2.8,2.2,-5,5,-5 s5,2.2,5,5 h2 c0,-3.93,-3.07,-7,-7,-7 S7,5.07,7,9 c0,1.26,0.38,2.65,1.07,3.9 c0.91,1.65,1.98,2.48,2.85,3.15 c0.81,0.62,1.39,1.07,1.71,2.05 c0.6,1.82,1.37,2.84,2.73,3.55 c0.51,0.23,1.07,0.35,1.64,0.35 c2.21,0,4,-1.79,4,-4 h-2 c0,1.1,-0.9,2,-2,2 Z M7.64,2.64 L6.22,1.22 C4.23,3.21,3,5.96,3,9 s1.23,5.79,3.22,7.78 l1.41,-1.41 C6.01,13.74,5,11.49,5,9 s1.01,-4.74,2.64,-6.36 Z M11.5,9 c0,1.38,1.12,2.5,2.5,2.5 s2.5,-1.12,2.5,-2.5 s-1.12,-2.5,-2.5,-2.5 s-2.5,1.12,-2.5,2.5 Z")

val Icons.Filled.History: ImageVector
    get() = filledIcon("History", "NZ@1@M13,3 c-4.97,0,-9,4.03,-9,9 L1,12 l3.89,3.89 l0.07,0.14 L9,12 L6,12 c0,-3.87,3.13,-7,7,-7 s7,3.13,7,7 s-3.13,7,-7,7 c-1.93,0,-3.68,-0.79,-4.94,-2.06 l-1.42,1.42 C8.27,19.99,10.51,21,13,21 c4.97,0,9,-4.03,9,-9 s-4.03,-9,-9,-9 Z M12,8 v5 l4.28,2.54 l0.72,-1.21 l-3.5,-2.08 L13.5,8 L12,8 Z")

val Icons.Filled.Image: ImageVector
    get() = filledIcon("Image", "NZ@1@M21,19 V5 c0,-1.1,-0.9,-2,-2,-2 H5 c-1.1,0,-2,0.9,-2,2 v14 c0,1.1,0.9,2,2,2 h14 c1.1,0,2,-0.9,2,-2 Z M8.5,13.5 l2.5,3.01 L14.5,12 l4.5,6 H5 l3.5,-4.5 Z")

val Icons.Filled.Input: ImageVector
    get() = filledIcon("Input", "NZ@1@M21,3.01 H3 c-1.1,0,-2,0.9,-2,2 V9 h2 V4.99 h18 v14.03 H3 V15 H1 v4.01 c0,1.1,0.9,1.98,2,1.98 h18 c1.1,0,2,-0.88,2,-1.98 v-14 c0,-1.11,-0.9,-2,-2,-2 Z M11,16 l4,-4 l-4,-4 v3 H1 v2 h10 v3 Z")

val Icons.Filled.Insights: ImageVector
    get() = filledIcon("Insights", "NZ@1@M21,8 c-1.45,0,-2.26,1.44,-1.93,2.51 l-3.55,3.56 c-0.3,-0.09,-0.74,-0.09,-1.04,0 l-2.55,-2.55 C12.27,10.45,11.46,9,10,9 c-1.45,0,-2.27,1.44,-1.93,2.52 l-4.56,4.55 C2.44,15.74,1,16.55,1,18 c0,1.1,0.9,2,2,2 c1.45,0,2.26,-1.44,1.93,-2.51 l4.55,-4.56 c0.3,0.09,0.74,0.09,1.04,0 l2.55,2.55 C12.73,16.55,13.54,18,15,18 c1.45,0,2.27,-1.44,1.93,-2.52 l3.56,-3.55 C21.56,12.26,23,11.45,23,10 C23,8.9,22.1,8,21,8 Z||NZ@1@M15,9 l0.94,-2.07 l2.06,-0.93 l-2.06,-0.93 l-0.94,-2.07 l-0.92,2.07 l-2.08,0.93 l2.08,0.93 Z||NZ@1@M3.5,11 l0.5,-2 l2,-0.5 l-2,-0.5 l-0.5,-2 l-0.5,2 l-2,0.5 l2,0.5 Z")

val Icons.Filled.Inventory2: ImageVector
    get() = filledIcon("Inventory2", "NZ@1@M20,2 H4 C3,2,2,2.9,2,4 v3.01 C2,7.73,2.43,8.35,3,8.7 V20 c0,1.1,1.1,2,2,2 h14 c0.9,0,2,-0.9,2,-2 V8.7 c0.57,-0.35,1,-0.97,1,-1.69 V4 C22,2.9,21,2,20,2 Z M15,14 H9 v-2 h6 V14 Z M20,7 H4 V4 h16 V7 Z")

val Icons.Filled.IosShare: ImageVector
    get() = filledIcon("IosShare", "NZ@1@M16,5 l-1.42,1.42 l-1.59,-1.59 L12.99,16 h-1.98 L11.01,4.83 L9.42,6.42 L8,5 l4,-4 l4,4 Z M20,10 v11 c0,1.1,-0.9,2,-2,2 L6,23 c-1.11,0,-2,-0.9,-2,-2 L4,10 c0,-1.11,0.89,-2,2,-2 h3 v2 L6,10 v11 h12 L18,10 h-3 L15,8 h3 c1.1,0,2,0.89,2,2 Z")

val Icons.Filled.Keyboard: ImageVector
    get() = filledIcon("Keyboard", "NZ@1@M20,5 L4,5 c-1.1,0,-1.99,0.9,-1.99,2 L2,17 c0,1.1,0.9,2,2,2 h16 c1.1,0,2,-0.9,2,-2 L22,7 c0,-1.1,-0.9,-2,-2,-2 Z M11,8 h2 v2 h-2 L11,8 Z M11,11 h2 v2 h-2 v-2 Z M8,8 h2 v2 L8,10 L8,8 Z M8,11 h2 v2 L8,13 v-2 Z M7,13 L5,13 v-2 h2 v2 Z M7,10 L5,10 L5,8 h2 v2 Z M16,17 L8,17 v-2 h8 v2 Z M16,13 h-2 v-2 h2 v2 Z M16,10 h-2 L14,8 h2 v2 Z M19,13 h-2 v-2 h2 v2 Z M19,10 h-2 L17,8 h2 v2 Z")
