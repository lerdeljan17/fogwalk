package com.fogwalk

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fogwalk.data.AppDatabase
import com.fogwalk.data.VisitedRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VisitedRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: VisitedRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = VisitedRepository(db.visitedPointDao(), minDistanceMeters = 15.0)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun firstPoint_isAlwaysRecorded() = runBlocking {
        val stored = repository.recordIfMoved(48.2082, 16.3738)
        assertNotNull(stored)
        assertEquals(1, repository.count())
    }

    @Test
    fun nearbyPoint_isDeduplicated() = runBlocking {
        repository.recordIfMoved(48.2082, 16.3738)
        // ~1 meter away, below the threshold.
        val second = repository.recordIfMoved(48.20821, 16.3738)
        assertNull(second)
        assertEquals(1, repository.count())
    }

    @Test
    fun distantPoint_isRecorded() = runBlocking {
        repository.recordIfMoved(48.2082, 16.3738)
        // ~150 meters away, above the threshold.
        val second = repository.recordIfMoved(48.2095, 16.3738)
        assertNotNull(second)
        assertEquals(2, repository.count())
    }

    @Test
    fun points_persistAndAreReturnedInOrder() = runBlocking {
        repository.recordIfMoved(48.2000, 16.3000)
        repository.recordIfMoved(48.2100, 16.3100)
        repository.recordIfMoved(48.2200, 16.3200)

        val all = repository.getAll()
        assertEquals(3, all.size)
        // Ordered ascending by timestamp == insertion order here.
        assertEquals(48.2000, all.first().latitude, 0.00001)
        assertEquals(48.2200, all.last().latitude, 0.00001)
    }

    @Test
    fun getInBounds_returnsOnlyPointsInsideBox() = runBlocking {
        repository.recordIfMoved(48.2082, 16.3738) // inside
        repository.recordIfMoved(10.0000, 10.0000) // far outside

        val inside = repository.getInBounds(
            minLat = 48.0,
            maxLat = 49.0,
            minLon = 16.0,
            maxLon = 17.0,
        )
        assertEquals(1, inside.size)
        assertEquals(48.2082, inside.first().latitude, 0.00001)
    }

    @Test
    fun clear_removesAllPoints() = runBlocking {
        repository.recordIfMoved(48.2082, 16.3738)
        repository.recordIfMoved(48.2095, 16.3738)
        repository.clear()
        assertEquals(0, repository.count())
    }
}
