plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val copyCustomKeySound by tasks.registering(Copy::class) {
    val soundFiles = listOf(
        "suara_ketik.ogg",
        "suara_spasi.ogg",
        "suara_enter.ogg",
        "suara_shift.ogg",
        "suara_hapus.ogg"
    ).map { rootProject.file(it) }.filter { it.exists() }

    from(soundFiles)
    into(layout.buildDirectory.dir("generated/customSound/res/raw"))
}

android {
    namespace = "com.keyboarizx"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.keyboarizx"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keyboarizx-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/customSound/res"))
    kotlinOptions { jvmTarget = "17" }
}

tasks.named("preBuild").configure {
    dependsOn(copyCustomKeySound)
}
