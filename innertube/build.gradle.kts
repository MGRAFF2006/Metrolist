@file:Suppress("UnstableApiUsage")

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule

val useMavenLocalInnerTubeX = providers.gradleProperty("useMavenLocalInnerTubeX").isPresent

abstract class FixInnerTubeXNativeMetadata : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) {
        val target = context.details.id.name.removePrefix("innertubex-")
            .replace("iossimulator", "iosSimulator")
            .replace("arm64", "Arm64")
        context.details.withVariant("${target}MetadataElements-published") {
            withFiles {
                removeAllFiles()
                addFile("${context.details.id.name}-${context.details.id.version}.klib")
            }
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    components {
        withModule<FixInnerTubeXNativeMetadata>("com.github.MetrolistGroup.innertubex:innertubex-iosarm64")
        withModule<FixInnerTubeXNativeMetadata>("com.github.MetrolistGroup.innertubex:innertubex-iossimulatorarm64")
    }
}

kotlin {
    android {
        namespace = "com.metrolist.innertube"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

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
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        getByName("androidHostTest") {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}
