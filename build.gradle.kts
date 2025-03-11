import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.25"

    // For documentation
    id("org.jetbrains.dokka") version "2.0.0"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    // For Maven repository upload
    `maven-publish`
    signing
}

group = "com.red-dove"
version = "0.1.2"

repositories {
    mavenCentral()
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.31")

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

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks.register<Javadoc>("Javadoc") {
    source(sourceSets["main"].allJava)
}

tasks.withType(Jar::class) {
    manifest {
        attributes["Manifest-Version"] = "1.0"
        attributes["Implementation-Title"] = "Red Dove CFG library"
        attributes["Implementation-Version"] = "0.1.2"
        attributes["Implementation-Vendor"] = "Red Dove Consultants Limited"
        attributes["Implementation-Vendor-Id"] = "com.red-dove"
    }
}

tasks.dokkaJavadoc.configure {
    outputDirectory.set(buildDir.resolve("docs/javadoc"))
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.red-dove"
            artifactId = "config"
            version = "0.1.2"

            from(components["java"])
            pom {
                name.set("CFG Library")
                description.set("A JVM library for working with the CFG configuration format.")
                url.set("https://docs.red-dove.com/cfg/kotlin.html")
                licenses {
                    license {
                        name.set("The 3-Clause BSD License")
                        url.set("https://opensource.org/licenses/BSD-3-Clause")
                    }
                }
                developers {
                    developer {
                        id.set("vsajip")
                        name.set("Vinay Sajip")
                        email.set("vinay_sajip@yahoo.co.uk")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/vsajip/jvm-cfg-lib.git")
                    developerConnection.set("scm:git:ssh://git@github.com/vsajip/jvm-cfg-lib.git")
                    url.set("https://github.com/vsajip/jvm-cfg-lib/")
                }
            }
        }
    }
    repositories {
        maven {
            //url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                val ossrhUsername: String? by project
                val ossrhPassword: String? by project
                username = ossrhUsername
                password = ossrhPassword
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
