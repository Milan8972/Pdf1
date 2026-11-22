package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.TintEditSign
import com.example.ui.theme.TintOptimize
import com.example.ui.theme.TintScan

@Composable
fun OnboardingIllustration(pageIndex: Int, size: Dp = 220.dp, modifier: Modifier = Modifier) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Soft organic backdrop halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    when (pageIndex) {
                        0 -> BrandCoral.copy(alpha = 0.22f)
                        1 -> TintScan.copy(alpha = 0.22f)
                        2 -> TintEditSign.copy(alpha = 0.22f)
                        else -> TintOptimize.copy(alpha = 0.22f)
                    },
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.52f),
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = Offset(w * 0.5f, h * 0.52f)
        )

        when (pageIndex) {
            0 -> {
                // Stacked modular PDF tool cards + floating paper plane
                drawRoundRect(
                    color = surfaceColor,
                    topLeft = Offset(w * 0.14f, h * 0.22f),
                    size = Size(w * 0.46f, h * 0.56f),
                    cornerRadius = CornerRadius(24f, 24f)
                )
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(w * 0.14f, h * 0.22f),
                    size = Size(w * 0.46f, h * 0.56f),
                    cornerRadius = CornerRadius(24f, 24f),
                    style = Stroke(width = 3f)
                )
                // Document lines
                for (i in 0..3) {
                    drawRoundRect(
                        color = if (i == 0) BrandCoral else outlineColor,
                        topLeft = Offset(w * 0.21f, h * (0.31f + i * 0.09f)),
                        size = Size(w * (if (i == 0) 0.22f else 0.30f), h * 0.032f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
                // Floating category tiles on right
                val tileColors = listOf(BrandCoral, BrandAmber, TintScan, TintEditSign)
                tileColors.forEachIndexed { idx, tint ->
                    val cx = w * (0.64f + (idx % 2) * 0.17f)
                    val cy = h * (0.28f + (idx / 2) * 0.20f)
                    drawRoundRect(
                        color = tint.copy(alpha = 0.18f),
                        topLeft = Offset(cx - w * 0.07f, cy - h * 0.07f),
                        size = Size(w * 0.14f, h * 0.14f),
                        cornerRadius = CornerRadius(18f, 18f)
                    )
                    drawCircle(color = tint, radius = w * 0.032f, center = Offset(cx, cy))
                }
            }

            1 -> {
                // Document scanner viewfinder with corner brackets and glowing teal scan bar
                drawRoundRect(
                    color = surfaceColor,
                    topLeft = Offset(w * 0.24f, h * 0.14f),
                    size = Size(w * 0.52f, h * 0.72f),
                    cornerRadius = CornerRadius(28f, 28f)
                )
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(w * 0.24f, h * 0.14f),
                    size = Size(w * 0.52f, h * 0.72f),
                    cornerRadius = CornerRadius(28f, 28f),
                    style = Stroke(width = 3.5f)
                )
                // Document lines inside
                for (i in 0..4) {
                    drawRoundRect(
                        color = outlineColor.copy(alpha = 0.9f),
                        topLeft = Offset(w * 0.32f, h * (0.25f + i * 0.09f)),
                        size = Size(w * (if (i == 4) 0.22f else 0.36f), h * 0.028f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
                // Teal scan laser bar
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, TintScan.copy(alpha = 0.32f), TintScan)
                    ),
                    topLeft = Offset(w * 0.20f, h * 0.42f),
                    size = Size(w * 0.60f, h * 0.12f)
                )
                drawLine(
                    color = TintScan,
                    start = Offset(w * 0.18f, h * 0.54f),
                    end = Offset(w * 0.82f, h * 0.54f),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
                // Corner crop dots
                listOf(
                    Offset(w * 0.24f, h * 0.14f),
                    Offset(w * 0.76f, h * 0.14f),
                    Offset(w * 0.24f, h * 0.86f),
                    Offset(w * 0.76f, h * 0.86f)
                ).forEach { pt ->
                    drawCircle(color = BrandCoral, radius = 10f, center = pt)
                }
            }

            2 -> {
                // Sheet with yellow highlight, violet edit pen, and signature flourish
                drawRoundRect(
                    color = surfaceColor,
                    topLeft = Offset(w * 0.20f, h * 0.16f),
                    size = Size(w * 0.60f, h * 0.68f),
                    cornerRadius = CornerRadius(26f, 26f)
                )
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(w * 0.20f, h * 0.16f),
                    size = Size(w * 0.60f, h * 0.68f),
                    cornerRadius = CornerRadius(26f, 26f),
                    style = Stroke(width = 3f)
                )
                // Highlight strip
                drawRoundRect(
                    color = BrandAmber.copy(alpha = 0.45f),
                    topLeft = Offset(w * 0.28f, h * 0.30f),
                    size = Size(w * 0.44f, h * 0.08f),
                    cornerRadius = CornerRadius(10f, 10f)
                )
                // Underline accent
                drawLine(
                    color = BrandCoral,
                    start = Offset(w * 0.28f, h * 0.48f),
                    end = Offset(w * 0.66f, h * 0.48f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                // Flowing signature curve
                val sigPath = Path().apply {
                    moveTo(w * 0.30f, h * 0.68f)
                    cubicTo(w * 0.38f, h * 0.56f, w * 0.45f, h * 0.76f, w * 0.54f, h * 0.62f)
                    cubicTo(w * 0.60f, h * 0.55f, w * 0.66f, h * 0.72f, w * 0.72f, h * 0.65f)
                }
                drawPath(sigPath, color = TintEditSign, style = Stroke(width = 6f, cap = StrokeCap.Round))
            }

            else -> {
                // Shield + Local Device Privacy Lock
                val shield = Path().apply {
                    moveTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.80f, h * 0.26f)
                    lineTo(w * 0.80f, h * 0.52f)
                    cubicTo(w * 0.80f, h * 0.72f, w * 0.64f, h * 0.82f, w * 0.50f, h * 0.88f)
                    cubicTo(w * 0.36f, h * 0.82f, w * 0.20f, h * 0.72f, w * 0.20f, h * 0.52f)
                    lineTo(w * 0.20f, h * 0.26f)
                    close()
                }
                drawPath(
                    shield,
                    brush = Brush.linearGradient(listOf(TintScan, TintOptimize))
                )
                // Crisp white checkmark inside shield
                val check = Path().apply {
                    moveTo(w * 0.38f, h * 0.52f)
                    lineTo(w * 0.47f, h * 0.61f)
                    lineTo(w * 0.64f, h * 0.41f)
                }
                drawPath(check, color = Color.White, style = Stroke(width = 12f, cap = StrokeCap.Round))
                // Small accent dot
                drawCircle(color = BrandAmber, radius = 9f, center = Offset(w * 0.82f, h * 0.20f))
                drawCircle(color = BrandCoral, radius = 7f, center = Offset(w * 0.18f, h * 0.74f))
                // Suppress unused warning
                textColor.alpha
            }
        }
    }
}

@Composable
fun EmptyFolderIllustration(size: Dp = 148.dp, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(
            color = BrandCoral.copy(alpha = 0.10f),
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.54f)
        )
        // Folder back
        drawRoundRect(
            color = BrandAmber.copy(alpha = 0.28f),
            topLeft = Offset(w * 0.18f, h * 0.30f),
            size = Size(w * 0.64f, h * 0.46f),
            cornerRadius = CornerRadius(22f, 22f)
        )
        // Tilted paper inside
        drawRoundRect(
            color = surface,
            topLeft = Offset(w * 0.27f, h * 0.20f),
            size = Size(w * 0.46f, h * 0.42f),
            cornerRadius = CornerRadius(14f, 14f)
        )
        drawRoundRect(
            color = outline,
            topLeft = Offset(w * 0.27f, h * 0.20f),
            size = Size(w * 0.46f, h * 0.42f),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(2.5f)
        )
        // Folder front
        drawRoundRect(
            brush = Brush.linearGradient(listOf(BrandCoral.copy(alpha = 0.85f), BrandAmber.copy(alpha = 0.85f))),
            topLeft = Offset(w * 0.16f, h * 0.44f),
            size = Size(w * 0.68f, h * 0.34f),
            cornerRadius = CornerRadius(22f, 22f)
        )
    }
}
