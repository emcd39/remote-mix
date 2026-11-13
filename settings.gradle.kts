pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "RemoteMix"
include(":app")
include(":adb_kt", ":spake2_kt")
project(":adb_kt").projectDir = file("third_party/adb_kt")
project(":spake2_kt").projectDir = file("third_party/spake2_kt")
