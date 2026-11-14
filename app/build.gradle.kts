import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    check(file.exists()) {
        "Missing secrets.properties. Add DB_URL, DB_USER, DB_PASSWORD, SUPABASE_URL, and SUPABASE_ANON_KEY there. That file stays out of git."
    }
    file.inputStream().use { load(it) }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun secret(key: String, required: Boolean = true): String {
    val value = secrets.getProperty(key)?.trim().orEmpty()
    if (required) {
        check(value.isNotEmpty()) {
            if (key == "SUPABASE_ANON_KEY") {
                "secrets.properties is missing SUPABASE_ANON_KEY. Copy the anon public key from Supabase → Project Settings → API."
            } else {
                "secrets.properties is missing $key"
            }
        }
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.seatspot.library"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.seatspot.library"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "DB_URL", "\"${secret("DB_URL")}\"")
        buildConfigField("String", "DB_USER", "\"${secret("DB_USER")}\"")
        buildConfigField("String", "DB_PASSWORD", "\"${secret("DB_PASSWORD")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY", required = false)}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

val postgresOriginal = configurations.create("postgresOriginal") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val patchedPostgresJar = layout.buildDirectory.file("patched-libs/postgresql-android.jar")

val patchPostgresForAndroid = tasks.register("patchPostgresForAndroid") {
    val parserSource = layout.projectDirectory.file("pgfix/PGPropertyMaxResultBufferParser.java")
    inputs.file(parserSource)
    outputs.file(patchedPostgresJar)
    doLast {
        val original = postgresOriginal.files.single { it.name.startsWith("postgresql-") && it.extension == "jar" }
        val dest = patchedPostgresJar.get().asFile
        val work = layout.buildDirectory.dir("pgfix-work").get().asFile
        work.deleteRecursively()
        work.mkdirs()
        copy {
            from(zipTree(original))
            into(work)
            exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        }
        val classes = work.resolve("compiled")
        classes.mkdirs()
        val javac = ProcessBuilder(
            File(System.getProperty("java.home"), "bin/javac").absolutePath,
            "-encoding", "UTF-8",
            "-source", "17",
            "-target", "17",
            "-cp", original.absolutePath,
            "-d", classes.absolutePath,
            parserSource.asFile.absolutePath,
        ).redirectErrorStream(true).start()
        val javacLog = javac.inputStream.bufferedReader().readText()
        if (javac.waitFor() != 0) {
            error(javacLog.ifBlank { "javac failed" })
        }
        copy {
            from(classes)
            into(work)
        }
        classes.deleteRecursively()
        dest.parentFile.mkdirs()
        dest.delete()
        ant.withGroovyBuilder {
            "jar"(
                "destfile" to dest.absolutePath,
                "basedir" to work.absolutePath,
            )
        }
        work.deleteRecursively()
    }
}

dependencies {
    add("postgresOriginal", libs.postgresql)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(fileTree(layout.buildDirectory.dir("patched-libs")) {
        include("postgresql-android.jar")
        builtBy(patchPostgresForAndroid)
    })

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
