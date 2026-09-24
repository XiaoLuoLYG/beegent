pluginManagement { repositories { google(); mavenCentral(); maven("https://maven.aliyun.com/repository/google"); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral(); maven("https://maven.aliyun.com/repository/google") } }
rootProject.name = "beegent"
include(":app")
