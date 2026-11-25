plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "id.emypedia.emycash"
    compileSdk = 34
    defaultConfig {
        applicationId = "id.emypedia.emycash"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }
    buildFeatures {
        buildConfig = true
        resValues = true
    }
    // Dua edisi: "user" (aplikasi publik) dan "dev" (versi developer: panel cakupan PG, uji parser, info server pemilik).
    // Edisi dev memakai applicationId berakhiran .dev sehingga terpasang terpisah dari edisi user.
    flavorDimensions += "edisi"
    productFlavors {
        create("user") {
            dimension = "edisi"
            resValue("string", "app_name", "Emy Cash")
            buildConfigField("boolean", "DEV", "false")
        }
        create("dev") {
            dimension = "edisi"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Emy Cash Dev")
            buildConfigField("boolean", "DEV", "true")
        }
    }
    // Tanda tangan tetap (opsional): isi secret KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD di GitHub.
    // Tanpa itu APK memakai kunci debug runner, dan pembaruan di atas versi lama gagal dipasang (tanda tangan beda).
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (!ks.isNullOrEmpty() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            val hasKs = !System.getenv("KEYSTORE_FILE").isNullOrEmpty()
            signingConfig = if (hasKs) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
