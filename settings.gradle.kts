pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        // m3e-compose из GitHub Packages: в CI — GITHUB_TOKEN (env),
        // локально — gprToken в ~/.gradle/gradle.properties
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aezochka/m3e-compose")
            credentials {
                username = providers.gradleProperty("gprUser").getOrElse(System.getenv("GPR_USER") ?: "aezochka")
                password = providers.gradleProperty("gprToken").getOrElse(System.getenv("GPR_TOKEN") ?: "")
            }
        }
    }
}
rootProject.name = "Glimpse"
include(":app")
