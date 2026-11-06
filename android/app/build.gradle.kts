import java.util.Properties

plugins {
    id("com.android.application")
}

// Se puede cambiar el backend de cualquier variante con -Plinkscanner.backendUrl=...
val backendUrlOverride = providers.gradleProperty("linkscanner.backendUrl").orNull

// La clave de firma y sus contraseñas nunca van al repo: ver "Publicar una versión" en el README.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

android {
    namespace = "com.linkscanner.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.linkscanner.app"
        minSdk = 29 // RoleManager, para pedir ser el navegador predeterminado.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // El emulador llega a la máquina host por 10.0.2.2.
            buildConfigField("String", "BACKEND_URL", "\"${backendUrlOverride ?: "http://10.0.2.2:8080"}\"")
        }
        release {
            buildConfigField(
                "String",
                "BACKEND_URL",
                "\"${backendUrlOverride ?: "https://linkscanner-production.up.railway.app"}\"",
            )
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("com.google.android.material:material:1.14.0")
}
