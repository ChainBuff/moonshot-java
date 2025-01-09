import java.net.URI

plugins {
    id("java")
}

group = "cc.monnshot"
version = "1.0-SNAPSHOT"


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}



repositories {
    maven {
        url = URI.create("https://maven.pkg.github.com/comodal/json-iterator")
        credentials {
            username = project.findProperty("gpr.user").toString()
            password = project.findProperty("gpr.token").toString()
        }
    }

    maven {
        url = URI.create("https://maven.pkg.github.com/sava-software/software.sava")
        credentials {
            username = project.findProperty("gpr.user").toString()
            password = project.findProperty("gpr.token").toString()
        }
    }
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation ("software.sava:anchor-programs:1.9.19")
    implementation ("software.sava:sava-core:1.16.1")
    implementation ("software.sava:sava-rpc:1.16.1")
    implementation ("software.sava:solana-programs:1.7.10")
    implementation ("software.sava:solana-web2:1.7.9")
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}