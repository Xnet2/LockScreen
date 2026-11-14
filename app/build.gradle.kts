plugins {
    id("com.android.application")
}

android {
    namespace = "com.custom.lockscreen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.custom.lockscreen"
        minSdk = 31
        // 32 on purpose: lets the app read the home-screen wallpaper on Android 13
        targetSdk = 32
        versionCode = 6
        versionName = "1.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        checkReleaseBuilds = false
        disable += "ExpiredTargetSdkVersion"
    }
}

