package com.opencamera.core.media

interface MediaPostProcessor {
    suspend fun process(result: ShotResult): ShotResult
}
