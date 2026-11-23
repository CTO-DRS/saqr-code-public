pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google's Maven Central mirror first (Central rate-limits shared CI IPs).
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        // Termux terminal-emulator (Apache-2.0): VT100/xterm emulation + PTY via JNI.
        maven("https://jitpack.io") { content { includeGroup("com.github.termux.termux-app") } }
    }
}
rootProject.name = "SaqrCode"
include(":app")
