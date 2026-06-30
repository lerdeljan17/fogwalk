package com.fogwalk.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.fogwalk.geo.GeoUtils
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/**
 * Draws the "fog of war": a translucent blue layer covering the whole map that
 * is permanently cleared along a narrow, continuous trail wherever the user has
 * walked — like the route trace in a running tracker (Strava etc.).
 *
 * The effect is produced on an offscreen bitmap: we fill it with the fog color,
 * then clear a thick rounded poly-line that connects consecutive visited points
 * (plus a round dot at each vertex) using [PorterDuff.Mode.CLEAR], and finally
 * blit the result onto the map canvas. Connecting along the path — rather than
 * punching a disc at each isolated point — keeps the cleared trail continuous
 * even when GPS samples are a few meters apart, while staying only about a
 * street/path wide.
 */
class FogOverlay(private val mapView: MapView) : Overlay() {

    /**
     * Radius of the cleared trail in real-world meters. The trail is drawn as a
     * stroke of width `2 * radius`, so this controls roughly half the trail
     * width (≈10 m wide at the default 5 m radius). Tweak this single constant
     * to make the trail wider or narrower.
     */
    var revealRadiusMeters: Double = DEFAULT_REVEAL_RADIUS_METERS

    /**
     * Maximum real-world distance between two consecutive points that we will
     * bridge with a connecting segment. Larger jumps (GPS dropouts, or the app
     * reopened somewhere else entirely) are left unconnected so we don't draw a
     * long false trail across never-walked ground; the endpoints still get their
     * round dots.
     */
    var maxConnectDistanceMeters: Double = DEFAULT_MAX_CONNECT_DISTANCE_METERS

    @Volatile
    private var points: List<GeoPoint> = emptyList()

    private val fogPaint = Paint().apply { color = FOG_COLOR }

    // Solid CLEAR stroke with round caps/joins: clearing along the path with
    // rounded ends gives a smooth, continuous running-app style trace.
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var buffer: Bitmap? = null
    private var bufferCanvas: Canvas? = null
    private val reuse = Point()
    private val reusePrev = Point()

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

        // Points arrive time-ordered (oldest first). Walk consecutive pairs:
        // bridge them with a thick rounded line to form a continuous trail, and
        // stamp a round dot at every vertex so isolated points and trail ends
        // stay round.
        for (i in snapshot.indices) {
            val cur = snapshot[i]
            val curInView = bounds.contains(cur.latitude, cur.longitude)

            val radiusPx = GeoUtils
                .metersToPixels(revealRadiusMeters, cur.latitude, zoom)
                .toFloat()
            if (radiusPx <= 0.5f) continue

            if (i > 0) {
                val prev = snapshot[i - 1]
                val prevInView = bounds.contains(prev.latitude, prev.longitude)
                // Only bother projecting/drawing a segment when at least one end
                // is on (or near) screen, and the two points are close enough to
                // really be the same continuous walk.
                if ((curInView || prevInView) &&
                    GeoUtils.isWithinConnectDistance(
                        prev.latitude,
                        prev.longitude,
                        cur.latitude,
                        cur.longitude,
                        maxConnectDistanceMeters,
                    )
                ) {
                    projection.toPixels(prev, reusePrev)
                    projection.toPixels(cur, reuse)
                    clearPaint.strokeWidth = radiusPx * 2f
                    offscreen.drawLine(
                        reusePrev.x.toFloat(),
                        reusePrev.y.toFloat(),
                        reuse.x.toFloat(),
                        reuse.y.toFloat(),
                        clearPaint,
                    )
                }
            }

            if (curInView) {
                projection.toPixels(cur, reuse)
                offscreen.drawCircle(reuse.x.toFloat(), reuse.y.toFloat(), radiusPx, clearPaint)
            }
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
        /**
         * Half-width of the cleared trail in meters. At 5 m the trail is ≈10 m
         * wide — about a street/path wide, matching a running-app route trace.
         */
        const val DEFAULT_REVEAL_RADIUS_METERS: Double = 5.0

        /**
         * Consecutive points farther apart than this (meters) are treated as a
         * gap and not connected, so GPS dropouts don't paint a false trail.
         */
        const val DEFAULT_MAX_CONNECT_DISTANCE_METERS: Double = 40.0

        /** Translucent deep-blue fog (~80% opaque). */
        private const val FOG_COLOR: Int = 0xCC0E2233.toInt()
    }
}
