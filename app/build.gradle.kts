plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.examplenativeandroidapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.examplenativeandroidapp"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Run instrumented tests against the minified release variant so they
    // exercise the R8-processed SDK classes (this is what catches missing
    // consumer keep rules — see AdModelR8Test).
    testBuildType = "release"

    buildTypes {
        release {
            // Mirror a real consumer (PixelPlayer): R8 + resource shrinking on.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign the minified release with the debug keystore so it (and its
            // instrumented tests) can be installed without a real keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("beta") {
            dimension = "environment"
        }
        create("qa") {
            dimension = "environment"
        }
        create("prod") {
            dimension = "environment"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = false
    }
}

dependencies {

    // Force Kotlin version to prevent conflicts
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.0"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // implementation("com.google.android.gms:play-services-ads:23.3.0")
    // implementation(libs.androidx.activity.compose)
    // implementation(platform(libs.androidx.compose.bom))
    // implementation(libs.androidx.ui)
    // implementation(libs.androidx.ui.graphics)
    // implementation(libs.androidx.ui.tooling.preview)
    // implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    implementation(project(":adgeistkit"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Gson is an `implementation` dep of :adgeistkit (not exposed transitively),
    // but AdModelR8Test drives deserialization directly, so it needs Gson too.
    androidTestImplementation("com.google.code.gson:gson:2.10.1")
    // androidTestImplementation(platform(libs.androidx.compose.bom))
    // androidTestImplementation(libs.androidx.ui.test.junit4)
    // debugImplementation(libs.androidx.ui.tooling)
    // debugImplementation(libs.androidx.ui.test.manifest)
}