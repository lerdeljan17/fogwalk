package com.fogwalk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedPointDao {

    @Insert
    suspend fun insert(point: VisitedPoint): Long

    @Query("SELECT * FROM visited_points ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<VisitedPoint>>

    @Query("SELECT * FROM visited_points ORDER BY timestamp ASC")
    suspend fun getAll(): List<VisitedPoint>

    @Query("SELECT * FROM visited_points ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLast(): VisitedPoint?

    @Query("SELECT COUNT(*) FROM visited_points")
    suspend fun count(): Int

    @Query("DELETE FROM visited_points")
    suspend fun clear()

    /**
     * Returns points whose coordinates fall inside the given bounding box. Used
     * to only load the points that can actually be visible on screen.
     */
    @Query(
        "SELECT * FROM visited_points WHERE latitude BETWEEN :minLat AND :maxLat " +
            "AND longitude BETWEEN :minLon AND :maxLon",
    )
    suspend fun getInBounds(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<VisitedPoint>
}
