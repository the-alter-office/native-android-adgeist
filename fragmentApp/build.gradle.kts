plugins {
    alias(libs.plugins.android.application)
}

// Consume the locally published AAR (what real publishers get, incl. its
// bundled consumer-rules.pro) instead of the in-repo module:
//   ./gradlew :adgeistkit:publishToMavenLocal
//   ./gradlew :fragmentApp:installProdRelease -PuseAarDependency
// The published artifact is the prodRelease variant, so use the prod flavor.
val useAarDependency = providers.gradleProperty("useAarDependency").isPresent

android {
    namespace = "com.examplefragmentapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.examplefragmentapp"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Mirror a real consumer (PixelPlayer): R8 + resource shrinking on.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign the minified release with the debug keystore so it can be
            // installed without a real keystore.
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
    buildFeatures {
        compose = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
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
    if (useAarDependency) {
        implementation("${property("GROUP")}:${property("POM_ARTIFACT_ID")}:${property("VERSION_NAME")}")
    } else {
        implementation(project(":adgeistkit"))
    }
}