import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.hilt) apply (false)
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.ksp) apply (false)
    alias(libs.plugins.kotlin.serialization) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
    }
    dependencies {
        classpath(libs.gradle)
        classpath(kotlin("gradle-plugin", libs.versions.kotlin.get()))
    }
}

// JitPack's native metadata variants advertise jars, but publish klibs.
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

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    dependencies {
        components {
            withModule<FixInnerTubeXNativeMetadata>("com.github.MetrolistGroup.innertubex:innertubex-iosarm64")
            withModule<FixInnerTubeXNativeMetadata>("com.github.MetrolistGroup.innertubex:innertubex-iossimulatorarm64")
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            if (project.findProperty("enableComposeCompilerReports") == "true") {
                arrayOf("reports", "metrics").forEach {
                    freeCompilerArgs.add("-P")
                    freeCompilerArgs.add("plugin:androidx.compose.compiler.plugins.kotlin:${it}Destination=${project.layout.buildDirectory}/compose_metrics")
                }
            }
        }
    }
}
