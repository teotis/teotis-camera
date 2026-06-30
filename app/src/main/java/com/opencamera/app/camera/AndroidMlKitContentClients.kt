package com.opencamera.app.camera

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class AndroidMlKitImageLabelClient(
    private val labeler: ImageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.55f)
            .build()
    )
) : MlKitImageLabelClient {
    override suspend fun detect(bitmap: Bitmap): List<RawMlKitImageLabel> =
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .awaitOrThrow()
            .map { label -> label.toRawMlKitImageLabel() }
}

internal class AndroidMlKitObjectDetectionClient(
    private val detector: ObjectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )
) : MlKitObjectDetectionClient {
    override suspend fun detect(bitmap: Bitmap): List<RawMlKitDetectedObject> =
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .awaitOrThrow()
            .mapIndexed { index, detected -> detected.toRawMlKitDetectedObject(index) }
}

internal class AndroidMlKitFaceDetectionClient(
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )
) : MlKitFaceDetectionClient {
    override suspend fun detect(bitmap: Bitmap): List<RawMlKitFace> =
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .awaitOrThrow()
            .mapIndexed { index, face -> face.toRawMlKitFace(index) }
}

private fun ImageLabel.toRawMlKitImageLabel(): RawMlKitImageLabel =
    RawMlKitImageLabel(
        label = text,
        confidence = confidence,
        index = index
    )

private fun DetectedObject.toRawMlKitDetectedObject(index: Int): RawMlKitDetectedObject =
    RawMlKitDetectedObject(
        objectIndex = index,
        bounds = RawMlKitBounds(
            left = boundingBox.left,
            top = boundingBox.top,
            right = boundingBox.right,
            bottom = boundingBox.bottom
        ),
        labels = labels.map { label ->
            RawMlKitObjectLabel(
                label = label.text,
                confidence = label.confidence,
                index = label.index
            )
        }
    )

private fun Face.toRawMlKitFace(index: Int): RawMlKitFace =
    RawMlKitFace(
        faceIndex = index,
        bounds = RawMlKitBounds(
            left = boundingBox.left,
            top = boundingBox.top,
            right = boundingBox.right,
            bottom = boundingBox.bottom
        ),
        smilingProbability = smilingProbability.takeIfKnownProbability(),
        leftEyeOpenProbability = leftEyeOpenProbability.takeIfKnownProbability(),
        rightEyeOpenProbability = rightEyeOpenProbability.takeIfKnownProbability()
    )

private fun Float?.takeIfKnownProbability(): Float? =
    this?.takeIf { it >= 0f }

private suspend fun <T> Task<T>.awaitOrThrow(): T =
    suspendCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
