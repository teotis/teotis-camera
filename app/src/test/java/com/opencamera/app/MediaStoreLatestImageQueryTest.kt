package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaStoreLatestImageQueryTest {
    @Test
    fun `app output photo lookup is attempted without gallery read permission`() {
        val plan = latestGalleryPhotoQueryPlan(hasGalleryReadPermission = false)

        assertTrue(plan.queryAppOutputPhotos)
        assertFalse(plan.querySystemGalleryPhotos)
    }

    @Test
    fun `system gallery photo lookup is only a fallback when read permission exists`() {
        val plan = latestGalleryPhotoQueryPlan(hasGalleryReadPermission = true)

        assertTrue(plan.queryAppOutputPhotos)
        assertTrue(plan.querySystemGalleryPhotos)
    }

    @Test
    fun `app photo output wins over newer system gallery photo`() {
        val appPhoto = LatestGalleryPhotoCandidate(
            outputPath = "Pictures/OpenCamera/OpenCamera_1.jpg",
            renderUri = "content://media/external/images/media/1",
            dateAdded = 100,
            source = LatestGalleryPhotoSource.APP_OUTPUT
        )
        val systemPhoto = LatestGalleryPhotoCandidate(
            outputPath = "/storage/emulated/0/DCIM/Camera/newer.jpg",
            renderUri = "content://media/external/images/media/9",
            dateAdded = 900,
            source = LatestGalleryPhotoSource.SYSTEM_GALLERY
        )

        assertEquals(appPhoto, selectLatestGalleryPhoto(appPhoto, systemPhoto))
    }

    @Test
    fun `system gallery photo is used when app output has no photo`() {
        val systemPhoto = LatestGalleryPhotoCandidate(
            outputPath = "/storage/emulated/0/DCIM/Camera/photo.jpg",
            renderUri = "content://media/external/images/media/3",
            dateAdded = 300,
            source = LatestGalleryPhotoSource.SYSTEM_GALLERY
        )

        assertEquals(systemPhoto, selectLatestGalleryPhoto(appOutput = null, systemGallery = systemPhoto))
    }
}
