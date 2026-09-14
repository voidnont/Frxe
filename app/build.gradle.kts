plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val voidKeystore = providers.environmentVariable("VOID_KEYSTORE").orNull
val voidStorePassword = providers.environmentVariable("VOID_STORE_PASSWORD").orNull
val voidKeyAlias = providers.environmentVariable("VOID_KEY_ALIAS").orNull ?: "void"
val voidKeyPassword = providers.environmentVariable("VOID_KEY_PASSWORD").orNull
val hasVoidSigning = listOf(voidKeystore, voidStorePassword, voidKeyPassword).all { !it.isNullOrBlank() }
val ciReleaseDebugSigning =
    providers.environmentVariable("FRXE_CI_RELEASE_DEBUG_SIGNING").orNull == "1"

val zexlBaseUrl = providers.gradleProperty("FRXE_ZEXL_BASE_URL").orNull
    ?: providers.environmentVariable("FRXE_ZEXL_BASE_URL").orNull
    ?: "https://zexl.onrender.com"
val zexlApiKey = providers.gradleProperty("FRXE_ZEXL_API_KEY").orNull
    ?: providers.environmentVariable("FRXE_ZEXL_API_KEY").orNull
    ?: ""
val cobaltBaseUrl = providers.gradleProperty("FRXE_COBALT_BASE_URL").orNull
    ?: providers.environmentVariable("FRXE_COBALT_BASE_URL").orNull
    ?: ""
val cobaltApiKey = providers.gradleProperty("FRXE_COBALT_API_KEY").orNull
    ?: providers.environmentVariable("FRXE_COBALT_API_KEY").orNull
    ?: ""
fun buildConfigString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.frxe.music"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.frxe.music"
        minSdk = 26
        targetSdk = 37
        versionCode = 25
        versionName = "0.6.7"
        buildConfigField("String", "OWNER", "\"void\"")
        buildConfigField("String", "PUBLISHER", "\"void\"")
        buildConfigField("String", "AUTHOR", "\"void\"")
        buildConfigField("String", "ZEXL_BASE_URL", buildConfigString(zexlBaseUrl))
        buildConfigField("String", "ZEXL_API_KEY", buildConfigString(zexlApiKey))
        buildConfigField("String", "COBALT_BASE_URL", buildConfigString(cobaltBaseUrl))
        buildConfigField("String", "COBALT_API_KEY", buildConfigString(cobaltApiKey))
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasVoidSigning) {
            create("voidRelease") {
                storeFile = file(voidKeystore!!)
                storePassword = voidStorePassword
                keyAlias = voidKeyAlias
                keyPassword = voidKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = when {
                hasVoidSigning -> signingConfigs.getByName("voidRelease")
                ciReleaseDebugSigning -> signingConfigs.getByName("debug")
                else -> null
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES"
        )
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")

    implementation("androidx.compose.runtime:runtime:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.animation:animation:1.12.1")
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.12.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.12.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("io.github.kyant0:backdrop:2.0.1")

    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation("androidx.media3:media3-datasource:1.11.0")
    implementation("androidx.media3:media3-database:1.11.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("androidx.media3:media3-cast:1.11.0")

    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
    implementation("androidx.mediarouter:mediarouter:1.8.1")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")

    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.4")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
