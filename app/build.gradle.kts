plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.sentinel.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.sentinel.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
