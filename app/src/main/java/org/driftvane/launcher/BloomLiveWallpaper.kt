package org.driftvane.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * bitDroid "Living Bloom": Midnight Clover, alive. Clovers drift and turn, the background glows
 * breathe, home screen swipes nudge them sideways, the clovers bloom open each time you unlock,
 * and everything breathes faster while charging. Draws at ~30 fps only while visible.
 */
/** One drifting clover of the living wallpaper (top level: inner classes can't hold classes). */
private data class Clover(val x: Float, val y: Float, val r: Float, val color: Int, val turn: Float, val speed: Float)

class BloomLiveWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = BloomEngine()

    private inner class BloomEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var xOffset = 0.5f
        private var charging = false
        private var unlockAt = 0L
        private val start = SystemClock.uptimeMillis()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val soft = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        private val clovers = listOf(
            Clover(0.29f, 0.29f, 0.28f, 0xFF9A82DB.toInt(), 0.3f, 1f),
            Clover(0.76f, 0.73f, 0.31f, 0xFFD97FB3.toInt(), 0.9f, -0.8f),
            Clover(0.78f, 0.22f, 0.10f, 0xFF7FD3C2.toInt(), 0.1f, 1.6f),
            Clover(0.19f, 0.69f, 0.12f, 0xFFB69DF8.toInt(), 0.6f, -1.3f),
            Clover(0.56f, 0.50f, 0.075f, 0xFFFFD08A.toInt(), 0.2f, 2f),
            Clover(0.24f, 0.91f, 0.085f, 0xFF9A82DB.toInt(), 0.4f, -1.7f),
        )

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_USER_PRESENT -> unlockAt = SystemClock.uptimeMillis()
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                        charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 &&
                            (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
                    }
                }
            }
        }

        private val frame = object : Runnable {
            override fun run() {
                draw()
                if (visible) handler.postDelayed(this, 33)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            val f = IntentFilter(Intent.ACTION_USER_PRESENT).apply { addAction(Intent.ACTION_BATTERY_CHANGED) }
            runCatching { registerReceiver(receiver, f, Context.RECEIVER_EXPORTED) }
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            runCatching { unregisterReceiver(receiver) }
            super.onDestroy()
        }

        // the Material You palette comes from these (the same lilac/pink as Midnight Clover)
        override fun onComputeColors(): android.app.WallpaperColors =
            android.app.WallpaperColors(
                Color.valueOf(0xFF9A82DB.toInt()), Color.valueOf(0xFFD97FB3.toInt()), Color.valueOf(0xFF1B1838.toInt()),
            )

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(frame)
            if (v) handler.post(frame)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(xOff: Float, yOff: Float, xStep: Float, yStep: Float, xPx: Int, yPx: Int) {
            xOffset = xOff
            if (!visible) draw()
        }

        private fun clover(cx: Float, cy: Float, r: Float, turn: Float): Path {
            path.reset()
            val lr = r * 0.5f
            for (k in 0 until 4) {
                val a = turn + k * PI.toFloat() / 2
                path.addCircle(cx + lr * cos(a), cy + lr * sin(a), lr, Path.Direction.CW)
            }
            path.addCircle(cx, cy, lr, Path.Direction.CW)
            return path
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            val c: Canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                val w = c.width.toFloat()
                val h = c.height.toFloat()
                val t = (SystemClock.uptimeMillis() - start) / 1000f
                val breath = if (charging) 1.6f else 0.55f
                // the unlock bloom: a quick open-and-settle
                val u = (SystemClock.uptimeMillis() - unlockAt) / 1000f
                val bloom = if (unlockAt > 0 && u < 1.6f) sin(min(1f, u / 0.35f) * PI.toFloat() / 2) * exp(-u * 2.2f) else 0f

                paint.shader = LinearGradient(0f, 0f, w, h, 0xFF1B1838.toInt(), 0xFF0D0B1A.toInt(), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, paint)
                paint.shader = null
                // breathing glows
                val g1 = 0.40f + 0.12f * sin(t * breath)
                val g2 = 0.32f + 0.10f * sin(t * breath * 0.8f + 1.3f)
                soft.shader = RadialGradient(w * 0.28f, h * 0.29f, w * 0.75f,
                    intArrayOf(withAlpha(0xFF6750A4.toInt(), g1), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, soft)
                soft.shader = RadialGradient(w * 0.76f, h * 0.73f, w * 0.8f,
                    intArrayOf(withAlpha(0xFF8E3F7A.toInt(), g2), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, soft)
                soft.shader = null

                val par = (xOffset - 0.5f) * w * 0.12f
                for ((i, cl) in clovers.withIndex()) {
                    val depth = 0.4f + cl.r * 2f
                    val dx = sin(t * 0.13f * cl.speed + i) * w * 0.02f - par * depth
                    val dy = cos(t * 0.11f * cl.speed + i * 2) * h * 0.012f
                    val cx = cl.x * w + dx
                    val cy = cl.y * h + dy
                    val r = cl.r * w * (1f + 0.25f * bloom + 0.02f * sin(t * breath + i))
                    val turn = cl.turn + t * 0.08f * cl.speed + bloom * 0.8f
                    paint.color = cl.color
                    paint.alpha = 70
                    paint.maskFilter = BlurMaskFilter(max(1f, r * 0.12f), BlurMaskFilter.Blur.NORMAL)
                    c.drawPath(clover(cx, cy, r * 1.08f, turn), paint)
                    paint.maskFilter = null
                    paint.alpha = 255
                    c.drawPath(clover(cx, cy, r, turn), paint)
                    paint.color = 0xFF1B1838.toInt()
                    paint.alpha = 140
                    c.drawCircle(cx, cy, r * 0.18f * (1f - 0.5f * bloom), paint)
                    paint.alpha = 255
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(c) }
            }
        }

        private fun withAlpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
    }
}
