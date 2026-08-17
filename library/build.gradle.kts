import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.bluewallet"
version = "0.0.1"

kotlin {
    jvm()
    androidLibrary {
        namespace = "io.bluewallet.headers"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava()
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
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(libs.ionspin.bignum)
            implementation(libs.kotlincrypto.sha2)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "bitcoin-headers", version.toString())

    pom {
        name = "bitcoin-headers"
        description = "Kotlin Multiplatform Bitcoin header PoW and chain-consensus validation."
        inceptionYear = "2026"
        url = "https://github.com/GladosBlueWallet/bitcoin-headers.kmp/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "overtorment"
                name = "Overtorment"
                url = "https://github.com/Overtorment/"
            }
        }
        scm {
            url = "https://github.com/GladosBlueWallet/bitcoin-headers.kmp/"
            connection = "scm:git:git://github.com/GladosBlueWallet/bitcoin-headers.kmp.git"
            developerConnection = "scm:git:ssh://git@github.com/GladosBlueWallet/bitcoin-headers.kmp.git"
        }
    }
}
