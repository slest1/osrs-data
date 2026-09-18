plugins {
    base
}

allprojects {
    group = "com.slest"
    version = "0.1.0-SNAPSHOT"

    repositories {
        maven {
            url = uri("https://repo.runelite.net")
            content { includeGroupByRegex("net\\.runelite.*") }
        }
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(11)
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        testLogging { events("passed", "skipped", "failed") }
    }
}
