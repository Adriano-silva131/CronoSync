package com.adriano.cronosync.desktop.integration.window

// Depende do --add-opens sun.awt.X11 em desktopApp/build.gradle.kts; sem ele, falha em silêncio.
fun applyLinuxWindowClass(name: String = System.getProperty("cronosync.linuxWindowClass") ?: "cronosync-CronoSync") {
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return
    runCatching {
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field.set(toolkit, name)
    }
}
