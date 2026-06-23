pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CyberShield"

// Declares which modules exist in the project
// Right now you only have :app
// When you add more modules later, list them here:
include(":app")
// include(":core:domain")
// include(":core:data")
// include(":core:firebase")
// include(":feature:auth")
// etc.