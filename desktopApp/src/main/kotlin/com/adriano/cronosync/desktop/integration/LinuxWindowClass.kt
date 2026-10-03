package com.adriano.cronosync.desktop.integration

/**
 * Linux: dá à janela o "nome de classe" (WM_CLASS) do atalho instalado pelo .deb/.rpm.
 *
 * Por padrão a janela do Java se apresenta ao sistema como "java-lang-Thread". O GNOME usa esse
 * nome para ligar a janela ao atalho do menu (cronosync-CronoSync.desktop, ou o da homologação);
 * sem bater, a dock mostra o app como um programa genérico, separado do ícone fixado. O build
 * informa o nome certo em -Dcronosync.linuxWindowClass.
 *
 * O Java não tem API pública para isso, então ajustamos o campo interno do toolkit X11 por
 * reflexão (o --add-opens no build.gradle.kts libera o acesso). Se falhar, nada quebra: só a dock
 * fica genérica.
 */
fun applyLinuxWindowClass(name: String = System.getProperty("cronosync.linuxWindowClass") ?: "cronosync-CronoSync") {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return
    runCatching {
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field.set(toolkit, name)
    }
}
