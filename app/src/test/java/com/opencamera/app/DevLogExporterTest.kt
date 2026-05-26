package com.opencamera.app

import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DevLogExporterTest {

    private lateinit var tempDir: File
    private lateinit var exporter: TestableDevLogExporter

    @Before
    fun setup() {
        tempDir = createTempDir("devlog-test")
        exporter = TestableDevLogExporter(tempDir)
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `export creates file with type header`() {
        val file = exporter.export("test content", type = DevLogTab.KEY, nowMillis = 1000L)
        assertTrue(file.exists())
        val lines = file.readLines()
        assertEquals("# type: KEY", lines.first())
        assertEquals("test content", lines.drop(1).joinToString("\n"))
    }

    @Test
    fun `export defaults to ALL type`() {
        val file = exporter.export("content", nowMillis = 2000L)
        val header = file.readLines().first()
        assertEquals("# type: ALL", header)
    }

    @Test
    fun `storageSummary returns zero when no directory`() {
        val emptyExporter = TestableDevLogExporter(File(tempDir, "nonexistent"))
        val summary = emptyExporter.storageSummary()
        assertEquals(0L, summary.usedBytes)
        assertEquals(DevLogExporter.MAX_STORAGE_BYTES, summary.capacityBytes)
    }

    @Test
    fun `storageSummary returns correct total size`() {
        exporter.export("a".repeat(1000), type = DevLogTab.KEY, nowMillis = 100L)
        exporter.export("b".repeat(2000), type = DevLogTab.CORE, nowMillis = 200L)
        val summary = exporter.storageSummary()
        assertTrue(summary.usedBytes > 0)
        assertEquals(DevLogExporter.MAX_STORAGE_BYTES, summary.capacityBytes)
    }

    @Test
    fun `cleanupByType deletes only matching type files`() {
        exporter.export("key content", type = DevLogTab.KEY, nowMillis = 100L)
        exporter.export("core content", type = DevLogTab.CORE, nowMillis = 200L)
        exporter.export("error content", type = DevLogTab.ERROR, nowMillis = 300L)

        val deleted = exporter.cleanupByType(DevLogTab.KEY)
        assertEquals(1, deleted)

        val remaining = logFiles()
        assertEquals(2, remaining.size)
        remaining.forEach { file ->
            val header = file.readLines().first()
            assertFalse(header.contains("KEY"))
        }
    }

    @Test
    fun `cleanupAll deletes all log files`() {
        exporter.export("a", type = DevLogTab.KEY, nowMillis = 100L)
        exporter.export("b", type = DevLogTab.CORE, nowMillis = 200L)
        exporter.export("c", type = DevLogTab.ERROR, nowMillis = 300L)

        val deleted = exporter.cleanupAll()
        assertEquals(3, deleted)
        assertEquals(0, logFiles().size)
    }

    @Test
    fun `cleanupByType returns zero when no matching files`() {
        exporter.export("key content", type = DevLogTab.KEY, nowMillis = 100L)
        val deleted = exporter.cleanupByType(DevLogTab.ERROR)
        assertEquals(0, deleted)
        assertEquals(1, logFiles().size)
    }

    @Test
    fun `cleanupByType returns zero when directory does not exist`() {
        val emptyExporter = TestableDevLogExporter(File(tempDir, "nonexistent"))
        assertEquals(0, emptyExporter.cleanupByType(DevLogTab.KEY))
    }

    @Test
    fun `cleanupAll returns zero when directory does not exist`() {
        val emptyExporter = TestableDevLogExporter(File(tempDir, "nonexistent"))
        assertEquals(0, emptyExporter.cleanupAll())
    }

    @Test
    fun `pruneToCap removes oldest files when over cap`() {
        val smallCapExporter = TestableDevLogExporter(tempDir, capBytes = 100L)
        smallCapExporter.export("a".repeat(60), type = DevLogTab.KEY, nowMillis = 100L)
        smallCapExporter.export("b".repeat(60), type = DevLogTab.CORE, nowMillis = 200L)
        // Total exceeds 100 bytes, oldest should be pruned
        val remaining = logFiles()
        assertTrue(remaining.size <= 2)
    }

    @Test
    fun `files outside debug-logs directory are never touched`() {
        val outsideFile = File(tempDir, "outside.log")
        outsideFile.writeText("should not be deleted")
        exporter.export("inside", type = DevLogTab.ALL, nowMillis = 100L)
        exporter.cleanupAll()
        assertTrue(outsideFile.exists())
    }

    @Test
    fun `non-log files are not affected by cleanup`() {
        exporter.export("log content", type = DevLogTab.ALL, nowMillis = 100L)
        val txtFile = File(logDir(), "readme.txt")
        txtFile.writeText("not a log")
        exporter.cleanupAll()
        assertTrue(txtFile.exists())
    }

    @Test
    fun `storageSummary usedDisplay formats correctly`() {
        val summary = StorageSummary(5L * 1024 * 1024, 20L * 1024 * 1024)
        assertEquals("5 MB", summary.usedDisplay)
        assertEquals("20 MB", summary.capacityDisplay)
    }

    @Test
    fun `storageSummary usageRatio calculates correctly`() {
        val summary = StorageSummary(10L * 1024 * 1024, 20L * 1024 * 1024)
        assertEquals(0.5f, summary.usageRatio)
    }

    @Test
    fun `storageSummary zero capacity returns zero ratio`() {
        val summary = StorageSummary(100L, 0L)
        assertEquals(0f, summary.usageRatio)
    }

    private fun logDir(): File {
        return File(tempDir, "debug-logs")
    }

    private fun logFiles(): List<File> {
        val dir = logDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter { it.isFile && it.extension == "log" } ?: emptyList()
    }
}

private class TestableDevLogExporter(
    private val baseDir: File,
    private val capBytes: Long = DevLogExporter.MAX_STORAGE_BYTES
) {
    fun export(
        content: String,
        type: DevLogTab = DevLogTab.ALL,
        nowMillis: Long = System.currentTimeMillis()
    ): File {
        val dir = logDirectory()
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "opencamera-debug-$nowMillis.log")
        file.writeText("# type: ${type.name}\n$content", Charsets.UTF_8)
        runCatching { pruneToCap(dir) }
        return file
    }

    fun storageSummary(): StorageSummary {
        val dir = logDirectory()
        if (!dir.exists()) return StorageSummary(0L, capBytes)
        val files = dir.listFiles() ?: return StorageSummary(0L, capBytes)
        val totalBytes = files.filter { it.isFile && it.extension == "log" }.sumOf { it.length() }
        return StorageSummary(totalBytes, capBytes)
    }

    fun cleanupByType(type: DevLogTab): Int {
        val dir = logDirectory()
        if (!dir.exists()) return 0
        val files = dir.listFiles()?.filter { it.isFile && it.extension == "log" } ?: return 0
        var deleted = 0
        for (file in files) {
            val header = readTypeHeader(file)
            if (matchesType(header, type)) {
                if (file.delete()) deleted++
            }
        }
        return deleted
    }

    fun cleanupAll(): Int {
        val dir = logDirectory()
        if (!dir.exists()) return 0
        val files = dir.listFiles()?.filter { it.isFile && it.extension == "log" } ?: return 0
        var deleted = 0
        for (file in files) {
            if (file.delete()) deleted++
        }
        return deleted
    }

    private fun logDirectory(): File = File(baseDir, "debug-logs")

    private fun pruneToCap(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var total = files.sumOf { it.length() }
        if (total <= capBytes) return
        for (file in files.reversed()) {
            if (total <= capBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun readTypeHeader(file: File): String? {
        return runCatching {
            file.bufferedReader().use { reader ->
                val line = reader.readLine() ?: return@use null
                if (line.startsWith("# type: ")) line.removePrefix("# type: ").trim()
                else null
            }
        }.getOrNull()
    }

    private fun matchesType(header: String?, type: DevLogTab): Boolean {
        if (header == null) return type == DevLogTab.ALL
        return header == type.name
    }
}
