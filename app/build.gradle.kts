plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id ("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.example.urduphotodesigner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.urduphotodesigner"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        setProperty("archivesBaseName", "UrduCanvas - V$versionCode($versionName)")
    }
    bundle{
        language{
            enableSplit = false
        }
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
    buildFeatures {
        viewBinding = true
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

    //dagger hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    //sdp
    implementation (libs.sdp.android)

    //room database
    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    //Glide
    implementation(libs.glide)
    kapt(libs.compiler)
    implementation(libs.okhttp)
    implementation("com.github.bumptech.glide:okhttp3-integration:4.13.1") {
        exclude(group = "glide-parent")
    }

    //Retrofit
    implementation (libs.retrofit)
    implementation (libs.converter.gson)
    implementation (libs.converter.scalars)
    implementation (libs.androidx.work.runtime.ktx)
    implementation(libs.logging.interceptor)

    //lottie
    implementation (libs.lottie)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // LiveData
    implementation(libs.androidx.lifecycle.livedata.ktx)
    kapt(libs.androidx.lifecycle.compiler)

    //DataStore
    implementation(libs.androidx.datastore.preferences)

    //Coroutines
    implementation (libs.kotlinx.coroutines.core)
    implementation (libs.kotlinx.coroutines.android)

    //Circular ImageView
    implementation (libs.circleimageview)

    //SVG Support
    implementation (libs.androidsvg)

    //Google Sign in
    implementation(libs.play.services.auth)

    //Shimmer
    implementation(libs.shimmer)

    //Swipe refresh
    implementation(libs.androidx.swiperefreshlayout)

    //Splash
    implementation(libs.androidx.core.splashscreen)

    //Print Media
    implementation(libs.androidx.print)
    
    //ML Kit
    implementation(libs.play.services.mlkit.subject.segmentation)

    //Firebase Crashlytics
    implementation(libs.firebase.crashlytics)
}