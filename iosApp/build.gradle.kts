plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop")
    jvmToolchain(21)
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "MetrolistShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":innertube"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.coil)
            implementation("io.coil-kt.coil3:coil-network-ktor3:${libs.versions.coil.get()}")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.kotlinxCoroutines.get()}")
        }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        getByName("desktopMain") {
            dependencies { implementation(libs.ktor.client.okhttp) }
        }
    }
}
