plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sakinah.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sakinah.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
}
