package com.example.careium.core.ai

import org.junit.Assert.*
import org.junit.Test

class PhotoSizingTest {
    @Test fun largePhotosKeepEnoughDetailBeforeFinalResize() {
        assertEquals(2, PhotoSizing.sampleSize(4000, 3000))
        assertEquals(1536 to 1152, PhotoSizing.fitted(2000, 1500))
        assertEquals(4, PhotoSizing.sampleSize(8000, 6000))
        assertEquals(1152 to 1536, PhotoSizing.fitted(1500, 2000))
    }
    @Test fun smallAndVeryWidePhotosRemainValidWithoutUpscaling() {
        assertEquals(1, PhotoSizing.sampleSize(400, 300))
        assertEquals(400 to 300, PhotoSizing.fitted(400, 300))
        assertEquals(1536 to 1, PhotoSizing.fitted(10000, 1))
    }
}
