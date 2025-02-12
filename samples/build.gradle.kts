/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-android")
}

android {
    compileSdk = 34
    namespace = "com.ricoh.livestreaming.sample"
    defaultConfig {
        applicationId = "com.ricoh.livestreaming.sample"
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        val props = Properties().apply { load(file("local.properties").inputStream()) }

        buildConfigField("String", "CLIENT_ID", "\"" + props["client_id"] + "\"")
        buildConfigField("String", "CLIENT_SECRET", "\"" + props["client_secret"] + "\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions.apply {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "../libs", "include" to "*.aar")))
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("com.github.tony19:logback-android:3.0.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
    api("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-orgjson:0.11.2") {
        exclude("org.json", "json")     // provided by Android natively
    }
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
}
