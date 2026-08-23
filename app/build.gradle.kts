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
        versionCode = 2
        versionName = "0.2"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
