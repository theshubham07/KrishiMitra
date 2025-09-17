plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("androidx.navigation.safeargs")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.example.krishimitra"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.krishimitra"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        mlModelBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // Ensure the Flex JNI .so is packaged so FlexDelegate works at runtime
    packagingOptions {
        jniLibs {
            pickFirst("**/libtensorflowlite_flex_jni.so")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.database)

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Firebase & Play Services
    implementation(platform("com.google.firebase:firebase-bom:33.2.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage-ktx:21.0.0")
    implementation("com.google.firebase:firebase-firestore:24.7.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // ML Kit
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.13.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.1.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.3.0")

    // Database & Room
    implementation("androidx.room:room-ktx:2.6.1")

    // Utilities
    implementation("com.google.guava:guava:31.1-android") // CameraX/Firestore conflict
    implementation("io.coil-kt:coil:2.7.0")
    implementation("com.intuit.sdp:sdp-android:1.0.6")
    implementation("com.intuit.ssp:ssp-android:1.0.6")
    implementation("pl.droidsonroids.gif:android-gif-drawable:1.2.17")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // AndroidX testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // circle image view – brings in the civ_* attributes
    implementation("com.mikhaellopez:circularimageview:4.3.1")

    implementation ("com.google.android.material:material:1.12.0")

    implementation ("com.google.mlkit:image-labeling:17.0.7")
}
