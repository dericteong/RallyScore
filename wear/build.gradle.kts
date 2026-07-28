import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing reads from the same git-ignored keystore.properties as the phone module.
// Both artifacts share one applicationId and must be signed with the identical key so Google
// Play accepts them in the same listing.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.courtside.pickleball.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.courtside.pickleball"
        minSdk = 30
        targetSdk = 36
        // Shares the phone's applicationId, so the Wear versionCode must stay distinct from and
        // higher than the phone's (currently 4): a watch can match both artifacts, and Play serves
        // the highest applicable versionCode. Keep Wear above the phone on every future bump.
        // 2 was consumed by a discarded first upload; Play retires a versionCode
        // permanently once a bundle carrying it has been uploaded.
        // Pooled sequence: 1 = phone 1.0.0, 2 = burned, 3 = Wear 1.0.0, 4 = phone 1.1.0, 5 = Wear 1.1.0.
        versionCode = 5
        versionName = "1.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(project(":shared"))

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.wear:wear:1.3.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
