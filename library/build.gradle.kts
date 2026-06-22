import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "io.github.lerchenflo"
version = "1.0.0-alpha01"

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
        namespace = "io.github.lerchenflo.kmp-voice-messages"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_25
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




publishing {

    repositories {
        maven {
            name = "kmp-voice-messages"
            description = "A Kotlin Multiplatform library for recording, storing, and playing voice messages, with a bundled Compose Multiplatform UI."
            url = URI("https://github.com/lerchenflo/kmp-voice-messages/")
        }
    }
}


mavenPublishing {

    coordinates(group.toString(), "kmp-voice-messages", version.toString())

    pom {
        name.set("Kmp voice messages")
        description.set("A simple library to create and play voice messages in kmp")
        inceptionYear.set("2026")
        url.set("https://github.com/lerchenflo/kmp-voice-messages/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("main")
                name.set("lerchenflo")
                url.set("https://github.com/lerchenflo")
            }
        }
    }

}