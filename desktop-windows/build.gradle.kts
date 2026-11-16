import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
}

val rawAppVersion: String = (project.findProperty("appVersion") as? String)
    ?.trim()
    ?.removePrefix("v")
    ?.removePrefix("V")
    ?.takeIf { it.isNotEmpty() }
    ?: "2.0.2"

// WiX (.msi) and macOS jpackage (.dmg) strictly require X.Y.Z numeric format with X >= 1
val nativePkgVersion: String = run {
    val nums = Regex("""\d+""").findAll(rawAppVersion).mapNotNull { it.value.toIntOrNull() }.toList()
    val major = nums.getOrElse(0) { 2 }.coerceIn(1, 255)
    val minor = nums.getOrElse(1) { 0 }.coerceIn(0, 255)
    val patch = nums.getOrElse(2) { 0 }.coerceIn(0, 65535)
    "$major.$minor.$patch"
}

group = "io.github.kasumikitsune.phonacq"
version = rawAppVersion

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

sourceSets {
    main {
        // Directly mount the shared Android source tree so UI screens, ViewModel,
        // AcousticAnalyzer, ZipUtility (.teproj & 4-tier .TextGrid), Entities, DAOs,
        // and ProjectRepository remain a single source of truth across Android & Windows.
        kotlin.srcDir("src/main/kotlin")
        kotlin.srcDir("../app/src/main/java")
        kotlin.exclude(
            "**/MainActivity.kt",
            "**/data/AppDatabase.kt",
            "**/ui/theme/Theme.kt"
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("org.json:json:20240303")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
}

compose.desktop {
    application {
        mainClass = "com.example.DesktopMainKt"

        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            "-Dsun.java2d.dpiaware=true",
            "-Dphonacq.version=$rawAppVersion"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "PhonAcq"
            packageVersion = nativePkgVersion
            description = "PhonAcq Acoustic Fieldwork Studio (PhonTracer .teproj / Praat .TextGrid)"
            vendor = "KasumiKitsune"
            includeAllModules = true

            windows {
                iconFile.set(project.file("src/main/resources/phonacq.ico"))
                menuGroup = "PhonAcq"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "3c68c82d-217d-4db1-839d-6656cd3ad6fa"
            }

            macOS {
                bundleID = "io.github.kasumikitsune.phonacq"
                iconFile.set(project.file("src/main/resources/phonacq.icns"))
                entitlementsFile.set(project.file("entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>PhonAcq 需要访问麦克风以录制田野调查语音与方言字表音频。</string>
                    """.trimIndent()
                }
            }
        }
    }
}

tasks.register<JavaExec>("generateIco") {
    group = "compose desktop"
    description = "Regenerates src/main/resources/phonacq.ico and phonacq.icns with the pure white Samsung pebble icon"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.DesktopIconRenderer")
    args(
        project.file("src/main/resources/phonacq.ico").absolutePath,
        project.file("src/main/resources/phonacq.icns").absolutePath
    )
    jvmArgs("-Djava.awt.headless=true")
}
