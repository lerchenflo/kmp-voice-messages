import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "io.github.lerchenflo"
version = "1.0.0"

kotlin {

    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    //Ignore expect actual warnings
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    jvm()

    android {
        namespace = "io.github.lerchenflo.voicemessages"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {

            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)

            //For icons
            implementation(libs.material.icons.extended)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {

    publishToMavenCentral()

    // JitPack builds locally (no GPG key available) and sets this env var; signing is only
    // needed for an actual Maven Central release, so skip it there.
    if (System.getenv("JITPACK") == null) {
        signAllPublications()
    }

    coordinates(group.toString(), "kmp-voice-messages", version.toString())

    pom {
        name = "kmp-voice-messages"
        description = "A Kotlin Multiplatform library for recording, storing, and playing voice messages, with a bundled Compose Multiplatform UI."
        inceptionYear = "2026"
        url = "https://github.com/lerchenflo/kmp-voice-messages/"


    }
}
