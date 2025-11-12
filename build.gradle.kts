import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij.platform") version "2.5.0"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "2.2.1"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    mavenCentral()

    // Add IntelliJ Platform repository
    intellijPlatform {
        defaultRepositories()
    }
}

// Set the JVM language level
kotlin {
    jvmToolchain(17)
}

// Dependencies block
dependencies {
    // IntelliJ Platform Gradle Plugin Dependencies Extension
    intellijPlatform {
        create(properties("platformType"), properties("platformVersion"))

        // Plugin Dependencies
        properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty).forEach {
            plugin(it)
        }

        pluginVerifier()
        zipSigner()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

// Configure Gradle IntelliJ Plugin
intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = properties("pluginUntilBuild")
        }

        // Extract the <!-- Plugin description --> section from README.md
        description = providers.provider {
            val content = projectDir.resolve("README.md").readText()
            val lines = content.lines()
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            if (!lines.containsAll(listOf(start, end))) {
                throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
            }

            val descLines = lines.subList(lines.indexOf(start) + 1, lines.indexOf(end))
            markdownToHTML(descLines.joinToString("\n"))
        }

        // Get the latest available change notes from the changelog file
        val changelog = project.changelog // local variable for configuration cache compatibility
        changeNotes = providers.provider {
            with(changelog) {
                renderItem(
                    getOrNull(properties("pluginVersion")) ?: getUnreleased(),
                    outputType = Changelog.OutputType.MARKDOWN
                )
            }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first())
    }
}

// Configure Gradle Changelog Plugin
changelog {
    version = properties("pluginVersion")
    groups = emptyList()
    headerParserRegex = Regex("""(\d+\.\d+\.\d+)""")
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    publishPlugin {
        dependsOn("patchChangelog")
    }
}