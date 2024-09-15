import java.util.Date
import java.text.SimpleDateFormat
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("local.app")
}

android {

    val catalogs = extensions.getByType<VersionCatalogsExtension>()
    val libs = catalogs.named("libs")

    namespace = "com.kanavi.automotive.nami.music"
    compileSdk = libs.findVersion("compileSdk").get().toString().toInt()

    defaultConfig {
        applicationId = "com.kanavi.automotive.nami.music"
        minSdk = libs.findVersion("minSdk").get().toString().toInt()
        targetSdk = libs.findVersion("compileSdk").get().toString().toInt()

        val timeFormatter = SimpleDateFormat("yyyyMMdd")
        val time = timeFormatter.format(Date())
        val versionFromDate = time.toInt()

        versionCode = versionFromDate
        versionName = "0.1-$time"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("front") {
            storeFile = file("../keystore/platform-front.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("tablet") {
            storeFile = file("../keystore/platform-tablet.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    flavorDimensions += "type"
    productFlavors {
        create("Front") {
            dimension = "type"
            signingConfig = signingConfigs.getByName("front")
        }
        create("MiddleRear") {
            dimension = "type"
            signingConfig = signingConfigs.getByName("tablet")
        }
        create("SideRear") {
            dimension = "type"
            signingConfig = signingConfigs.getByName("tablet")
        }
    }

    buildTypes {
        all {
            signingConfig = null
            isMinifyEnabled = false
            applicationVariants.all {
                outputs.all {
                    (this as BaseVariantOutputImpl).outputFileName =
                        "Nami_UsbMusicService${flavorName}-${buildType.name}-${defaultConfig.versionName}.apk"
                }
            }
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.bundles.common)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.lifecycle)
    implementation(libs.worker)
    implementation(libs.bundles.room)
    implementation(libs.junit.ktx)
    ksp(libs.roomCompiler)
    implementation(libs.glide)
    implementation(libs.bundles.rootTools)

    implementation("androidx.media:media:1.6.0")
    implementation(libs.gson)

    //SMB
    implementation ("com.hierynomus:sshj:0.31.0")
    implementation ("com.hierynomus:smbj:0.10.0")
    implementation ("org.apache.poi:poi-ooxml:5.2.2")
    implementation ("org.nanohttpd:nanohttpd:2.3.1")
}