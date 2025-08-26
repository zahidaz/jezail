import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val jezailUiDownloadUrl =
    "https://github.com/zahidaz/jezail_ui/releases/latest/download/web-assets.zip"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlinx-serialization")
    id("de.undercouch.download") version "5.6.0"
}

android {
    namespace = "com.azzahid.jezail"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.azzahid.jezail"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        disable.add("QueryAllPackagesPermission")
    }

    packaging {
        resources {
            excludes += arrayOf(
                "META-INF/ASL-2.0.txt",
                "draftv4/schema",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
                "META-INF/LGPL-3.0.txt",
                "META-INF/LGPL-2.1.txt",
                "META-INF/LGPL-2.1",
                "META-INF/LGPL-3.0",
                "draftv3/schema"
            )
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("${layout.buildDirectory.get()}/generated/assets")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx.v290)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)


    implementation(libs.libsu.core)
    implementation(libs.service)
    implementation(libs.libsu.nio)
    implementation(libs.xz)

    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.gson)

    implementation("io.github.smiley4:ktor-openapi:5.2.0") {
        exclude(group = "javax.validation", module = "validation-api")
    }
    implementation(libs.ktor.swagger.ui)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v120)
    androidTestImplementation(libs.androidx.espresso.core.v360)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

val downloadWebAssets = tasks.register<Download>("downloadWebAssets") {
    group = "assets"
    description = "Downloads latest web assets from GitHub releases"
    src(jezailUiDownloadUrl)
    dest(layout.buildDirectory.file("downloads/web-assets.zip"))
    onlyIfModified(true)
    useETag(true)
}

val extractWebAssets = tasks.register<Copy>("extractWebAssets") {
    group = "assets"
    description = "Extracts web assets to build directory"
    dependsOn(downloadWebAssets)
    from(zipTree(downloadWebAssets.get().dest))
    into(layout.buildDirectory.dir("generated/assets/web"))
}

afterEvaluate {
    tasks.named("mergeDebugAssets") {
        dependsOn(extractWebAssets)
    }

    tasks.named("mergeReleaseAssets") {
        dependsOn(extractWebAssets)
    }
    
    tasks.matching { it.name.contains("LintReportModel") }.configureEach {
        dependsOn(extractWebAssets)
    }
}
