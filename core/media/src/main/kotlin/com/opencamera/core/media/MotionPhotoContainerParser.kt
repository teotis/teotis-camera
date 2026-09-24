package com.opencamera.core.media

/**
 * Segment-oriented JPEG walker plus Google Motion Photo v1 container parser and
 * structural validator.
 *
 * Recognition sources, in order of authority:
 * 1. `GCamera:MicroVideoOffset` (micro-video era readers; also written by modern
 *    Google Camera for legacy readers).
 * 2. `Container:Directory` Primary `Item:Length` (Google Motion Photo v1).
 * 3. Fallback: MP4 box scan (used only when XMP offsets are absent or inconsistent).
 */
object MotionPhotoContainerParser {

    val ADOBE_XMP_IDENTIFIER: ByteArray =
        "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)

    private const val JPEG_SOI = 0xD8
    private const val JPEG_EOI = 0xD9
    private const val JPEG_APP0 = 0xE0
    private const val JPEG_APP15 = 0xEF

    private const val GOOGLE_CAMERA_NS = "http://ns.google.com/photos/1.0/camera/"

    private val XMP_START = Regex("<\\?xpacket[^>]*\\?>")
    private val XMP_END = Regex("<\\?xpacket end=.*\\?>")
    private val NS_PREFIX = Regex("xmlns:(\\w+)=\"([^\"]+)\"")
    private val ATTRIBUTE_VALUE = Regex("((?:Camera|GCamera):[A-Za-z]+)=\"([^\"]*)\"")
    private val CONTAINER_ITEM = Regex("<rdf:li\\b([^>]*?)/?>")
    private val ITEM_ATTRIBUTE = Regex("Item:(Mime|Semantic|Length)=\"([^\"]*)\"")
    private val CONTAINER_DIRECTORY = Regex("<Container:Directory\\b")

    data class JpegSegment(
        val marker: Int,
        val start: Int,
        val end: Int,
        val payload: ByteArray? = null
    ) {
        val length: Int get() = end - start
        val isAdobeXmp: Boolean
            get() {
                val p = payload ?: return false
                return marker == 0xE1 && p.size >= ADOBE_XMP_IDENTIFIER.size &&
                    p.copyOfRange(0, ADOBE_XMP_IDENTIFIER.size)
                        .contentEquals(ADOBE_XMP_IDENTIFIER)
            }
    }

    data class ContainerItem(
        val mimeType: String?,
        val semantic: String?,
        val lengthBytes: Long?
    )

    data class XmpMotionData(
        val hasCameraNamespace: Boolean,
        val hasGCameraNamespace: Boolean,
        val cameraMotionPhoto: Boolean,
        val gCameraMotionPhoto: Boolean,
        val motionPhotoVersion: String?,
        val presentationTimestampUs: Long?,
        val microVideoOffset: Long?,
        val items: List<ContainerItem>
    ) {
        val motionPhotoDeclared: Boolean get() = cameraMotionPhoto || gCameraMotionPhoto

        val containerMotionLength: Long?
            get() = items.firstOrNull { it.semantic == "MotionPhoto" }?.lengthBytes

        val containerPrimaryLength: Long?
            get() = items.firstOrNull { it.semantic == "Primary" }?.lengthBytes
    }

    data class Analysis(
        val valid: Boolean,
        val issues: List<String>,
        val jpegEnd: Int?,
        val motionOffset: Long?,
        val motionLength: Long?,
        val xmp: XmpMotionData?
    ) {
        val motionDeclared: Boolean
            get() = motionOffset != null && motionLength != null
    }

    fun analyze(bytes: ByteArray): Analysis {
        val issues = mutableListOf<String>()

        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != JPEG_SOI.toByte()) {
            return Analysis(false, listOf("not-a-jpeg-soi"), null, null, null, null)
        }

        val segments = readJpegSegments(bytes)
        val xmpSegment = segments.firstOrNull { it.isAdobeXmp }
        val xmp = xmpSegment?.payload?.let { parseXmp(it) }
        if (xmpSegment == null) {
            issues.add("xmp-app1-missing")
        } else if (xmp == null) {
            issues.add("xmp-unparseable")
        }

        if (xmp != null) {
            if (!xmp.motionPhotoDeclared) {
                issues.add("motion-photo-flag-missing")
            }
            if (!xmp.hasCameraNamespace && !xmp.hasGCameraNamespace) {
                issues.add("google-camera-namespace-missing")
            }
            if (xmp.presentationTimestampUs != null && xmp.presentationTimestampUs < 0) {
                issues.add("negative-presentation-timestamp")
            }
            if (!hasContainerDirectory(xmpSegment!!.payload!!)) {
                // Legacy micro-video vector (Samsung / early Google Camera): no Container
                // Directory; MicroVideoOffset is the authoritative offset.
            } else if (xmp.items.size < 2) {
                issues.add("container-items-incomplete")
            } else {
                val primary = xmp.items.firstOrNull { it.semantic == "Primary" }
                val motion = xmp.items.firstOrNull { it.semantic == "MotionPhoto" }
                if (primary == null || motion == null) {
                    issues.add("container-semantics-incomplete")
                } else {
                    if (primary.mimeType != null && primary.mimeType != "image/jpeg") {
                        issues.add("primary-mime-not-jpeg")
                    }
                    if (motion.mimeType != null && motion.mimeType != "video/mp4") {
                        issues.add("motion-mime-not-mp4")
                    }
                    if (primary.lengthBytes != null && motion.lengthBytes != null) {
                        val declaredTotal = primary.lengthBytes + motion.lengthBytes
                        if (declaredTotal != bytes.size.toLong()) {
                            issues.add("length-total-mismatch")
                        }
                        if (primary.lengthBytes != bytes.size.toLong() - (motion.lengthBytes)) {
                            // covered by total check; kept for clarity
                        }
                    }
                }
            }
        }

        // Motion offset resolution.
        val xmpOffset = xmp?.microVideoOffset
        val xmpPrimaryLength = xmp?.containerPrimaryLength
        val motionOffset = when {
            xmpOffset != null -> {
                if (xmpOffset < 0 || xmpOffset >= bytes.size) {
                    issues.add("micro-video-offset-out-of-range")
                    xmpPrimaryLength?.takeIf { it in 1 until bytes.size }
                } else {
                    xmpOffset
                }
            }
            xmpPrimaryLength != null && xmpPrimaryLength in 1 until bytes.size -> xmpPrimaryLength
            else -> findMotionOffsetByBoxScan(bytes)
        }

        val motionLength = motionOffset?.let { bytes.size.toLong() - it }
        if (motionOffset != null) {
            if (motionOffset < 2) {
                issues.add("motion-offset-too-small")
            }
            val atOffset = motionOffset.toInt()
            if (atOffset < bytes.size && !looksLikeMp4BoxStart(bytes, atOffset)) {
                issues.add("motion-offset-not-mp4")
            }
            if (xmpOffset != null && xmpPrimaryLength != null && xmpOffset != xmpPrimaryLength) {
                issues.add("offset-primary-length-mismatch")
            }
        } else {
            issues.add("motion-offset-undetermined")
        }

        if (xmp?.containerMotionLength != null && motionLength != null &&
            xmp.containerMotionLength != motionLength
        ) {
            issues.add("motion-length-mismatch")
        }

        // Structural sanity: the still part must terminate with an EOI when offsets exist.
        if (motionOffset != null && motionOffset >= 4) {
            val eoiPos = motionOffset.toInt() - 2
            val eoiOk = eoiPos >= 0 && bytes[eoiPos] == 0xFF.toByte() && bytes[eoiPos + 1] == JPEG_EOI.toByte()
            if (!eoiOk) {
                issues.add("still-part-no-eoi")
            }
        }

        return Analysis(
            valid = issues.isEmpty(),
            issues = issues,
            jpegEnd = xmp?.containerPrimaryLength?.let { it.toInt() },
            motionOffset = motionOffset,
            motionLength = motionLength,
            xmp = xmp
        )
    }

    /**
     * Walks JPEG markers after SOI, collecting segments with markers that carry a
     * 2-byte length (APPn, COM, DQT, DHT, SOF*, DRI, etc.). Stops at SOS (0xDA)
     * whose length covers the scan header; entropy data and EOI are not segments.
     */
    fun readJpegSegments(bytes: ByteArray): List<JpegSegment> {
        val segments = mutableListOf<JpegSegment>()
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != JPEG_SOI.toByte()) {
            return segments
        }
        var pos = 2
        while (pos + 3 < bytes.size) {
            if (bytes[pos] != 0xFF.toByte()) {
                break
            }
            val marker = bytes[pos + 1].toInt() and 0xFF
            if (marker == JPEG_SOI || marker == JPEG_EOI) {
                break
            }
            if (marker == 0xDA) { // SOS: length covers scan header only
                val len = ((bytes[pos + 2].toInt() and 0xFF) shl 8) or (bytes[pos + 3].toInt() and 0xFF)
                if (len < 2) break
                segments += JpegSegment(marker, pos, minOf(pos + 2 + len, bytes.size))
                break
            }
            if (marker == 0x01 || marker in 0xD0..0xD7) {
                // Standalone markers without a length field.
                pos += 2
                continue
            }
            val len = ((bytes[pos + 2].toInt() and 0xFF) shl 8) or (bytes[pos + 3].toInt() and 0xFF)
            if (len < 2 || pos + 2 + len > bytes.size) {
                break
            }
            val payload = if (marker in JPEG_APP0..JPEG_APP15) {
                bytes.copyOfRange(pos + 4, pos + 2 + len)
            } else {
                null
            }
            segments += JpegSegment(marker, pos, pos + 2 + len, payload)
            pos += 2 + len
        }
        return segments
    }

    private fun hasContainerDirectory(xmpBytes: ByteArray): Boolean {
        return CONTAINER_DIRECTORY.containsMatchIn(String(xmpBytes, Charsets.UTF_8))
    }

    private fun parseXmp(xmpBytes: ByteArray): XmpMotionData? {
        val xmp = String(xmpBytes, Charsets.UTF_8)
        val start = XMP_START.find(xmp)?.range?.last ?: 0
        val end = XMP_END.find(xmp, start)?.range?.first ?: xmp.length
        val packet = xmp.substring(start, end)

        val description = packet.substringAfter("<rdf:Description").substringBefore(">")
        val namespaces = NS_PREFIX.findAll(description).associate { m ->
            m.groupValues[1] to m.groupValues[2]
        }
        val hasCamera = namespaces["Camera"] == GOOGLE_CAMERA_NS
        val hasGCamera = namespaces["GCamera"] == GOOGLE_CAMERA_NS

        fun attr(prefix: String, name: String): String? {
            return ATTRIBUTE_VALUE.findAll(description)
                .firstOrNull { it.groupValues[1] == "$prefix:$name" }
                ?.groupValues?.get(2)
        }

        val items = CONTAINER_ITEM.findAll(packet).mapNotNull { itemMatch ->
            val attrs = ITEM_ATTRIBUTE.findAll(itemMatch.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            ContainerItem(
                mimeType = attrs["Mime"],
                semantic = attrs["Semantic"],
                lengthBytes = attrs["Length"]?.toLongOrNull()
            )
        }.toList()

        return XmpMotionData(
            hasCameraNamespace = hasCamera,
            hasGCameraNamespace = hasGCamera,
            cameraMotionPhoto = attr("Camera", "MotionPhoto") == "1",
            gCameraMotionPhoto = attr("GCamera", "MotionPhoto") == "1",
            motionPhotoVersion = attr("Camera", "MotionPhotoVersion")
                ?: attr("GCamera", "MotionPhotoVersion"),
            presentationTimestampUs = (attr("Camera", "MotionPhotoPresentationTimestampUs")
                ?: attr("GCamera", "MotionPhotoPresentationTimestampUs"))?.toLongOrNull(),
            microVideoOffset = attr("GCamera", "MicroVideoOffset")?.toLongOrNull(),
            items = items
        )
    }

    fun extractXmp(bytes: ByteArray): XmpMotionData? {
        val segment = readJpegSegments(bytes).firstOrNull { it.isAdobeXmp }
        return segment?.payload?.let { parseXmp(it) }
    }

    /**
     * Fallback MP4 detection: scans for the leftmost plausible MP4 box header
     * (size + known box type) in the trailing region of the file. Only used when
     * the XMP does not provide an authoritative offset.
     */
    private fun findMotionOffsetByBoxScan(bytes: ByteArray): Long? {
        val knownTypes = setOf("ftyp", "moov", "mdat", "free", "wide", "skip", "uuid", "udta")
        var pos = bytes.size - 8
        while (pos >= 2) {
            if (bytes[pos] in 0x41..0x7A) {
                val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
                if (type in knownTypes && pos >= 4) {
                    val size = ((bytes[pos].toLong() and 0xFF) shl 24) or
                        ((bytes[pos + 1].toLong() and 0xFF) shl 16) or
                        ((bytes[pos + 2].toLong() and 0xFF) shl 8) or
                        (bytes[pos + 3].toLong() and 0xFF)
                    if (size >= 8 && pos.toLong() + size <= bytes.size.toLong()) {
                        return pos.toLong()
                    }
                }
            }
            pos--
        }
        return null
    }

    private fun looksLikeMp4BoxStart(bytes: ByteArray, offset: Int): Boolean {
        if (offset + 8 > bytes.size) return false
        val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
        return type in setOf("ftyp", "moov", "mdat", "free", "wide", "skip", "uuid", "udta", "meta")
    }
}
