package org.driftvane.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.cos
import kotlin.math.sin

/**
 * bitDroid Flow wallpapers: soft color blobs drifting slowly over a deep base, like a moving
 * mesh gradient. A home-screen swipe nudges them, and they bloom a little each time you unlock.
 * Draws at ~30 fps only while visible, so it costs almost nothing in the background.
 */
abstract class FlowWallpaper : WallpaperService() {
    /** Base color and the moving blobs (color, start x/y as screen fractions, radius, speed). */
    abstract val base: Int
    abstract val blobs: List<FlowBlob>

    override fun onCreateEngine(): Engine = FlowEngine()

    private inner class FlowEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val start = SystemClock.uptimeMillis()
        private var visible = false
        private var xOffset = 0.5f
        private var shownX = 0.5f
        private var unlockAt = 0L
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { unlockAt = SystemClock.uptimeMillis() }
        }
        private val frame = object : Runnable {
            override fun run() { draw(); if (visible) handler.postDelayed(this, 33) }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            runCatching { registerReceiver(receiver, IntentFilter(Intent.ACTION_USER_PRESENT)) }
        }
        override fun onDestroy() {
            handler.removeCallbacks(frame)
            runCatching { unregisterReceiver(receiver) }
            super.onDestroy()
        }
        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(frame)
            if (v) handler.post(frame)
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) { visible = false; handler.removeCallbacks(frame); super.onSurfaceDestroyed(holder) }
        // Material You colors come from the wallpaper's own blobs (without this, the system fell
        // back to its default green for the fingerprint icon and the lock screen shortcuts)
        override fun onComputeColors(): android.app.WallpaperColors =
            android.app.WallpaperColors(
                android.graphics.Color.valueOf(blobs[0].color or 0xFF000000.toInt()),
                android.graphics.Color.valueOf(blobs.getOrElse(1) { blobs[0] }.color or 0xFF000000.toInt()),
                android.graphics.Color.valueOf(base),
            )

        override fun onOffsetsChanged(xo: Float, yo: Float, xs: Float, ys: Float, xp: Int, yp: Int) { xOffset = xo }

        private fun draw() {
            val h = surfaceHolder
            val c: Canvas = runCatching { h.lockHardwareCanvas() }.getOrNull() ?: runCatching { h.lockCanvas() }.getOrNull() ?: return
            try {
                val w = c.width.toFloat(); val ht = c.height.toFloat()
                val t = (SystemClock.uptimeMillis() - start) / 1000f
                shownX += (xOffset - shownX) * 0.08f
                val since = (SystemClock.uptimeMillis() - unlockAt) / 1000f
                val bloom = if (unlockAt > 0 && since < 1.6f) sin(since / 1.6f * Math.PI.toFloat()) * 0.18f else 0f
                c.drawColor(base)
                blobs.forEachIndexed { i, b ->
                    val px = (b.x + 0.16f * sin(t * 0.11f * b.speed + i * 1.7f) + (shownX - 0.5f) * 0.12f * (if (i % 2 == 0) 1 else -1)) * w
                    val py = (b.y + 0.12f * cos(t * 0.09f * b.speed + i * 2.3f)) * ht
                    val r = b.r * (1f + 0.12f * sin(t * 0.21f + i) + bloom) * maxOf(w, ht)
                    paint.shader = RadialGradient(px, py, r, intArrayOf(b.color, b.color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                    c.drawCircle(px, py, r, paint)
                }
            } finally {
                runCatching { h.unlockCanvasAndPost(c) }
            }
        }
    }
}

data class FlowBlob(val color: Int, val x: Float, val y: Float, val r: Float, val speed: Float)

/** Twilight: violet, blue and teal over deep navy. The bitDroid default. */
class TwilightFlowWallpaper : FlowWallpaper() {
    override val base = 0xFF0C0F24.toInt()
    override val blobs = listOf(
        FlowBlob(0xCC6A4CFF.toInt(), 0.2f, 0.25f, 0.55f, 1f),
        FlowBlob(0xB3FF6FA8.toInt(), 0.85f, 0.15f, 0.42f, -0.8f),
        FlowBlob(0xB300B8D4.toInt(), 0.65f, 0.8f, 0.55f, 1.2f),
        FlowBlob(0x993D5AFE.toInt(), 0.15f, 0.85f, 0.45f, -1.1f),
    )
}

/** Ember: orange, rose and plum. */
class EmberFlowWallpaper : FlowWallpaper() {
    override val base = 0xFF1A0B14.toInt()
    override val blobs = listOf(
        FlowBlob(0xCCFF7A3D.toInt(), 0.25f, 0.2f, 0.5f, 1f),
        FlowBlob(0xB3FF3D7F.toInt(), 0.8f, 0.45f, 0.48f, -0.9f),
        FlowBlob(0xB37B2CBF.toInt(), 0.3f, 0.85f, 0.55f, 1.1f),
    )
}

/** Ocean: teal, cyan and deep blue. */
class OceanFlowWallpaper : FlowWallpaper() {
    override val base = 0xFF04121F.toInt()
    override val blobs = listOf(
        FlowBlob(0xCC00C2A8.toInt(), 0.2f, 0.3f, 0.5f, 1f),
        FlowBlob(0xB31E88E5.toInt(), 0.8f, 0.2f, 0.45f, -1f),
        FlowBlob(0xB30D47A1.toInt(), 0.6f, 0.85f, 0.6f, 0.9f),
    )
}

/** Mono: graphite and silver, for a clean dark look. */
class MonoFlowWallpaper : FlowWallpaper() {
    override val base = 0xFF0B0B0D.toInt()
    override val blobs = listOf(
        FlowBlob(0x80A0A4B8.toInt(), 0.25f, 0.25f, 0.5f, 1f),
        FlowBlob(0x664A4E63.toInt(), 0.75f, 0.7f, 0.55f, -0.9f),
        FlowBlob(0x59C9CCD9.toInt(), 0.6f, 0.15f, 0.3f, 1.3f),
    )
}
