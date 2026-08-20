package com.example.meritrankerstudent.observability

import org.junit.Assert.*
import org.junit.Test

class BucketsTest {

    @Test
    fun latencyBucket_boundaries() {
        assertEquals("lt_1s", Buckets.latencyBucket(500))
        assertEquals("1_3s", Buckets.latencyBucket(1500))
        assertEquals("3_5s", Buckets.latencyBucket(4000))
        assertEquals("5_10s", Buckets.latencyBucket(8000))
        assertEquals("10_30s", Buckets.latencyBucket(15000))
        assertEquals("gt_30s", Buckets.latencyBucket(45000))
    }

    @Test
    fun progressBucket_boundaries() {
        assertEquals("0_25", Buckets.progressBucket(10))
        assertEquals("0_25", Buckets.progressBucket(25))
        assertEquals("25_50", Buckets.progressBucket(30))
        assertEquals("25_50", Buckets.progressBucket(50))
        assertEquals("50_75", Buckets.progressBucket(70))
        assertEquals("75_99", Buckets.progressBucket(90))
        assertEquals("100", Buckets.progressBucket(100))
    }

    @Test
    fun questionCountBucket_boundaries() {
        assertEquals("1_10", Buckets.questionCountBucket(5))
        assertEquals("1_10", Buckets.questionCountBucket(10))
        assertEquals("11_25", Buckets.questionCountBucket(20))
        assertEquals("26_50", Buckets.questionCountBucket(50))
        assertEquals("51_100", Buckets.questionCountBucket(100))
        assertEquals("gt_100", Buckets.questionCountBucket(150))
    }

    @Test
    fun statusBucket_boundaries() {
        assertEquals("2xx", Buckets.statusBucket(200))
        assertEquals("2xx", Buckets.statusBucket(204))
        assertEquals("401", Buckets.statusBucket(401))
        assertEquals("403", Buckets.statusBucket(403))
        assertEquals("404", Buckets.statusBucket(404))
        assertEquals("409", Buckets.statusBucket(409))
        assertEquals("429", Buckets.statusBucket(429))
        assertEquals("4xx", Buckets.statusBucket(422))
        assertEquals("5xx", Buckets.statusBucket(500))
        assertEquals("5xx", Buckets.statusBucket(503))
        assertEquals("unknown", Buckets.statusBucket(null))
    }
}
