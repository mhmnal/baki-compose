plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("dev.chrisbanes.haze:haze:2.1.0")
                implementation("dev.chrisbanes.haze:haze-glass:2.1.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
            }
        }
        val commonTest by getting { dependencies { implementation(kotlin("test")) } }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.3")
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("com.google.android.gms:play-services-location:21.3.0")
                implementation("io.ktor:ktor-client-okhttp:3.0.3")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("io.ktor:ktor-client-cio:3.0.3")
            }
        }
    }
}

android {
    namespace = "app.baki"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.baki.compose"   // coexists with com.baki.desktop
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "0.1.7"
        // opt-in: build the same app under a separate package id purely to verify an
        // R8-minified build on a device without touching the real installation's data
        if (project.findProperty("bakiVerify") == "true") applicationIdSuffix = ".verify"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            // R8: drop unused code and shrink resources. The app ships Compose + Ktor +
            // serialization, most of which the linker can prove is unreachable.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    lint {
        // MainActivity is a ComponentActivity, not a FragmentActivity; the check is a false
        // positive that only fires now that R8 runs lintVital on release builds.
        disable += "InvalidFragmentVersionForActivityResult"
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST",
        )
        // align uncompressed native libs to 16 KB pages (Play requirement)
        jniLibs.useLegacyPackaging = false
    }
}

compose.desktop {
    application { mainClass = "app.baki.MainKt" }
}

// Skiko caches into user.home; in this sandbox that path is read-only.
tasks.withType<JavaExec>().configureEach {
    systemProperty("user.home", System.getProperty("java.io.tmpdir"))
    systemProperty("skiko.renderApi", "SOFTWARE")
}

tasks.withType<Test>().configureEach {
    systemProperty("user.home", System.getProperty("java.io.tmpdir"))
    // benchmarks print their numbers; keep them visible
    testLogging { showStandardStreams = true }
}
