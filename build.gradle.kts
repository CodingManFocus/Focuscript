plugins {
    // Root project: no plugin required
}

allprojects {
    group = "kr.codename.focuscript"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
