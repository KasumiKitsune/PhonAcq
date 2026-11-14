pluginManagement {
  val useAliyunMirror = System.getenv("CI") == null && !System.getProperty("os.name").orEmpty().lowercase().contains("linux")
  repositories {
    if (useAliyunMirror) {
      maven { url = uri("https://maven.aliyun.com/repository/google") }
      maven { url = uri("https://maven.aliyun.com/repository/public") }
      maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  val useAliyunMirror = System.getenv("CI") == null && !System.getProperty("os.name").orEmpty().lowercase().contains("linux")
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    if (useAliyunMirror) {
      maven { url = uri("https://maven.aliyun.com/repository/google") }
      maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
    google()
    mavenCentral()
  }
}

rootProject.name = "PhonAcq"

include(":app")
