/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.10")
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
