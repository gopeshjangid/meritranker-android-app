package com.example.meritrankerstudent.util

import org.junit.Assert.*
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun calculateInSampleSize_smallImage_returnsOne() {
        val sampleSize = ImageUtils.calculateInSampleSize(
            outWidth = 800,
            outHeight = 600,
            reqWidth = 1024,
            reqHeight = 1024
        )
        assertEquals(1, sampleSize)
    }

    @Test
    fun calculateInSampleSize_12MegaPixelCameraPhoto_downsamplesSafely() {
        // Typical 12MP camera image: 4032 x 3024
        val sampleSize = ImageUtils.calculateInSampleSize(
            outWidth = 4032,
            outHeight = 3024,
            reqWidth = 1024,
            reqHeight = 1024
        )
        // 4032 / 2 = 2016 >= 1024, 3024 / 2 = 1512 >= 1024
        // 4032 / 4 = 1008 < 1024 -> loop stops at sampleSize = 2 (or 4 depending on threshold)
        assertTrue("Sample size must downsample large camera image", sampleSize >= 2)
        
        val effectiveWidth = 4032 / sampleSize
        val effectiveHeight = 3024 / sampleSize
        assertTrue("Effective width must be within bounded range", effectiveWidth <= 2048)
        assertTrue("Effective height must be within bounded range", effectiveHeight <= 2048)
    }

    @Test
    fun calculateInSampleSize_48MegaPixelMassiveImage_downsamplesAggressively() {
        // High-end 48MP camera photo: 8000 x 6000
        val sampleSize = ImageUtils.calculateInSampleSize(
            outWidth = 8000,
            outHeight = 6000,
            reqWidth = 1024,
            reqHeight = 1024
        )
        assertTrue("48MP image must have inSampleSize of at least 4", sampleSize >= 4)
        
        val effectiveWidth = 8000 / sampleSize
        val effectiveHeight = 6000 / sampleSize
        assertTrue("Effective width must be memory-safe (< 2048)", effectiveWidth <= 2048)
        assertTrue("Effective height must be memory-safe (< 2048)", effectiveHeight <= 2048)
    }

    @Test
    fun maxCompressedImageBytes_isEnforcedAtOnePointFiveMegabytes() {
        assertEquals(1_500_000, ImageUtils.MAX_COMPRESSED_IMAGE_BYTES)
    }
}
