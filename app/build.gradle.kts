plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Versão a partir da tag (vX.Y.Z), passada pelo workflow em VERSAO. Localmente: 0.0.1.
val versao: String = (System.getenv("VERSAO") ?: "0.0.1").removePrefix("v")
val partes = Regex("""^(\d+)\.(\d+)\.(\d+)$""").find(versao)?.groupValues
    ?: error("VERSAO tem de ser X.Y.Z (recebido: $versao)")
val codigoVersao = partes[1].toInt() * 10000 + partes[2].toInt() * 100 + partes[3].toInt()

android {
    namespace = "com.fernandoamorim.estudotv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fernandoamorim.estudotv"
        minSdk = 28
        targetSdk = 36
        versionCode = codigoVersao
        versionName = versao
        // As versões 0.x arrancam no Stage (primeiro teste); a 1.0.0 em diante, na Prod.
        buildConfigField("boolean", "STAGE_POR_OMISSAO", (partes[1] == "0").toString())
    }

    signingConfigs {
        create("release") {
            // Só no GitHub Actions: a keystore vem dos segredos do repositório e nunca entra no código.
            val ficheiro = System.getenv("KEYSTORE_FILE")
            if (ficheiro != null) {
                storeFile = file(ficheiro)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("KEYSTORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += setOf("UseKtx", "GradleDependency", "ObsoleteSdkInt")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.webkit:webkit:1.14.0")
    testImplementation("junit:junit:4.13.2")
}
