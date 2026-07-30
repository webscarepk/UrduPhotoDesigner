import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val appVersionCode = 30
val appVersionName = "1.3.0"

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
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
        }
    }

    flavorDimensions += "mode"

    productFlavors {
        create("devAds") {
            dimension = "mode"
            buildConfigField("Boolean", "ENABLE_ADS", "true")
            buildConfigField("Boolean", "AUTO_SUBSCRIBE_DEV", "false")
            buildConfigField("Boolean", "IS_PROD_LOGIC", "false")

            // Test AdMob IDs
            buildConfigField("String", "AD_APP_OPEN_SPLASH", "\"ca-app-pub-3940256099942544/9257395921\"")
            buildConfigField("String", "AD_NATIVE_HOME", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "AD_NATIVE_CATEGORIES", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "AD_NATIVE_TEMPLATES", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "AD_NATIVE_EMPTY_STATE", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "AD_REWARDED_BG_REMOVAL", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "AD_REWARDED_EXPORT", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "AD_INTERSTITIAL_EXPORT", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "AD_NATIVE_EXPORT_SUCCESS", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "AD_BANNER_SETTINGS", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "AD_BANNER_MAIN", "\"ca-app-pub-3940256099942544/6300978111\"")
        }

        create("dev") {
            dimension = "mode"
            buildConfigField("Boolean", "ENABLE_ADS", "false")
            buildConfigField("Boolean", "AUTO_SUBSCRIBE_DEV", "true")
            buildConfigField("Boolean", "IS_PROD_LOGIC", "false")

            // Empty AdMob IDs for no-ads build
            buildConfigField("String", "AD_APP_OPEN_SPLASH", "\"\"")
            buildConfigField("String", "AD_NATIVE_HOME", "\"\"")
            buildConfigField("String", "AD_NATIVE_CATEGORIES", "\"\"")
            buildConfigField("String", "AD_NATIVE_TEMPLATES", "\"\"")
            buildConfigField("String", "AD_NATIVE_EMPTY_STATE", "\"\"")
            buildConfigField("String", "AD_REWARDED_BG_REMOVAL", "\"\"")
            buildConfigField("String", "AD_REWARDED_EXPORT", "\"\"")
            buildConfigField("String", "AD_INTERSTITIAL_EXPORT", "\"\"")
            buildConfigField("String", "AD_NATIVE_EXPORT_SUCCESS", "\"\"")
            buildConfigField("String", "AD_BANNER_SETTINGS", "\"\"")
            buildConfigField("String", "AD_BANNER_MAIN", "\"\"")
        }

        create("prod") {
            dimension = "mode"
            buildConfigField("Boolean", "ENABLE_ADS", "true")
            buildConfigField("Boolean", "AUTO_SUBSCRIBE_DEV", "false")
            buildConfigField("Boolean", "IS_PROD_LOGIC", "true")

            // Production AdMob IDs
            buildConfigField("String", "AD_APP_OPEN_SPLASH", "\"ca-app-pub-4379805490947109/2348663407\"")
            buildConfigField("String", "AD_NATIVE_HOME", "\"ca-app-pub-4379805490947109/5002746903\"")
            buildConfigField("String", "AD_NATIVE_CATEGORIES", "\"ca-app-pub-4379805490947109/1115472783\"")
            buildConfigField("String", "AD_NATIVE_TEMPLATES", "\"ca-app-pub-4379805490947109/9281023402\"")
            buildConfigField("String", "AD_NATIVE_EMPTY_STATE", "\"ca-app-pub-4379805490947109/9852014833\"")
            buildConfigField("String", "AD_REWARDED_BG_REMOVAL", "\"ca-app-pub-4379805490947109/5341778394\"")
            buildConfigField("String", "AD_REWARDED_EXPORT", "\"ca-app-pub-4379805490947109/7496102785\"")
            buildConfigField("String", "AD_INTERSTITIAL_EXPORT", "\"ca-app-pub-4379805490947109/7489309447\"")
            buildConfigField("String", "AD_NATIVE_EXPORT_SUCCESS", "\"ca-app-pub-4379805490947109/2081336029\"")
            buildConfigField("String", "AD_BANNER_SETTINGS", "\"ca-app-pub-4379805490947109/9089451712\"")
            buildConfigField("String", "AD_BANNER_MAIN", "\"ca-app-pub-4379805490947109/1420335941\"")
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

androidComponents {
    beforeVariants { variantBuilder ->
        val mode = variantBuilder.productFlavors.firstOrNull { it.first == "mode" }?.second
        val buildType = variantBuilder.buildType
        if ((mode == "devAds" && buildType == "release") ||
            (mode == "dev" && buildType == "release") ||
            (mode == "prod" && buildType == "debug")) {
            variantBuilder.enable = false
        }
    }
}

dependencies {
    implementation(libs.webscare.ads.v102)

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
    ksp(libs.hilt.android.compiler)
    //sdp
    implementation (libs.sdp.android)

    //room database
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    //Glide
    implementation(libs.glide)
    ksp(libs.compiler)
    implementation(libs.okhttp)
    implementation("com.github.bumptech.glide:okhttp3-integration:5.0.7") {
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

    // LiveData
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    ksp(libs.androidx.lifecycle.compiler)

    //DataStore
    implementation(libs.androidx.datastore.preferences)

    //Coroutines
    implementation (libs.kotlinx.coroutines.core)
    implementation (libs.kotlinx.coroutines.android)

    //Circular ImageView
    implementation (libs.circleimageview)

    //SVG Support
    implementation (libs.androidsvg)

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

    //Subscription
    implementation(libs.billing.ktx)

    //Dynamic Animation
    implementation(libs.androidx.dynamicanimation.ktx)

    //In-App Updates
    implementation(libs.app.update.ktx)
}