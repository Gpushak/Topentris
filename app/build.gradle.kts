import java.util.Properties
import javax.inject.Inject
import org.gradle.api.file.FileSystemOperations
import org.gradle.process.ExecOperations

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
        versionCode = 4
        versionName = "1.2"
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
        noCompress += listOf("flac", "ogg", "mp3", "mp4")
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

val localSettings = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull?.reader()?.use { load(it) }
}
val configuredEncoder = providers.gradleProperty("ffmpegExecutable").orNull
    ?: providers.environmentVariable("FFMPEG_EXECUTABLE").orNull
    ?: localSettings.getProperty("ffmpegExecutable")
val ffmpeg = configuredEncoder?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it).takeIf { file -> file.isFile } }
    ?: providers.environmentVariable("PATH").orNull.orEmpty().split(File.pathSeparator)
        .filter { it.isNotBlank() }
        .asSequence()
        .flatMap { dir -> sequenceOf(File(dir, "ffmpeg"), File(dir, "ffmpeg.exe")) }
        .firstOrNull { it.isFile && it.canExecute() }

val stageSoundtrack = tasks.register<StageSoundtrack>("stageSoundtrack") {
    tracks.from(fileTree(rootProject.file("Soundtrek")) { include("**/*.flac") })
    if (ffmpeg != null) encoder.set(ffmpeg)
}

val stagePrikolAssets = tasks.register<StagePrikolAssets>("stagePrikolAssets") {
    assets.from(fileTree(rootProject.file("PrikolFiles")) {
        include("MetalOst/*.mp3", "SFX/*/*.mp3", "VisualMemes/*.gif", "VisualMemes/*.jpg", "VisualMemes/*.mp4")
    })
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.apply {
        addGeneratedSourceDirectory(stageSoundtrack, StageSoundtrack::destination)
        addGeneratedSourceDirectory(stagePrikolAssets, StagePrikolAssets::destination)
    }
}

abstract class StagePrikolAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assets: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun stage() {
        files.sync {
            from(assets)
            into(destination.dir("prikol"))
            includeEmptyDirs = false
        }
    }
}

abstract class StageSoundtrack : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tracks: ConfigurableFileCollection

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val encoder: RegularFileProperty

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @get:Inject
    abstract val processes: ExecOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun stage() {
        val originals = tracks.files
        val ffmpeg = encoder.orNull?.asFile
        if (ffmpeg == null && originals.isNotEmpty()) {
            logger.lifecycle("Packaging FLAC soundtrack. Set ffmpegExecutable for a smaller Opus soundtrack.")
        }
        val packaged = if (ffmpeg == null) originals else originals.map { track ->
            val output = temporaryDir.resolve(assetName(track.name, "ogg"))
            processes.exec {
                commandLine(
                    ffmpeg, "-nostdin", "-v", "error", "-xerror", "-y", "-i", track,
                    "-map", "0:a:0", "-map_metadata", "-1", "-c:a", "libopus",
                    "-b:a", "160k", "-vbr", "on", "-application", "audio", output,
                )
            }.assertNormalExitValue()
            output
        }
        files.sync {
            from(packaged)
            if (ffmpeg == null) rename { assetName(it, "flac") }
            into(destination.dir("music"))
        }
    }

    private fun assetName(filename: String, extension: String): String {
        val slug = filename.substringBeforeLast('.').lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return "$slug.$extension"
    }
}
