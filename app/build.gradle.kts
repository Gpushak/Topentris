plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.topentris"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.topentris"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        compose = true
    }

    androidResources {
        noCompress += listOf("flac", "ogg")
    }

    sourceSets.named("main") {
        assets.srcDir(layout.buildDirectory.dir("generated/musicAssets"))
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

val stageSoundtrack = tasks.register<Copy>("stageSoundtrack") {
    from(rootProject.file("Soundtrek")) {
        include("**/*.flac")
    }
    into(layout.buildDirectory.dir("generated/musicAssets/music"))
    eachFile {
        val copied = this as org.gradle.api.file.FileCopyDetails
        copied.relativePath = org.gradle.api.file.RelativePath(true, soundtrackAssetName(copied.name))
    }
    includeEmptyDirs = false
}

tasks.configureEach {
    if (name == "mergeDebugAssets" || name == "mergeReleaseAssets") {
        dependsOn(stageSoundtrack)
    }
}

fun soundtrackAssetName(filename: String): String {
    val base = filename.substringBeforeLast('.')
    val slug = base.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return "$slug.flac"
}
