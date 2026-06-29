package com.fogwalk.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.fogwalk.geo.GeoUtils
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/**
 * Draws the "fog of war": a translucent blue layer covering the whole map that
 * is permanently cleared in a soft circle around every place the user has
 * walked.
 *
 * The effect is produced on an offscreen bitmap: we fill it with the fog color,
 * then punch transparent holes at each visited point using a [PorterDuff.Mode.CLEAR]
 * radial gradient (so the edges fade out smoothly), and finally blit the result
 * onto the map canvas.
 */
class FogOverlay(private val mapView: MapView) : Overlay() {

    /** Radius around each visited point that gets cleared, in real-world meters. */
    var revealRadiusMeters: Double = DEFAULT_REVEAL_RADIUS_METERS

    @Volatile
    private var points: List<GeoPoint> = emptyList()

    private val fogPaint = Paint().apply { color = FOG_COLOR }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private var buffer: Bitmap? = null
    private var bufferCanvas: Canvas? = null
    private val reuse = Point()

    /** Replace the set of revealed points and request a redraw. */
    fun setPoints(newPoints: List<GeoPoint>) {
        points = newPoints
        mapView.postInvalidate()
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        val width = mapView.width
        val height = mapView.height
        if (width <= 0 || height <= 0) return

        var bmp = buffer
        if (bmp == null || bmp.width != width || bmp.height != height) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            buffer = bmp
            bufferCanvas = Canvas(bmp)
        }
        val offscreen = bufferCanvas ?: return

        // Reset and lay down the fog.
        offscreen.drawColor(0, PorterDuff.Mode.CLEAR)
        offscreen.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fogPaint)

        val zoom = mapView.zoomLevelDouble
        val bounds = projection.boundingBox.increaseByScale(1.3f)
        val snapshot = points

        for (gp in snapshot) {
            if (!bounds.contains(gp.latitude, gp.longitude)) continue
            projection.toPixels(gp, reuse)
            val radiusPx = GeoUtils
                .metersToPixels(revealRadiusMeters, gp.latitude, zoom)
                .toFloat()
            if (radiusPx <= 0.5f) continue

            clearPaint.shader = RadialGradient(
                reuse.x.toFloat(),
                reuse.y.toFloat(),
                radiusPx,
                intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, SOFT_EDGE_START, 1f),
                Shader.TileMode.CLAMP,
            )
            offscreen.drawCircle(reuse.x.toFloat(), reuse.y.toFloat(), radiusPx, clearPaint)
        }

        canvas.drawBitmap(bmp, 0f, 0f, null)
    }

    override fun onDetach(mapView: MapView?) {
        buffer?.recycle()
        buffer = null
        bufferCanvas = null
        super.onDetach(mapView)
    }

    companion object {
        const val DEFAULT_REVEAL_RADIUS_METERS: Double = 45.0

        /** Translucent deep-blue fog (~80% opaque). */
        private const val FOG_COLOR: Int = 0xCC0E2233.toInt()

        /** Fraction of the radius that stays fully cleared before fading out. */
        private const val SOFT_EDGE_START: Float = 0.65f
    }
}
