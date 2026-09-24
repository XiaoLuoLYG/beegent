pluginManagement { repositories { google(); maven("https://maven.aliyun.com/repository/google"); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); maven("https://maven.aliyun.com/repository/google"); mavenCentral() } }
rootProject.name = "beegent"
include(":app")
