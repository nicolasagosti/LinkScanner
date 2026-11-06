plugins {
    id("com.android.application")
}

// El emulador llega a la máquina host por 10.0.2.2. Se cambia con -Plinkscanner.backendUrl=...
val backendUrl = providers.gradleProperty("linkscanner.backendUrl").getOrElse("http://10.0.2.2:8080")

android {
    namespace = "com.linkscanner.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.linkscanner.app"
        minSdk = 29 // RoleManager, para pedir ser el navegador predeterminado.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("com.google.android.material:material:1.14.0")
}
