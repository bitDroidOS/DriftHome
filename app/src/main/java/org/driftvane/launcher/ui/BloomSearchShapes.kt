package org.driftvane.launcher.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** bitDroid Bloom: a cookie with [lobes] soft scallops. */
class BloomCookieShape(private val lobes: Int, private val depth: Float = 0.08f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        val r = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = lobes * 20
        for (i in 0..steps) {
            val t = i.toFloat() / steps * 2f * PI.toFloat()
            val rr = r * (1f + depth * cos(lobes * t)) / (1f + depth)
            val x = cx + rr * cos(t)
            val y = cy + rr * sin(t)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

/** bitDroid Bloom: a pill whose whole edge is softly scalloped, like a stretched cookie. */
class BloomPillShape(private val waveDp: Float = 14f, private val ampDp: Float = 2.2f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        val h = size.height
        val w = size.width
        val r = h / 2f
        val st = max(0f, w - h)
        val arc = PI.toFloat() * r
        val per = 2 * st + 2 * arc
        val waves = max(6, (per / (waveDp * density.density)).roundToInt())
        val amp = ampDp * density.density
        val n = waves * 14
        for (i in 0..n) {
            val s = i.toFloat() / n * per
            var x: Float; var y: Float; val nx: Float; val ny: Float
            when {
                s < st -> { x = r + s; y = 0f; nx = 0f; ny = -1f }
                s < st + arc -> {
                    val a = -PI.toFloat() / 2 + (s - st) / r
                    nx = cos(a); ny = sin(a); x = w - r + r * nx; y = r + r * ny
                }
                s < 2 * st + arc -> { x = w - r - (s - st - arc); y = h; nx = 0f; ny = 1f }
                else -> {
                    val a = PI.toFloat() / 2 + (s - 2 * st - arc) / r
                    nx = cos(a); ny = sin(a); x = r + r * nx; y = r + r * ny
                }
            }
            val o = amp * (cos(2 * PI.toFloat() * waves * i / n) - 1f) / 2f
            x += nx * o; y += ny * o
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}
