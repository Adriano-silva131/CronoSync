import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Telas compartilhadas (e, por tabela, o :shared com domínio, sincronização e ViewModels).
    implementation(project(":sharedUi"))
    // Compose Desktop para o sistema em que o build roda (Linux ou Windows).
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    // Dispatchers.Main no desktop = thread de interface do Swing (onde o Compose Desktop desenha).
    implementation(libs.kotlinx.coroutines.swing)
    // Motor de rede do cliente Ktor no desktop.
    implementation(libs.ktor.client.cio)
    // Bandeja no Linux pelo protocolo moderno (StatusNotifierItem via D-Bus): transparência e menu nativos.
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport.unixsocket)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.multiplatform.settings.test)
}

/*
 * Ambiente do app desktop:
 * - "local" (padrão nesta fase) = versão de testes, presa ao servidor em localhost:8080;
 * - "homologacao" = presa ao servidor de homologação (wss://homologacao.focussync.com.br):
 *     ./gradlew :desktopApp:run -Pcronosync.environment=homologacao
 * - qualquer outro valor = versão final, com o campo "Servidor" editável.
 * Vale para o `run` E para os instaladores. Quando houver servidor publicado, gere os pacotes com
 * `-Pcronosync.environment=production`.
 */
val cronosyncEnvironment: String = providers.gradleProperty("cronosync.environment").getOrElse("local")

/*
 * Identidade do pacote. A homologação ganha nome, pasta e atalho próprios para instalar AO LADO da
 * versão normal, em vez de substituí-la (as configurações salvas também são separadas; ver
 * DesktopModule).
 */
val isStaging = cronosyncEnvironment == "homologacao"
val appPackageName = if (isStaging) "CronoSync-Homolog" else "CronoSync"
val linuxPackageName = if (isStaging) "cronosync-homologacao" else "cronosync"

compose.desktop {
    application {
        mainClass = "com.adriano.cronosync.desktop.app.MainKt"
        // JDK dos instaladores (e do `run`): Temurin, que sempre traz o jpackage. Sem isso valeria o
        // Java do próprio Gradle — que pode ser o do Android Studio, sem jpackage. Se não houver um
        // Temurin 17 na máquina, o Gradle baixa sozinho (plugin foojay no settings.gradle.kts).
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(17)
            vendor = JvmVendorSpec.ADOPTIUM
        }.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += "-Dcronosync.environment=$cronosyncEnvironment"
        // Nome do atalho que o .deb/.rpm instala (<pacote linux>-<nome>.desktop): ver LinuxWindowClass.kt.
        jvmArgs += "-Dcronosync.linuxWindowClass=$linuxPackageName-$appPackageName"
        // Linux: libera o ajuste do "nome da janela" (ver LinuxWindowClass.kt). Só faz sentido no
        // pacote Linux; o jpackage gera cada formato no próprio sistema, então dá para decidir aqui.
        if (System.getProperty("os.name").lowercase().contains("linux")) {
            jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
        }

        /*
         * Instaladores com o Java embutido (jpackage): quem instala não precisa ter Java.
         * O jpackage só gera o formato do sistema em que roda: .deb/.rpm no Linux, .msi/.exe no
         * Windows (este último via CI, quando o repositório existir).
         *   ./gradlew :desktopApp:packageDeb   → desktopApp/build/compose/binaries/main/deb/
         */
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Exe)
            packageName = appPackageName
            packageVersion = "1.0.2"
            description = "Cronômetro, timer e Pomodoro sincronizados entre aparelhos"
            vendor = "Adriano"
            // Módulos do Java incluídos no pacote (o mínimo que o app usa, para o instalador ficar menor).
            // jdk.security.auth: a bandeja do Linux (D-Bus) se identifica ao barramento pelo usuário do sistema.
            modules("java.instrument", "java.management", "java.prefs", "jdk.security.auth", "jdk.unsupported")

            linux {
                packageName = linuxPackageName
                iconFile.set(project.file("packaging/icons/cronosync.png"))
                appCategory = "Utility"
                menuGroup = "Utility"
                shortcut = true
                // Só o e-mail: o Compose já monta "vendor <e-mail>".
                debMaintainer = "adrianosilva6662@gmail.com"
            }
            windows {
                iconFile.set(project.file("packaging/icons/cronosync.ico"))
                menu = true
                menuGroup = appPackageName
                shortcut = true
                // Instala na pasta do usuário: não pede permissão de administrador.
                perUserInstall = true
                dirChooser = true
                // Identidade fixa do produto: é o que faz uma versão nova SUBSTITUIR a antiga ao instalar.
                // A homologação tem a sua: assim ela não substitui a versão normal.
                upgradeUuid = if (isStaging) "c6d3e10e-e6b8-4fd9-8882-145aa2dbead7" else "c2695b46-62b0-4a88-8e13-3609cadbc7d0"
            }
        }
    }
}
