import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

// 读取 local.properties 或环境变量
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}

android {
    namespace = "com.example.traktneosync"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.traktneosync"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${localProps.getProperty("traktClientId", "")}\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${localProps.getProperty("traktClientSecret", "")}\"")
        buildConfigField("String", "NEODB_CLIENT_ID", "\"${localProps.getProperty("neodbClientId", "")}\"")
        buildConfigField("String", "NEODB_CLIENT_SECRET", "\"${localProps.getProperty("neodbClientSecret", "")}\"")
    }

    signingConfigs {
        create("release") {
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") 
                ?: localProps.getProperty("SIGNING_KEY_ALIAS", "traktneosync")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") 
                ?: localProps.getProperty("SIGNING_KEY_PASSWORD", "")
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "traktneosync.keystore")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") 
                ?: localProps.getProperty("SIGNING_STORE_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.5"
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // DataStore (Preferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Coil (图片加载)
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}
