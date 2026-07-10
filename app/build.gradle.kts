import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    alias(libs.plugins.detekt)
}

val appVersionCode = 26
val appVersionName = "1.2.6"

base.archivesName.set("UrduCanvas - V$appVersionCode($appVersionName)")

android {
    namespace = "com.webscare.urducanvas"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.webscare.urducanvas"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // dagger hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    // sdp
    implementation(libs.sdp.android)

    // room database
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // Glide
    implementation(libs.glide)
    ksp(libs.compiler)
    implementation(libs.okhttp)
    implementation("com.github.bumptech.glide:okhttp3-integration:5.0.7") {
        exclude(group = "glide-parent")
    }

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.converter.scalars)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.logging.interceptor)

    // lottie
    implementation(libs.lottie)

    // LiveData
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    ksp(libs.androidx.lifecycle.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Circular ImageView
    implementation(libs.circleimageview)

    // SVG Support
    implementation(libs.androidsvg)

    // Shimmer
    implementation(libs.shimmer)

    // Swipe refresh
    implementation(libs.androidx.swiperefreshlayout)

    // Splash
    implementation(libs.androidx.core.splashscreen)

    // Print Media
    implementation(libs.androidx.print)

    // ML Kit
    implementation(libs.play.services.mlkit.subject.segmentation)

    // Firebase Crashlytics
    implementation(libs.firebase.crashlytics)

    // Subscription
    implementation(libs.billing.ktx)

    // Dynamic Animation
    implementation(libs.androidx.dynamicanimation.ktx)

    // In-App Updates
    implementation(libs.app.update.ktx)

    detektPlugins(libs.detekt.formatting)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    ignoreFailures = true
}

tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektAutoFix") {
    description = "Runs detekt with auto-correct for safe fixes"
    setSource(files("src/main/java", "src/main/kotlin"))
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    autoCorrect = true
    buildUponDefaultConfig = true
}
