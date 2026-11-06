pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        // the KMP shadow library used for the neumorphic material
        maven(url = "https://jitpack.io")
    }
}
rootProject.name = "baki-compose"
include(":composeApp")
