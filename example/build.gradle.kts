plugins {
    id("application")
}

group = "sk.tuke.meta"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":persistence"))
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    annotationProcessor(project(":processor"))
}

application {
    mainClass = "sk.tuke.meta.example.Main"
}

tasks.test {
    useJUnitPlatform()
}
