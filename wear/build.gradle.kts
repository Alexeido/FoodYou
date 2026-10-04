plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.serialization)
}

/** El mismo número de compilación que la app del móvil (ver app/build.gradle.kts). */
val foodyouBuild: Int =
    (findProperty("foodyou.build") as String?)?.toIntOrNull()
        ?: System.getenv("FOODYOU_BUILD")?.toIntOrNull()
        ?: 0

android {
    namespace = "com.maksimowiczm.foodyou.wear"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // El mismo paquete que la app del móvil, como piden las apps de reloj compañeras.
        applicationId = "com.maksimowiczm.foodyou"
        minSdk = 30 // Wear OS 3
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.android.versionCode.get().toInt() * 1000 + foodyouBuild
        versionName =
            libs.versions.version.name.get() + if (foodyouBuild > 0) " ($foodyouBuild)" else ""
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
