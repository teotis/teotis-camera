package com.opencamera.app.camera

import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaOutputHandle
import java.io.File

internal fun stillCaptureCleanupPaths(
    outputPath: String,
    outputHandle: MediaOutputHandle,
    livePhotoBundle: LivePhotoBundle? = null,
    intermediateOutputPaths: List<String> = emptyList()
): List<String> {
    val cleanupPaths = linkedSetOf<String>()

    fun registerAbsolutePath(path: String?) {
        if (!path.isNullOrBlank() && File(path).isAbsolute) {
            cleanupPaths += path
        }
    }

    registerAbsolutePath(outputHandle.filePath)
    registerAbsolutePath(outputPath)
    registerAbsolutePath(livePhotoBundle?.stillPath)
    registerAbsolutePath(livePhotoBundle?.motionHandle?.filePath)
    registerAbsolutePath(livePhotoBundle?.motionPath)
    registerAbsolutePath(livePhotoBundle?.sidecarHandle?.filePath)
    registerAbsolutePath(livePhotoBundle?.sidecarPath)
    registerAbsolutePath(livePhotoBundle?.thumbnailHandle?.filePath)
    registerAbsolutePath(livePhotoBundle?.thumbnailPath)
    intermediateOutputPaths.forEach(::registerAbsolutePath)
    return cleanupPaths.toList()
}

internal fun stillCaptureCleanupContentUris(
    outputHandle: MediaOutputHandle,
    livePhotoBundle: LivePhotoBundle? = null
): List<String> {
    return buildList {
        outputHandle.contentUri?.let(::add)
        livePhotoBundle?.motionHandle?.contentUri?.let(::add)
        livePhotoBundle?.sidecarHandle?.contentUri?.let(::add)
        livePhotoBundle?.thumbnailHandle?.contentUri?.let(::add)
    }.distinct()
}

internal fun cleanupStillCaptureArtifacts(
    outputPath: String,
    outputHandle: MediaOutputHandle,
    livePhotoBundle: LivePhotoBundle? = null,
    intermediateOutputPaths: List<String> = emptyList(),
    deleteContentUri: (String) -> Unit = {}
): List<String> {
    stillCaptureCleanupContentUris(
        outputHandle = outputHandle,
        livePhotoBundle = livePhotoBundle
    ).forEach(deleteContentUri)
    return cleanupAbsoluteFilePaths(
        stillCaptureCleanupPaths(
            outputPath = outputPath,
            outputHandle = outputHandle,
            livePhotoBundle = livePhotoBundle,
            intermediateOutputPaths = intermediateOutputPaths
        )
    )
}

internal fun cleanupAbsoluteFilePaths(
    paths: List<String>
): List<String> {
    return paths
        .filter { File(it).isAbsolute }
        .distinct()
        .filter { path ->
            val file = File(path)
            file.exists() && file.delete()
        }
}
