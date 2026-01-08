import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.adgeistkit"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "environment"

    productFlavors {
        create("beta") {
            dimension = "environment"
            buildConfigField("String", "BASE_API_URL", "\"https://beta.v2.bg-services.adgeist.ai\"")
        }
        create("qa") {
            dimension = "environment"
            buildConfigField("String", "BASE_API_URL", "\"https://qa.v2.bg-services.adgeist.ai\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BASE_API_URL", "\"https://prod.v2.bg-services.adgeist.ai\"")
        }
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Force Kotlin version to prevent conflicts
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.0"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core:1.12.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    val publishVariant = findProperty("publishVariant")?.toString() ?: "prodRelease"
    configure(AndroidSingleVariantLibrary(publishVariant))
    
    // Use OSSRH (DEFAULT) for snapshots, CENTRAL_PORTAL for releases
    val isSnapshot = findProperty("IS_SNAPSHOT")?.toString()?.toBoolean() ?: false
    
    publishToMavenCentral(
        if (isSnapshot) com.vanniktech.maven.publish.SonatypeHost.DEFAULT 
        else com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL
    )
    
    signAllPublications()
}