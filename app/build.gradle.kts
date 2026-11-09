import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionCode = versionProps.getProperty("VERSION_CODE").toInt()
val appVersionName = versionProps.getProperty("VERSION_NAME")
// 默认使用公开仓库里的更新清单；本地可用 -PupdateManifestUrl=... 覆盖。
val defaultUpdateManifest = "https://raw.githubusercontent.com/ac1314-520/nianri/refs/heads/main/release/update.json"

val keystorePropsFile = rootProject.file("keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKey = keystorePropsFile.exists() &&
    rootProject.file(keystoreProps.getProperty("storeFile", "")).exists()

android {
    namespace = "com.nianri.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.nianri.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = false

        // 默认指向念日公开仓库；用户仍可在「设置 -> 在线更新」里改成自己的地址或清空。
        buildConfigField(
            "String",
            "DEFAULT_UPDATE_MANIFEST",
            "\"${project.findProperty("updateManifestUrl") ?: defaultUpdateManifest}\""
        )
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            // Build in this restricted workspace with the project-local key
            // instead of trying to write a debug keystore into the user home.
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        warningsAsErrors = false
        disable += listOf("GradleDependency", "OldTargetApi", "UnusedResources")
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.kotlin_module"
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
