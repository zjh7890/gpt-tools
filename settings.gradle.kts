plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "gpt-tools"
include("goland")
include("pycharm")
include("core")
include("java")
include("cpp")
include("javascript")
include("kotlin")
include("rust")
include("scala")
