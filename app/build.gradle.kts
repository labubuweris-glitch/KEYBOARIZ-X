plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val copyCustomKeySound by tasks.registering(Copy::class) {
    from(rootProject.file("suara_ketik.ogg"))
    into(layout.buildDirectory.dir("generated/customSound/res/raw"))
    rename { "suara_ketik.ogg" }
}

android {
    namespace = "com.keyboarizx"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.keyboarizx"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
}
