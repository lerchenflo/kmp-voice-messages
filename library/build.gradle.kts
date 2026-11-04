import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Same group JitPack serves a multi-module repo under. With any other group JitPack moves the
// artifacts and rewrites the Gradle module metadata, dropping the "-metadata" / "-sources" classifiers
// from the file names, which breaks the IDE sync of Kotlin Multiplatform apps (iOS metadata jar not found)
group = "com.github.lerchenflo.kmp-voice-messages"
//JitPack passes the git tag as -Pversion, local builds get a snapshot version
version = providers.gradleProperty("version").getOrElse("1.0.0-SNAPSHOT")

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

    jvm {
        compilerOptions {
            //11 so consumers with an older jvmTarget can use the library
            jvmTarget = JvmTarget.JVM_11
        }
    }

    android {
        namespace = "io.github.lerchenflo.voicemessages"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            //11 so consumers with an older jvmTarget can use the library
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

        jvmMain.dependencies {
            //Java Sound can't read the AAC (.m4a) voice messages that Android and iOS record
            implementation(libs.jaad)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

//Keeps the Java part of the jvm target on the same bytecode level as the Kotlin part
tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

// JitPack rewrites the Gradle module metadata and drops the classifier from every file name
// ("x-1.0.0-metadata.jar" becomes "x-1.0.0.jar"). The IDE sync of Kotlin Multiplatform apps then
// fails on the iOS metadata jar. So iOS files that carry a classifier are published a second time
// without one, under the name the rewritten metadata points to.
publishing {
    publications.withType<MavenPublication>().matching { it.name.startsWith("ios") }.configureEach {
        val publicationName = name
        for ((configurationName, fileExtension) in listOf("MetadataElements" to "jar", "ResourcesElements" to "zip")) {
            val outgoing = configurations.findByName(publicationName + configurationName)?.outgoing?.artifacts ?: continue
            artifact(provider { outgoing.files.singleFile }) {
                classifier = null
                extension = fileExtension
                builtBy(outgoing.buildDependencies)
            }
        }
    }

    // Sources jars as plain Maven "-sources" files, outside the module metadata (where JitPack's rewrite
    // would point them to the classes jar). The IDE fetches them by classifier and shows the KDoc on hover.
    // Looked up lazily: the Android plugin registers its sources jar task after the publication
    publications.withType<MavenPublication>().configureEach {
        val sourcesJarName = if (name == "kotlinMultiplatform") "sourcesJar" else name + "SourcesJar"
        val sourcesJar = provider { tasks.named<AbstractArchiveTask>(sourcesJarName).get() }
        artifact(sourcesJar.flatMap { it.archiveFile }) {
            classifier = "sources"
            extension = "jar"
            builtBy(sourcesJar)
        }
    }
}

mavenPublishing {

    // No sources variants in the module metadata: JitPack's rewrite would point them to the wrong files.
    // The sources jars are published as plain Maven files instead, see above
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.None()))

    coordinates(group.toString(), "kmp-voice-messages", version.toString())

    pom {
        name.set("kmp-voice-messages")
        description.set("Record, store and play voice messages in Kotlin Multiplatform, with a bundled Compose Multiplatform UI")
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
        scm {
            url.set("https://github.com/lerchenflo/kmp-voice-messages/")
        }
    }
}
