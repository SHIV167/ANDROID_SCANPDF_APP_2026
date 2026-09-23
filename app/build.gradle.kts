plugins { id("com.android.application") }

android {
    namespace = "com.scanforge.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.scanforge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":pdf-core"))
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.core:core:1.15.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
