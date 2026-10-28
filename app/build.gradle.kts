plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yadea.wallpaperstudio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.yadea.wallpaperstudio"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-diagnostic"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
}
