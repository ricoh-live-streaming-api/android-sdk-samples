/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
        classpath("com.github.kezong:fat-aar:1.3.6")
    }
}

allprojects {
    repositories {
        maven { url = uri("https://github.com/ricohapi/theta-plugin-library/raw/master/repository") }
        google()
        mavenCentral()

        flatDir {
            dirs("libs")
        }
    }
}

tasks.create(BasePlugin.CLEAN_TASK_NAME, Delete::class.java) {
    group = BasePlugin.BUILD_GROUP
    delete(rootProject.buildDir)
}
