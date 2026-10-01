plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.kingzcheung.ximecgen"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kingzcheung.ximecgen"
        minSdk = 28
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 20260912
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 只打包 arm64-v8a（真机目标）。调试用 x86_64 模拟器时临时加回 "x86_64"。
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        // CI/本地发布签名：通过环境变量注入（KEYSTORE_FILE/PASSWORD、KEY_ALIAS/KEY_PASSWORD）。
        // 四个变量齐全才创建并启用；缺省时 release 不签名，产物可手动 sign。
        // 注意：不能把 null/"" 赋给 storePassword 等（AGP 会报 Cannot convert '' to File），
        // 所以整个配置延迟到 buildTypes 里按条件启用。
    }

    buildTypes {
        release {
            // 开启 R8 混淆 + 资源收缩：material-icons-extended 全量 dex 约 40MB，
            // 不开启时 APK 会膨胀到 45MB+，开启后只保留实际用到的图标
            isMinifyEnabled = true
            isShrinkResources = true
            val ksFile = System.getenv("KEYSTORE_FILE")
            val ksPass = System.getenv("KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("KEY_ALIAS")
            val keyPass = System.getenv("KEY_PASSWORD")
            if (!ksFile.isNullOrEmpty() && !ksPass.isNullOrEmpty() && !keyAlias.isNullOrEmpty() && !keyPass.isNullOrEmpty()) {
                signingConfig = signingConfigs.maybeCreate("release").apply {
                    storeFile = file(ksFile)
                    storePassword = ksPass
                    this.keyAlias = keyAlias
                    keyPassword = keyPass
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

// Rust 核心 (libxime_config_core.so)：构建 APK 前自动跑 scripts/build-native.sh。
// 脚本在缺少 Rust/NDK 工具链时会打印警告并以 0 退出（已有 .so 产物则直接复用）。
val buildNative by tasks.registering(Exec::class) {
    workingDir = rootDir
    commandLine("bash", "scripts/build-native.sh")
    // cargo 自带增量编译，交给脚本与 cargo 判断
    outputs.upToDateWhen { false }
}

tasks.named("preBuild") { dependsOn(buildNative) }

// 产物命名参考 Xime：<应用名>-<版本>-<abi>.apk。
// AGP 9 移除了 applicationVariants API，无法像 Xime 那样改 outputFileName，
// 故在 assembleRelease 之后于标准输出目录 app/build/outputs/apk/release/ 原地重命名。
// 不声明任务输入输出（doLast），避免与 AGP 的 apk listing 任务产生输出目录冲突。
val renameReleaseApk by tasks.registering {
    val outDir = layout.buildDirectory.dir("outputs/apk/release")
    val versionName = android.defaultConfig.versionName ?: "1.0"
    doLast {
        val dir = outDir.get().asFile
        dir.listFiles { f -> f.isFile && f.name.startsWith("app-release") && f.name.endsWith(".apk") }
            ?.forEach { f ->
                val target = File(dir, "ximecgen-$versionName-arm64-v8a.apk")
                if (f.absolutePath != target.absolutePath) f.renameTo(target)
            }
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(renameReleaseApk)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
