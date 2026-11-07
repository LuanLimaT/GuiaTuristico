plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "br.edu.ifto.guiaturistico"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "br.edu.ifto.guiaturistico"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // Mapa: OSMDroid + OSMBonusPack (mesmas libs do slide e do projeto MeuMapa)
    implementation(files("libs/osmbonuspack_6.9.0.aar"))
    implementation("org.osmdroid:osmdroid-android:6.1.13")
    implementation("org.apache.commons:commons-lang3:3.8.1")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.squareup.okhttp3:okhttp:4.7.2")

    // MVVM: ViewModel + LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.0")
    implementation("androidx.lifecycle:lifecycle-livedata:2.9.0")

    // Navegação entre fragments
    implementation("androidx.navigation:navigation-fragment:2.9.0")
    implementation("androidx.navigation:navigation-ui:2.9.0")

    // Localização (slide de Permissões)
    implementation("com.google.android.gms:play-services-location:21.4.0")

    // Câmera (slide de Permissões)
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
}
