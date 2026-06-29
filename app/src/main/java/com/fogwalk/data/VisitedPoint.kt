package com.fogwalk.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single location the user has visited. The collection of these defines the
 * area that has been cleared of fog.
 */
@Entity(tableName = "visited_points")
data class VisitedPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
)
