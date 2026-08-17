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
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.otaliastudios:cameraview:2.7.2") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
}
