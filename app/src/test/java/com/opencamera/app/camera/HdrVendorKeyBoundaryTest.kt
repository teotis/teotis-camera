package com.opencamera.app.camera

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Static guard that no private HDR vendor key is written into CaptureRequest
 * in runtime code. This test scans production Kotlin source files for
 * vendor-key write patterns that would indicate accidental HDR vendor-key
 * runtime enablement.
 */
class HdrVendorKeyBoundaryTest {

    companion object {
        /**
         * Forbidden HDR vendor-key prefixes. These are private HAL keys whose
         * request/result semantics are undocumented; writing them into
         * CaptureRequest risks undefined behavior.
         */
        private val forbiddenKeyPrefixes = listOf(
            "com.mediatek.hdr",
            "com.mediatek.control.capture.hdr",
            "vivo.control.hdr",
            "vivo.control.rawhdr",
            "vivo.control.llhdr",
            "vivo.control.shdr",
            "vivo.control.EnableDCGHDR",
            "vivo.parameter.rawHDR",
            "vivo.parameter.bandingHDR",
            "com.vivo.CaptureHDRType",
            "vivo.feedback.hdr",
            "vivo.feedback.AlgoRAWHDR",
        )

        /**
         * Patterns that indicate a runtime CaptureRequest write (as opposed
         * to probe/diagnostic reads).
         */
        private val runtimeWritePatterns = listOf(
            "CaptureRequest.Builder",
            ".set(",
            "setCaptureRequestOption",
            "captureRequest.set",
            "sessionConfiguration",
        )
    }

    @Test
    fun `no private HDR vendor key is written in runtime request code`() {
        val sourceRoots = findSourceRoots()
        assertTrue(sourceRoots.isNotEmpty(), "No production source roots found")

        val violations = mutableListOf<Violation>()

        for (root in sourceRoots) {
            if (!root.isDirectory) continue
            val ktFiles = root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name.endsWith("Test.kt").not() }
                .toList()

            for (file in ktFiles) {
                val lines = file.readLines()
                for ((index, line) in lines.withIndex()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue

                    val isWriteLine = runtimeWritePatterns.any { trimmed.contains(it) }
                    if (!isWriteLine) continue

                    for (prefix in forbiddenKeyPrefixes) {
                        if (trimmed.contains(prefix, ignoreCase = true)) {
                            violations += Violation(
                                file = file.path,
                                line = index + 1,
                                key = prefix,
                                content = trimmed
                            )
                        }
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            buildString {
                appendLine("HDR vendor-key runtime write violations detected:")
                for (v in violations) {
                    appendLine("  ${v.file}:${v.line} — key prefix '${v.key}'")
                    appendLine("    ${v.content}")
                }
                appendLine()
                appendLine("Private vendor HDR keys must not be written into CaptureRequest.")
                appendLine("See docs/camera/hdr-vendor-evidence-boundary.md")
            }
        )
    }

    @Test
    fun `forbidden key prefixes are documented and non-empty`() {
        assertTrue(
            forbiddenKeyPrefixes.isNotEmpty(),
            "Forbidden key prefix list must not be empty"
        )
        assertTrue(
            forbiddenKeyPrefixes.all { it.isNotBlank() },
            "All forbidden key prefixes must be non-blank"
        )
    }

    private data class Violation(
        val file: String,
        val line: Int,
        val key: String,
        val content: String
    )

    private fun findSourceRoots(): List<File> {
        val roots = mutableListOf<File>()

        // Try to find source roots relative to the classpath
        val classLoader = javaClass.classLoader
        val resources = classLoader.getResources("")
        while (resources.hasMoreElements()) {
            val url = resources.nextElement()
            if (url.protocol == "file") {
                val classDir = File(url.toURI())
                // Navigate up from build output to find src/main/java
                var dir = classDir
                repeat(6) {
                    dir = dir.parentFile ?: return@repeat
                }
                if (dir.isDirectory) {
                    val srcMain = File(dir, "app/src/main/java")
                    if (srcMain.isDirectory) roots += srcMain
                }
            }
        }

        // Fallback: check common locations from the project root
        if (roots.isEmpty()) {
            val userDir = System.getProperty("user.dir") ?: "."
            val projectRoot = File(userDir)
            val candidates = listOf(
                File(projectRoot, "app/src/main/java"),
                File(projectRoot, "../app/src/main/java"),
                File(projectRoot, "../../app/src/main/java"),
            )
            for (candidate in candidates) {
                if (candidate.isDirectory) {
                    roots += candidate
                    break
                }
            }
        }

        return roots
    }
}
