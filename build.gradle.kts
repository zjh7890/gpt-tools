import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension

// The same as `--stacktrace` param
gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    id("org.jetbrains.intellij.platform") version "2.1.0"
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
    alias(libs.plugins.serialization)
    id("net.saliman.properties") version "1.5.2"
}

fun prop(name: String): String =
    extra.properties[name] as? String ?: error("Property `$name` is not defined in gradle.properties")


group = properties("pluginGroup").get()
version = properties("pluginVersion").get()
val platformVersion = prop("platformVersion").toInt()

// Configure project's dependencies
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    intellijPlatform {
//        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
            intellijIde(prop("ideaVersion"))
            instrumentationTools()
            testFramework(TestFrameworkType.Platform)
            pluginVerifier()
            pluginModule(implementation(project(":goland")))
            pluginModule(implementation(project(":core")))
        }
        testImplementation("junit:junit:4.13.2")

//    implementation(libs.exampleLibrary)
}

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellijPlatform {
    autoReload=false
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = properties("pluginUntilBuild")
        }

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    publishing {
        token = environment("PUBLISH_TOKEN")
        channels = properties("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    signing {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaUltimate, "2024.2")
            recommended()
            select {
                sinceBuild = properties("pluginSinceBuild")
                untilBuild = properties("pluginUntilBuild")
            }
        }
    }
}


configure(
    subprojects
) {
    apply {
        plugin("org.jetbrains.intellij.platform.module")
        plugin("org.jetbrains.kotlin.jvm") // 使用完整的插件 ID
        plugin("org.jetbrains.kotlinx.kover") // 使用完整的插件 ID
        plugin("org.jetbrains.kotlin.plugin.serialization")

    }

    repositories {
        mavenCentral()

        intellijPlatform {
            defaultRepositories()
        }
    }

    dependencies {
        implementation("com.nfeld.jsonpathkt:jsonpathkt:2.0.1")
    }

    sourceSets {
            main {
//                java.srcDirs("src/gen")
//                if (platformVersion == 241) {
//                    resources.srcDirs("src/233/main/resources")
//                }
                resources.srcDirs("src/$platformVersion/main/resources")
            }
            test {
                resources.srcDirs("src/$platformVersion/test/resources")
            }
        }

    kotlin {
            sourceSets {
                main {
                    // share 233 code to 241
                    if (platformVersion == 241) {
                        kotlin.srcDirs("src/233/main/kotlin")
                    }
                    kotlin.srcDirs("src/$platformVersion/main/kotlin")
                }
                test {
                    kotlin.srcDirs("src/$platformVersion/test/kotlin")
                }
            }
    }
}

project(":goland") {
    dependencies(fun DependencyHandlerScope.() {
        intellijPlatform {
            intellijIde(prop("ideaVersion"))
            // 添加 instrumentationTools 依赖
            instrumentationTools()
//        intellijPlugins("org.jetbrains.plugins.go:233.11799.196".split(',').map(String::trim).filter(String::isNotEmpty))
        }
    })
}

project(":java") {
    dependencies(fun DependencyHandlerScope.() {
        intellijPlatform {
            intellijIde(prop("ideaVersion"))
            // 添加 instrumentationTools 依赖
            instrumentationTools()
            intellijPlugins("com.intellij.java".split(',').map(String::trim).filter(String::isNotEmpty))
        }
    })
}

project(":core") {
    dependencies(fun DependencyHandlerScope.() {
        implementation("com.azure:azure-ai-openai:1.0.0-beta.12")
        implementation("com.squareup.okhttp3:okhttp:4.4.1")
        implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
        implementation("io.reactivex.rxjava3:rxjava:3.1.9")
        implementation("com.nfeld.jsonpathkt:jsonpathkt:2.0.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        implementation("org.apache.commons:commons-text:1.9")


        intellijPlatform {
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
            intellijIde(prop("ideaVersion"))
            // 添加 instrumentationTools 依赖
            instrumentationTools()
//        intellijPlugins("org.jetbrains.plugins.go:233.11799.196".split(',').map(String::trim).filter(String::isNotEmpty))
        }
    })
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = properties("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }
}

fun IntelliJPlatformDependenciesExtension.intellijIde(versionWithCode: String) {
    val (type, version) = versionWithCode.toTypeWithVersion()
    create(type, version, useInstaller = false)
}

fun IntelliJPlatformDependenciesExtension.intellijPlugins(vararg notations: String) {
    for (notation in notations) {
        if (notation.contains(":")) {
            plugin(notation)
        } else {
            bundledPlugin(notation)
        }
    }
}

fun IntelliJPlatformDependenciesExtension.intellijPlugins(notations: List<String>) {
    intellijPlugins(*notations.toTypedArray())
}

data class TypeWithVersion(val type: IntelliJPlatformType, val version: String)

fun String.toTypeWithVersion(): TypeWithVersion {
    val (code, version) = split("-", limit = 2)
    return TypeWithVersion(IntelliJPlatformType.fromCode(code), version)
}