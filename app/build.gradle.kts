plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

// 固定签名：CI 通过环境变量注入 keystore 绝对路径与密码。
// 环境变量存在但文件缺失/密码为空时直接失败，避免静默降级为默认 debug 签名。
val fixedKeystorePath: String? = System.getenv("YANXING_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val fixedKeystorePassword: String? = System.getenv("YANXING_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val fixedKeyAlias: String = System.getenv("YANXING_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "yanxing"
val fixedKeyPassword: String? = System.getenv("YANXING_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    ?: fixedKeystorePassword
val useFixedSigning: Boolean = fixedKeystorePath != null && fixedKeystorePassword != null

if (fixedKeystorePath != null) {
    require(java.io.File(fixedKeystorePath).exists()) {
        "固定签名 keystore 不存在：$fixedKeystorePath"
    }
    require(fixedKeystorePassword != null) { "缺少 YANXING_KEYSTORE_PASSWORD" }
}

android {
    namespace = "com.yanxing.agent"
    compileSdk = 35

    signingConfigs {
        if (useFixedSigning) {
            create("fixed") {
                storeFile = file(fixedKeystorePath!!)
                storePassword = fixedKeystorePassword
                keyAlias = fixedKeyAlias
                keyPassword = fixedKeyPassword
                storeType = "PKCS12"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.yanxing.agent"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            if (useFixedSigning) {
                signingConfig = signingConfigs.getByName("fixed")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (useFixedSigning) {
                signingConfig = signingConfigs.getByName("fixed")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// kapt { correctErrorTypes = true } // 临时注释，避免编译错误

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
