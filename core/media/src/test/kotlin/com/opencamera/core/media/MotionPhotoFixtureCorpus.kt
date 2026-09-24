package com.opencamera.core.media

/**
 * Interop fixture corpus for Motion Photo container testing.
 *
 * Each vector models a real-world reader expectation:
 * - Google Photos vector: dual-namespace XMP (Camera: + GCamera:) with Container
 *   Directory, Primary/MotionPhoto items and MicroVideoOffset (modern Google Camera).
 * - Samsung legacy vector: GCamera:-only namespace with MicroVideoOffset (micro-video
 *   era; still read by Samsung Gallery).
 * - Legacy Camera-only vector: Camera:-only namespace, no GCamera (old Google Camera).
 * - Multi-APPn vector: APP0 (JFIF) + APP1 (EXIF) + XMP APP1 must be inserted after
 *   the full APPn sequence, before DQT/SOF.
 */
object MotionPhotoFixtureCorpus {

    fun minimalJpeg(): ByteArray {
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE0.toByte(), // APP0
            0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00,
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xDA.toByte(), // SOS
            0x00, 0x08,
            0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x7F, 0x00,
            0xFF.toByte(), 0xD9.toByte()  // EOI
        )
    }

    /** JPEG with APP0 (JFIF) followed by a non-XMP APP1 (simulated EXIF). */
    fun multiAppnJpeg(): ByteArray {
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE0.toByte(), // APP0 JFIF
            0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00,
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xE1.toByte(), // APP1 EXIF
            0x00, 0x0A,
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00, 0x00, 0x00, // "Exif\0\0\0\0"
            0xFF.toByte(), 0xDA.toByte(), // SOS
            0x00, 0x08,
            0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x7F, 0x00,
            0xFF.toByte(), 0xD9.toByte()  // EOI
        )
    }

    fun fakeMp4(): ByteArray {
        return byteArrayOf(
            0x00, 0x00, 0x00, 0x1C,
            0x66, 0x74, 0x79, 0x70, // "ftyp"
            0x69, 0x73, 0x6F, 0x6D,
            0x00, 0x00, 0x02, 0x00,
            0x69, 0x73, 0x6F, 0x6D,
            0x69, 0x73, 0x6F, 0x32,
            0x61, 0x76, 0x63, 0x31
        )
    }

    /** Modern Google Camera style: dual namespaces + container + MicroVideoOffset. */
    fun googleDualNamespace(
        jpegBytes: ByteArray = minimalJpeg(),
        motionBytes: ByteArray = fakeMp4()
    ): ByteArray {
        return MotionPhotoJpegContainer.write(
            jpegBytes = jpegBytes,
            motionBytes = motionBytes,
            spec = MotionPhotoContainerSpec(
                motionLengthBytes = motionBytes.size.toLong(),
                stillLengthBytes = jpegBytes.size.toLong(),
                presentationTimestampUs = 42L
            )
        )
    }

    /**
     * Samsung micro-video era vector: GCamera:-only namespace, MotionPhoto flags and
     * MicroVideoOffset, no Container Directory, no Camera: prefix.
     */
    fun samsungMicroVideo(
        jpegBytes: ByteArray = minimalJpeg(),
        motionBytes: ByteArray = fakeMp4()
    ): ByteArray {
        // Offset placeholder "000000000" replaced after XMP built; 9 digits is a
        // practical upper bound for real files, so a single rebuild is exact.
        val xmp = buildString {
            append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
            append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
            append("  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
            append("    <rdf:Description rdf:about=\"\"\n")
            append("      xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"\n")
            append("      GCamera:MotionPhoto=\"1\"\n")
            append("      GCamera:MotionPhotoVersion=\"1\"\n")
            append("      GCamera:MicroVideoOffset=\"000000000\">\n")
            append("    </rdf:Description>\n")
            append("  </rdf:RDF>\n")
            append("</x:xmpmeta>\n")
            append("<?xpacket end=\"w\"?>")
        }
        val app1 = buildApp1(xmp)
        val jpegPart = insertAfterAppn(jpegBytes, app1)
        val offset = jpegPart.size
        val paddedOffset = "0".repeat(9 - offset.toString().length.coerceAtMost(9)) + offset
        val finalXmp = xmp.replace("MicroVideoOffset=\"000000000\"", "MicroVideoOffset=\"$paddedOffset\"")
        val finalApp1 = buildApp1(finalXmp)
        val finalJpegPart = insertAfterAppn(jpegBytes, finalApp1)
        return finalJpegPart + motionBytes
    }

    /** Legacy Google Camera: Camera:-only namespace, no MicroVideoOffset. */
    fun legacyCameraOnly(
        jpegBytes: ByteArray = minimalJpeg(),
        motionBytes: ByteArray = fakeMp4()
    ): ByteArray {
        val xmp = buildString {
            append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
            append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
            append("  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
            append("    <rdf:Description rdf:about=\"\"\n")
            append("      xmlns:Camera=\"http://ns.google.com/photos/1.0/camera/\"\n")
            append("      Camera:MotionPhoto=\"1\"\n")
            append("      Camera:MotionPhotoVersion=\"1\">\n")
            append("      <Container:Directory>\n")
            append("        <rdf:Seq>\n")
            append("          <rdf:li Item:Mime=\"image/jpeg\" Item:Semantic=\"Primary\" Item:Length=\"JPLEN\"/>\n")
            append("          <rdf:li Item:Mime=\"video/mp4\" Item:Semantic=\"MotionPhoto\" Item:Length=\"MP4LEN\"/>\n")
            append("        </rdf:Seq>\n")
            append("      </Container:Directory>\n")
            append("    </rdf:Description>\n")
            append("  </rdf:RDF>\n")
            append("</x:xmpmeta>\n")
            append("<?xpacket end=\"w\"?>")
        }
        val placeholder = xmp
            .replace("JPLEN", "000000000")
            .replace("MP4LEN", motionBytes.size.toString())
        val app1 = buildApp1(placeholder)
        val jpegPart = insertAfterAppn(jpegBytes, app1)
        val paddedPrimary = "0".repeat(9 - jpegPart.size.toString().length.coerceAtMost(9)) + jpegPart.size
        val finalXmp = placeholder.replace("Item:Length=\"000000000\"", "Item:Length=\"$paddedPrimary\"")
        val finalApp1 = buildApp1(finalXmp)
        return insertAfterAppn(jpegBytes, finalApp1) + motionBytes
    }

    private fun buildApp1(xmp: String): ByteArray {
        val xmpBytes = xmp.toByteArray(Charsets.UTF_8)
        val namespace = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
        val payload = namespace + xmpBytes
        val length = payload.size + 2
        val segment = ByteArray(2 + 2 + payload.size)
        segment[0] = 0xFF.toByte()
        segment[1] = 0xE1.toByte()
        segment[2] = (length shr 8).toByte()
        segment[3] = (length and 0xFF).toByte()
        System.arraycopy(payload, 0, segment, 4, payload.size)
        return segment
    }

    private fun insertAfterAppn(jpegBytes: ByteArray, app1Segment: ByteArray): ByteArray {
        var insertPos = 2
        val segments = MotionPhotoContainerParser.readJpegSegments(jpegBytes)
        for (segment in segments) {
            if (segment.marker in 0xE0..0xEF) {
                insertPos = segment.end
            } else {
                break
            }
        }
        val result = ByteArray(jpegBytes.size + app1Segment.size)
        System.arraycopy(jpegBytes, 0, result, 0, insertPos)
        System.arraycopy(app1Segment, 0, result, insertPos, app1Segment.size)
        System.arraycopy(jpegBytes, insertPos, result, insertPos + app1Segment.size, jpegBytes.size - insertPos)
        return result
    }
}
