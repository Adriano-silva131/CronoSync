package com.adriano.cronosync.desktop.integration.tray.statusnotifier

import com.adriano.cronosync.desktop.integration.tray.TRAY_ICON_RESOURCE
import com.adriano.cronosync.desktop.integration.tray.statusnotifier.protocol.Pixmap
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

internal fun loadTrayIconPixmaps(): List<Pixmap> {
    val source = Pixmap::class.java.getResourceAsStream(TRAY_ICON_RESOURCE)?.use(ImageIO::read)
        ?: error("ícone da bandeja não encontrado")
    return listOf(16, 22, 24, 32, 48, 64).map { size -> scaled(source, size).toPixmap() }
}

private fun scaled(source: BufferedImage, size: Int): BufferedImage {
    var current = source
    while (current.width / 2 >= size) current = resize(current, current.width / 2)
    return if (current.width == size) current else resize(current, size)
}

private fun resize(image: BufferedImage, size: Int) =
    BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).apply {
        createGraphics().run {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(image, 0, 0, size, size, null)
            dispose()
        }
    }

private fun BufferedImage.toPixmap(): Pixmap {
    val bytes = ByteArray(width * height * 4)
    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val argb = getRGB(x, y)
            bytes[i++] = (argb ushr 24).toByte()
            bytes[i++] = (argb ushr 16).toByte()
            bytes[i++] = (argb ushr 8).toByte()
            bytes[i++] = argb.toByte()
        }
    }
    return Pixmap(width, height, bytes)
}
