plugins {
    id("java-library")
}

group = "sk.tuke.meta.persistence"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":annotations"))
    implementation(project(":persistence"))
    api("org.xerial:sqlite-jdbc:3.41.2.2")
    implementation(project(mapOf("path" to ":annotations")))
}