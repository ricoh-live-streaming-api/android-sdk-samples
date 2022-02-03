/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

import org.jetbrains.kotlin.config.KotlinCompilerVersion
import java.util.*

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-android")
}

android {
    compileSdkVersion(31)
    defaultConfig {
        applicationId = "com.ricoh.livestreaming.sample"
        minSdkVersion(25)
        targetSdkVersion(30)
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val props = Properties().apply { load(file("credentials.properties").inputStream()) }

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
    }

    flavorDimensions("type")
    productFlavors {
        create("android") {
        }
        create("unity") {
        }
    }

    variantFilter {
        if (name.contains("unity")) {
            ignore = true
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "../libs", "include" to "*.aar")))
    implementation(kotlin("stdlib-jdk8", KotlinCompilerVersion.VERSION))
    implementation("org.slf4j:slf4j-api:1.7.30")
    implementation("com.github.tony19:logback-android:1.3.0-3")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    api("io.jsonwebtoken:jjwt-api:0.11.2")
    implementation("com.google.android.material:material:1.4.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.2")
    runtimeOnly("io.jsonwebtoken:jjwt-orgjson:0.11.2") {
        exclude("org.json", "json")     // provided by Android natively
    }
    testImplementation("junit:junit:4.12")
    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.8.1")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
}
