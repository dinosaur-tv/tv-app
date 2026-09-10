import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dinotv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dinotv.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 37
        versionName = "0.3.34"
        val homeUrl = providers.gradleProperty("DINO_HOME_URL").orElse("https://home.example.invalid/tv/").get()
        val apiUrl = providers.gradleProperty("DINO_API_BASE_URL").orElse("https://home.example.invalid/api").get().trimEnd('/')
        for (url in listOf(homeUrl, apiUrl)) {
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "Dino endpoints must be trusted HTTPS URLs without credentials, query or fragment"
            }
        }
        buildConfigField("String", "HOME_URL", "\"$homeUrl\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
    }

    buildFeatures { compose = true; buildConfig = true }

    // Release signing comes from the environment, never from Git. Without it assembleRelease stays unsigned.
    signingConfigs {
        create("release") {
            val store = providers.environmentVariable("DINO_KEYSTORE_PATH").orNull
            if (!store.isNullOrBlank()) {
                storeFile = file(store)
                storePassword = providers.environmentVariable("DINO_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("DINO_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("DINO_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        checkReleaseBuilds = false
        ignoreTestSources = true
        abortOnError = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
