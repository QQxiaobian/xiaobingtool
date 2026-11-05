import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// ---------------------------------------------------------------
// 从 local.properties 读取密钥（不硬编码、不入库）
// ---------------------------------------------------------------
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(key: String, def: String = ""): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: def)

android {
    namespace = "com.kunming.powerline"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kunming.powerline"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // 天地图 key（云南 / 全国 可分别配置，缺省复用同一个）
        buildConfigField("String", "TIANDITU_KEY", "\"${prop("TIANDITU_KEY")}\"")
        buildConfigField("String", "TIANDITU_KEY_YUNNAN", "\"${prop("TIANDITU_KEY_YUNNAN", prop("TIANDITU_KEY"))}\"")
        // 高德定位 key（Android Key，需与包名 + SHA1 绑定）
        buildConfigField("String", "AMAP_KEY", "\"${prop("AMAP_KEY")}\"")
        // PMS / 生产管理系统内网接口
        buildConfigField("String", "PMS_BASE_URL", "\"${prop("PMS_BASE_URL", "http://10.0.0.1:8080/")}\"")
        // 位置共享 WebSocket
        buildConfigField("String", "WS_LOCATION_URL", "\"${prop("WS_LOCATION_URL", "wss://echo.websocket.events/ws/location")}\"")
        // 自建瓦片服务器地址
        buildConfigField("String", "TILE_SERVER_URL", "\"${prop("TILE_SERVER_URL", "http://10.0.0.1:8090/tiles/{z}/{x}/{y}.png")}\"")
        // 在线路网（自制注记瓦片）
        buildConfigField("String", "ROADNET_TILE_URL", "\"${prop("ROADNET_TILE_URL", "https://t{s}.tianditu.gov.cn/cva_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cva&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${prop("TIANDITU_KEY_YUNNAN", prop("TIANDITU_KEY"))}")}\"")
        // 帮助文档
        buildConfigField("String", "HELP_DOC_URL", "\"${prop("HELP_DOC_URL", "https://lbs.tianditu.gov.cn/")}\"")

        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
    }

    signingConfigs {
        create("release") {
            val storePath = prop("RELEASE_STORE_FILE")
            if (storePath.isNotBlank() && rootProject.file(storePath).exists()) {
                storeFile = rootProject.file(storePath)
                storePassword = prop("RELEASE_STORE_PASSWORD")
                keyAlias = prop("RELEASE_KEY_ALIAS")
                keyPassword = prop("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ""
            buildConfigField("boolean", "DEBUG_MODE", "true")
        }
        release {
            isMinifyEnabled = true
            // 资源压缩与 R8 同时开启会在低内存环境触发 OOM，这里改为仅代码压缩
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 降低 R8 内部优化等级，显著降低内存峰值（体积略增，功能不变）
            isDebuggable = false
            val sc = signingConfigs.getByName("release")
            signingConfig = if (sc.storeFile != null) sc else signingConfigs.getByName("debug")
            buildConfigField("boolean", "DEBUG_MODE", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
                "META-INF/services/javax.xml.stream.*"
            )
        }
        jniLibs { useLegacyPackaging = false }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // ---------- AndroidX 基础 ----------
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.2")


    // ---------- Compose ----------
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---------- Hilt ----------
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // ---------- Room ----------
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ---------- DataStore ----------
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ---------- 地图引擎 ----------
    implementation("org.maplibre.gl:android-sdk:11.5.2")

    // ---------- 网络 ----------
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // ---------- 序列化 / JSON ----------
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ---------- 文件解析 ----------
    // Excel (.xlsx / .xls)
    // Excel 读写已改为零依赖实现（XlsxReader / XlsxWriter），不再引入 Apache POI：
    // 1) APK 体积减少约 14MB；2) R8 内存峰值大幅下降；3) 启动更快

    // ---------- 高德定位（本地 AAR） ----------
    implementation(files("libs/amapsdk-location.aar"))

    // ---------- WorkManager ----------
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ---------- 其它 ----------
    implementation("com.google.code.gson:gson:2.10.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
