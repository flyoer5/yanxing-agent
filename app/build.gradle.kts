plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.yanxing.agent"
    compileSdk = 35

    signingConfigs {
        // 固定签名：CI 通过环境变量注入 keystore 路径与密码；本地无环境变量时跳过（使用默认 debug 签名）
        create("fixed") {
            val keystoreEnv = providers.environmentVariable("YANXING_KEYSTORE_FILE")
            val passwordEnv = providers.environmentVariable("YANXING_KEYSTORE_PASSWORD")
            if (keystoreEnv.isPresent && passwordEnv.isPresent && file(keystoreEnv.get()).exists()) {
                storeFile = file(keystoreEnv.get())
                storePassword = passwordEnv.get()
                keyAlias = providers.environmentVariable("YANXING_KEY_ALIAS").orElse("yanxing").get()
                keyPassword = providers.environmentVariable("YANXING_KEY_PASSWORD").orElse(passwordEnv.get()).get()
                storeType = "PKCS12"
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
            val fixed = signingConfigs.findByName("fixed")
            if (fixed?.storeFile != null) {
                signingConfig = fixed
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val fixed = signingConfigs.findByName("fixed")
            if (fixed?.storeFile != null) {
                signingConfig = fixed
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
