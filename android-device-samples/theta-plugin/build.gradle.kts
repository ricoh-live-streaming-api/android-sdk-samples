/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.konan.properties.hasProperty
import java.util.*

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 34
    namespace = "com.ricoh.livestreaming.theta"
    defaultConfig {
        applicationId = "com.ricoh.livestreaming.theta"
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        val props = Properties().apply { load(file("local.properties").inputStream()) }

        buildConfigField("String", "CLIENT_ID", "\"" + props["client_id"] + "\"")
        buildConfigField("String", "CLIENT_SECRET", "\"" + props["client_secret"] + "\"")
        buildConfigField("String", "ROOM_ID", "\"" + props["room_id"] + "\"")
        if (props.hasProperty("video_bitrate")) {
            buildConfigField("int", "VIDEO_BITRATE", props["video_bitrate"].toString())
        } else {
            buildConfigField("int", "VIDEO_BITRATE", "10000")
        }
        buildConfigField("String", "INITIAL_AUDIO_MUTE", "\"" + props["initial_audio_mute"] + "\"")
        buildConfigField("String", "PROXY", "\"" + props["proxy"] + "\"")
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
    implementation(fileTree(mapOf("dir" to "../../libs", "include" to listOf("*.aar"))))
    implementation(kotlin("stdlib-jdk8", KotlinCompilerVersion.VERSION))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("com.github.tony19:logback-android:3.0.0")
    implementation("com.theta360:pluginlibrary:4.0.0")
    implementation("org.apache.commons:commons-collections4:4.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    api("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-orgjson:0.11.2") {
        exclude("org.json", "json")     // provided by Android natively
    }
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
}
