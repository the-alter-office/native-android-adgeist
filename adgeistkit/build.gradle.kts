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
        minSdk = 23

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
            buildConfigField("String", "VERSION_NAME", "\"${project.property("VERSION_NAME")}\"")
        }
        debug {
            buildConfigField("String", "VERSION_NAME", "\"${project.property("VERSION_NAME")}-${project.property("VERSION_SUFFIX")}\"")
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
    // Real sources are proprietary; Maven Central still requires a -sources.jar, so a stub is attached below.
    configure(AndroidSingleVariantLibrary(publishVariant, sourcesJar = false, publishJavadocJar = true))
}

val stubSourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(layout.projectDirectory.file("src/stub-sources/NOTICE.txt"))
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(stubSourcesJar)
    }
}

// Skip signing when publishing to Maven Local (no GPG key needed for local development)
tasks.withType<org.gradle.plugins.signing.Sign>().configureEach {
    onlyIf {
        !gradle.startParameter.taskNames.any { it.contains("publishToMavenLocal", ignoreCase = true) }
    }
}