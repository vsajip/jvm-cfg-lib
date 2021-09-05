import com.jfrog.bintray.gradle.BintrayExtension
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.5.21"

    // For documentation
    id("org.jetbrains.dokka") version "1.4.32"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    // For Maven repository upload
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.4"
}

group = "com.reddove"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.21")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.4.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.4.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.4.2")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    //testImplementation("org.jetbrains.kotlin:kotlin-test-junit")

    // additional dependencies used in tests
    testImplementation("org.apache.logging.log4j:log4j-api:2.13.0")
    testImplementation("org.apache.logging.log4j:log4j-core:2.13.0")
}

sourceSets {
    main {
        java {
            srcDir("src/main")
        }
    }
    test {
        java {
            srcDir("src/test")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
    // otherwise can't handle nested try/catch blocks
    // see https://youtrack.jetbrains.com/issue/KT-47851
    kotlinOptions.jvmTarget = "1.6"
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.withType(Jar::class) {
    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Implementation-Title"] = "Red Dove CFG library"
        attributes["Implementation-Version"] = "0.1.0"
        attributes["Implementation-Vendor"] = "Red Dove Consultants Limited"
        attributes["Implementation-Vendor-Id"] = "com.reddove"
    }
}

tasks.withType(DokkaTask::class) {
    //outputFormat = "html"
    //outputDirectory = "$buildDir/dokka"
    // add other Dokka configuration here
}

fun prop(s: String) = project.findProperty(s) as String?

publishing {
    publications.register("publication", MavenPublication::class) {
        from(components["java"])
    }

    repositories {
        maven {
            setUrl("https://bintray.com/reddove/public")
            metadataSources {
                gradleMetadata()
            }
        }
    }
}

val publication by publishing.publications

bintray {
    user = prop("bintrayUser")
    key = prop("bintrayAPIKey")
    publish = true
    override = true
    setPublications(publication.name)
    pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
        repo = "public"
        name = "com.reddove.config"
        userOrg = "reddove"
        websiteUrl = "https://www.red-dove.com"
        vcsUrl = "https://github.com/vsajip/ktlib"
        setLabels("configuration")
        setLicenses("BSD-3-Clause")
    })
}
