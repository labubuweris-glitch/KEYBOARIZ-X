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
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
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
