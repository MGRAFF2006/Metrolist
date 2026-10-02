@file:Suppress("UnstableApiUsage")

val useMavenLocalInnerTubeX = providers.gradleProperty("useMavenLocalInnerTubeX").isPresent

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.metrolist.innertube"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    jvmToolchain(21)
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            if (useMavenLocalInnerTubeX) {
                api("com.github.MetrolistGroup:innertubex:${libs.versions.innertubex.get()}")
            } else {
                api(libs.innertubex)
            }
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.encoding)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.quickjs)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.timber)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.kotlinxCoroutines.get()}")
            implementation(libs.ktor.client.mock)
        }
        getByName("desktopMain") {
            dependencies { implementation(libs.ktor.client.okhttp) }
        }
        getByName("desktopTest") {
            kotlin.srcDirs(
                "src/test/kotlin/com/metrolist/innertube/pages",
                "src/test/kotlin/com/metrolist/innertube/models",
                "src/test/kotlin/com/metrolist/innertube/utils",
            )
            dependencies { implementation(libs.junit) }
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        getByName("androidHostTest") {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(libs.junit)
                implementation(libs.ktor.client.mock)
            }
        }
    }
}
