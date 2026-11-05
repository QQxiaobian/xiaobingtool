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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 国内可用镜像（如构建缓慢可取消注释）
        // maven { url = uri("https://maven.aliyun.com/repository/public") }
        // maven { url = uri("https://maven.aliyun.com/repository/google") }
        flatDir { dirs("app/libs") }
    }
}

rootProject.name = "PowerLineInspection"
include(":app")
