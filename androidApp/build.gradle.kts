import java.net.Inet4Address
import java.net.NetworkInterface

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * IP deste computador na rede local (ex.: 192.168.0.102), para a versão de testes do celular
 * alcançar o servidor pelo Wi-Fi, sem cabo.
 *
 * Ignora interfaces que o celular não alcança: virtuais (Docker, VMs) e de VPN — uma VPN ligada
 * cria uma interface (ex.: tun0 com 10.x.x.x) que só existe dentro do túnel. Entre as que sobram,
 * prefere as físicas: cabo (eno/enp/eth) e Wi-Fi (wl).
 *
 * É um ValueSource para o configuration cache do Gradle recalcular a cada build: se o roteador
 * der outro IP ao computador, o próximo build já grava o novo.
 */
abstract class LocalNetworkAddress : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String? {
        val ignored = listOf(
            "docker", "br-", "virbr", "veth", "vmnet", "vbox", // contêineres e máquinas virtuais
            "tun", "tap", "wg", "ppp", "tailscale", "zt", "utun", "ipsec", // VPNs
        )
        val physical = listOf("eno", "enp", "eth", "wl", "en")
        return NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .filterNot { iface -> ignored.any { iface.name.startsWith(it) } }
            .sortedBy { iface -> if (physical.any { iface.name.startsWith(it) }) 0 else 1 }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }
}

// Pode ser forçado com: ./gradlew ... -Pcronosync.localServer=192.168.0.50:8080
val localTestServer: String = providers.gradleProperty("cronosync.localServer")
    .orElse(providers.of(LocalNetworkAddress::class) {}.map { "$it:8080" })
    .getOrElse("localhost:8080")

android {
    namespace = "com.adriano.cronosync"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.adriano.cronosync"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.0.2"
    }

    buildTypes {
        // ENVIRONMENT: "local", "homologacao" ou "production" (ver AppEnvironment e AndroidModule).
        // Versão de testes: conecta sempre no servidor deste computador, pelo Wi-Fi.
        debug {
            buildConfigField("String", "ENVIRONMENT", "\"local\"")
            buildConfigField("String", "LOCAL_SERVER", "\"$localTestServer\"")
        }
        // Homologação: conecta sempre no servidor de testes publicado (homologacao.focussync.com.br).
        //   ./gradlew :androidApp:assembleHomologacao
        // Outro applicationId (".homologacao"): instala AO LADO da versão de testes, sem substituí-la.
        // Nome na tela: src/homologacao/res/values/strings.xml.
        create("homologacao") {
            // Mesma assinatura (chave de debug) da versão de testes: instala direto, sem keystore de release.
            initWith(getByName("debug"))
            applicationIdSuffix = ".homologacao"
            versionNameSuffix = "-homologacao"
            buildConfigField("String", "ENVIRONMENT", "\"homologacao\"")
            buildConfigField("String", "LOCAL_SERVER", "\"\"")
        }
        release {
            buildConfigField("String", "ENVIRONMENT", "\"production\"")
            buildConfigField("String", "LOCAL_SERVER", "\"\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":sharedUi"))
    // Motor de rede do cliente Ktor no Android.
    implementation(libs.ktor.client.okhttp)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
