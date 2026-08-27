plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Consume the locally published AAR (what real publishers get, incl. its
// bundled consumer-rules.pro) instead of the in-repo module:
//   ./gradlew :adgeistkit:publishToMavenLocal
//   ./gradlew :app:connectedProdReleaseAndroidTest -PuseAarDependency
// The published artifact is the prodRelease variant, so test the prod flavor.
val useAarDependency = providers.gradleProperty("useAarDependency").isPresent

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
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.0"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.fragment.ktx)
    testImplementation(libs.junit)
    if (useAarDependency) {
        implementation("${property("GROUP")}:${property("POM_ARTIFACT_ID")}:${property("VERSION_NAME")}")
    } else {
        implementation(project(":adgeistkit"))
    }
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("com.google.code.gson:gson:2.10.1")
}