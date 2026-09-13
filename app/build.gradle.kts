import java.util.Properties
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val releaseSigningError: String? = when {
    !keystorePropertiesFile.exists() -> "缺少 keystore.properties，禁止构建 Release"
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").any {
        keystoreProperties.getProperty(it).isNullOrBlank()
    } -> "keystore.properties 缺少 Release 签名字段"
    !rootProject.file(keystoreProperties.getProperty("storeFile")).isFile ->
        "Release 签名文件不存在"
    else -> null
}
val hasReleaseSigning = releaseSigningError == null

android {
    namespace = "com.sitecam.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sitecam.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "0.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Help is read offline from the repository's single guide source. The
    // generated asset keeps the in-app guide in sync without a second copy.
    sourceSets {
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/sitecam-assets"))
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val syncUserGuideAsset = tasks.register<Copy>("syncUserGuideAsset") {
    from(rootProject.file("docs/USER_GUIDE.md"))
    into(layout.buildDirectory.dir("generated/sitecam-assets/docs"))
    rename { "USER_GUIDE.md" }
}

tasks.named("preBuild") {
    dependsOn(syncUserGuideAsset)
}

// Keep debug builds usable for development, but never silently emit an
// unsigned artifact when a formal Release build is requested.
tasks.configureEach {
    if (name in setOf("validateSigningRelease", "assembleRelease", "bundleRelease") && releaseSigningError != null) {
        doFirst { throw GradleException(releaseSigningError) }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }
}

dependencies {
    implementation("androidx.window:window:1.5.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Coil Image Loading
    implementation(libs.coil.compose)

    // EXIF & Location
    implementation(libs.androidx.exifinterface)
    implementation(libs.play.services.location)

    // Media3 Transformer: post-recording video watermark burn-in with
    // hardware MediaCodec/OpenGL processing while retaining the audio track.
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    // Android / Compose Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
