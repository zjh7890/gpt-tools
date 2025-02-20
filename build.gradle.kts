import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.JavaVersion.VERSION_17


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
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
}

var lang = extra.properties["lang"] ?: "java"

fun prop(name: String): String =
    extra.properties[name] as? String ?: error("Property `$name` is not defined in gradle.properties")


group = properties("pluginGroup").get()
version = properties("pluginVersion").get()
val platformVersion = prop("platformVersion").toInt()

val clionVersion = prop("clionVersion")

// https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html#modules-specific-to-functionality
val clionPlugins = listOf(
    "com.intellij.cidr.base",
    "com.intellij.cidr.lang",
    "com.intellij.clion",
    prop("rustPlugin"),
    "org.toml.lang"
)

var cppPlugins: List<String> = listOf(
    "com.intellij.cidr.lang",
    "com.intellij.clion",
    "com.intellij.cidr.base",
    "org.jetbrains.plugins.clion.test.google",
    "org.jetbrains.plugins.clion.test.catch"
)

val javaPlugins = listOf("com.intellij.java", "org.jetbrains.kotlin")

val ideaPlugins =
    listOf(
        "com.intellij.java",
        "org.jetbrains.plugins.gradle",
        "org.jetbrains.idea.maven",
        "org.jetbrains.kotlin",
        "JavaScript"
    )

val javaScriptPlugins = listOf("JavaScript")

val scalaPlugin = prop("scalaPlugin")
val pycharmPlugins = listOf(prop("pythonPlugin"))

val rustPlugins = listOf(
    prop("rustPlugin"),
    "org.toml.lang"
)


// Configure project's dependencies
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
        jetbrainsRuntime()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    intellijPlatform {
        val pluginList: MutableList<String> = mutableListOf("Git4Idea")
        when (lang) {
            "idea" -> {
                pluginList += javaPlugins
            }

            "scala" -> {
                pluginList += javaPlugins + scalaPlugin
            }

            "python" -> {
                pluginList += pycharmPlugins
            }

            "go" -> {
                pluginList += listOf("org.jetbrains.plugins.go")
            }

            "cpp" -> {
                pluginList += clionPlugins
            }

            "rust" -> {
                pluginList += rustPlugins
            }
        }
        intellijPlugins(pluginList)
//        bundledPlugins("com.intellij.modules.json")
//        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
//        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        if (lang == "go") {
            goland("2024.1", useInstaller = true)
        }
        else if (lang == "cpp") {
            clion("2024.1", useInstaller = true)
        }
        else {
            intellijIde(prop("ideaVersion"))
        }



        jetbrainsRuntime()
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        pluginModule(implementation(project(":core")))
//        pluginModule(implementation(project(":cpp")))
        pluginModule(implementation(project(":goland")))
        pluginModule(implementation(project(":java")))
        pluginModule(implementation(project(":javascript")))
        pluginModule(implementation(project(":kotlin")))
        pluginModule(implementation(project(":pycharm")))
        pluginModule(implementation(project(":rust")))
        pluginModule(implementation(project(":scala")))
    }
    testImplementation("junit:junit:4.13.2")

//    implementation(libs.exampleLibrary)
}

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

tasks {
    patchPluginXml {
//        inputFile.set(file("src/main/resources/META-INF/plugin.xml"))
    }
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellijPlatform {
    autoReload=false
    instrumentCode = false
    buildSearchableOptions = false

    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")

        ideaVersion {
            sinceBuild = prop("pluginSinceBuild")
            untilBuild = prop("pluginUntilBuild")
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
//        ides {
//            val (type, version) = prop("ideaVersion").toTypeWithVersion()
//            ide(type, version)
////            recommended()
//            select {
//                sinceBuild = prop("pluginSinceBuild")
//                untilBuild = prop("pluginUntilBuild")
//            }
//        }
    }
}
//
//intellijPlatformTesting {
//    runIde.register("runWhat") {
//
//    }
//}


configure(
    subprojects
) {
    apply {
        plugin("idea")
        plugin("kotlin")
        plugin("org.jetbrains.intellij.platform.module")
        plugin("org.jetbrains.kotlin.jvm") // 使用完整的插件 ID
        plugin("org.jetbrains.kotlinx.kover") // 使用完整的插件 ID
        plugin("org.jetbrains.kotlin.plugin.serialization")
    }

    repositories {
        mavenCentral()

        intellijPlatform {
            defaultRepositories()
            jetbrainsRuntime()
        }
    }

    intellijPlatform {
        instrumentCode = false
        buildSearchableOptions = false
    }

    dependencies {
        implementation("com.nfeld.jsonpathkt:jsonpathkt:2.0.1")
        intellijPlatform {
//            bundledPlugins("com.intellij.modules.json")
        }
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

    configure<JavaPluginExtension> {
        sourceCompatibility = VERSION_17
        targetCompatibility = VERSION_17
    }

    tasks {
        withType<KotlinCompile> {
            kotlinOptions {
                jvmTarget = VERSION_17.toString()
//                languageVersion = "1.9"
//                // see https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
//                apiVersion = "1.7"
//                freeCompilerArgs = listOf("-Xjvm-default=all")
            }
        }

        prepareSandbox { enabled = false }
    }
}

project(":core") {
    dependencies(fun DependencyHandlerScope.() {
        implementation("com.azure:azure-ai-openai:1.0.0-beta.11")
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
        implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
        implementation("io.reactivex.rxjava3:rxjava:3.1.9")
        implementation("com.nfeld.jsonpathkt:jsonpathkt:2.0.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        implementation("org.apache.commons:commons-text:1.9")

        implementation("io.github.bonede:tree-sitter:0.22.6")
        implementation("io.github.bonede:tree-sitter-java:0.23.4")

        // Exposed 核心依赖
        implementation("org.jetbrains.exposed:exposed-core:0.41.1")
        // Exposed DAO 依赖
        implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
        // Exposed JDBC 依赖
        implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")

        intellijPlatform {
            intellijPlugins(javaPlugins)
//            bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
            intellijIde(prop("ideaVersion"))
//            bundledPlugins("com.intellij.modules.json")
            // 添加 instrumentationTools 依赖
            instrumentationTools()
        }
    })
}

//project(":cpp") {
//    if (platformVersion == 233 || platformVersion == 241) {
//        cppPlugins += "com.intellij.nativeDebug"
//    }
//
//    dependencies {
//        intellijPlatform {
//            intellijIde(clionVersion)
//            intellijPlugins(cppPlugins)
//            instrumentationTools()
//        }
//
//        implementation(project(":core"))
//    }
//}

project(":goland") {
    dependencies {
        intellijPlatform {
            intellijIde(prop("ideaVersion"))
            intellijPlugins(prop("goPlugin").split(',').map(String::trim).filter(String::isNotEmpty))
            instrumentationTools()
        }

        implementation(project(":core"))
    }
}

project(":java") {
    dependencies {
        intellijPlatform {
            intellijIde(prop("ideaVersion"))
            // 添加 instrumentationTools 依赖
            instrumentationTools()
            intellijPlugins("com.intellij.java".split(',').map(String::trim).filter(String::isNotEmpty))
        }

        implementation(project(":core"))
    }
}

project(":javascript") {
    dependencies {
        intellijPlatform {
            intellijIde(prop("ideaVersion"))
            intellijPlugins(ideaPlugins)
            intellijPlugins(javaScriptPlugins)
            instrumentationTools()
        }

        implementation(project(":core"))
    }
}


project(":kotlin") {
    dependencies {
        intellijPlatform {
            intellijIde(prop("ideaVersion"))
            intellijPlugins(ideaPlugins)
            instrumentationTools()
        }

        implementation(project(":core"))
        implementation(project(":java"))
    }
}

project(":pycharm") {
    dependencies {
        intellijPlatform {
            intellijIde(prop("ideaVersion"))
            intellijPlugins(ideaPlugins + pycharmPlugins)
            instrumentationTools()
        }

        implementation(project(":core"))
    }
}

project(":scala") {
    dependencies {
        intellijPlatform {
            intellijIde(prop("ideaVersion"))
            intellijPlugins(ideaPlugins + scalaPlugin)
            instrumentationTools()
        }

        implementation(project(":core"))
        implementation(project(":java"))
    }
}

project(":rust") {
    dependencies {
        intellijPlatform {
            intellijIde(prop("ideaVersion"))
            intellijPlugins(ideaPlugins + rustPlugins)
            instrumentationTools()
        }

        implementation(project(":core"))
    }
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