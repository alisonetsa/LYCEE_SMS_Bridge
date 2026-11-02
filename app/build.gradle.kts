plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dz.lycee.smsbridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "dz.lycee.smsbridge"
        minSdk = 26
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"
    }
}
