plugins {
    id("com.android.application")
}

android {
    namespace = "com.patreze.brigadadevalidade"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.patreze.brigadadevalidade"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
