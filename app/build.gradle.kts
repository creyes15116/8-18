plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.xiaomi.miwear.mishow"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.xiaomi.miwear.mishow"
        minSdk = 33
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
    packaging { resources { excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF" } }
}

dependencies {
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation("org.conscrypt:conscrypt-android:2.5.2")
}
