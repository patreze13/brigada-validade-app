import java.util.Properties

plugins {
    id("com.android.application") version "9.3.0"
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

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.patreze.brigadadevalidade"
        minSdk = 23
        targetSdk = 36
        versionCode = 6
        versionName = "0.6"

        buildConfigField(
            "String",
            "KODEBAR_API_KEY",
            "\"$kodebarApiKey\""
        )
    }
}

dependencies {
    implementation(
        "com.google.android.gms:play-services-code-scanner:16.1.0"
    )

}
