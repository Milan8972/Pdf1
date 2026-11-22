package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.DarkLogoBadgeBg
import com.example.ui.theme.LocalPaperPilotColors
import com.example.ui.theme.SpaceGroteskFontFamily

enum class LogoVariant { BADGE, MARK, FULL }

@Composable
fun PaperPilotLogo(
    size: Dp = 32.dp,
    variant: LogoVariant = LogoVariant.FULL,
    modifier: Modifier = Modifier
) {
    val isDark = LocalPaperPilotColors.current.isDark
    val wordColor = MaterialTheme.colorScheme.onBackground

    when (variant) {
        LogoVariant.BADGE -> PaperPilotBadgeCanvas(size = size, isDark = isDark, modifier = modifier)
        LogoVariant.MARK -> PaperPilotMarkCanvas(size = size, isDark = isDark, modifier = modifier)
        LogoVariant.FULL -> {
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PaperPilotBadgeCanvas(size = size, isDark = isDark)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = wordColor)) { append("paper") }
                        withStyle(SpanStyle(color = BrandCoral)) { append("pilot") }
                    },
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.66f).coerceIn(18f, 34f).sp,
                    letterSpacing = (-0.4).sp
                )
            }
        }
    }
}

@Composable
private fun PaperPilotBadgeCanvas(
    size: Dp,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val corner = w * 0.28f
        val gradient = Brush.linearGradient(
            colors = listOf(BrandCoral, BrandAmber),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        if (isDark) {
            drawRoundRect(
                color = DarkLogoBadgeBg,
                cornerRadius = CornerRadius(corner, corner)
            )
        } else {
            drawRoundRect(
                brush = gradient,
                cornerRadius = CornerRadius(corner, corner)
            )
        }

        // Two clean geometric triangles forming a folded paper-plane glyph
        val upperWing = Path().apply {
            moveTo(w * 0.21f, h * 0.51f)
            lineTo(w * 0.80f, h * 0.22f)
            lineTo(w * 0.45f, h * 0.59f)
            close()
        }
        val lowerWing = Path().apply {
            moveTo(w * 0.50f, h * 0.63f)
            lineTo(w * 0.80f, h * 0.26f)
            lineTo(w * 0.64f, h * 0.79f)
            close()
        }

        if (isDark) {
            drawPath(upperWing, brush = gradient)
            drawPath(lowerWing, brush = gradient, alpha = 0.88f)
        } else {
            drawPath(upperWing, color = Color.White)
            drawPath(lowerWing, color = Color.White.copy(alpha = 0.88f))
        }
    }
}

@Composable
private fun PaperPilotMarkCanvas(
    size: Dp,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val gradient = Brush.linearGradient(
            colors = listOf(BrandCoral, BrandAmber),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        val upperWing = Path().apply {
            moveTo(w * 0.08f, h * 0.52f)
            lineTo(w * 0.92f, h * 0.14f)
            lineTo(w * 0.42f, h * 0.62f)
            close()
        }
        val lowerWing = Path().apply {
            moveTo(w * 0.48f, h * 0.67f)
            lineTo(w * 0.92f, h * 0.19f)
            lineTo(w * 0.68f, h * 0.88f)
            close()
        }
        drawPath(upperWing, brush = gradient)
        drawPath(lowerWing, brush = gradient, alpha = if (isDark) 0.9f else 0.82f)
    }
}
