package com.example.meritrankerstudent.observability

object Buckets {

    fun latencyBucket(durationMs: Long): String {
        return when {
            durationMs < 1000 -> "lt_1s"
            durationMs < 3000 -> "1_3s"
            durationMs < 5000 -> "3_5s"
            durationMs < 10000 -> "5_10s"
            durationMs < 30000 -> "10_30s"
            else -> "gt_30s"
        }
    }

    fun progressBucket(percentage: Int): String {
        return when {
            percentage <= 25 -> "0_25"
            percentage <= 50 -> "25_50"
            percentage <= 75 -> "50_75"
            percentage < 100 -> "75_99"
            else -> "100"
        }
    }

    fun questionCountBucket(count: Int): String {
        return when {
            count <= 10 -> "1_10"
            count <= 25 -> "11_25"
            count <= 50 -> "26_50"
            count <= 100 -> "51_100"
            else -> "gt_100"
        }
    }

    fun statusBucket(statusCode: Int?): String {
        if (statusCode == null) return "unknown"
        return when (statusCode) {
            in 200..299 -> "2xx"
            401 -> "401"
            403 -> "403"
            404 -> "404"
            409 -> "409"
            429 -> "429"
            in 400..499 -> "4xx"
            in 500..599 -> "5xx"
            else -> "unknown"
        }
    }
}
