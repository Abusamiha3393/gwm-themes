plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sahra.split"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sahra.split"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        // نفس مفتاح صحراء داش (release.jks في جذر المستودع)، وكلمة مروره في أسرار GitHub
        create("release") {
            val pw = System.getenv("KEYSTORE_PASSWORD")
            if (!pw.isNullOrEmpty()) {
                storeFile = rootProject.file("../release.jks")
                storeType = "PKCS12"
                storePassword = pw
                keyAlias = "sahra"
                keyPassword = pw
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val release = signingConfigs.getByName("release")
            if (release.storeFile != null) signingConfig = release
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
