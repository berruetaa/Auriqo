plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.auriqo.music.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The producer has no own ABI/variant dimensions. Keep the default profile generation
        // deterministic and target the same FOSS universal artifact used by local validation.
        missingDimensionStrategy("abi", "universal")
        missingDimensionStrategy("variant", "foss")
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
