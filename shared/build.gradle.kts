import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Alvo Android: gera uma biblioteca Android consumida pelo :androidApp.
    android {
        namespace = "com.adriano.cronosync.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Roda os testes de commonTest também como testes unitários Android (na JVM, sem emulador).
        withHostTest {}
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Alvo JVM: usado pelo servidor Ktor (:server) e, depois, pelo app desktop (Windows/Linux).
    // Também é onde os testes de commonTest rodam mais rápido (`./gradlew :shared:jvmTest`).
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Web (wasmJs/js) entra aqui depois — o código de commonMain já não depende de nada específico.

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.androidx.lifecycle.viewmodel)
            // api: a versão do Koin (definida pelo BOM) precisa chegar a quem usa o shared (app e servidor).
            api(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            api(libs.koin.core.viewmodel)
            api(libs.multiplatform.settings)
            // Cliente WebSocket. O "motor" de rede (OkHttp no Android, CIO/OkHttp no desktop...) é escolhido por cada app.
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.multiplatform.settings.test)
        }
        jvmTest.dependencies {
            implementation(libs.koin.test)
        }
    }
}
