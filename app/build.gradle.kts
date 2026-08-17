plugins {
    id("com.android.application")
}

android {
    namespace = "com.huangkang.gtneo2tint"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.huangkang.gtneo2tint"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.otaliastudios:cameraview:2.7.2")
}
