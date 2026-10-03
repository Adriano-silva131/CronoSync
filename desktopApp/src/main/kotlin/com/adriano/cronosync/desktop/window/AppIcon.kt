package com.adriano.cronosync.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * Ícone do app (janela e bandeja): o desenho "timer" do Material Symbols, branco, sobre um círculo
 * azul. O fundo colorido deixa o ícone visível tanto em barras claras quanto escuras.
 * Feito em código (vetor), sem arquivo de imagem.
 */
@Composable
fun rememberAppIcon(): Painter = rememberVectorPainter(remember { appIconVector() })

internal fun appIconVector(): ImageVector = ImageVector.Builder(
    name = "CronoSync",
    defaultWidth = 64.dp,
    defaultHeight = 64.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // Círculo de fundo.
    path(fill = SolidColor(Color(0xFF3558D6))) {
        moveTo(12f, 0f)
        arcToRelative(12f, 12f, 0f, true, true, 0f, 24f)
        arcToRelative(12f, 12f, 0f, true, true, 0f, -24f)
        close()
    }
    // Desenho "timer" do Material, reduzido para caber dentro do círculo.
    addPath(
        pathData = addPathNodes(
            "M14.25,4.5h-4.5v1.5h4.5v-1.5zM11.25,14.25h1.5v-4.5h-1.5v4.5zM17.27,9.29l1.07,-1.07c-0.32,-0.38 " +
                "-0.68,-0.74 -1.06,-1.06l-1.07,1.07C15.05,7.31 13.59,6.75 12,6.75c-3.73,0 -6.75,3.02 -6.75,6.75s3.02," +
                "6.75 6.75,6.75 6.75,-3.02 6.75,-6.75C18.75,11.91 18.2,10.45 17.27,9.29zM12,18.75c-2.9,0 -5.25,-2.35 " +
                "-5.25,-5.25s2.35,-5.25 5.25,-5.25 5.25,2.35 5.25,5.25S14.9,18.75 12,18.75z",
        ),
        fill = SolidColor(Color.White),
    )
}.build()
