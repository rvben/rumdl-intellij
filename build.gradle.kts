import java.util.Properties
import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

buildscript {
    // Patch the build plugins' transitive libraries without changing plugin runtime jars.
    dependencies {
        classpath(platform("com.fasterxml.jackson:jackson-bom:2.22.2"))
        constraints {
            classpath("org.jsoup:jsoup:1.23.2") {
                because("Fix GHSA-pmhh-3w7g-xqp8 in IntelliJ structure tooling")
            }
        }
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.changelog)
}

// Keep IDE compatibility jobs isolated: each target resolves a different platform graph.
val lockedConfigurations = setOf(
    "compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath"
)
val platformTarget = providers.gradleProperty("platformVersion").get()
val defaultPlatformTarget = Properties().apply {
    file("gradle.properties").inputStream().use { load(it) }
}.getProperty("platformVersion")
dependencyLocking {
    lockMode = LockMode.STRICT
    // Local IDE artifacts are synthetic coordinates, not external Maven packages.
    // The IDE target remains pinned separately in gradle.properties / the CI matrix.
    ignoredDependencies.addAll("bundledPlugin:*", "bundledModule:*", "idea:*")
    if (platformTarget != defaultPlatformTarget) {
        require(platformTarget.matches(Regex("[0-9]+(?:\\.[0-9]+)*"))) {
            "Unsupported platformVersion for dependency locking: $platformTarget"
        }
        lockFile = file("gradle/locks/$platformTarget.lockfile")
    }
}
configurations.matching { it.name in lockedConfigurations }.configureEach {
    resolutionStrategy.activateDependencyLocking()
}
tasks.register("verifyDependencyLocks") {
    group = "verification"
    description = "Resolves locked dependency graphs without rewriting their lockfiles."
    doLast {
        require(file("buildscript-gradle.lockfile").isFile || gradle.startParameter.isWriteDependencyLocks) {
            "Missing buildscript-gradle.lockfile; run make lock-dependencies"
        }
        lockedConfigurations.forEach { name ->
            configurations.getByName(name).incoming.resolutionResult.allDependencies.forEach { dependency ->
                if (dependency is org.gradle.api.artifacts.result.UnresolvedDependencyResult) {
                    throw dependency.failure
                }
            }
        }
    }
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map {
            it.split(',').filter { s -> s.isNotBlank() }
        })
        plugins(providers.gradleProperty("platformPlugins").map {
            it.split(',').filter { s -> s.isNotBlank() }
        })
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
    // The IDE test framework also brings Jackson onto the test classpath.
    testImplementation(platform("com.fasterxml.jackson:jackson-bom:2.22.2"))
    testImplementation(libs.junit)
    testImplementation(libs.lsp4j)
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // Open-ended: drop the until-build attribute so the plugin stays
            // compatible with future IDE builds. Safe because the plugin
            // depends only on stable public APIs.
            untilBuild = provider { null }
        }
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
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
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion")
            .map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// The real-server LSP contract test (`*ContractTest`) drives an actual `rumdl`
// subprocess. Keep it out of the default `test` task so plain unit runs and the
// IDE inner loop need neither the binary nor network access; run it as an
// explicit, opt-in `integrationTest` gate (invoked by CI via the Makefile).
val contractTestPattern = "*ContractTest"

val integrationTest = tasks.register<Test>("integrationTest") {
    val unitTest = tasks.named<Test>("test").get()
    description = "Runs real-server LSP contract tests against a pinned rumdl."
    group = JavaBasePlugin.VERIFICATION_GROUP

    testClassesDirs = unitTest.testClassesDirs
    classpath = unitTest.classpath
    dependsOn(unitTest.dependsOn)

    // The IntelliJ Platform plugin supplies the sandbox config and required
    // JVM module-access args (e.g. --add-opens java.base/sun.nio.fs) via lazy
    // argument providers on the `test` task. Reuse the providers (resolved at
    // execution time) so BasePlatformTestCase initialises identically here.
    jvmArgumentProviders.addAll(unitTest.jvmArgumentProviders)
    systemProperties(unitTest.systemProperties)

    // Make the binary deterministic: the Makefile passes the directory of the
    // pinned rumdl (uv's tool-bin dir). Give the contract test the exact binary
    // and also prepend its directory to PATH for the plugin's normal discovery
    // path. This keeps the pin effective even when a developer has another
    // rumdl installed.
    providers.gradleProperty("rumdlBinDir").orNull?.let { binDir ->
        val executableName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "rumdl.exe"
        } else {
            "rumdl"
        }
        // Do not rely on IntelliJ's PATH lookup inside the test JVM. The test
        // already supports this property; wiring it here makes the binary pin
        // effective even if the platform snapshots PATH before doFirst runs.
        systemProperty("rumdl.test.binary", file("$binDir/$executableName").absolutePath)
        doFirst {
            environment(
                "PATH",
                binDir + System.getProperty("path.separator") + (System.getenv("PATH") ?: ""),
            )
        }
    }

    filter { includeTestsMatching(contractTestPattern) }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    test {
        filter { excludeTestsMatching(contractTestPattern) }
    }
}
