import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use {
        localProperties.load(it)
    }
}

val kodebarApiKey =
    localProperties.getProperty("KODEBAR_API_KEY", "")

android {
    namespace = "com.patreze.brigadadevalidade"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.patreze.brigadadevalidade"
        minSdk = 23
        targetSdk = 36
        versionCode = 4
        versionName = "0.4"

        buildConfigField(
            "String",
            "KODEBAR_API_KEY",
            "\"$kodebarApiKey\""
        )
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
