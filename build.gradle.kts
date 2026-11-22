plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Optional out-of-tree build dir (used in the CI sandbox): SAQR_BUILD_DIR=/path ./gradlew assembleDebug
System.getenv("SAQR_BUILD_DIR")?.let { dir ->
    allprojects { layout.buildDirectory.set(file("$dir/${project.name}")) }
}
