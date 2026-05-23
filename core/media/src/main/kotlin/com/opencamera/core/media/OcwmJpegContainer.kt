package com.opencamera.core.media

object OcwmJpegContainer {

    private val OCWM_MAGIC = byteArrayOf('O'.code.toByte(), 'C'.code.toByte(), 'W'.code.toByte(), 'M'.code.toByte(), 0x00)
    private const val MAX_OCWM_SEGMENT_PAYLOAD_BYTES = 60000
    private const val FIXED_HEADER_SIZE = 26 // 5 magic + 1 version + 1 flags + 4 chunkIndex + 4 chunkCount + 8 totalPayloadLength + 4 manifestLength

    data class EmbeddedArchive(
        val manifest: ReversibleWatermarkArchiveManifest,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EmbeddedArchive) return false
            return manifest == other.manifest && payload.contentEquals(other.payload)
        }
        override fun hashCode(): Int = 31 * manifest.hashCode() + payload.contentHashCode()
    }

    fun embedArchive(visibleJpeg: ByteArray, archive: EmbeddedArchive): ByteArray {
        require(visibleJpeg.size >= 2 && visibleJpeg[0] == 0xFF.toByte() && visibleJpeg[1] == 0xD8.toByte()) {
            "invalid jpeg"
        }
        require(archive.payload.isNotEmpty()) { "empty payload" }
        require(archive.manifest.payloadLength == archive.payload.size.toLong()) { "payloadLength mismatch" }
        require(archive.manifest.payloadSha256 == sha256Hex(archive.payload)) { "payloadSha256 mismatch" }

        val manifestJson = archive.manifest.toJson().toByteArray(Charsets.UTF_8)
        val manifestLength = manifestJson.size

        // Chunk the payload
        val chunkPayloadCapacity = MAX_OCWM_SEGMENT_PAYLOAD_BYTES - FIXED_HEADER_SIZE - manifestLength
        require(chunkPayloadCapacity > 0) { "manifest too large" }
        val totalPayload = archive.payload.size
        val chunkCount = (totalPayload + chunkPayloadCapacity - 1) / chunkPayloadCapacity
        require(chunkCount > 0) { "empty payload" }

        // Build APP15 segments
        val segments = mutableListOf<ByteArray>()
        for (chunkIndex in 0 until chunkCount) {
            val offset = chunkIndex * chunkPayloadCapacity
            val sliceLen = minOf(chunkPayloadCapacity, totalPayload - offset)
            val hasManifest = chunkIndex == 0
            val segmentPayloadSize = FIXED_HEADER_SIZE + (if (hasManifest) manifestLength else 0) + sliceLen

            val segment = ByteArray(4 + segmentPayloadSize) // 4 for marker(2) + length(2)
            var pos = 0

            // Marker
            segment[pos++] = 0xFF.toByte()
            segment[pos++] = 0xEF.toByte() // APP15

            // Length (includes length field itself)
            val segLen = segmentPayloadSize + 2
            segment[pos++] = ((segLen shr 8) and 0xFF).toByte()
            segment[pos++] = (segLen and 0xFF).toByte()

            // Fixed header
            OCWM_MAGIC.copyInto(segment, pos); pos += 5
            segment[pos++] = 1 // formatVersion
            segment[pos++] = 0 // flags
            putInt(segment, pos, chunkIndex); pos += 4
            putInt(segment, pos, chunkCount); pos += 4
            putLong(segment, pos, totalPayload.toLong()); pos += 8
            putInt(segment, pos, if (hasManifest) manifestLength else 0); pos += 4

            // Manifest (chunk 0 only)
            if (hasManifest) {
                manifestJson.copyInto(segment, pos)
                pos += manifestLength
            }

            // Payload slice
            archive.payload.copyInto(segment, pos, offset, offset + sliceLen)

            segments.add(segment)
        }

        // Find SOS marker insertion point
        val sosIndex = findSosIndex(visibleJpeg)
        require(sosIndex >= 0) { "jpeg-sos-missing" }

        // Assemble output: before SOS + all APP15 segments + from SOS onward
        val totalSize = sosIndex + segments.sumOf { it.size } + (visibleJpeg.size - sosIndex)
        val output = ByteArray(totalSize)
        var outPos = 0

        visibleJpeg.copyInto(output, 0, 0, sosIndex)
        outPos = sosIndex

        for (seg in segments) {
            seg.copyInto(output, outPos)
            outPos += seg.size
        }

        visibleJpeg.copyInto(output, outPos, sosIndex, visibleJpeg.size)

        return output
    }

    fun extractArchive(jpeg: ByteArray): EmbeddedArchive? {
        if (jpeg.size < 4) return null
        if (jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return null

        val sosIndex = findSosIndex(jpeg)
        if (sosIndex < 0) return null

        // Scan APP15 segments with OCWM magic
        val ocwmChunks = mutableListOf<OcwmChunk>()
        var i = 2
        while (i < sosIndex) {
            if (i + 1 >= jpeg.size) break
            if (jpeg[i] != 0xFF.toByte()) { i++; continue }
            val marker = jpeg[i + 1]
            if (i + 3 >= jpeg.size) break
            val segLen = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            if (segLen < 2) { i += 2; continue }

            if (marker == 0xEF.toByte()) { // APP15
                val payloadStart = i + 4
                val payloadLen = segLen - 2
                if (payloadLen >= FIXED_HEADER_SIZE && matchesMagic(jpeg, payloadStart)) {
                    val chunk = parseOcwmChunk(jpeg, payloadStart, payloadLen)
                    if (chunk != null) ocwmChunks.add(chunk)
                }
            }

            i += 2 + segLen
        }

        if (ocwmChunks.isEmpty()) return null

        // Group by manifest payloadSha256 (all chunks share same group for v1)
        val groups = ocwmChunks.groupBy { it.payloadSha256 }
        require(groups.size == 1) { "ocwm-chunk-missing" }
        val chunks = groups.values.first()

        val chunkCount = chunks.first().chunkCount
        require(chunks.size == chunkCount) { "ocwm-chunk-missing" }

        val sorted = sortedByChunkIndex(chunks)
        for (idx in sorted.indices) {
            require(sorted[idx].chunkIndex == idx) { "ocwm-chunk-missing" }
        }

        // Reassemble payload
        val totalPayloadLength = sorted.first().totalPayloadLength.toInt()
        val payload = ByteArray(totalPayloadLength)
        var offset = 0
        for (chunk in sorted) {
            chunk.payloadSlice.copyInto(payload, offset)
            offset += chunk.payloadSlice.size
        }
        require(offset == totalPayloadLength) { "ocwm-chunk-missing" }

        // Parse manifest from chunk 0
        val manifestJson = sorted.first().manifestUtf8
            ?: throw IllegalArgumentException("ocwm-chunk-missing")
        val manifest = ReversibleWatermarkArchiveManifest.fromJson(manifestJson)

        // Verify version
        require(manifest.version == 1) { "ocwm-version-unsupported" }

        // Verify payload integrity
        val actualHash = sha256Hex(payload)
        require(actualHash == manifest.payloadSha256) { "ocwm-payload-sha256-mismatch" }

        return EmbeddedArchive(manifest, payload)
    }

    private data class OcwmChunk(
        val formatVersion: Int,
        val chunkIndex: Int,
        val chunkCount: Int,
        val totalPayloadLength: Long,
        val manifestUtf8: String?,
        val payloadSha256: String,
        val payloadSlice: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OcwmChunk) return false
            return chunkIndex == other.chunkIndex && payloadSlice.contentEquals(other.payloadSlice)
        }
        override fun hashCode(): Int = 31 * chunkIndex + payloadSlice.contentHashCode()
    }

    private fun parseOcwmChunk(data: ByteArray, offset: Int, payloadLen: Int): OcwmChunk? {
        if (payloadLen < FIXED_HEADER_SIZE) return null
        var pos = offset

        // Magic check
        for (j in 0 until 5) {
            if (data[pos + j] != OCWM_MAGIC[j]) return null
        }
        pos += 5

        val formatVersion = data[pos].toInt() and 0xFF
        pos++ // skip version
        pos++ // skip flags

        val chunkIndex = getInt(data, pos); pos += 4
        val chunkCount = getInt(data, pos); pos += 4
        val totalPayloadLength = getLong(data, pos); pos += 8
        val manifestLength = getInt(data, pos); pos += 4

        var manifestUtf8: String? = null
        if (manifestLength > 0) {
            manifestUtf8 = String(data, pos, manifestLength, Charsets.UTF_8)
            pos += manifestLength
        }

        val sliceLen = payloadLen - (pos - offset)
        if (sliceLen < 0) return null
        val payloadSlice = ByteArray(sliceLen)
        data.copyInto(payloadSlice, 0, pos, pos + sliceLen)

        // We need payloadSha256 from manifest for grouping, but manifest may not be on this chunk.
        // Use chunkIndex + chunkCount as a proxy for grouping - we'll validate later.
        // For simplicity, extract payloadSha256 from manifest if present.
        val payloadSha256 = if (manifestUtf8 != null) {
            extractPayloadSha256FromJson(manifestUtf8) ?: "unknown"
        } else {
            // Group by totalPayloadLength + chunkCount as proxy
            "$totalPayloadLength-$chunkCount"
        }

        return OcwmChunk(
            formatVersion = formatVersion,
            chunkIndex = chunkIndex,
            chunkCount = chunkCount,
            totalPayloadLength = totalPayloadLength,
            manifestUtf8 = manifestUtf8,
            payloadSha256 = payloadSha256,
            payloadSlice = payloadSlice
        )
    }

    private fun extractPayloadSha256FromJson(json: String): String? {
        val key = "\"payloadSha256\":\""
        val start = json.indexOf(key)
        if (start < 0) return null
        val valueStart = start + key.length
        val valueEnd = json.indexOf('"', valueStart)
        if (valueEnd < 0) return null
        return json.substring(valueStart, valueEnd)
    }

    private fun matchesMagic(data: ByteArray, offset: Int): Boolean {
        if (offset + 5 > data.size) return false
        for (j in 0 until 5) {
            if (data[offset + j] != OCWM_MAGIC[j]) return false
        }
        return true
    }

    private fun findSosIndex(jpeg: ByteArray): Int {
        var i = 2 // skip SOI
        while (i < jpeg.size - 1) {
            if (jpeg[i] != 0xFF.toByte()) { i++; continue }
            val marker = jpeg[i + 1]
            if (marker == 0xDA.toByte()) return i // SOS
            if (i + 3 >= jpeg.size) break
            val segLen = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            i += 2 + segLen
        }
        return -1
    }

    private fun putInt(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    private fun putLong(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = ((value shr 56) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 48) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 40) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 32) and 0xFF).toByte()
        buf[offset + 4] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 5] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 6] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 7] = (value and 0xFF).toByte()
    }

    private fun getInt(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 24) or
        ((buf[offset + 1].toInt() and 0xFF) shl 16) or
        ((buf[offset + 2].toInt() and 0xFF) shl 8) or
        (buf[offset + 3].toInt() and 0xFF)

    private fun getLong(buf: ByteArray, offset: Int): Long =
        ((buf[offset].toLong() and 0xFF) shl 56) or
        ((buf[offset + 1].toLong() and 0xFF) shl 48) or
        ((buf[offset + 2].toLong() and 0xFF) shl 40) or
        ((buf[offset + 3].toLong() and 0xFF) shl 32) or
        ((buf[offset + 4].toLong() and 0xFF) shl 24) or
        ((buf[offset + 5].toLong() and 0xFF) shl 16) or
        ((buf[offset + 6].toLong() and 0xFF) shl 8) or
        (buf[offset + 7].toLong() and 0xFF)

    private fun sortedByChunkIndex(chunks: List<OcwmChunk>): List<OcwmChunk> =
        chunks.sortedBy { it.chunkIndex }
}
