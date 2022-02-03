/*
 * Copyright 2022 RICOH Company, Ltd. All rights reserved.
 */

pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":samples")
include("android-device-samples:theta-plugin")
include("android-device-samples:setting-app")
include("android-device-samples:wearable-glass")
