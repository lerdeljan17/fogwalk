package com.fogwalk.data

import com.fogwalk.geo.GeoUtils
import kotlinx.coroutines.flow.Flow

/**
 * Mediates access to visited points and enforces the de-duplication rule so we
 * don't store dozens of near-identical points when the user is barely moving.
 */
class VisitedRepository(
    private val dao: VisitedPointDao,
    private val minDistanceMeters: Double = DEFAULT_MIN_DISTANCE_METERS,
) {

    fun observeAll(): Flow<List<VisitedPoint>> = dao.observeAll()

    suspend fun getAll(): List<VisitedPoint> = dao.getAll()

    suspend fun count(): Int = dao.count()

    suspend fun clear() = dao.clear()

    suspend fun getInBounds(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<VisitedPoint> = dao.getInBounds(minLat, maxLat, minLon, maxLon)

    /**
     * Records a location only if it is far enough from the most recent point.
     * Returns the stored [VisitedPoint] when inserted, or null when skipped as
     * a duplicate.
     */
    suspend fun recordIfMoved(latitude: Double, longitude: Double): VisitedPoint? {
        val last = dao.getLast()
        val shouldRecord = GeoUtils.shouldRecord(
            lastLat = last?.latitude,
            lastLon = last?.longitude,
            newLat = latitude,
            newLon = longitude,
            minDistanceMeters = minDistanceMeters,
        )
        if (!shouldRecord) return null

        val point = VisitedPoint(latitude = latitude, longitude = longitude)
        val id = dao.insert(point)
        return point.copy(id = id)
    }

    companion object {
        const val DEFAULT_MIN_DISTANCE_METERS: Double = 3.0
    }
}
