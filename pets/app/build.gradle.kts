plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pets.desktoppets"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pets.desktoppets"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "2.2.0"
    }

    signingConfigs {
        // Release is signed with a locally generated keystore (see README). Replace for Play distribution.
        create("release") {
            storeFile = file("../pets-release.jks")
            storePassword = "petspets"
            keyAlias = "pets"
            keyPassword = "petspets"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // ./gradlew -Plite ... builds with only 3 pets (src/lite/assets, git-ignored): for slow emulators
    if (project.hasProperty("lite")) sourceSets.getByName("main").assets.setSrcDirs(listOf("src/lite/assets"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    // Animation packs are random-accessed through AssetFileDescriptor -> must stay uncompressed.
    androidResources { noCompress += listOf("mp4", "jpg", "bin") }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
