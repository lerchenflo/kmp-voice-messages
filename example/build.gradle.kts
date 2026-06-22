import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "io.github.lerchenflo.voicemessages.example"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget = JvmTarget.JVM_25
        }
    }
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_25
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":library"))
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.lerchenflo.voicemessages.example.MainKt"
    }
}
